package com.reburp.routes

import burp.api.montoya.MontoyaApi
import com.reburp.ErrorResponse
import com.reburp.skipLogAttr
import io.ktor.http.*
import io.ktor.server.application.*
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
import javax.swing.JComponent
import javax.swing.JTabbedPane
import javax.swing.JTable
import javax.swing.SwingUtilities

@Serializable
data class UiTabGroup(val tabs: List<String>, val selected: Int, val selected_title: String?)

@Serializable
data class UiTabsResponse(
    val tabs: List<String>,
    val selected: Int,
    val selected_title: String?,
    /** Tab bars nested inside the open tab, e.g. an extension's own sub-tabs, in tree order. */
    val nested: List<UiTabGroup> = emptyList()
)

@Serializable
data class UiSelectTabRequest(val name: String? = null, val index: Int? = null)

@Serializable
data class UiClickButtonRequest(val text: String)

@Serializable
data class UiSelectRowRequest(val index: Int? = null, val last: Boolean = false)

@Serializable
data class UiActionResponse(val ok: Boolean, val message: String, val matched: String? = null)

/** Raised inside an EDT block when a requested element isn't present; mapped to 404. */
internal class UiNotFound(message: String) : RuntimeException(message)

/** Raised inside an EDT block when the element exists but cannot take the requested action; mapped to 400. */
internal class UiBadTarget(message: String) : RuntimeException(message)

internal fun suiteFrame(api: MontoyaApi): Frame? =
    runCatching { api.userInterface().swingUtils().suiteFrame() }.getOrNull()

/** Depth-first walk of the Swing hierarchy. */
internal fun walkTree(c: Component): Sequence<Component> = sequence {
    yield(c)
    if (c is Container) for (child in c.components) yieldAll(walkTree(child))
}

/**
 * Run a block on the Event Dispatch Thread and return its result. Ktor handlers run on Netty
 * threads, never the EDT, so invokeAndWait is safe and keeps Swing single-threaded. Do not call
 * ktor's suspend `respond` inside here - compute a value and respond after.
 */
internal fun <T> onEdt(block: () -> T): T {
    var result: Result<T>? = null
    SwingUtilities.invokeAndWait { result = runCatching(block) }
    return result!!.getOrThrow()
}

internal suspend fun ApplicationCall.failFrom(e: Throwable) {
    val code = when (e) {
        is UiNotFound -> HttpStatusCode.NotFound
        is UiBadTarget -> HttpStatusCode.BadRequest
        else -> HttpStatusCode.InternalServerError
    }
    respond(code, ErrorResponse(e.message ?: "Error"))
}

/**
 * Drive Burp's own Swing UI from inside the JVM - no OS accessibility, no synthetic input, works
 * even when the window is minimized or on another Space. `suiteFrame()` is the one officially
 * exposed handle; everything else is reached by walking its component tree, so treat tab/button
 * targeting as best-effort against Burp's (unsupported) internal layout.
 */
