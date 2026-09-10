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
                "reburp could not start: port $port is already in use. Close whatever holds it " +
                    "(often another Burp instance with reburp loaded), then reload the extension."
            } else {
                "reburp failed to start: ${e.message ?: e.javaClass.simpleName}"
            }
            api.logging().logToError(msg)
            api.logging().logToOutput(msg)
            return
        }

        api.logging().logToOutput("reburp listening on http://localhost:$port")
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
