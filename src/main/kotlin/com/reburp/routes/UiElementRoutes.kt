package com.reburp.routes

import burp.api.montoya.MontoyaApi
import com.reburp.ErrorResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.awt.Component
import java.awt.Container
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.Rectangle
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.AbstractButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPopupMenu
import javax.swing.JSlider
import javax.swing.JSpinner
import javax.swing.JTabbedPane
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.text.JTextComponent

@Serializable
data class UiBounds(val x: Int, val y: Int, val width: Int, val height: Int)

@Serializable
data class UiWindow(val id: String, val type: String, val title: String?, val bounds: UiBounds, val focused: Boolean)

@Serializable
data class UiWindowsResponse(val windows: List<UiWindow>)

@Serializable
data class UiElement(
    val id: String,
    val type: String,
    val text: String? = null,
    val name: String? = null,
    val tooltip: String? = null,
    val bounds: UiBounds,
    val enabled: Boolean,
    val showing: Boolean,
    val focused: Boolean,
    val items: List<String>? = null,
    val selected: Int? = null,
    val rows: Int? = null,
    val children: Int,
    /** The concrete class when it is not a standard Swing one (Burp's are obfuscated), e.g. `Zmel`. */
    val impl: String? = null,
    /** Whether this element reacts to mouse clicks itself: a button, or it has mouse listeners. */
    val clickable: Boolean = false
)

@Serializable
data class UiElementsResponse(val window: String, val count: Int, val truncated: Boolean, val elements: List<UiElement>)

@Serializable
data class UiClickRequest(
    val id: String? = null,
    val x: Int? = null,
    val y: Int? = null,
    val window: String? = null,
    val button: String = "left",
    val count: Int = 1,
    val modifiers: List<String> = emptyList(),
    /** With the id of a tree, table or list: the row to click, by index. */
    val row: Int? = null,
    /** With the id of a tree, table or list: the first row whose label contains this. */
    val row_text: String? = null
)

@Serializable
data class UiTypeRequest(val id: String, val text: String, val replace: Boolean = true, val enter: Boolean = false)

@Serializable
data class UiSelectRequest(val id: String, val text: String? = null, val index: Int? = null)

@Serializable
data class UiKeyRequest(val key: String, val id: String? = null, val modifiers: List<String> = emptyList())

// ── Element addressing ───────────────────────────────────────────────────────
// An element id is "w<window>:<child>/<child>/..." - the window's position in /api/ui/windows
// and the path of child indices from that window down to the component. Ids are stable while the
// layout does not change, which is the span between a /elements call and the action that uses it.

internal fun showingWindows(frame: Frame): List<Window> =
    listOf<Window>(frame) + Window.getWindows().filter { it !== frame && it.isShowing }

internal fun windowById(id: String, windows: List<Container>): Container {
    val idx = id.removePrefix("w").toIntOrNull() ?: throw UiBadTarget("Bad window id '$id', expected w<n>")
    return windows.getOrNull(idx) ?: throw UiNotFound("No showing window $id (have w0..w${windows.size - 1})")
}

internal fun resolveElement(id: String, windows: List<Container>): Component {
    val colon = id.indexOf(':')
    if (colon < 0) throw UiBadTarget("Bad element id '$id', expected w<n>:<path>")
    var c: Component = windowById(id.substring(0, colon), windows)
    val path = id.substring(colon + 1)
    if (path.isEmpty()) return c
    for (step in path.split('/')) {
        val i = step.toIntOrNull() ?: throw UiBadTarget("Bad element id '$id': '$step' is not a child index")
        val parent = c as? Container ?: throw UiNotFound("Element $id: ${c.javaClass.simpleName} has no children")
        c = parent.components.getOrNull(i) ?: throw UiNotFound("Element $id no longer exists (layout changed?)")
    }
    return c
}

