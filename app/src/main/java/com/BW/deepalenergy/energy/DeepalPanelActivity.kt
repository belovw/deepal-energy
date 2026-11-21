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
import java.util.concurrent.TimeUnit

/** Тестовый экран новых сигналов Deepal. Ничего не заменяет, только показывает значения. */
class DeepalPanelActivity : AppCompatActivity() {
    private val source = DumpsysEnergySource()
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val known = HashMap<String, JSONObject>()
    private var previousDriveTime: Double? = null
    private lateinit var text: TextView
    @Volatile private var diagText: String = "Диагностика: запускается…"

    /** Проверяет, какие сервисы видит приложение и открыт ли порт 40004 из старого журнала. */
    private fun runDiagnostics(): String {
        fun grep(cmd: String, label: String): String = try {
            val p = ProcessBuilder("sh", "-c", cmd).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor(5, TimeUnit.SECONDS)
            val all = out.lines()
            val hits = all.filter {
                it.contains("car", true) || it.contains("mega", true) || it.contains("energy", true)
            }
            "$label: строк ${all.size}, подходящих ${hits.size}\n" + hits.take(8).joinToString("\n")
        } catch (e: Exception) {
            "$label: ошибка ${e.javaClass.simpleName}: ${e.message}"
        }
        val port = try {
            java.net.Socket().use { s ->
                s.connect(java.net.InetSocketAddress("127.0.0.1", 40004), 2000)
                "порт 40004: ОТКРЫТ"
            }
        } catch (e: Exception) {
            "порт 40004: закрыт (${e.javaClass.simpleName})"
        }
        return listOf(
            grep("service list", "service list"),
            grep("dumpsys -l", "dumpsys -l"),
            port,
        ).joinToString("\n")
    }

    private val tick = object : Runnable {
        override fun run() {
            worker.execute {
                val result = readSignalsFromFile()
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
        setContentView(android.widget.ScrollView(this).apply { addView(text) })
        if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        worker.execute { diagText = runDiagnostics() }
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
            "",
            "Диагностика:",
            diagText,
        ).joinToString("\n")
    }

    private fun pretty(v: Any): String = when (v) {
        is Double -> String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
        is Float -> String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
        else -> v.toString()
    }
}
