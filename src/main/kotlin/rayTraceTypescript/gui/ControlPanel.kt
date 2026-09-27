package rayTraceTypescript.gui

import java.awt.Component
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.io.File
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JScrollPane
import javax.swing.JSpinner
import javax.swing.ListSelectionModel
import javax.swing.SpinnerNumberModel
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * The viewer's side panel: output size and sample target, denoising and saving, and the models
 * added to the scene - loading them, choosing one, and setting where it stands, how it is
 * turned, how tall it is and what it is made of.
 */
class ControlPanel(private val viewer: Viewer, private val frame: JFrame) : JPanel(GridBagLayout()) {
    private var row = 0
    /** Set while the panel fills its own fields, so that does not count as an edit. */
    private var syncing = false

    private val presets = listOf(
        "Jak scena" to viewer.sceneResolution,
        "640 × 360" to Viewer.Resolution(640, 360),
        "800 × 800" to Viewer.Resolution(800, 800),
        "1280 × 720" to Viewer.Resolution(1280, 720),
        "1920 × 1080" to Viewer.Resolution(1920, 1080),
        "2000 × 2000" to Viewer.Resolution(2000, 2000),
        "3840 × 2160" to Viewer.Resolution(3840, 2160)
    )
    private val preset = JComboBox(presets.map { it.first }.toTypedArray())
    private val width = JSpinner(SpinnerNumberModel(viewer.sceneResolution.width, 64, 8192, 16))
    private val height = JSpinner(SpinnerNumberModel(viewer.sceneResolution.height, 64, 8192, 16))
    private val applySize = JButton("Zastosuj rozdzielczość")
    private val target = JSpinner(SpinnerNumberModel(viewer.targetSamples, 1, 1_000_000, 64))
    private val progress = JProgressBar(0, 1000).apply { isStringPainted = true }
    private val denoise = JCheckBox("Odszumianie (D)", viewer.denoiseOn)
    private val save = JButton("Zapisz obraz (S)")