internal fun boundsInWindow(c: Component, root: Container): Rectangle {
    if (c === root) return Rectangle(0, 0, root.width, root.height)
    val p = SwingUtilities.convertPoint(c, Point(0, 0), root)
    return Rectangle(p.x, p.y, c.width, c.height)
}

private fun isStandard(k: Class<*>): Boolean {
    val pkg = k.`package`?.name ?: return false
    return pkg.startsWith("javax.swing") || pkg.startsWith("java.awt")
}

/** The nearest javax.swing / java.awt ancestor's name, so Burp's obfuscated `Zmel` reads as `JLabel`. */
private fun typeName(c: Component): String {
    var k: Class<*> = c.javaClass
    while (!isStandard(k)) k = k.superclass ?: break
    return k.simpleName
}

/** The concrete class name when it is not itself a standard Swing class (and is not anonymous). */
private fun implName(c: Component): String? {
    var k: Class<*> = c.javaClass
    while (k.simpleName.isEmpty() || k.isAnonymousClass) k = k.superclass ?: break
    return k.simpleName.takeIf { !isStandard(k) }
}

internal fun listensForMouse(c: Component): Boolean =
    c is AbstractButton || c.mouseListeners.isNotEmpty() || c.mouseMotionListeners.isNotEmpty()

/**
 * What AWT itself would hand a mouse event to: the deepest component at the point, unless it
 * ignores mouse input, in which case the nearest ancestor that listens. Custom widgets such as
 * Burp's Send button are a label inside a panel, with the listener on one of the two; dispatching
 * to the wrong one is a silent no-op. Returns the target and the point in its coordinates.
 */
internal fun retargetForClick(from: Component, p: Point, root: Container): Pair<Component, Point> {
    var t = from
    var pt = p
    while (t !== root && !listensForMouse(t)) {
        val parent = t.parent ?: break
        pt = SwingUtilities.convertPoint(t, pt, parent)
        t = parent
    }
    return if (t === root && !listensForMouse(t)) from to p else t to pt
}

private fun textOf(c: Component): String? = when (c) {
    is AbstractButton -> c.text
    is JLabel -> c.text
    is JTextComponent -> c.text?.take(200)
    is JTabbedPane -> c.selectedIndex.takeIf { it >= 0 }?.let { c.getTitleAt(it) }
    is JComboBox<*> -> c.selectedItem?.toString()
    is JList<*> -> c.selectedValue?.toString()
    is Frame -> c.title
    is JDialog -> c.title
    else -> null
}?.takeIf { it.isNotEmpty() }

/** What each row of a tree, table or list shows, or null for anything else. */
internal fun rowLabels(c: Component): List<String>? = when (c) {
    is JTree -> (0 until c.rowCount).map { treeRowLabel(c, it) }
    is JTable -> (0 until c.rowCount).map { r ->
        (0 until c.columnCount).joinToString(" | ") { c.getValueAt(r, it)?.toString() ?: "" }
    }
    is JList<*> -> (0 until c.model.size).map { c.model.getElementAt(it).toString() }
    else -> null
}

/**
 * Pick a row of a tree, table or list by index or label, select it and scroll it into view, and
 * return its index, label and a point inside it in the component's coordinates. Right-clicking
 * a row used to need window coordinates, which land on something else once the window or its
 * panes are resized; Burp's context menus also act on the selection, so the row is selected first.
 */
