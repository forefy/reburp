package com.reburp.routes

import com.reburp.capMessage
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The /api/ui helpers against plain Swing trees, with no display. Burp's own widgets are
 * obfuscated subclasses of standard components, which is what the Zmel stand-in imitates.
 */
class UiElementHelpersTest {

    /** Burp-style obfuscated subclass: a label that acts as a button. */
    private class Zmel : JLabel("Send")

    private fun root(): JPanel = JPanel(null).apply { setSize(400, 300) }

    @Test
    fun `ids resolve down the child path`() {
        val root = root()
        val panel = JPanel(null).also { root.add(it) }
        val button = JButton("ok").also { panel.add(it) }

        assertSame(root, resolveElement("w0:", listOf(root)))
        assertSame(panel, resolveElement("w0:0", listOf(root)))
        assertSame(button, resolveElement("w0:0/0", listOf(root)))
    }

    @Test
    fun `bad or vanished ids fail with the right class`() {
        val root = root()
        root.add(JPanel())

        assertFailsWith<UiBadTarget> { resolveElement("nonsense", listOf(root)) }
        assertFailsWith<UiBadTarget> { resolveElement("w0:0/x", listOf(root)) }
        assertFailsWith<UiBadTarget> { windowById("zzz", listOf(root)) }
        assertFailsWith<UiNotFound> { resolveElement("w1:", listOf(root)) }
        assertFailsWith<UiNotFound> { resolveElement("w0:0/5", listOf(root)) }
    }

    @Test
    fun `elements report the Swing base type and keep an obfuscated class as impl`() {
        val root = root()
        val send = Zmel().also { root.add(it) }
        val plain = JButton("x").also { root.add(it) }
        val anonymous = object : JLabel("y") {}.also { root.add(it) }

        val sendInfo = describe(send, "w0:0", root)
        assertEquals("JLabel", sendInfo.type)
        assertEquals("Zmel", sendInfo.impl)
        assertEquals("Send", sendInfo.text)

        val plainInfo = describe(plain, "w0:1", root)
        assertEquals("JButton", plainInfo.type)
        assertNull(plainInfo.impl)
        assertTrue(plainInfo.clickable, "a button is clickable by itself")

        val anonInfo = describe(anonymous, "w0:2", root)
        assertEquals("JLabel", anonInfo.type)
        assertNull(anonInfo.impl, "an anonymous subclass has no name worth reporting")
    }

    @Test
    fun `bounds are reported in root coordinates`() {
        val root = root()
        val panel = JPanel(null).apply { setBounds(100, 50, 200, 100) }.also { root.add(it) }
        val label = JLabel("a").apply { setBounds(10, 20, 30, 15) }.also { panel.add(it) }

        val b = describe(label, "w0:0/0", root).bounds
        assertEquals(listOf(110, 70, 30, 15), listOf(b.x, b.y, b.width, b.height))
        assertEquals(listOf(0, 0, 400, 300), describe(root, "w0:", root).bounds.let { listOf(it.x, it.y, it.width, it.height) })
    }

    @Test
    fun `clicks retarget to the nearest ancestor that listens, as AWT does`() {
        val root = root()
        val holder = JPanel(null).apply { setBounds(0, 0, 100, 40) }.also { root.add(it) }
        val label = Zmel().apply { setBounds(10, 20, 60, 15) }.also { holder.add(it) }
        holder.addMouseListener(object : MouseAdapter() {})

        val (target, point) = retargetForClick(label, Point(3, 4), root)
        assertSame(holder, target)
        assertEquals(Point(13, 24), point)
        assertTrue(describe(holder, "w0:0", root).clickable)
        assertTrue(!describe(label, "w0:0/0", root).clickable)
    }

    @Test
    fun `clicks stay on a component that listens itself`() {
        val root = root()
        val button = JButton("go").also { root.add(it) }
        val (target, point) = retargetForClick(button, Point(5, 5), root)
        assertSame(button, target)
        assertEquals(Point(5, 5), point)
    }

    @Test
    fun `clicks on a tree with no listener are left where they were`() {
        val root = root()
        val label = JLabel("mute").also { root.add(it) }
        val (target, point) = retargetForClick(label, Point(1, 2), root)
        assertSame(label, target)
        assertEquals(Point(1, 2), point)
    }

    @Test
    fun `synthetic mouse events fire a button's action`() {
        var fired = 0
        val button = JButton("Send").apply {
            setSize(100, 30)
            addActionListener { fired++ }
        }
        clickAt(button, 50, 15, "left", 1)
        assertEquals(1, fired)
    }

    @Test
    fun `right clicks raise the popup trigger exactly once`() {
        val panel = JPanel().apply { setSize(50, 50) }
        var triggers = 0
        var presses = 0
        panel.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) { presses++; if (e.isPopupTrigger) triggers++ }
            override fun mouseReleased(e: MouseEvent) { if (e.isPopupTrigger) triggers++ }
        })
        clickAt(panel, 10, 10, "right", 1)
        assertEquals(1, presses)
        assertEquals(1, triggers, "the platform's popup trigger fires on press or release, never both")
        assertFailsWith<UiBadTarget> { clickAt(panel, 1, 1, "sideways", 1) }
    }

    @Test
    fun `key names and modifiers map to AWT codes`() {
        assertEquals(KeyEvent.VK_ENTER, keyCode("ENTER"))
        assertEquals(KeyEvent.VK_ENTER, keyCode("enter"))
        assertEquals(KeyEvent.VK_F5, keyCode("F5"))
        assertEquals(KeyEvent.VK_BACK_SPACE, keyCode("back space"))
        assertEquals(KeyEvent.VK_R, keyCode("r"))
        assertEquals(KeyEvent.VK_7, keyCode("7"))
        assertFailsWith<UiBadTarget> { keyCode("NO_SUCH_KEY") }

        assertEquals(0, modifierMask(emptyList()))
        assertEquals(InputEvent.META_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK, modifierMask(listOf("cmd", "shift")))
        assertEquals(InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK, modifierMask(listOf("control", "option")))
        assertFailsWith<UiBadTarget> { modifierMask(listOf("hyper")) }
    }

    @Test
    fun `an unknown key is refused before anything is dispatched`() {
        val field = JTextField()
        var seen = 0
        field.addKeyListener(object : KeyAdapter() { override fun keyPressed(e: KeyEvent) { seen++ } })
        assertFailsWith<UiBadTarget> { pressKey(field, "NO_SUCH_KEY", emptyList()) }
        assertEquals(0, seen)
    }

    @Test
    fun `icon-only toolbar labels count as elements and match by tooltip`() {
        val icon = JLabel().apply { toolTipText = "Enable line wrapping" }
        icon.addMouseListener(object : MouseAdapter() {})
        val mute = JLabel()
        val caption = JLabel("Request")

        assertTrue(interesting(icon), "a tooltip plus a listener is a control")
        assertTrue(interesting(caption), "a caption is worth listing")
        assertTrue(!interesting(mute), "a blank label with nothing attached is noise")

        // matchesLabel needs a showing component; nothing is showing headless, so only the
        // negative path is observable here. The live smoke test covers the positive one.
        assertTrue(!matchesLabel(icon, "line wrapping"))
    }

    @Test
    fun `history messages are capped with a marker and 0 lifts the cap`() {
        val message = "x".repeat(100)
        val capped = capMessage(message, 10)!!
        assertTrue(capped.startsWith("x".repeat(10)))
        assertTrue("truncated 90 more chars" in capped)
        assertSame(message, capMessage(message, 0))
        assertSame(message, capMessage(message, 100))
        assertNull(capMessage(null, 5))
    }
}
