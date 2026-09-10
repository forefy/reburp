package com.reburp.routes

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.message.requests.HttpRequest
import com.reburp.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.awt.Component
import java.awt.Container
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.JTabbedPane
import javax.swing.SwingUtilities

// Iteratively finds the first component matching type T and predicate (BFS)
private inline fun <reified T : Component> findComponent(root: Component, crossinline predicate: (T) -> Boolean): T? {
    val queue = ArrayDeque<Component>()
    queue.add(root)
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        if (current is T && predicate(current)) return current
        if (current is Container) current.components.forEach { queue.add(it) }
    }
    return null
}

// Locates the inner JTabbedPane inside the Repeater tool panel
private fun findRepeaterTabsPane(api: MontoyaApi): JTabbedPane? {
    val frame = api.userInterface().swingUtils().suiteFrame()
    // Find the top-level suite JTabbedPane (has a "Repeater" tab)
    val suiteTabs = findComponent<JTabbedPane>(frame) { tp ->
        (0 until tp.tabCount).any { tp.getTitleAt(it).equals("Repeater", ignoreCase = true) }
    } ?: return null
    val repeaterIdx = (0 until suiteTabs.tabCount)
        .firstOrNull { suiteTabs.getTitleAt(it).equals("Repeater", ignoreCase = true) } ?: return null
    val repeaterPanel = suiteTabs.getComponentAt(repeaterIdx) ?: return null
    // The first JTabbedPane inside the Repeater panel holds the individual request tabs
    return findComponent<JTabbedPane>(repeaterPanel) { true }
}

// [Montoya API] - api.repeater() and api.intruder()
fun Routing.repeaterIntruderRoutes(api: MontoyaApi) {

    // ── Repeater ──────────────────────────────────────────────────────────────

    /**
     * Sends a request to the Burp Repeater tab.
     * The request appears immediately in the Repeater UI - you still need to click Send in Burp.
     */
    post("/api/repeater/send") {
        val req = runCatching { call.receive<SendToRepeaterRequest>() }.getOrElse {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Invalid request body"))
        }
        val httpReq = runCatching {
            HttpRequest.httpRequest(normalizeRequest(req.raw_request))
        }.getOrElse {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Failed to parse raw_request: ${it.message}. Ensure the request includes a valid HTTP first line and a Host header.")
            )
        }
        runCatching {
            if (req.tab_name != null) api.repeater().sendToRepeater(httpReq, req.tab_name)
            else api.repeater().sendToRepeater(httpReq)
        }.onFailure {
            return@post call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("sendToRepeater failed: ${it.message}")
            )
        }
        call.respond(MessageResponse("Request sent to Repeater${req.tab_name?.let { " (tab: '$it')" } ?: ""}. Open the Repeater tab in Burp to review and send."))
    }

    /**
     * Closes all tabs in Burp Repeater by traversing the Swing component tree.
     * The last tab (the "+" placeholder) is preserved so Repeater remains functional.
     */
    delete("/api/repeater/tabs") {
        var removed = 0
        var errorMsg: String? = null
        val latch = CountDownLatch(1)
        SwingUtilities.invokeLater {
            try {
                val pane = findRepeaterTabsPane(api)
                if (pane == null) {
                    errorMsg = "Could not locate Repeater tabs pane - make sure the Repeater tab has been opened in Burp at least once"
                } else {
                    // Keep the last tab (the '+' button); remove all others from front
                    while (pane.tabCount > 1) {
                        pane.removeTabAt(0)
                        removed++
                    }
                }
            } catch (e: Exception) {
                errorMsg = e.message
            } finally {
                latch.countDown()
            }
        }
        latch.await(5, TimeUnit.SECONDS)
        if (errorMsg != null) {
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(errorMsg!!))
        } else {
            call.respond(MessageResponse("Cleared $removed Repeater tab(s)"))
        }
    }

    // ── Intruder ──────────────────────────────────────────────────────────────

    /**
     * Sends a request to the Burp Intruder tab.
     * Mark injection points with § symbols in raw_request (e.g. "username=§admin§").
     * After sending, configure the attack type and payload sets in the Burp Intruder UI.
     */
    post("/api/intruder/send") {
        val req = runCatching { call.receive<SendToIntruderRequest>() }.getOrElse {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Invalid request body"))
        }
        val httpReq = runCatching {
            HttpRequest.httpRequest(normalizeRequest(req.raw_request))
        }.getOrElse {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Failed to parse raw_request: ${it.message}. Ensure the request includes a valid HTTP first line and a Host header.")
            )
        }
        runCatching {
            if (req.tab_name != null) api.intruder().sendToIntruder(httpReq, req.tab_name)
            else api.intruder().sendToIntruder(httpReq)
        }.onFailure {
            return@post call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("sendToIntruder failed: ${it.message}")
            )
        }
        call.respond(MessageResponse("Request sent to Intruder${req.tab_name?.let { " (tab: '$it')" } ?: ""}. Configure attack type and payloads in the Burp Intruder UI, then start the attack."))
    }
}
