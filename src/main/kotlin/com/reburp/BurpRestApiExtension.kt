package com.reburp

import burp.api.montoya.BurpExtension
import burp.api.montoya.MontoyaApi
import com.reburp.routes.deregisterAllObservers
import java.net.BindException

class BurpRestApiExtension : BurpExtension {

    private var server: RestApiServer? = null

    override fun initialize(api: MontoyaApi) {
        api.extension().setName("reburp")

        val port = 9090
        val activityLog = ActivityLogTab(api)
        api.userInterface().registerSuiteTab("reburp", activityLog.panel)

        // Start the REST server behind a guard. A failure here (most commonly the port
        // already being in use by another Burp with reburp loaded) must not surface as an
        // unhandled stack trace: log a clear message and leave the extension loaded but idle.
        val instance = RestApiServer(api, port, activityLog)
        server = instance
        try {
            instance.start()
        } catch (e: Throwable) {
            runCatching { instance.stop() }
            server = null
            val portInUse = generateSequence(e) { it.cause }.any {
                it is BindException || it.message?.contains("Address already in use", ignoreCase = true) == true
            }
            val msg = if (portInUse) {
                // Name the version already serving when we can reach it. A reload that fails
                // to bind leaves the previous build answering, which looks exactly like a
                // successful reload until someone compares versions.
                // Short timeouts: this runs on Burp's extension-loading thread, so a socket
                // that accepts but never answers must not hang the load.
                val holder = runCatching {
                    val conn = java.net.URL("http://127.0.0.1:$port/api/status").openConnection()
                    conn.connectTimeout = 1000
                    conn.readTimeout = 1000
                    conn.getInputStream().use {
                        Regex("\"extension_version\"\\s*:\\s*\"([^\"]+)\"")
                            .find(it.readBytes().decodeToString())?.groupValues?.get(1)
                    }
                }.getOrNull()
                if (holder != null)
                    "reburp $REBURP_VERSION could not start: port $port is already served by reburp " +
                        "$holder, which is still loaded. Unload that extension in Burp's Extensions tab " +
                        "(untick it) and load this build again, otherwise Burp keeps answering with $holder."
                else
                    "reburp $REBURP_VERSION could not start: port $port is already in use. Close whatever " +
                        "holds it (often another Burp instance with reburp loaded), then reload the extension."
            } else {
                "reburp failed to start: ${e.message ?: e.javaClass.simpleName}"
            }
            api.logging().logToError(msg)
            api.logging().logToOutput(msg)
            return
        }

        api.logging().logToOutput("reburp $REBURP_VERSION listening on http://localhost:$port")
        api.logging().logToOutput("API docs:              http://localhost:$port/docs")

        api.extension().registerUnloadingHandler {
            // Detach the event observers before stopping the server. An observer left
            // registered would keep capturing into a buffer no one can read once the REST
            // server is gone. Both steps are guarded so a failure in one still runs the other
            // and unloading never throws back into Burp.
            runCatching { deregisterAllObservers() }
            runCatching { server?.stop() }
        }
    }
}
