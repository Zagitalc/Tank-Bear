package uk.tankbear.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Path
import android.graphics.Typeface
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

/** Price and pump are one collision-tested symbol. Density is explicit: pixels are not dp. */
internal fun priceMarker(context: Context, label: String, colour: Int, ink: Int,
                         border: Int, selected: Boolean, fontScale: Float): Bitmap {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = (if (selected) 16f else 14f) * fontScale
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    val icon = if (selected) 22f else 20f
    val height = maxOf(36f, paint.fontMetrics.descent - paint.fontMetrics.ascent + 16f)
    val width = kotlin.math.ceil(12f + icon + 6f + paint.measureText(label) + 12f)
    val bitmap = Bitmap.createBitmap((width * 2).toInt(), ((height + 12f) * 2).toInt(), Bitmap.Config.ARGB_8888)
    bitmap.density = 320 // 2 pixels per logical dp, understood by MapLibre Style.addImage.
    val canvas = Canvas(bitmap).apply { scale(2f, 2f) }
    val pointer = Path().apply {
        moveTo(width / 2 - 6, height - 2)
        lineTo(width / 2, height + 7)
        lineTo(width / 2 + 6, height - 2)
        close()
    }
    paint.color = border
    canvas.drawPath(pointer, paint)
    canvas.drawRoundRect(RectF(0f, 0f, width, height), height / 2, height / 2, paint)
    val stroke = if (selected) 3f else 1.5f
    paint.color = colour
    canvas.drawRoundRect(RectF(stroke, stroke, width - stroke, height - stroke), height / 2, height / 2, paint)
    requireNotNull(context.getDrawable(uk.tankbear.app.R.drawable.ic_petrol_station)).mutate().apply {
        setTint(ink)
        val top = ((height - icon) / 2).toInt()
        setBounds(12, top, (12 + icon).toInt(), (top + icon).toInt())
        draw(canvas)
    }
    paint.color = ink
    canvas.drawText(label, 12f + icon + 6f, height / 2 - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2, paint)
    return bitmap
}
