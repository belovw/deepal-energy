package com.BW.deepalenergy.energy

/**
 * Пересчёт тяговой мощности из процентов (EnergyInfo/EnginePower, 0x5000030) в киловатты.
 * 100% соответствует пиковой мощности тягового мотора.
 * Значение 175 кВт выбрано владельцем автомобиля; при смене комплектации поменять здесь.
 */
const val TRACTION_PEAK_KW = 175.0

/** Мощность в кВт из процентов. Отрицательные значения (рекуперация) сохраняются как есть. */
fun tractionKwFromPercent(percent: Double): Double = percent * TRACTION_PEAK_KW / 100.0