internal fun targetRow(c: Component, index: Int?, text: String?): Triple<Int, String, Point> {
    val labels = rowLabels(c)
        ?: throw UiBadTarget("${typeName(c)} has no rows; row and row_text work on trees, tables and lists")
    val i = when {
        index != null -> index
        text != null -> labels.indexOfFirst { it.contains(text, ignoreCase = true) }
        else -> throw UiBadTarget("Give row or row_text")
    }
    if (i < 0 || i >= labels.size) throw UiNotFound("No row matches ${text ?: index} (${labels.size} rows)")
    val bounds = when (c) {
        is JTree -> { c.setSelectionRow(i); c.scrollRowToVisible(i); c.getRowBounds(i) }
        is JTable -> {
            // Never a checkbox column: in Burp's Extensions table column 0 is Loaded, and a
            // click there would unload the extension instead of picking the row.
            val col = (0 until c.columnCount).firstOrNull {
                c.getColumnClass(it) != java.lang.Boolean::class.java && c.getValueAt(i, it) !is Boolean
            } ?: throw UiBadTarget("Every column of this table is a checkbox, so a row click would toggle one")
            c.setRowSelectionInterval(i, i)
            c.getCellRect(i, col, true).also { c.scrollRectToVisible(it) }
        }
        is JList<*> -> { c.selectedIndex = i; c.ensureIndexIsVisible(i); c.getCellBounds(i, i) }
        else -> null
    } ?: throw UiNotFound("Row $i has no bounds (is it inside a collapsed branch?)")
    // A little in from the left edge, where the row's own label is, rather than its centre,
    // which for a wide table can be in a column that handles clicks differently.
    return Triple(i, labels[i], Point(bounds.x + minOf(12, bounds.width / 2), bounds.y + bounds.height / 2))
}

/** All text a rendered cell shows; renderers are often panels holding several labels. */
internal fun renderedText(c: Component): String? =
    textOf(c)?.takeIf { it.isNotBlank() }
        ?: (c as? Container)?.components?.mapNotNull { renderedText(it) }?.joinToString(" ")?.takeIf { it.isNotBlank() }

/**
 * What a tree row shows. Burp's site map nodes print as object ids (burp.Zv8w@3864a7fc), so
 * matching on toString() never found a host by name; ask the tree's renderer instead.
 */
internal fun treeRowLabel(tree: JTree, row: Int): String {
    val node = tree.getPathForRow(row).lastPathComponent
    val shown = runCatching {
        tree.cellRenderer.getTreeCellRendererComponent(tree, node, tree.isRowSelected(row),
            tree.isExpanded(row), tree.model.isLeaf(node), row, false)
    }.getOrNull()?.let(::renderedText)
    return shown ?: node.toString()
}

private fun itemsOf(c: Component): List<String>? = when (c) {
    is JComboBox<*> -> (0 until minOf(c.itemCount, 100)).map { c.getItemAt(it).toString() }
    is JList<*> -> (0 until minOf(c.model.size, 100)).map { c.model.getElementAt(it).toString() }
    is JTabbedPane -> (0 until c.tabCount).map { c.getTitleAt(it) }
    is JTree -> (0 until minOf(c.rowCount, 100)).map { treeRowLabel(c, it) }
    else -> null
}

private fun selectedOf(c: Component): Int? = when (c) {
    is JComboBox<*> -> c.selectedIndex
    is JList<*> -> c.selectedIndex
    is JTabbedPane -> c.selectedIndex
    is JTable -> c.selectedRow
    is JTree -> c.minSelectionRow
    else -> null
}

private fun rowsOf(c: Component): Int? = when (c) {
    is JTable -> c.rowCount
    is JList<*> -> c.model.size
    is JTree -> c.rowCount
    else -> null
}

/** The components an agent would act on; plain containers are noise unless `all` is asked for. */
internal fun interesting(c: Component): Boolean = when (c) {
    is AbstractButton, is JTextComponent, is JComboBox<*>, is JList<*>, is JTree, is JTable,
    is JTabbedPane, is JSpinner, is JSlider, is JPopupMenu, is Window -> true
    // Burp's editor toolbar icons (line wrapping, hide headers, non-printables) are labels with
    // no text, only a tooltip and a mouse listener; those are exactly what an agent wants to find.
    is JLabel -> !c.text.isNullOrBlank() || !c.toolTipText.isNullOrBlank() || listensForMouse(c)
    is JComponent -> !c.toolTipText.isNullOrBlank() && listensForMouse(c)
    else -> false
}

