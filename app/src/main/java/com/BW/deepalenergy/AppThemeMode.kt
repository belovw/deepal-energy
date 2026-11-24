package com.BW.deepalenergy

internal enum class AppThemeMode(val labelRes: Int) {
    SYSTEM(R.string.ui_theme_system), LIGHT(R.string.ui_theme_light), DARK(R.string.ui_theme_dark);

    fun isLight(systemNight: Boolean): Boolean = when (this) {
        SYSTEM -> !systemNight
        LIGHT -> true
        DARK -> false
    }

    fun next(): AppThemeMode = when (this) {
        SYSTEM -> LIGHT
        LIGHT -> DARK
        DARK -> SYSTEM
    }

    companion object {
        fun fromStored(value: String?): AppThemeMode = values().firstOrNull { it.name == value } ?: SYSTEM
    }
}
