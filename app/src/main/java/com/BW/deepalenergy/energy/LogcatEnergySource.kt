package com.BW.deepalenergy.energy

import org.json.JSONObject

/**
 * Чтение сигналов из logcat: строки вида
 * I MessageProperty: receive EnergyInfo/SocPercent, value : CarPropertyValue{id=..., area=..., status=0, value=9.0, time=..., ...}
 * Нужно разрешение READ_LOGS, выданное через adb pm grant.
 */
private val MESSAGE_LINE = Regex(
    """MessageProperty:\s+receive\s+([A-Za-z0-9_/]+),\s+value\s*:\s*CarPropertyValue\{id=0x[0-9a-fA-F]+,\s*area=[^,]*,\s*status=\d+,\s*value=([^,}]+)"""
)

fun readSignalsFromLogcat(): DumpsysResult {
    val output = try {
        val process = ProcessBuilder("logcat", "-d", "-s", "MessageProperty:I", "-t", "3000").redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        text
    } catch (e: Exception) {
        return DumpsysResult(emptyMap(), "logcat недоступен: ${e.javaClass.simpleName} ${e.message}")
    }
    val signals = LinkedHashMap<String, JSONObject>()
    output.lineSequence().forEach { line ->
        val m = MESSAGE_LINE.find(line) ?: return@forEach
        val (name, raw) = m.destructured
        val number = raw.trim().toDoubleOrNull() ?: return@forEach
        signals[name] = JSONObject().put("value", number)
    }
    return if (signals.isEmpty()) {
        DumpsysResult(emptyMap(), "в logcat нет MessageProperty (проверьте READ_LOGS)")
    } else {
        DumpsysResult(signals)
    }
}