/** A showing, enabled component an agent can click by its label or tooltip. */
internal fun matchesLabel(c: Component, text: String): Boolean {
    if (!c.isShowing || !c.isEnabled) return false
    val label = (c as? AbstractButton)?.text ?: (c as? JLabel)?.text
    val tip = (c as? JComponent)?.toolTipText
    return (label?.contains(text, ignoreCase = true) == true || tip?.contains(text, ignoreCase = true) == true) &&
        (c is AbstractButton || listensForMouse(c))
}

internal fun describe(c: Component, id: String, root: Container): UiElement {
    val r = boundsInWindow(c, root)
    return UiElement(
        id = id,
        type = typeName(c),
        text = textOf(c),
        name = c.name?.takeIf { it.isNotEmpty() },
        tooltip = (c as? JComponent)?.toolTipText?.takeIf { it.isNotEmpty() },
        bounds = UiBounds(r.x, r.y, r.width, r.height),
        enabled = c.isEnabled,
        showing = c.isShowing,
        focused = c.isFocusOwner,
        items = itemsOf(c),
        selected = selectedOf(c),
        rows = rowsOf(c),
        children = (c as? Container)?.componentCount ?: 0,
        impl = implName(c),
        clickable = listensForMouse(c)
    )
}

// ── Synthetic input ──────────────────────────────────────────────────────────

private val popupOnPress = !System.getProperty("os.name", "").lowercase().contains("windows")

/** Click `target` at a point in its own coordinates with real press/release/click events. */
internal fun clickAt(target: Component, x: Int, y: Int, button: String, count: Int, extraMods: Int = 0) {
    val btn = when (button.lowercase()) {
        "left" -> MouseEvent.BUTTON1
        "middle" -> MouseEvent.BUTTON2
        "right" -> MouseEvent.BUTTON3
        else -> throw UiBadTarget("button must be left, middle or right")
    }
    val down = when (btn) {
        MouseEvent.BUTTON1 -> InputEvent.BUTTON1_DOWN_MASK
        MouseEvent.BUTTON2 -> InputEvent.BUTTON2_DOWN_MASK
        else -> InputEvent.BUTTON3_DOWN_MASK
    }
    val popup = btn == MouseEvent.BUTTON3
    fun ev(id: Int, clicks: Int, mods: Int, trigger: Boolean) =
        target.dispatchEvent(MouseEvent(target, id, System.currentTimeMillis(), mods or extraMods, x, y, clicks, trigger, btn))
    target.dispatchEvent(MouseEvent(target, MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), 0, x, y, 0, false))
    for (i in 1..count.coerceIn(1, 3)) {
        ev(MouseEvent.MOUSE_PRESSED, i, down, popup && popupOnPress)
        ev(MouseEvent.MOUSE_RELEASED, i, 0, popup && !popupOnPress)
        ev(MouseEvent.MOUSE_CLICKED, i, 0, false)
    }
}

internal fun keyCode(name: String): Int {
    val n = name.trim().uppercase().replace(' ', '_')
    if (n.length == 1 && n[0].isLetterOrDigit()) return KeyEvent.getExtendedKeyCodeForChar(n[0].code)
    return runCatching { KeyEvent::class.java.getField("VK_$n").getInt(null) }
        .getOrElse { throw UiBadTarget("Unknown key '$name'; use a java.awt.event.KeyEvent VK_ name such as ENTER, TAB, ESCAPE, F5") }
}

internal fun modifierMask(names: List<String>): Int = names.fold(0) { acc, m ->
    acc or when (m.lowercase()) {
        "shift" -> InputEvent.SHIFT_DOWN_MASK
        "ctrl", "control" -> InputEvent.CTRL_DOWN_MASK
        "alt", "option" -> InputEvent.ALT_DOWN_MASK
        "meta", "cmd", "command" -> InputEvent.META_DOWN_MASK
        else -> throw UiBadTarget("Unknown modifier '$m'; use shift, ctrl, alt or meta")
    }
}

