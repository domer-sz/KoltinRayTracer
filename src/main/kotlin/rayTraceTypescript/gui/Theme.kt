package rayTraceTypescript.gui

import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.themes.FlatMacDarkLaf
import java.awt.Color
import java.awt.Font
import javax.swing.UIManager

/**
 * The viewer's look: FlatLaf's dark theme with an amber accent (the colour of the placement
 * outline), rounded controls and a dark title bar, like a modern 3D tool. Installed once,
 * before the first window is made.
 */
object Theme {
    /** The accent: selections, focus rings, progress, the placement outline. */
    val accent = Color(0xE8A33D)
    /** Behind the rendered image: a shade darker than the panels, so the image stands out. */
    val canvas = Color(0x161719)

    private var installed = false

    fun install() {
        if (installed) return
        installed = true
        FlatLaf.setGlobalExtraDefaults(mapOf("@accentColor" to "#E8A33D"))
        FlatMacDarkLaf.setup()
        UIManager.put("Component.arc", 8)
        UIManager.put("Button.arc", 8)
        UIManager.put("TextComponent.arc", 6)
        UIManager.put("ProgressBar.arc", 6)
        UIManager.put("ScrollBar.showButtons", false)
        UIManager.put("ScrollBar.width", 10)
        UIManager.put("ScrollBar.thumbArc", 999)
        UIManager.put("ScrollBar.thumbInsets", java.awt.Insets(2, 2, 2, 2))
        UIManager.put("TitlePane.unifiedBackground", true)
        UIManager.put("List.selectionArc", 6)
    }

    /** Secondary text: hints, section titles. */
    val muted: Color get() = UIManager.getColor("Label.disabledForeground") ?: Color(0x8A8D93)
    val text: Color get() = UIManager.getColor("Label.foreground") ?: Color(0xDDDDDD)
    val separator: Color get() = UIManager.getColor("Separator.foreground") ?: Color(0x3A3C40)

    fun hex(c: Color) = "#%02x%02x%02x".format(c.red, c.green, c.blue)

    fun font(size: Float, bold: Boolean = false): Font {
        val base = UIManager.getFont("Label.font") ?: Font(Font.SANS_SERIF, Font.PLAIN, 13)
        return base.deriveFont(if (bold) Font.BOLD else Font.PLAIN, size)
    }
}
