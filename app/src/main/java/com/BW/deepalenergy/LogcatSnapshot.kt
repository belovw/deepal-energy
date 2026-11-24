package com.BW.deepalenergy

import com.BW.deepalenergy.energy.tractionKwFromPercent
import org.json.JSONObject

/**
 * Дополняет снимок данными из logcat (MessageProperty), когда прямое чтение CarPropertyManager
 * ничего не дало. Значения из logcat перекрывают только те поля, которые известны по живому захвату.
 * Единицы: см. claude/loger-handoff.md.
 */
fun FuelEnergySnapshot.withLogcatSignals(signals: Map<String, JSONObject>): FuelEnergySnapshot {
    fun num(name: String): Double? = signals[name]?.optDouble("value", Double.NaN)?.takeIf { !it.isNaN() }
    val fuelPercent = num("EnergyInfo/FuelPercent")?.toInt() ?: fuelPercent
    val fuelRange = num("EnergyInfo/FuelResidualRange")?.toInt() ?: oilRangeKm
    val avgFuel = num("DrivingInfo/FuelDriveCycleAverageConsumption")?.toFloat() ?: avgFuelTrip2
    val odometer = num("DrivingInfo/TotalDriveDistance")?.toFloat() ?: odometerKm
    val tripDistance = num("DrivingInfo/FuelTripDriveRange")?.toFloat() ?: trip2DistanceKm
    val traction = num("DrivingInfo/EnginePower")?.let { tractionKwFromPercent(it).toFloat() } ?: tractionKw
    return copy(
        avgFuelTrip2 = avgFuel,
        fuelPercent = fuelPercent,
        oilRangeKm = fuelRange,
        odometerKm = odometer,
        trip2DistanceKm = tripDistance,
        tractionKw = traction,
        diagnostics = diagnostics + "logcat signals=${signals.size}",
    )
}