internal fun pressKey(target: Component, key: String, modifiers: List<String>) {
    val code = keyCode(key)
    val mods = modifierMask(modifiers)
    val ch = when (key.trim().uppercase()) {
        "ENTER" -> '\n'
        "TAB" -> '\t'
        "SPACE" -> ' '
        "BACK_SPACE" -> '\b'
        else -> if (key.length == 1) key[0] else KeyEvent.CHAR_UNDEFINED
    }
    val now = System.currentTimeMillis()
    target.dispatchEvent(KeyEvent(target, KeyEvent.KEY_PRESSED, now, mods, code, ch))
    if (ch != KeyEvent.CHAR_UNDEFINED && mods and (InputEvent.CTRL_DOWN_MASK or InputEvent.META_DOWN_MASK) == 0) {
        target.dispatchEvent(KeyEvent(target, KeyEvent.KEY_TYPED, now, mods, KeyEvent.VK_UNDEFINED, ch))
    }
    target.dispatchEvent(KeyEvent(target, KeyEvent.KEY_RELEASED, now, mods, code, ch))
}

private suspend inline fun <reified T : Any> ApplicationCall.bodyOr400(): T? =
    runCatching { receive<T>() }.getOrElse {
        respond(HttpStatusCode.BadRequest, ErrorResponse(it.message ?: "Bad request body")); null
    }

/**
 * Generic access to every visible Swing element in Burp's windows: list them with stable ids,
 * then click, type into, select within, or send keys to any of them. This is what lets an agent
 * drive extension panels, context menus and dialogs that the purpose-built /api/ui routes do not
 * know about. Everything here runs on the EDT and dispatches real AWT events, so Burp's own
 * listeners (including popup triggers) fire as if the user had done it.
 */
