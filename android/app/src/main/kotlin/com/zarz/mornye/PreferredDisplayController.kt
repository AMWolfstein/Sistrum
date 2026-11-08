package com.zarz.mornye

import android.app.Activity
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display

internal data class PreferredMode(val id: Int, val width: Int, val height: Int, val hz: Float)

/** A window preference only: neither a system setting nor a presentation guarantee. */
internal fun preferred60Mode(modes: List<PreferredMode>, current: PreferredMode): PreferredMode? =
    modes.filter { it.width == current.width && it.height == current.height && kotlin.math.abs(it.hz - 60f) <= 1f }
        .minByOrNull { kotlin.math.abs(it.hz - 60f) }

/** Asks the window for the 60Hz mode at the current resolution, and asks again when the display changes. */
internal class PreferredDisplayController(
    private val activity: Activity,
) : DisplayManager.DisplayListener {
    private val manager = activity.getSystemService(DisplayManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var listening = false

    @Suppress("DEPRECATION")
    private fun display(): Display = activity.windowManager.defaultDisplay
    private fun Display.Mode.model() = PreferredMode(modeId, physicalWidth, physicalHeight, refreshRate)

    fun start() {
        if (!listening) {
            manager.registerDisplayListener(this, handler)
            listening = true
        }
        applyPreference()
    }

    fun stop() {
        if (listening) manager.unregisterDisplayListener(this)
        listening = false
    }

    fun close() = stop()

    fun applyPreference() {
        val display = display()
        val target = preferred60Mode(display.supportedModes.map { it.model() }, display.mode.model())
        val attributes = activity.window.attributes
        val wanted = target?.id ?: 0
        if (attributes.preferredDisplayModeId != wanted || attributes.preferredRefreshRate != 0f) {
            attributes.preferredDisplayModeId = wanted
            attributes.preferredRefreshRate = 0f
            activity.window.attributes = attributes
        }
    }

    override fun onDisplayChanged(displayId: Int) {
        if (displayId == display().displayId) applyPreference()
    }
    override fun onDisplayAdded(displayId: Int) = Unit
    override fun onDisplayRemoved(displayId: Int) = Unit
}
