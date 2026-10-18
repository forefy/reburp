package com.reburp.routes

import burp.api.montoya.MontoyaApi
import com.reburp.ErrorResponse
import com.reburp.skipLogAttr
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.awt.Component
import java.awt.Container
import java.awt.Frame
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.swing.AbstractButton
import javax.swing.JTabbedPane
import javax.swing.JTable
import javax.swing.SwingUtilities

@Serializable
data class UiTabsResponse(val tabs: List<String>, val selected: Int, val selected_title: String?)

@Serializable
data class UiSelectTabRequest(val name: String? = null, val index: Int? = null)

@Serializable
data class UiClickButtonRequest(val text: String)

@Serializable
data class UiSelectRowRequest(val index: Int? = null, val last: Boolean = false)

@Serializable
data class UiActionResponse(val ok: Boolean, val message: String, val matched: String? = null)

/** Raised inside an EDT block when a requested element isn't present; mapped to 404. */
private class UiNotFound(message: String) : RuntimeException(message)

/**
 * Drive Burp's own Swing UI from inside the JVM - no OS accessibility, no synthetic input, works
 * even when the window is minimized or on another Space. `suiteFrame()` is the one officially
 * exposed handle; everything else is reached by walking its component tree, so treat tab/button
 * targeting as best-effort against Burp's (unsupported) internal layout.
 */