fun Routing.uiElementRoutes(api: MontoyaApi) {

    suspend fun ApplicationCall.frameOr503(): Frame? =
        suiteFrame(api) ?: run { respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Burp UI frame not available")); null }


    route("/api/ui") {

        // ── Showing windows: the suite frame, dialogs, and heavyweight popups ──
        get("/windows") {
            val frame = call.frameOr503() ?: return@get
            runCatching {
                onEdt {
                    UiWindowsResponse(showingWindows(frame).mapIndexed { i, w ->
                        UiWindow("w$i", typeName(w), textOf(w), UiBounds(w.x, w.y, w.width, w.height), w.isFocused)
                    })
                }
            }.onSuccess { call.respond(it) }.onFailure { call.failFrom(it) }
        }

        // ── Inventory of elements in one window ───────────────────────────────
        // ?window=w0  ?all=true (plain containers too)  ?hidden=true (not currently showing)
        // ?text=<substring of text/name/tooltip>  ?type=<substring of class name>  ?limit=500
        get("/elements") {
            val frame = call.frameOr503() ?: return@get
            val q = call.request.queryParameters
            val windowId = q["window"] ?: "w0"
            val all = q["all"]?.toBoolean() ?: false
            val hidden = q["hidden"]?.toBoolean() ?: false
            val textFilter = q["text"]?.lowercase()
            val typeFilter = q["type"]?.lowercase()
            val limit = (q["limit"]?.toIntOrNull() ?: 500).coerceIn(1, 5000)
            runCatching {
                onEdt {
                    val win = windowById(windowId, showingWindows(frame))
                    val out = ArrayList<UiElement>()
                    var truncated = false
                    fun visit(c: Component, path: String) {
                        if (truncated) return
                        if (!hidden && !c.isShowing && c !== win) return
                        if (all || interesting(c)) {
                            val e = describe(c, "$windowId:$path", win)
                            val textOk = textFilter == null || listOfNotNull(e.text, e.name, e.tooltip, e.items?.joinToString(" "))
                                .any { it.lowercase().contains(textFilter) }
                            val typeOk = typeFilter == null || e.type.lowercase().contains(typeFilter)
                            if (textOk && typeOk) {
                                if (out.size >= limit) { truncated = true; return }
                                out += e
                            }
                        }
                        if (c is Container) c.components.forEachIndexed { i, child ->
                            visit(child, if (path.isEmpty()) "$i" else "$path/$i")
                        }
                    }
                    visit(win, "")
                    UiElementsResponse(windowId, out.size, truncated, out)
                }
            }.onSuccess { call.respond(it) }.onFailure { call.failFrom(it) }
        }

        // ── Click an element by id, or a point in a window ────────────────────
        // Right-click raises the popup trigger, so Burp's context menus open; list the new
        // JPopupMenu's items with /elements (or click-button by label) to pick one.
        post("/click") {
            val req = call.bodyOr400<UiClickRequest>() ?: return@post
            val frame = call.frameOr503() ?: return@post
            runCatching {
                actOnEdt({
                    val wins = showingWindows(frame)
                    var rowLabel: String? = null
                    val (deepest, at, win) = when {
                        req.id != null && (req.row != null || req.row_text != null) -> {
                            val c = resolveElement(req.id, wins)
                            if (!c.isShowing) throw UiBadTarget("Element ${req.id} is not showing")
                            val (i, label, point) = targetRow(c, req.row, req.row_text)
                            rowLabel = "row $i '$label'"
                            Triple(c, point, SwingUtilities.getWindowAncestor(c) ?: frame)
                        }
                        req.id != null -> {
                            val c = resolveElement(req.id, wins)
                            if (!c.isShowing) throw UiBadTarget("Element ${req.id} is not showing")
                            val cx = c.width / 2
                            val cy = c.height / 2
                            val d = (c as? Container)?.let { SwingUtilities.getDeepestComponentAt(it, cx, cy) } ?: c
                            Triple(d, SwingUtilities.convertPoint(c, Point(cx, cy), d), SwingUtilities.getWindowAncestor(c) ?: frame)
                        }
                        req.x != null && req.y != null -> {
                            val w = windowById(req.window ?: "w0", wins)
                            val d = SwingUtilities.getDeepestComponentAt(w, req.x, req.y)
                                ?: throw UiNotFound("Nothing at (${req.x}, ${req.y}) in ${req.window ?: "w0"}")
                            Triple(d, SwingUtilities.convertPoint(w, Point(req.x, req.y), d), w)
                        }
                        else -> throw UiBadTarget("Give an element id, or x and y (window coordinates)")
                    }
                    val (target, p) = retargetForClick(deepest, at, win)
                    val label = rowLabel?.let { " $it" } ?: (textOf(target) ?: textOf(deepest))?.let { " '$it'" } ?: ""
                    val via = if (target !== deepest) " via ${implName(target) ?: typeName(target)}" else ""
                    UiActionResponse(true, "Clicked", "${typeName(target)}$label$via") to {
                        clickAt(target, p.x, p.y, req.button, req.count, modifierMask(req.modifiers))
                    }
                })
            }.onSuccess { call.respond(it) }.onFailure { call.failFrom(it) }
        }

        // ── Type into a text field or area ────────────────────────────────────
        post("/type") {
            val req = call.bodyOr400<UiTypeRequest>() ?: return@post
            val frame = call.frameOr503() ?: return@post
            runCatching {
                onEdt {
                    val c = resolveElement(req.id, showingWindows(frame))
                    val field = when (c) {
                        is JTextComponent -> c
                        is JComboBox<*> -> c.editor?.editorComponent as? JTextComponent
                            ?: throw UiBadTarget("Element ${req.id} is a non-editable combo box; use /api/ui/select")
                        else -> throw UiBadTarget("Element ${req.id} is a ${typeName(c)}, not a text field")
                    }
                    if (!field.isEditable || !field.isEnabled) throw UiBadTarget("Element ${req.id} is not editable")
                    field.requestFocusInWindow()
                    if (req.replace) field.text = req.text
                    else field.document.insertString(field.caretPosition, req.text, null)
                    if (req.enter) pressKey(field, "ENTER", emptyList())
                    UiActionResponse(true, if (req.replace) "Replaced text" else "Inserted text", "${req.text.length} chars")
                }
            }.onSuccess { call.respond(it) }.onFailure { call.failFrom(it) }
        }

        // ── Select within a combo box, list, tab bar, table or tree ───────────
        post("/select") {
            val req = call.bodyOr400<UiSelectRequest>() ?: return@post
            val frame = call.frameOr503() ?: return@post
            runCatching {
                onEdt {
                    val c = resolveElement(req.id, showingWindows(frame))
                    fun pick(labels: List<String>): Int {
                        val i = when {
                            req.index != null -> req.index
                            req.text != null -> labels.indexOfFirst { it.contains(req.text, ignoreCase = true) }
                            else -> throw UiBadTarget("Give text or index")
                        }
                        if (i < 0 || i >= labels.size) throw UiNotFound("No entry matches ${req.text ?: req.index} (${labels.size} entries)")
                        return i
                    }
                    when (c) {
                        is JComboBox<*> -> {
                            val i = pick((0 until c.itemCount).map { c.getItemAt(it).toString() })
                            c.selectedIndex = i
                            UiActionResponse(true, "Selected item", c.getItemAt(i).toString())
                        }
                        is JList<*> -> {
                            val i = pick((0 until c.model.size).map { c.model.getElementAt(it).toString() })
                            c.selectedIndex = i
                            c.ensureIndexIsVisible(i)
                            UiActionResponse(true, "Selected item", c.model.getElementAt(i).toString())
                        }
                        is JTabbedPane -> {
                            val i = pick((0 until c.tabCount).map { c.getTitleAt(it) })
                            c.selectedIndex = i
                            UiActionResponse(true, "Selected tab", c.getTitleAt(i))
                        }
                        is JTable -> {
                            val labels = (0 until c.rowCount).map { r ->
                                (0 until c.columnCount).joinToString(" | ") { c.getValueAt(r, it)?.toString() ?: "" }
                            }
                            val i = pick(labels)
                            val rect = c.getCellRect(i, 0, true)
                            c.scrollRectToVisible(rect)
                            c.setRowSelectionInterval(i, i)
                            clickAt(c, rect.x + 5, rect.y + rect.height / 2, "left", 1)
                            UiActionResponse(true, "Selected row", "row $i of ${c.rowCount}")
                        }
                        is JTree -> {
                            val labels = (0 until c.rowCount).map { treeRowLabel(c, it) }
                            val i = pick(labels)
                            c.setSelectionRow(i)
                            c.scrollRowToVisible(i)
                            UiActionResponse(true, "Selected node", labels[i])
                        }
                        else -> throw UiBadTarget("Element ${req.id} is a ${typeName(c)}; select works on combo boxes, lists, tab bars, tables and trees")
                    }
                }
            }.onSuccess { call.respond(it) }.onFailure { call.failFrom(it) }
        }

        // ── Send a key to an element, or to whatever has focus ────────────────
        post("/key") {
            val req = call.bodyOr400<UiKeyRequest>() ?: return@post
            val frame = call.frameOr503() ?: return@post
            runCatching {
                actOnEdt({
                    val target = when {
                        req.id != null -> resolveElement(req.id, showingWindows(frame))
                        else -> KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: frame
                    }
                    // AWT's focus manager drops key events for anything not showing, silently.
                    if (!target.isShowing) throw UiBadTarget("Element ${req.id ?: "(focus owner)"} is not showing, so it cannot take keys")
                    if (target !== frame && target.isFocusable) target.requestFocusInWindow()
                    UiActionResponse(true, "Pressed key", (req.modifiers + req.key.uppercase()).joinToString("+")) to {
                        pressKey(target, req.key, req.modifiers)
                    }
                })
            }.onSuccess { call.respond(it) }.onFailure { call.failFrom(it) }
        }
    }
}
