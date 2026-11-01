package com.glasshome.tv

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Panels over the home screen: the tile menu (hold OK), the wallpaper picker
 * and the "photos from your phone" instructions. Only one is open at a time.
 */
class Overlays(
    private val activity: Activity,
    private val container: FrameLayout,
    private val app: GlassHomeApp,
) {
    var onOpened: () -> Unit = {}
    var onClosed: () -> Unit = {}
    var onWallpaperChanged: () -> Unit = {}
    var onDockChanged: () -> Unit = {}

    private val handler = Handler(Looper.getMainLooper())
    private val thumbLoader = Background.executor("glasshome-thumbs")
    private val thumbs = ArrayList<Bitmap>()
    private var generation = 0
    private var showingPhoneInfo = false

    val isOpen: Boolean
        get() = container.visibility == View.VISIBLE

    fun close() {
        if (!isOpen) {
            return
        }
        generation += 1
        showingPhoneInfo = false
        container.removeAllViews()
        container.visibility = View.GONE
        for (thumb in thumbs) {
            thumb.recycle()
        }
        thumbs.clear()
        onClosed()
    }

    // ---------- tile menu ----------

    fun openTileMenu(item: DockItem, index: Int, count: Int) {
        val panel = newPanel(item.label)
        if (index > 0) {
            addAction(panel, I18n.t("menu.move_left")) {
                app.catalog.move(item.key, -1)
                close()
                onDockChanged()
            }
        }
        if (index < count - 1) {
            addAction(panel, I18n.t("menu.move_right")) {
                app.catalog.move(item.key, 1)
                close()
                onDockChanged()
            }
        }
        addAction(panel, I18n.t("menu.change_icon")) { openIconPicker(item) }
        addAction(panel, I18n.t("menu.color_button")) { openButtonPicker(item) }
        addAction(panel, I18n.t("menu.hide")) {
            app.catalog.hide(item.key)
            close()
            onDockChanged()
        }
        if (app.catalog.canUninstall(item)) {
            addAction(panel, I18n.t("menu.uninstall")) {
                close()
                app.catalog.uninstall(item)
            }
        }
        if (app.catalog.hasAppInfo(item)) {
            addAction(panel, I18n.t("menu.app_info")) {
                close()
                app.catalog.openAppInfo(item)
            }
        }
        addAction(panel, I18n.t("menu.wallpaper")) { openWallpaperPicker() }
        addAction(panel, I18n.t("menu.tv_settings")) { openSettings() }
        addAction(panel, I18n.t("menu.phone")) { openPhoneInfo() }
        val hiddenCount = app.catalog.hiddenCount()
        if (hiddenCount > 0) {
            addAction(panel, I18n.t("menu.show_hidden", "count" to hiddenCount)) {
                app.catalog.showAll()
                close()
                onDockChanged()
            }
        }
        show(panel, Gravity.START or Gravity.BOTTOM)
    }

    // ---------- wallpaper picker ----------

    fun openWallpaperPicker() {
        val store = app.wallpapers
        val ownCount = store.own().size
        val panel = newPanel(I18n.t("wallpaper.title"))

        val modes = LinearLayout(activity)
        modes.orientation = LinearLayout.HORIZONTAL
        addAction(modes, checked(I18n.t("wallpaper.all"), store.mode == WallpaperStore.MODE_ALL)) {
            store.mode = WallpaperStore.MODE_ALL
            close()
            onWallpaperChanged()
        }
        if (ownCount > 0) {
            addAction(modes, checked(I18n.t("wallpaper.mine", "count" to ownCount), store.mode == WallpaperStore.MODE_OWN)) {
                store.mode = WallpaperStore.MODE_OWN
                close()
                onWallpaperChanged()
            }
        }
        addAction(modes, I18n.t("menu.phone")) { openPhoneInfo() }
        panel.addView(modes)

        val hint = newText(I18n.t("wallpaper.pick_one"), 13f, 0xB3FFFFFF.toInt())
        hint.setPadding(dp(8), dp(14), 0, dp(8))
        panel.addView(hint)

        val scroll = HorizontalScrollView(activity)
        scroll.isHorizontalScrollBarEnabled = false
        scroll.clipToPadding = false
        val row = LinearLayout(activity)
        row.orientation = LinearLayout.HORIZONTAL
        scroll.addView(row)
        panel.addView(scroll)

        val pinnedKey = if (store.mode == WallpaperStore.MODE_PINNED) store.pinnedKey else null
        var firstThumb: View? = null
        var pinnedThumb: View? = null
        val requestGeneration = generation
        for (wallpaper in store.all()) {
            val frame = FrameLayout(activity)
            frame.setPadding(dp(3), dp(3), dp(3), dp(3))
            frame.foreground = activity.getDrawable(R.drawable.thumb_frame)
            frame.isFocusable = true
            frame.isClickable = true
            val image = ImageView(activity)
            image.scaleType = ImageView.ScaleType.CENTER_CROP
            image.setBackgroundColor(0x33FFFFFF)
            frame.addView(image, FrameLayout.LayoutParams(dp(160), dp(90)))
            if (wallpaper.key == pinnedKey) {
                val mark = newText(I18n.t("wallpaper.kept"), 11f, 0xFFFFFFFF.toInt())
                mark.setBackgroundColor(0x99000000.toInt())
                mark.setPadding(dp(6), dp(2), dp(6), dp(2))
                frame.addView(mark, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START))
                pinnedThumb = frame
            }
            frame.setOnClickListener {
                store.pinnedKey = wallpaper.key
                store.mode = WallpaperStore.MODE_PINNED
                close()
                onWallpaperChanged()
            }
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.marginEnd = dp(10)
            row.addView(frame, params)
            if (firstThumb == null) {
                firstThumb = frame
            }

            thumbLoader.execute {
                val bitmap = store.loadThumb(wallpaper)
                handler.post {
                    if (bitmap == null) {
                        return@post
                    }
                    if (requestGeneration != generation) {
                        bitmap.recycle()
                        return@post
                    }
                    thumbs.add(bitmap)
                    image.setImageBitmap(bitmap)
                }
            }
        }

        show(panel, Gravity.BOTTOM, matchWidth = true)
        val focusTarget = pinnedThumb ?: firstThumb
        focusTarget?.requestFocus()
    }

    // ---------- colour buttons ----------

    /** Assigns the tile to the red, green, yellow or blue remote button. */
    fun openButtonPicker(item: DockItem) {
        val settings = app.settings
        val panel = newPanel(I18n.t("buttons.title", "app" to item.label))
        for (color in HomeSettings.BUTTON_COLORS) {
            val current = settings.buttonTarget(color)
            val colorName = I18n.t("tv.color.$color")
            val label = I18n.t("tv.color_button", "color" to colorName.replaceFirstChar { letter -> letter.uppercase() })
            addAction(panel, checked(label, current == item.key)) {
                settings.setButtonTarget(color, item.key)
                close()
            }
        }
        for (color in HomeSettings.BUTTON_COLORS) {
            if (settings.buttonTarget(color) == item.key) {
                addAction(panel, I18n.t("buttons.remove", "color" to I18n.t("tv.color.$color"))) {
                    settings.setButtonTarget(color, null)
                    close()
                }
            }
        }
        show(panel, Gravity.START or Gravity.BOTTOM)
    }

    // ---------- icon picker ----------

    fun openIconPicker(item: DockItem) {
        val icons = app.icons
        val chosenId = icons.chosenId(item.key)
        val panel = newPanel(I18n.t("icons.title", "app" to item.label))
        var currentTile: View? = null

        val automaticLabel = if (item.packageName != null) I18n.t("icons.original") else I18n.t("icons.automatic")
        val automaticIcon = item.originalIcon ?: icons.resolve("", null, IconStore.deviceType(item.label))
        val automaticRow = newIconRow(panel, automaticLabel)
        val automaticTile = addIconTile(automaticRow, automaticIcon, chosenId == null) {
            icons.reset(item.key)
            close()
            onDockChanged()
        }
        if (chosenId == null) {
            currentTile = automaticTile
        }

        var currentGroup = ""
        var row: LinearLayout? = null
        for (choice in icons.choices()) {
            if (choice.group != currentGroup) {
                currentGroup = choice.group
                row = newIconRow(panel, I18n.t("icons.group." + choice.group))
            }
            val targetRow = row ?: continue
            val isCurrent = choice.id == chosenId
            val tile = addIconTile(targetRow, icons.load(choice.id), isCurrent) {
                icons.choose(item.key, choice.id)
                close()
                onDockChanged()
            }
            if (isCurrent) {
                currentTile = tile
            }
        }

        val hint = newText(I18n.t("icons.hint"), 12f, 0x99FFFFFF.toInt())
        hint.setPadding(dp(8), dp(10), 0, 0)
        panel.addView(hint)

        show(panel, Gravity.BOTTOM, matchWidth = true)
        currentTile?.requestFocus()
    }

    /** One group: its name on the left, its icons in a row to the right. */
    private fun newIconRow(panel: LinearLayout, title: String): LinearLayout {
        val line = LinearLayout(activity)
        line.orientation = LinearLayout.HORIZONTAL
        line.gravity = Gravity.CENTER_VERTICAL
        line.setPadding(0, dp(2), 0, dp(2))

        val label = newText(title, 13f, 0xB3FFFFFF.toInt())
        label.setPadding(dp(8), 0, 0, 0)
        line.addView(label, LinearLayout.LayoutParams(dp(96), ViewGroup.LayoutParams.WRAP_CONTENT))

        val scroll = HorizontalScrollView(activity)
        scroll.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(activity)
        row.orientation = LinearLayout.HORIZONTAL
        scroll.addView(row)
        line.addView(scroll)
        panel.addView(line)
        return row
    }

    private fun addIconTile(row: LinearLayout, icon: Drawable?, isCurrent: Boolean, action: () -> Unit): View {
        val frame = FrameLayout(activity)
        frame.setPadding(dp(3), dp(3), dp(3), dp(3))
        frame.foreground = activity.getDrawable(R.drawable.thumb_frame)
        frame.isFocusable = true
        frame.isClickable = true
        val image = ImageView(activity)
        image.setImageDrawable(icon)
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        image.setBackgroundResource(R.drawable.tile_clip)
        image.clipToOutline = true
        frame.addView(image, FrameLayout.LayoutParams(dp(44), dp(44)))
        if (isCurrent) {
            val mark = newText("✓", 13f, 0xFFFFFFFF.toInt())
            mark.setBackgroundColor(0xB3000000.toInt())
            mark.setPadding(dp(5), 0, dp(5), 0)
            frame.addView(mark, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END))
        }
        frame.setOnClickListener { action() }
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.marginEnd = dp(8)
        row.addView(frame, params)
        return frame
    }

    // ---------- photos from the phone ----------

    /** What the "set up from your phone" panel shows. */
    class PhoneInfo(
        val signInLink: String?,
        val friendlyAddress: String,
        val ipAddress: String?,
        val code: String,
        val onNewCode: () -> Unit,
    )

    fun openPhoneInfo() {
        val server = app.server
        showPhoneInfo(PhoneInfo(
            signInLink = server.signInLink(),
            friendlyAddress = server.friendlyAddress(),
            ipAddress = server.address(),
            code = server.code,
            onNewCode = { server.signInGuard.resetCode() },
        ))
    }

    fun showPhoneInfo(info: PhoneInfo) {
        val panel = newPanel(I18n.t("phone.title"))

        val row = LinearLayout(activity)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL

        val link = info.signInLink
        if (link != null) {
            val qr = ImageView(activity)
            qr.setImageBitmap(qrBitmap(link))
            qr.contentDescription = I18n.t("phone.qr_description")
            val qrParams = LinearLayout.LayoutParams(dp(150), dp(150))
            qrParams.marginEnd = dp(24)
            row.addView(qr, qrParams)
        }

        val steps = LinearLayout(activity)
        steps.orientation = LinearLayout.VERTICAL
        if (link != null) {
            steps.addView(newText(I18n.t("phone.scan"), 15f, 0xFFFFFFFF.toInt()))
            steps.addView(newText(I18n.t("phone.scan_detail"), 13f, 0xB3FFFFFF.toInt()))
            val orType = newText(I18n.t("phone.or_open"), 13f, 0xB3FFFFFF.toInt())
            orType.setPadding(0, dp(16), 0, 0)
            steps.addView(orType)
        } else {
            steps.addView(newText(I18n.t("phone.no_wifi"), 15f, 0xFFFFFFFF.toInt()))
        }

        val address = newText(info.friendlyAddress.removePrefix("http://"), 24f, 0xFFFFFFFF.toInt())
        address.typeface = activity.resources.getFont(R.font.instrument_serif_regular)
        steps.addView(address)
        val ip = info.ipAddress
        if (ip != null) {
            steps.addView(newText(I18n.t("phone.or_ip", "address" to ip.removePrefix("http://")), 12f, 0x99FFFFFF.toInt()))
        }

        val codeLabel = newText(I18n.t("phone.code"), 13f, 0xB3FFFFFF.toInt())
        codeLabel.setPadding(0, dp(12), 0, 0)
        steps.addView(codeLabel)
        val code = newText(info.code, 24f, 0xFFFFFFFF.toInt())
        code.typeface = activity.resources.getFont(R.font.instrument_serif_regular)
        code.letterSpacing = 0.12f
        steps.addView(code)
        row.addView(steps)

        // Space before the buttons as padding, not a spacer view: a narrow spacer
        // is the only child of the panel without MATCH_PARENT, so the panel would
        // take its 1px width and squeeze the QR code into a line.
        row.setPadding(0, 0, 0, dp(14))
        panel.addView(row)
        // Done first: it has the focus, so a stray OK never signs phones out.
        addAction(panel, I18n.t("menu.done")) { close() }
        addAction(panel, I18n.t("phone.open_here")) { openSettings() }
        addAction(panel, I18n.t("phone.new_code")) { info.onNewCode() }
        show(panel, Gravity.CENTER)
        showingPhoneInfo = true
    }

    /** The phone website on the TV itself, worked with the remote. */
    fun openSettings() {
        close()
        activity.startActivity(app.settingsIntent(activity))
    }

    /** The code changed (a reset, or too many wrong guesses): show the new one. */
    fun codeChanged() {
        if (showingPhoneInfo) {
            openPhoneInfo()
        }
    }

    /** Black modules on white with the quiet zone QR readers need. */
    private fun qrBitmap(text: String): Bitmap {
        val modules = QrModules.of(text, border = 2)
        val side = modules.size
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        for (y in 0 until side) {
            for (x in 0 until side) {
                val color = if (modules[y][x]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                bitmap.setPixel(x, y, color)
            }
        }
        // Scaled up without smoothing, so the modules stay sharp.
        val scaled = Bitmap.createScaledBitmap(bitmap, side * 8, side * 8, false)
        bitmap.recycle()
        return scaled
    }

    // ---------- building blocks ----------

    private fun newPanel(title: String): LinearLayout {
        close()
        val panel = LinearLayout(activity)
        panel.orientation = LinearLayout.VERTICAL
        panel.setBackgroundResource(R.drawable.panel)
        panel.setPadding(dp(20), dp(16), dp(20), dp(16))
        val heading = newText(title, 22f, 0xFFFFFFFF.toInt())
        heading.typeface = activity.resources.getFont(R.font.instrument_serif_regular)
        heading.setPadding(dp(8), 0, 0, dp(8))
        panel.addView(heading)
        return panel
    }

    private fun addAction(parent: LinearLayout, label: String, action: () -> Unit) {
        val button = newText(label, 15f, 0)
        button.setTextColor(activity.resources.getColorStateList(R.color.menu_text, activity.theme))
        button.setBackgroundResource(R.drawable.menu_item)
        button.setPadding(dp(12), dp(8), dp(12), dp(8))
        button.isFocusable = true
        button.isClickable = true
        button.setOnClickListener { action() }
        parent.addView(button)
    }

    private fun checked(label: String, isChecked: Boolean): String {
        return if (isChecked) "✓ $label" else label
    }

    private fun newText(text: String, sizeDp: Float, color: Int): TextView {
        val view = TextView(activity)
        view.text = text
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp)
        view.setTextColor(color)
        return view
    }

    private fun show(panel: LinearLayout, gravity: Int, matchWidth: Boolean = false) {
        container.setBackgroundColor(0x66000000)
        val width = if (matchWidth) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        val params = FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, gravity)
        params.setMargins(dp(44), dp(32), dp(44), dp(32))
        container.addView(panel, params)
        container.visibility = View.VISIBLE
        onOpened()
        focusFirstAction(panel)
    }

    private fun focusFirstAction(view: View): Boolean {
        if (view.isFocusable && view !is ViewGroup) {
            return view.requestFocus()
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                if (focusFirstAction(view.getChildAt(index))) {
                    return true
                }
            }
        }
        return false
    }

    private fun dp(value: Int): Int {
        return (value * activity.resources.displayMetrics.density).toInt()
    }
}
