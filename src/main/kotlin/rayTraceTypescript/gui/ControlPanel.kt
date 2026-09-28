package rayTraceTypescript.gui

import java.awt.Component
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.io.File
import javax.swing.BorderFactory
import javax.swing.ButtonGroup
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JColorChooser
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JRadioButton
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
    private val quick = JRadioButton("Szybki podgląd", !viewer.raytrace)
    private val raytrace = JRadioButton("Ray tracing", viewer.raytrace)
    private val denoise = JCheckBox("Odszumianie (D)", viewer.denoiseOn)
    private val save = JButton("Zapisz obraz (S)")

    private val lightSwitches = viewer.lights.map { light ->
        JCheckBox(light.part.name, light.enabled).apply {
            addActionListener { viewer.setLight(light, isSelected) }
        }
    }
    private val restore = JButton("Przywróć obiekty sceny")

    private val listModel = DefaultListModel<SceneEntry>()
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
    private val addBall = JButton("Dodaj kulę")
    private val addBlock = JButton("Dodaj graniastosłup")
    private val blockWidth = JSpinner(SpinnerNumberModel(viewer.defaultObjectHeight / 2, 1e-3, 1e6, step))
    private val blockDepth = JSpinner(SpinnerNumberModel(viewer.defaultObjectHeight / 2, 1e-3, 1e6, step))
    private val materialPreset = JComboBox<Any>((listOf<Any>(CUSTOM) + MaterialPreset.values()).toTypedArray())
    private val materialKind = JComboBox(MaterialKind.values())
    private val colour = JButton("Wybierz kolor…")
    private val parameterLabel = JLabel("Parametr")
    private val parameter = JSpinner(SpinnerNumberModel(0.0, 0.0, 100.0, 0.05))
    private val zUp = JCheckBox("Oś Z w górę (pliki do druku 3D)")
    private val info = JLabel(" ")
    /** Fields only an added model has; the scene's own objects can only be removed. */
    private val modelFields: List<Component> =
        listOf(centre, posX, posY, posZ, turn, size, materialPreset, materialKind, colour, parameter, zUp)

    private var lastFolder: File? = File("../stls").takeIf { it.isDirectory } ?: File(".")

    init {
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        section("Tryb (T)")
        ButtonGroup().apply { add(quick); add(raytrace) }
        full(JPanel(GridBagLayout()).apply {
            add(quick, GridBagConstraints().apply { weightx = 1.0; anchor = GridBagConstraints.WEST })
            add(raytrace, GridBagConstraints().apply { weightx = 1.0; anchor = GridBagConstraints.WEST })
        })

        section("Obraz")
        field("Gotowe", preset)
        field("Szerokość", width)
        field("Wysokość", height)
        full(applySize)
        field("Cel spp", target)
        full(progress)
        full(denoise)
        full(save)
        full(JButton("Pomoc - skróty (F1)").apply { addActionListener { HelpDialog.show(frame) } })

        if (lightSwitches.isNotEmpty()) {
            section("Światła")
            lightSwitches.forEach { full(it) }
        }

        section("Obiekty na scenie")
        full(add)
        full(JPanel(GridBagLayout()).apply {
            add(addBall, GridBagConstraints().apply { weightx = 1.0; fill = GridBagConstraints.HORIZONTAL; insets = Insets(0, 0, 0, 4) })
            add(addBlock, GridBagConstraints().apply { weightx = 1.0; fill = GridBagConstraints.HORIZONTAL })
        })
        full(JScrollPane(list).apply { preferredSize = Dimension(250, 110); minimumSize = Dimension(250, 110) })
        full(JPanel(GridBagLayout()).apply {
            add(remove, GridBagConstraints().apply { weightx = 1.0; fill = GridBagConstraints.HORIZONTAL; insets = Insets(0, 0, 0, 4) })
            add(centre, GridBagConstraints().apply { weightx = 1.0; fill = GridBagConstraints.HORIZONTAL })
        })
        field("X", posX)
        field("Y (podstawa)", posY)
        field("Z", posZ)
        field("Obrót [°]", turn)
        field("Wysokość", size)
        field("Szerokość", blockWidth)
        field("Głębokość", blockDepth)
        section("Materiał zaznaczonego obiektu")
        field("Gotowy", materialPreset)
        field("Rodzaj", materialKind)
        field("Kolor", colour)
        field(parameterLabel, parameter)
        full(zUp)
        full(info)
        if (viewer.builtIns.any { !it.part.isLight }) full(restore)
        full(JLabel("<html><small>Ctrl + przeciąganie przestawia zaznaczony obiekt, strzałki go przesuwają (F1: wszystkie skróty).</small></html>"))
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
        quick.addActionListener { viewer.raytrace = false; syncMode() }
        raytrace.addActionListener { viewer.raytrace = true; syncMode() }
        save.addActionListener { viewer.requestSave() }

        add.addActionListener { chooseModel() }
        remove.addActionListener { viewer.selected?.let { viewer.removeObject(it); refreshObjects() } }
        centre.addActionListener { viewer.selectedModel?.let { viewer.centreInView(it); refreshSelection() } }
        restore.addActionListener { viewer.restoreBuiltIns(); refreshObjects() }
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
        materialPreset.addActionListener {
            if (syncing) return@addActionListener
            val chosen = materialPreset.selectedItem as? MaterialPreset ?: return@addActionListener
            edit { o -> o.material = chosen.spec }
            refreshSelection()
        }
        materialKind.addActionListener {
            if (syncing) return@addActionListener
            val kind = materialKind.selectedItem as MaterialKind
            edit { o ->
                // A new kind starts from its usual value: slightly rough metal, window glass, a bright light.
                if (o.material.kind != kind) o.material = o.material.copy(kind = kind, parameter = defaultParameter(kind))
            }
            refreshSelection()
        }
        colour.addActionListener {
            val o = viewer.selectedModel ?: return@addActionListener
            val picked = JColorChooser.showDialog(frame, "Kolor materiału - ${o.name}", o.material.screenColor())
                ?: return@addActionListener
            edit { it.material = it.material.withScreenColor(picked) }
            refreshSelection()
        }
        parameter.addChangeListener { edit { o -> o.material = o.material.copy(parameter = parameter.value as Double) } }
        zUp.addActionListener { edit { o -> o.zUp = zUp.isSelected } }
        blockWidth.addChangeListener { edit { o -> o.width = blockWidth.value as Double } }
        blockDepth.addChangeListener { edit { o -> o.depth = blockDepth.value as Double } }
        addBall.addActionListener { viewer.addShape(Shape.Ball); refreshObjects() }
        addBlock.addActionListener { viewer.addShape(Shape.Block); refreshObjects() }
        refreshObjects()
        syncMode()
    }

    /** Applies a field change to the selected object, unless the panel is filling its fields. */
    private fun edit(change: (PlacedObject) -> Unit) {
        if (syncing) return
        val o = viewer.selectedModel ?: return
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
        viewer.entries().forEach { listModel.addElement(it) }
        list.setSelectedValue(viewer.selected, true)
        syncing = false
        refreshSelection()
    }

    /** Fills the object fields from the selected model, or greys them out for anything else. */
    fun refreshSelection() {
        val o = viewer.selectedModel
        syncing = true
        remove.isEnabled = viewer.selected != null
        modelFields.forEach { it.isEnabled = o != null }
        // Only a loaded model can be stood up from z; only a block has its own width and depth.
        zUp.isEnabled = o?.isModel == true
        blockWidth.isEnabled = o?.shape == Shape.Block
        blockDepth.isEnabled = o?.shape == Shape.Block
        restore.isEnabled = viewer.builtIns.any { !it.part.isLight && !it.enabled }
        if (viewer.selected is BuiltInObject) {
            info.text = "Obiekt sceny - można go tylko usunąć"
        } else if (o != null) {
            posX.value = o.x
            posY.value = o.y
            posZ.value = o.z
            turn.value = o.rotation
            size.value = o.height
            showMaterial(o.material)
            zUp.isSelected = o.zUp
            blockWidth.value = o.width
            blockDepth.value = o.depth
            info.text = o.description
        } else {
            info.text = if (viewer.entries().isEmpty()) "Scena jest pusta" else "Zaznacz obiekt na liście"
        }
        syncing = false
    }

    /**
     * Shows [spec] in the material fields: the matching ready-made material (or "Własny"), the
     * kind, a colour swatch, and the kind's number with its own name and range.
     */
    private fun showMaterial(spec: MaterialSpec) {
        materialPreset.selectedItem = MaterialPreset.values().firstOrNull { it.spec == spec } ?: CUSTOM
        materialKind.selectedItem = spec.kind
        colour.icon = Swatch(spec.screenColor())
        colour.isEnabled = spec.kind != MaterialKind.GLASS
        colour.toolTipText = if (spec.kind == MaterialKind.GLASS) "Szkło w tym rendererze jest bezbarwne" else null
        val kind = spec.kind
        parameterLabel.text = kind.parameterLabel ?: "Parametr"
        parameter.isEnabled = kind.parameterLabel != null
        parameter.model = SpinnerNumberModel(spec.parameter.coerceIn(kind.min, kind.max), kind.min, kind.max, kind.step)
    }

    private fun defaultParameter(kind: MaterialKind): Double = when (kind) {
        MaterialKind.MATTE -> 0.0
        MaterialKind.METAL -> 0.1
        MaterialKind.GLASS -> 1.5
        MaterialKind.LIGHT -> 4.0
    }

    /** A small filled square showing a material's colour on its button. */
    private class Swatch(private val colour: java.awt.Color) : javax.swing.Icon {
        override fun getIconWidth() = 14
        override fun getIconHeight() = 14
        override fun paintIcon(c: Component?, g: java.awt.Graphics, x: Int, y: Int) {
            g.color = colour
            g.fillRect(x, y, 14, 14)
            g.color = java.awt.Color.DARK_GRAY
            g.drawRect(x, y, 13, 13)
        }
    }

    /** A fixed width; the height is whatever the fields need (the window scrolls the panel). */
    override fun getPreferredSize(): Dimension = Dimension(PANEL_WIDTH, super.getPreferredSize().height)

    fun showProgress(samples: Int, target: Int) {
        progress.value = (samples.toLong() * 1000 / target.coerceAtLeast(1)).toInt()
        progress.string = "$samples / $target spp"
    }

    fun syncDenoise() {
        denoise.isSelected = viewer.denoiseOn
    }

    /** Shows the viewer's mode; the sample target and denoising only matter when ray tracing. */
    fun syncMode() {
        quick.isSelected = !viewer.raytrace
        raytrace.isSelected = viewer.raytrace
        target.isEnabled = viewer.raytrace
        denoise.isEnabled = viewer.raytrace
    }

    private fun numberSpinner(step: Double) = JSpinner(SpinnerNumberModel(0.0, -1e6, 1e6, step)).apply {
        editor = JSpinner.NumberEditor(this, "0.##")
    }

    private fun section(title: String) {
        add(JLabel(title).apply { font = font.deriveFont(java.awt.Font.BOLD) }, GridBagConstraints().apply {
            gridx = 0; gridy = row++; gridwidth = 2; anchor = GridBagConstraints.WEST
            insets = Insets(if (row > 1) 14 else 0, 0, 6, 0)
        })
    }

    private fun field(label: String, component: Component) = field(JLabel(label), component)

    /** A labelled row; the label is passed in when it changes later (the material parameter). */
    private fun field(label: JLabel, component: Component) {
        add(label, GridBagConstraints().apply {
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

    companion object {
        private const val PANEL_WIDTH = 300
        /** The preset list's entry for a material changed by hand. */
        private const val CUSTOM = "Własny"
    }
}
