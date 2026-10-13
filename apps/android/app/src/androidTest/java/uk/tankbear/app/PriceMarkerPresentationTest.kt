package uk.tankbear.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import uk.tankbear.app.ui.priceMarker
import uk.tankbear.app.ui.theme.LightTankBearColors
import uk.tankbear.app.ui.theme.DarkTankBearColors
import androidx.compose.ui.graphics.toArgb

@RunWith(AndroidJUnit4::class)
class PriceMarkerPresentationTest {
    @Test fun pillsRenderBothThemesAndLargeTextWithoutChangingPriceColourOnSelection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for ((name, colors) in listOf("light" to LightTankBearColors, "dark" to DarkTankBearColors)) {
            val sheet = Bitmap.createBitmap(840, 920, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet)
            canvas.drawColor(colors.background.toArgb())
            val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.text.toArgb(); textSize = 26f }
            for ((row, scale) in listOf(1f, 2f).withIndex()) {
                canvas.drawText("$name · ${if (scale == 1f) "normal" else "200% text"}", 24f, 40f + row * 440f, title)
                for ((index, fill) in listOf(colors.pumpBelow, colors.pumpAround, colors.pumpAbove, colors.pumpUnknown).withIndex()) {
                    val label = if (index == 3) "—" else "172.9p"
                    val ink = if (name == "dark") colors.background else colors.surface
                    val normal = priceMarker(context, label, fill.toArgb(), ink.toArgb(), colors.surface.toArgb(), false, scale)
                    val selected = priceMarker(context, label, fill.toArgb(), ink.toArgb(), colors.text.toArgb(), true, scale)
                    assertEquals(320, normal.density)
                    assertTrue(selected.width >= normal.width)
                    assertTrue(normal.width / 2f < 360f)
                    // A background sample inside the pill retains the same semantic fill.
                    assertEquals(fill.toArgb(), normal.getPixel(normal.width / 2, 8))
                    assertEquals(fill.toArgb(), selected.getPixel(selected.width / 2, 8))
                    val y = 60f + row * 440f + index * 88f
                    canvas.drawBitmap(normal, null, android.graphics.RectF(24f, y, 24f + normal.width / 2, y + normal.height / 2), null)
                    canvas.drawBitmap(selected, null, android.graphics.RectF(440f, y, 440f + selected.width / 2, y + selected.height / 2), null)
                }
            }
            java.io.File(context.cacheDir, "price-pills-$name.png").outputStream().use {
                sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
