package rayTraceTypescript.gui

import java.awt.event.KeyEvent
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JEditorPane
import javax.swing.JFrame
import javax.swing.JScrollPane
import javax.swing.KeyStroke

/** Every mouse gesture and key the viewer understands, in one window (F1 or H). */
object HelpDialog {
    private val sections = listOf(
        "Kamera" to listOf(
            "Lewy przycisk + przeciąganie" to "Obrót wokół punktu, na który patrzy kamera",
            "Shift + lewy przycisk + przeciąganie" to "Przesuwanie kamery (scena jedzie za kursorem)",
            "Środkowy przycisk + przeciąganie" to "Przesuwanie kamery",
            "Kółko myszy" to "Przybliżanie i oddalanie",
            "Prawy przycisk + przeciąganie" to "Przybliżanie i oddalanie",
            "+ / -" to "Węższy / szerszy kąt widzenia",
            "R" to "Powrót do widoku startowego"
        ),
        "Obiekty" to listOf(
            "Ctrl + przeciąganie (lub klik)" to "Przestaw zaznaczony model; żółty zarys pokazuje, gdzie stanie: na podłodze, kolumnie, kuli, innym modelu, przy ścianie u jej stóp",
            "Strzałki" to "Przesuń zaznaczony model w lewo, w prawo, dalej, bliżej (względem kamery)",
            "PageUp / PageDown" to "Podnieś / opuść zaznaczony model",
            "Q / E  lub  [ / ]" to "Obróć zaznaczony model o 15°",
            "Shift + strzałki, PageUp, PageDown" to "To samo, pięć razy większy krok",
            "Delete" to "Usuń zaznaczony obiekt (także kolumnę lub kulę sceny)",
            "Panel: Dodaj model…" to "Wczytaj STL, OBJ, PLY, OFF, glTF, GLB lub 3MF",
            "Panel: X, Y, Z, Obrót, Wysokość" to "Ustaw zaznaczony model",
            "Panel: Materiał - Gotowy" to "Szary, biały, czerwony, niebieski mat, szkło, diament, lustro, złoto, miedź, stal, świecący",
            "Panel: Materiał - Rodzaj, Kolor, parametr" to "Matowy, metal (chropowatość), szkło (wsp. załamania) lub świecący (moc)",
            "Panel: Przywróć obiekty sceny" to "Wróć usunięte obiekty sceny na miejsce"
        ),
        "Światła" to listOf(
            "Panel: Lampa sufitowa" to "Włącz / wyłącz lampę pod sufitem",
            "Panel: Światło przednie" to "Włącz / wyłącz panel doświetlający przód sceny"
        ),
        "Obraz" to listOf(
            "D" to "Odszumianie włącz / wyłącz",
            "S" to "Zapisz obraz (PPM i PNG, z odszumioną kopią)",
            "Panel: rozdzielczość, Cel spp" to "Rozmiar obrazu i liczba próbek, przy której render się kończy"
        ),
        "Okno" to listOf(
            "F1 lub H" to "Ta pomoc",
            "Esc" to "Zamknij okno"
        )
    )

    private val html: String = buildString {
        append("<html><body style='font-family:sans-serif; font-size:11pt; margin:4px'>")
        append("<p style='margin-top:0'>Podczas ruchu kamery podgląd liczy się w 1/4 rozdzielczości przy 1 spp; ")
        append("po zatrzymaniu obraz dochodzi do pełnej jakości i jest odszumiany co 4× więcej próbek.</p>")
        for ((title, rows) in sections) {
            append("<h3 style='margin-bottom:4px'>$title</h3><table cellspacing='0' cellpadding='3'>")
            for ((keys, action) in rows) {
                append("<tr><td style='white-space:nowrap; padding-right:14px'><b>$keys</b></td><td>$action</td></tr>")
            }
            append("</table>")
        }
        append("</body></html>")
    }

    fun show(owner: JFrame) {
        val dialog = JDialog(owner, "Pomoc - skróty", false)
        val text = JEditorPane("text/html", html).apply {
            isEditable = false
            border = BorderFactory.createEmptyBorder(8, 12, 8, 12)
            caretPosition = 0
        }
        dialog.contentPane.add(JScrollPane(text))
        // Esc, F1 or H close the help again.
        for (key in listOf(KeyEvent.VK_ESCAPE, KeyEvent.VK_F1, KeyEvent.VK_H)) {
            dialog.rootPane.registerKeyboardAction({ dialog.dispose() }, KeyStroke.getKeyStroke(key, 0), JComponent.WHEN_IN_FOCUSED_WINDOW)
        }
        dialog.setSize(560, 560)
        dialog.setLocationRelativeTo(owner)
        dialog.isVisible = true
    }
}