fun Routing.uiRoutes(api: MontoyaApi) {

    fun suiteFrame(): Frame? = runCatching { api.userInterface().swingUtils().suiteFrame() }.getOrNull()

    // Depth-first walk of the Swing hierarchy.
    fun walk(c: Component): Sequence<Component> = sequence {
        yield(c)
        if (c is Container) for (child in c.components) yieldAll(walk(child))
    }

    // Burp's main tab bar is the JTabbedPane that carries a "Dashboard" tab; fall back to the
    // largest tabbed pane so this still answers if that title ever changes.
    fun mainTabbedPane(frame: Frame): JTabbedPane? {
        val panes = walk(frame).filterIsInstance<JTabbedPane>().toList()
        return panes.firstOrNull { tp ->
            (0 until tp.tabCount).any { tp.getTitleAt(it).equals("Dashboard", ignoreCase = true) }
        } ?: panes.maxByOrNull { it.tabCount }
    }

    // Run a block on the Event Dispatch Thread and return its result. Ktor handlers run on Netty
    // threads, never the EDT, so invokeAndWait is safe and keeps Swing single-threaded. Note: do
    // not call ktor's suspend `respond` inside here - compute a value and respond after.
    fun <T> onEdt(block: () -> T): T {
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    suspend fun io.ktor.server.application.ApplicationCall.failFrom(e: Throwable) {
        val code = if (e is UiNotFound) HttpStatusCode.NotFound else HttpStatusCode.InternalServerError
        respond(code, ErrorResponse(e.message ?: "Error"))
    }

    route("/api/ui") {

        // ── Screenshot the whole Burp window (PNG) ────────────────────────────
        get("/screenshot") {
            call.attributes.put(skipLogAttr, true) // don't log raw PNG bytes into the activity log
            val frame = suiteFrame()
                ?: return@get call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Burp UI frame not available"))
            runCatching {
                val w = frame.width.coerceAtLeast(1)
                val h = frame.height.coerceAtLeast(1)
                val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
                onEdt {
                    val g = img.createGraphics()
                    // printAll renders the live component hierarchy off-screen, so it works even
                    // when the window is minimized, occluded, or on another macOS Space.
                    frame.printAll(g)
                    g.dispose()
                }
                val baos = ByteArrayOutputStream()
                ImageIO.write(img, "png", baos)
                baos.toByteArray()
            }.onSuccess { call.respondBytes(it, ContentType.Image.PNG) }
                .onFailure { call.respond(HttpStatusCode.InternalServerError, ErrorResponse(it.message ?: "Screenshot failed")) }
        }

        // ── List the main tabs ────────────────────────────────────────────────
        get("/tabs") {
            val frame = suiteFrame()
                ?: return@get call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Burp UI frame not available"))
            runCatching {
                onEdt {
                    val tp = mainTabbedPane(frame) ?: throw UiNotFound("Main tab bar not found")
                    val titles = (0 until tp.tabCount).map { tp.getTitleAt(it) }
                    UiTabsResponse(titles, tp.selectedIndex, titles.getOrNull(tp.selectedIndex))
                }
            }.onSuccess { call.respond(it) }.onFailure { call.failFrom(it) }
        }

        // ── Select a top-level tab by name (substring, case-insensitive) or index ──
        post("/select-tab") {
            val req = runCatching { call.receive<UiSelectTabRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            val frame = suiteFrame()
                ?: return@post call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Burp UI frame not available"))
            runCatching {
                onEdt {
                    val tp = mainTabbedPane(frame) ?: throw UiNotFound("Main tab bar not found")
                    val idx = when {
                        req.index != null -> req.index
                        req.name != null -> (0 until tp.tabCount).firstOrNull {
                            tp.getTitleAt(it).contains(req.name, ignoreCase = true)
                        } ?: -1
                        else -> -1
                    }
                    if (idx < 0 || idx >= tp.tabCount) throw UiNotFound("No tab matches ${req.name ?: req.index}")
                    tp.selectedIndex = idx
                    UiActionResponse(true, "Selected tab", tp.getTitleAt(idx))
                }
            }.onSuccess { call.respond(it) }.onFailure { call.failFrom(it) }
        }

        // ── Click a button by its visible text (substring, case-insensitive) ──
        post("/click-button") {
            val req = runCatching { call.receive<UiClickButtonRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            val frame = suiteFrame()
                ?: return@post call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Burp UI frame not available"))
            runCatching {
                onEdt {
                    val btn = walk(frame)
                        .filterIsInstance<AbstractButton>()
                        .filter { it.isShowing && it.isEnabled }
                        .firstOrNull { it.text != null && it.text.contains(req.text, ignoreCase = true) }
                        ?: throw UiNotFound("No visible, enabled button matches '${req.text}'")
                    btn.doClick()
                    UiActionResponse(true, "Clicked button", btn.text)
                }
            }.onSuccess { call.respond(it) }.onFailure { call.failFrom(it) }
        }

        // ── Select a row in the current tab's main table (by index, or the last/newest) ──
        // Targets the largest visible table, which is the content table of the open tab
        // (e.g. Collaborator interactions, Proxy HTTP history). Selecting a row drives the
        // detail panes below it, so a follow-up /screenshot shows that row's request/response.
        post("/select-row") {
            val req = runCatching { call.receive<UiSelectRowRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            val frame = suiteFrame()
                ?: return@post call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Burp UI frame not available"))
            runCatching {
                onEdt {
                    val table = walk(frame)
                        .filterIsInstance<JTable>()
                        .filter { it.isShowing && it.rowCount > 0 }
                        .maxByOrNull { it.rowCount }
                        ?: throw UiNotFound("No visible table with rows in the current tab")
                    val row = when {
                        req.last -> table.rowCount - 1
                        req.index != null -> req.index
                        else -> -1
                    }
                    if (row < 0 || row >= table.rowCount) throw UiNotFound("Row $row out of range (0..${table.rowCount - 1})")
                    val rect = table.getCellRect(row, 0, true)
                    table.scrollRectToVisible(rect)
                    table.setRowSelectionInterval(row, row)
                    // Burp wires row detail panes to the table's MouseListeners, not the selection
                    // model, so a programmatic selection alone highlights the row without updating
                    // the detail. Dispatch a real click on the cell so those listeners fire.
                    val x = rect.x + 5
                    val y = rect.y + rect.height / 2
                    val now = System.currentTimeMillis()
                    fun click(id: Int) = table.dispatchEvent(
                        java.awt.event.MouseEvent(table, id, now, 0, x, y, 1, false, java.awt.event.MouseEvent.BUTTON1)
                    )
                    click(java.awt.event.MouseEvent.MOUSE_PRESSED)
                    click(java.awt.event.MouseEvent.MOUSE_RELEASED)
                    click(java.awt.event.MouseEvent.MOUSE_CLICKED)
                    UiActionResponse(true, "Selected row", "row $row of ${table.rowCount}")
                }
            }.onSuccess { call.respond(it) }.onFailure { call.failFrom(it) }
        }
    }
}