fun Routing.uiRoutes(api: MontoyaApi) {

    fun suiteFrame(): Frame? = suiteFrame(api)
    fun walk(c: Component): Sequence<Component> = walkTree(c)

    // Burp's main tab bar is the JTabbedPane that carries a "Dashboard" tab; fall back to the
    // largest tabbed pane so this still answers if that title ever changes.
    fun mainTabbedPane(frame: Frame): JTabbedPane? {
        val panes = walk(frame).filterIsInstance<JTabbedPane>().toList()
        return panes.firstOrNull { tp ->
            (0 until tp.tabCount).any { tp.getTitleAt(it).equals("Dashboard", ignoreCase = true) }
        } ?: panes.maxByOrNull { it.tabCount }
    }

    // Tab bars other than the main one that are currently showing: an extension's own sub-tabs,
    // Repeater's request tabs, and so on. Hidden panes (inside unselected tabs) are skipped so a
    // name only resolves against what the user can see.
    fun nestedTabbedPanes(frame: Frame, main: JTabbedPane?): List<JTabbedPane> =
        walk(frame).filterIsInstance<JTabbedPane>().filter { it !== main && it.isShowing && it.tabCount > 0 }.toList()

    fun JTabbedPane.titles(): List<String> = (0 until tabCount).map { getTitleAt(it) }

    fun JTabbedPane.indexOfTitle(name: String): Int =
        (0 until tabCount).firstOrNull { getTitleAt(it).contains(name, ignoreCase = true) } ?: -1

    route("/api/ui") {

        // ── Screenshot the whole Burp window (PNG) ────────────────────────────
        // `window` picks another showing window (a dialog or popup, ids from /api/ui/windows);
        // `id` crops to one element (ids from /api/ui/elements).
        get("/screenshot") {
            call.attributes.put(skipLogAttr, true) // don't log raw PNG bytes into the activity log
            val frame = suiteFrame()
                ?: return@get call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Burp UI frame not available"))
            val windowId = call.request.queryParameters["window"]
            val elementId = call.request.queryParameters["id"]
            runCatching {
                onEdt {
                    val wins = showingWindows(frame)
                    val target: Component = when {
                        elementId != null -> resolveElement(elementId, wins)
                        windowId != null -> windowById(windowId, wins)
                        else -> frame
                    }
                    val win = SwingUtilities.getWindowAncestor(target) ?: target as? java.awt.Window ?: frame
                    val img = BufferedImage(win.width.coerceAtLeast(1), win.height.coerceAtLeast(1), BufferedImage.TYPE_INT_RGB)
                    val g = img.createGraphics()
                    // printAll renders the live component hierarchy off-screen, so it works even
                    // when the window is minimized, occluded, or on another macOS Space.
                    win.printAll(g)
                    g.dispose()
                    val out = if (target === win) img else {
                        val r = boundsInWindow(target, win)
                        val x = r.x.coerceIn(0, img.width - 1)
                        val y = r.y.coerceIn(0, img.height - 1)
                        img.getSubimage(x, y, r.width.coerceIn(1, img.width - x), r.height.coerceIn(1, img.height - y))
                    }
                    val baos = ByteArrayOutputStream()
                    ImageIO.write(out, "png", baos)
                    baos.toByteArray()
                }
            }.onSuccess { call.respondBytes(it, ContentType.Image.PNG) }
                .onFailure { call.failFrom(it) }
        }

        // ── List the main tabs ────────────────────────────────────────────────
        get("/tabs") {
            val frame = suiteFrame()
                ?: return@get call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Burp UI frame not available"))
            runCatching {
                onEdt {
                    val tp = mainTabbedPane(frame) ?: throw UiNotFound("Main tab bar not found")
                    val titles = tp.titles()
                    val nested = nestedTabbedPanes(frame, tp).map { pane ->
                        val t = pane.titles()
                        UiTabGroup(t, pane.selectedIndex, t.getOrNull(pane.selectedIndex))
                    }
                    UiTabsResponse(titles, tp.selectedIndex, titles.getOrNull(tp.selectedIndex), nested)
                }
            }.onSuccess { call.respond(it) }.onFailure { call.failFrom(it) }
        }

        // ── Select a tab by name (substring, case-insensitive) or index ──────────
        // The main tab bar is tried first. A name that matches nothing there falls back to the
        // tab bars currently showing inside the open tab (e.g. Autorize's Configuration), so
        // "Proxy" keeps meaning Burp's Proxy even if some extension also has a tab called that.
        // An index always refers to the main bar.
        post("/select-tab") {
            val req = runCatching { call.receive<UiSelectTabRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            val frame = suiteFrame()
                ?: return@post call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Burp UI frame not available"))
            runCatching {
                onEdt {
                    val main = mainTabbedPane(frame) ?: throw UiNotFound("Main tab bar not found")
                    var tp = main
                    var idx = when {
                        req.index != null -> req.index
                        req.name != null -> main.indexOfTitle(req.name)
                        else -> -1
                    }
                    if (idx < 0 && req.index == null && req.name != null) {
                        nestedTabbedPanes(frame, main).firstOrNull { it.indexOfTitle(req.name) >= 0 }?.let {
                            tp = it
                            idx = it.indexOfTitle(req.name)
                        }
                    }
                    if (idx < 0 || idx >= tp.tabCount) throw UiNotFound("No tab matches ${req.name ?: req.index}")
                    tp.selectedIndex = idx
                    UiActionResponse(true, if (tp === main) "Selected tab" else "Selected nested tab", tp.getTitleAt(idx))
                }
            }.onSuccess { call.respond(it) }.onFailure { call.failFrom(it) }
        }

        // ── Click a button by its visible text or tooltip (substring, case-insensitive) ──
        // Real buttons are pressed through their own action. Burp's toolbar icons are labels
        // with a tooltip and a mouse listener, so those get a real click instead.
        post("/click-button") {
            val req = runCatching { call.receive<UiClickButtonRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body"))
            }
            val frame = suiteFrame()
                ?: return@post call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Burp UI frame not available"))
            runCatching {
                onEdt {
                    val target = walk(frame).firstOrNull { matchesLabel(it, req.text) }
                        ?: throw UiNotFound("No visible, enabled button or tooltip matches '${req.text}'")
                    val label = (target as? AbstractButton)?.text?.takeIf { it.isNotBlank() }
                        ?: (target as? JComponent)?.toolTipText
                    if (target is AbstractButton) target.doClick()
                    else clickAt(target, target.width / 2, target.height / 2, "left", 1)
                    UiActionResponse(true, "Clicked button", label)
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
