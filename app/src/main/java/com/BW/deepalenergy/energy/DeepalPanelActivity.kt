package com.BW.deepalenergy.energy

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Тестовый экран новых сигналов Deepal. Ничего не заменяет, только показывает значения. */
class DeepalPanelActivity : AppCompatActivity() {
    private val source = DumpsysEnergySource()
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val known = HashMap<String, JSONObject>()
    private var previousDriveTime: Double? = null
    private lateinit var text: TextView

    private val tick = object : Runnable {
        override fun run() {
            worker.execute {
                val result = source.readSignals()
                runOnUiThread { show(result) }
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        text = TextView(this).apply {
            setBackgroundColor(Color.parseColor("#09111A"))
            setTextColor(Color.WHITE)
            textSize = 20f
            setPadding(48, 48, 48, 48)
            gravity = Gravity.START
        }
        setContentView(text)
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun show(result: DumpsysResult) {
        if (isFinishing) return
        known.putAll(result.signals)

        fun value(name: String): Any? = known[name]?.opt("value")?.takeIf { it != JSONObject.NULL }
        fun line(label: String, name: String, unit: String = ""): String {
            val v = value(name) ?: return "$label: нет данных"
            return "$label: ${pretty(v)}$unit"
        }

        val driveTime: Double? = (value("DrivingInfo/FuelTotalDriveTime") as? Number)?.toDouble()
        val prev = previousDriveTime
        val driveLine = if (driveTime == null) {
            "Счётчик работы ДВС: нет данных"
        } else if (prev == null) {
            "Счётчик работы ДВС: ${pretty(driveTime)}"
        } else if (driveTime > prev) {
            "Счётчик работы ДВС: ${pretty(driveTime)} (растёт — ДВС, вероятно, работает)"
        } else {
            "Счётчик работы ДВС: ${pretty(driveTime)} (не меняется)"
        }
        if (driveTime != null) previousDriveTime = driveTime

        val status = if (result.error == null) "чтение OK" else "ошибка: ${result.error}"
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

        text.text = listOf(
            "Панель Deepal — новые сигналы (тест)",
            "Обновлено: $time, $status",
            "",
            line("Заряд батареи (SOC)", "EnergyInfo/SocPercent", " %"),
            line("Электро-запас хода", "EnergyInfo/SocResidualRange", " км"),
            line("Запас хода на бензине", "EnergyInfo/FuelResidualRange", " км"),
            line("Датчик топлива EnergyInfo/FuelLevel (единицы уточняются)", "EnergyInfo/FuelLevel"),
            driveLine,
            line("Выработка генератора VCU_318", "VCU_318/DrvgCycTotGenrEgy"),
            line("Мощность батареи VCU_1A1", "VCU_1A1/VcuExtHvPwrCnsAct"),
            line("Напряжение 12 В", "EnergyInfo/LowVoltBatteryVoltage", " В"),
        ).joinToString("\n")
    }

    private fun pretty(v: Any): String = when (v) {
        is Double -> String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
        is Float -> String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
        else -> v.toString()
    }
}
