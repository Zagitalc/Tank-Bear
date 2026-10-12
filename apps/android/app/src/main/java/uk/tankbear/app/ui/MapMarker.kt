package uk.tankbear.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.annotation.DrawableRes

/** Self-contained map symbols, with a contrasting border visible against both roads and route lines. */
internal fun mapMarker(context: Context, @DrawableRes icon: Int, colour: Int, selected: Boolean = false, ink: Int = Color.WHITE): Bitmap {
    val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = ink
    canvas.drawCircle(24f, 24f, 23f, paint)
    paint.color = colour
    canvas.drawCircle(24f, 24f, if (selected) 20f else 18f, paint)
    requireNotNull(context.getDrawable(icon)).mutate().apply {
        setTint(ink)
        setBounds(10, 10, 38, 38)
        draw(canvas)
    }
    return bitmap
}
