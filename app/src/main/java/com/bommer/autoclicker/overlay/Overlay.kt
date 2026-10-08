package com.bommer.autoclicker.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager

fun Context.dp(v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

/** Paramètres d'une fenêtre flottante d'accessibilité (aucune permission « superposition » requise). */
fun overlayParams(width: Int, height: Int, x: Int, y: Int, touchable: Boolean = true) = WindowManager.LayoutParams(
    width, height,
    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
    baseFlags(touchable),
    PixelFormat.TRANSLUCENT,
).apply {
    gravity = Gravity.TOP or Gravity.START
    this.x = x
    this.y = y
    if (Build.VERSION.SDK_INT >= 28) {
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
}

fun baseFlags(touchable: Boolean): Int {
    var f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    if (!touchable) f = f or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    return f
}

/** Taille physique de l'écran en pixels. */
fun screenSize(wm: WindowManager): Pair<Int, Int> =
    if (Build.VERSION.SDK_INT >= 30) {
        wm.currentWindowMetrics.bounds.let { it.width() to it.height() }
    } else {
        val m = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(m)
        m.widthPixels to m.heightPixels
    }
