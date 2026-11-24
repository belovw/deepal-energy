package com.BW.deepalenergy

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Рисует главный экран в PNG без эмулятора (Robolectric), чтобы проверить расположение блоков.
 * Файл: app/build/screenshots/main_screen.png. Значения выдуманные, для проверки вёрстки.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainScreenScreenshotTest {

    @Test
    fun renderMainScreen() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = OriginalEnergyView(context)
        view.setSnapshot(
            FuelEnergySnapshot(
                avgFuelTrip1 = 6.4f,
                avgFuelTrip2 = 6.2f,
                fuelPercent = 47,
                oilRangeKm = 410,
                totalRangeKm = null,
                odometerKm = 5088f,
                trip1DistanceKm = 42f,
                trip1AvgSpeed = 48f,
                trip1DurationMinutes = 55,
                trip2DistanceKm = 12f,
                trip2AvgSpeed = 36f,
                trip2DurationMinutes = 20,
                singleTripResetOption = null,
                diagnostics = emptyList(),
                tractionKw = 95f,
            ),
            isPreview = true,
        )

        val width = 1920
        val height = 1080
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))

        val out = File("build/screenshots/main_screen.png")
        out.parentFile?.mkdirs()
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
