package com.dsikic.cluecose.core.dataplane

/**
 * 1:1 port of the Libre 3 status enums from LibreCRKit
 * (RealtimeGlucoseReading.swift + supporting enums).
 */

/** Raw 16-bit sensor value → displayable mg/dL classification. */
sealed class Libre3GlucoseValueStatus {
    data class Valid(val mgDL: Int) : Libre3GlucoseValueStatus()
    data class BelowDisplayRange(val raw: Int, val display: Int) : Libre3GlucoseValueStatus()
    data class AboveDisplayRange(val raw: Int, val display: Int) : Libre3GlucoseValueStatus()
    data class Unavailable(val raw: Int) : Libre3GlucoseValueStatus()

    companion object {
        @JvmStatic
        fun fromRawSensorValue(rawSensorValue: Int): Libre3GlucoseValueStatus = when {
            rawSensorValue in 1..38 -> BelowDisplayRange(rawSensorValue, 39)
            rawSensorValue in 39..501 -> Valid(rawSensorValue)
            rawSensorValue in 502..999 -> AboveDisplayRange(rawSensorValue, 501)
            else -> Unavailable(rawSensorValue)
        }
    }

    val displayMgDL: Int?
        get() = when (this) {
            is Valid -> mgDL
            is BelowDisplayRange -> display
            is AboveDisplayRange -> display
            is Unavailable -> null
        }

    val isDisplayable: Boolean
        get() = displayMgDL != null

    override fun toString(): String = when (this) {
        is Valid -> "valid($mgDL)"
        is BelowDisplayRange -> "belowDisplayRange(raw=$raw, displayMgDL=$display)"
        is AboveDisplayRange -> "aboveDisplayRange(raw=$raw, displayMgDL=$display)"
        is Unavailable -> "unavailable(raw=$raw)"
    }
}

enum class Libre3DataQualityError(val rawValue: Int, val isGood: Boolean, val isNotDisplayable: Boolean) {
    GOOD(0, true, false),
    SENSOR_TOO_HOT(0xA000, false, true),
    SENSOR_TOO_COLD(0xC000, false, true),
    NOT_DISPLAYABLE(0x8000, false, true),
    RAW(0, false, false);

    companion object {
        fun fromRaw(rawValue: Int): Libre3DataQualityError = when {
            rawValue == 0 -> GOOD
            (rawValue and 0xE000) == 0xA000 -> SENSOR_TOO_HOT
            (rawValue and 0xE000) == 0xC000 -> SENSOR_TOO_COLD
            (rawValue and 0x8000) != 0 -> NOT_DISPLAYABLE
            else -> RAW
        }
    }

    // Callers keep the exact raw word alongside (e.g. RealtimeGlucoseReading.dqErrorRaw).

    override fun toString(): String = when (this) {
        GOOD -> "good"
        SENSOR_TOO_HOT -> "sensorTooHot"
        SENSOR_TOO_COLD -> "sensorTooCold"
        NOT_DISPLAYABLE -> "notDisplayable"
        RAW -> "raw"
    }
}

enum class Libre3SensorCondition(val rawValue: Int) {
    OK(0),
    INVALID(1),
    ESA(2),
    RAW(3);

    companion object {
        fun fromRaw(raw: Int): Libre3SensorCondition = when (raw) {
            0 -> OK
            1 -> INVALID
            2 -> ESA
            else -> RAW
        }
    }
}

enum class Libre3ResultRangeStatus(val rawValue: Int) {
    IN_RANGE(0),
    BELOW_RANGE(1),
    ABOVE_RANGE(2),
    RAW(3);

    companion object {
        fun fromRaw(raw: Int): Libre3ResultRangeStatus = when (raw) {
            0 -> IN_RANGE
            1 -> BELOW_RANGE
            2 -> ABOVE_RANGE
            else -> RAW
        }
    }
}

enum class Libre3ActionableStatus(val rawValue: Int) {
    NOT_ACTIONABLE(0),
    ACTIONABLE(1),
    RAW(2);

    companion object {
        fun fromRaw(raw: Int): Libre3ActionableStatus = when (raw) {
            0 -> NOT_ACTIONABLE
            1 -> ACTIONABLE
            else -> RAW
        }
    }
}

enum class Libre3Trend(val rawValue: Int) {
    NOT_DETERMINED(0),
    FALLING_QUICKLY(1),
    FALLING(2),
    STABLE(3),
    RISING(4),
    RISING_QUICKLY(5),
    RAW(6);

    companion object {
        fun fromRaw(raw: Int): Libre3Trend = when (raw) {
            0 -> NOT_DETERMINED
            1 -> FALLING_QUICKLY
            2 -> FALLING
            3 -> STABLE
            4 -> RISING
            5 -> RISING_QUICKLY
            else -> RAW
        }
    }
}

enum class Libre3GlycemicAlarmStatus(val rawValue: Int) {
    ALARM_NOT_DETERMINED(0),
    LOW_GLUCOSE(1),
    PROJECTED_LOW_GLUCOSE(2),
    NORMAL_GLUCOSE(3),
    PROJECTED_HIGH_GLUCOSE(4),
    HIGH_GLUCOSE(5),
    RAW(6);

    companion object {
        fun fromRaw(raw: Int): Libre3GlycemicAlarmStatus = when (raw) {
            0 -> ALARM_NOT_DETERMINED
            1 -> LOW_GLUCOSE
            2 -> PROJECTED_LOW_GLUCOSE
            3 -> NORMAL_GLUCOSE
            4 -> PROJECTED_HIGH_GLUCOSE
            5 -> HIGH_GLUCOSE
            else -> RAW
        }
    }
}
