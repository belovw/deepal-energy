package com.BW.deepalenergy.energy

import org.json.JSONObject

/**
 * Вариант A: чтение живых CAN-сигналов через `dumpsys com.mega.car.CarService`.
 *
 * Формат строк (по наблюдению из прошлого проекта, нужно сверить с реальным выводом):
 *   Группа/Сигнал, {"value": ...}
 *
 * ВАЖНО: обычное приложение обычно не может запускать dumpsys с правами shell.
 * Этот класс работает, если приложению доступен вывод dumpsys (например, при запуске
 * с правами системы или через ADB-мост). Если Runtime.exec вернёт пусто или ошибку,
 * источник помечается как недоступный и никаких данных не выдумывает.
 */
data class EnergySnapshot(
    val socPercent: Float? = null,
    val fuelRangeKm: Int? = null,
    val engineStatus: Int? = null,        // 0=STOP, 1=CRANK, 2=RUNNING (GW_1E5/EmsEngSts)
    val powerFlowDirection: Int? = null,  // EnergyInfo/PowerFlowDirection
    val fuelRechargeFlag: Boolean? = null, // VCU_2A1/VcuFuelrechargeFlag
    val gear: Int? = null,                // DrivingInfo/Gear
    val error: String? = null,
)

object MegaCarDumpParser {
    private val LINE = Regex("""^\s*([A-Za-z0-9_]+/[A-Za-z0-9_]+)\s*,\s*(\{.*\})\s*$""")

    /** Возвращает последнее значение каждого сигнала из текста dumpsys. */
    fun parse(text: String): Map<String, JSONObject> {
        val result = LinkedHashMap<String, JSONObject>()
        text.lineSequence().forEach { line ->
            val match = LINE.find(line) ?: return@forEach
            val (name, json) = match.destructured
            runCatching { JSONObject(json) }.getOrNull()?.let { result[name] = it }
        }
        return result
    }
}

class DumpsysEnergySource {
    fun read(): EnergySnapshot {
        val output = runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "dumpsys com.mega.car.CarService"))
            val text = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            text
        }.getOrElse { return EnergySnapshot(error = "dumpsys недоступен: ${it.javaClass.simpleName}") }

        if (output.isBlank()) return EnergySnapshot(error = "dumpsys вернул пустой вывод")

        val signals = MegaCarDumpParser.parse(output)
        fun value(name: String): Any? = signals[name]?.opt("value")?.takeIf { it != JSONObject.NULL }

        return EnergySnapshot(
            socPercent = (value("EnergyInfo/SocPercent") as? Number)?.toFloat(),
            fuelRangeKm = (value("EnergyInfo/FuelResidualRange") as? Number)?.toInt(),
            engineStatus = (value("GW_1E5/EmsEngSts") as? Number)?.toInt(),
            powerFlowDirection = (value("EnergyInfo/PowerFlowDirection") as? Number)?.toInt(),
            fuelRechargeFlag = (value("VCU_2A1/VcuFuelrechargeFlag") as? Boolean)
                ?: (value("VCU_2A1/VcuFuelrechargeFlag") as? Number)?.let { it.toInt() != 0 },
            gear = (value("DrivingInfo/Gear") as? Number)?.toInt(),
            error = if (signals.isEmpty()) "в выводе dumpsys нет сигналов MessageProperty" else null,
        )
    }
}

data class DumpsysResult(val signals: Map<String, JSONObject>, val error: String? = null)

/** Все последние значения сигналов из dumpsys, без выбора конкретных полей. */
fun DumpsysEnergySource.readSignals(): DumpsysResult {
    var stderrText = ""
    var exitCode = -1
    val output = runCatching {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "dumpsys com.mega.car.CarService"))
        val text = process.inputStream.bufferedReader().use { it.readText() }
        stderrText = process.errorStream.bufferedReader().use { it.readText() }
        exitCode = process.waitFor()
        text
    }.getOrElse { return DumpsysResult(emptyMap(), "dumpsys недоступен: ${it.javaClass.simpleName} ${it.message}") }
    if (output.isBlank()) {
        val err = stderrText.trim().take(200)
        return DumpsysResult(emptyMap(), "dumpsys пустой, код $exitCode, stderr: ${if (err.isEmpty()) "нет" else err}")
    }
    return DumpsysResult(MegaCarDumpParser.parse(output))
}