    private val listModel = DefaultListModel<PlacedObject>()
    private val list = JList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        visibleRowCount = 5
    }
    private val add = JButton("Dodaj model…")
    private val remove = JButton("Usuń")
    private val centre = JButton("Na środek widoku")
    private val step = viewer.defaultObjectHeight / 20
    private val posX = numberSpinner(step)
    private val posY = numberSpinner(step)
    private val posZ = numberSpinner(step)
    private val turn = JSpinner(SpinnerNumberModel(0.0, -360.0, 360.0, 15.0))
    private val size = JSpinner(SpinnerNumberModel(viewer.defaultObjectHeight, 1e-3, 1e6, step))
    private val finish = JComboBox(Finish.values())
    private val zUp = JCheckBox("Oś Z w górę (pliki do druku 3D)")
    private val info = JLabel(" ")
    private val objectFields: List<Component> = listOf(remove, centre, posX, posY, posZ, turn, size, finish, zUp)

    private var lastFolder: File? = File("../stls").takeIf { it.isDirectory } ?: File(".")

    init {
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        preferredSize = Dimension(290, 0)

        section("Obraz")
        field("Gotowe", preset)
        field("Szerokość", width)
        field("Wysokość", height)
        full(applySize)
        field("Cel spp", target)
        full(progress)
        full(denoise)
        full(save)

        section("Obiekty")
        full(add)
        full(JScrollPane(list).apply { preferredSize = Dimension(250, 96) })
        full(JPanel(GridBagLayout()).apply {
            add(remove, GridBagConstraints().apply { weightx = 1.0; fill = GridBagConstraints.HORIZONTAL; insets = Insets(0, 0, 0, 4) })
            add(centre, GridBagConstraints().apply { weightx = 1.0; fill = GridBagConstraints.HORIZONTAL })
        })
        field("X", posX)
        field("Y (podstawa)", posY)
        field("Z", posZ)
        field("Obrót [°]", turn)
        field("Wysokość", size)
        field("Materiał", finish)
        full(zUp)
        full(info)
        full(JLabel("<html><small>Ctrl + klik w obraz stawia zaznaczony obiekt na podłodze w tym miejscu.</small></html>"))
        add(JPanel(), GridBagConstraints().apply { gridy = row++; weighty = 1.0 })

        preset.addActionListener {
            val r = presets[preset.selectedIndex].second
            syncing = true
            width.value = r.width
            height.value = r.height
            syncing = false
        }
        applySize.addActionListener {
            viewer.resolution = Viewer.Resolution(width.value as Int, height.value as Int)
        }
        target.addChangeListener { viewer.targetSamples = target.value as Int }
        denoise.addActionListener { viewer.denoiseOn = denoise.isSelected }
        save.addActionListener { viewer.requestSave() }

        add.addActionListener { chooseModel() }
        remove.addActionListener { viewer.selected?.let { viewer.removeObject(it); refreshObjects() } }
        centre.addActionListener { viewer.selected?.let { viewer.centreInView(it); refreshSelection() } }
        list.addListSelectionListener {
            if (!it.valueIsAdjusting && !syncing) {
                viewer.selected = list.selectedValue
                refreshSelection()
            }
        }
        posX.addChangeListener { edit { o -> o.x = posX.value as Double } }
        posY.addChangeListener { edit { o -> o.y = posY.value as Double } }
        posZ.addChangeListener { edit { o -> o.z = posZ.value as Double } }
        turn.addChangeListener { edit { o -> o.rotation = turn.value as Double } }
        size.addChangeListener { edit { o -> o.height = size.value as Double } }
        finish.addActionListener { edit { o -> o.finish = finish.selectedItem as Finish } }
        zUp.addActionListener { edit { o -> o.zUp = zUp.isSelected } }
        refreshSelection()
    }

    /** Applies a field change to the selected object, unless the panel is filling its fields. */
    private fun edit(change: (PlacedObject) -> Unit) {
        if (syncing) return
        val o = viewer.selected ?: return
        change(o)
        viewer.sceneChanged()
    }

    private fun chooseModel() {
        val chooser = JFileChooser(lastFolder).apply {
            fileFilter = FileNameExtensionFilter("Modele 3D (STL, OBJ, PLY, OFF, glTF, GLB, 3MF)", "stl", "obj", "ply", "off", "gltf", "glb", "3mf")
        }
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return
        val file = chooser.selectedFile
        lastFolder = file.parentFile
        add.isEnabled = false
        add.text = "Wczytuję ${file.name}…"
        viewer.addModel(file) { placed, error ->
            add.isEnabled = true
            add.text = "Dodaj model…"
            if (error != null) {
                JOptionPane.showMessageDialog(frame, "Nie udało się wczytać ${file.name}:\n$error", "Błąd wczytywania", JOptionPane.ERROR_MESSAGE)
            }
            if (placed != null) refreshObjects()
        }
    }

    /** Rebuilds the list from the viewer's objects and shows the selected one's fields. */
    fun refreshObjects() {
        syncing = true
        listModel.clear()
        viewer.objects.forEach { listModel.addElement(it) }
        list.setSelectedValue(viewer.selected, true)
        syncing = false
        refreshSelection()
    }

    /** Fills the object fields from the selected object, or greys them out without one. */
    fun refreshSelection() {
        val o = viewer.selected
        syncing = true
        objectFields.forEach { it.isEnabled = o != null }
        if (o != null) {
            posX.value = o.x
            posY.value = o.y
            posZ.value = o.z
            turn.value = o.rotation
            size.value = o.height
            finish.selectedItem = o.finish
            zUp.isSelected = o.zUp
            info.text = "%,d trójkątów".format(o.triangleCount)
        } else {
            info.text = if (viewer.objects.isEmpty()) "Brak dodanych obiektów" else "Zaznacz obiekt na liście"
        }
        syncing = false
    }

    fun showProgress(samples: Int, target: Int) {
        progress.value = (samples.toLong() * 1000 / target.coerceAtLeast(1)).toInt()
        progress.string = "$samples / $target spp"
    }

    fun syncDenoise() {
        denoise.isSelected = viewer.denoiseOn
    }

    private fun numberSpinner(step: Double) = JSpinner(SpinnerNumberModel(0.0, -1e6, 1e6, step)).apply {
        editor = JSpinner.NumberEditor(this, "0.##")
    }

    private fun section(title: String) {
        add(JLabel("<html><b>$title</b></html>"), GridBagConstraints().apply {
            gridx = 0; gridy = row++; gridwidth = 2; anchor = GridBagConstraints.WEST
            insets = Insets(if (row > 1) 14 else 0, 0, 6, 0)
        })
    }

    private fun field(label: String, component: Component) {
        add(JLabel(label), GridBagConstraints().apply {
            gridx = 0; gridy = row; anchor = GridBagConstraints.WEST; insets = Insets(2, 0, 2, 8)
        })
        add(component, GridBagConstraints().apply {
            gridx = 1; gridy = row++; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL; insets = Insets(2, 0, 2, 0)
        })
    }

    private fun full(component: Component) {
        add(component, GridBagConstraints().apply {
            gridx = 0; gridy = row++; gridwidth = 2; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL; insets = Insets(3, 0, 3, 0)
        })
    }
}
