package com.dsikic.cluecose.core.dataplane

/**
 * Sensor warmup/active/expiry classification (1:1 port of
 * LibreCRKit SensorLifecycle.swift).
 */
enum class SensorLifecyclePhase { WARMUP, ACTIVE, EXPIRED }

class SensorLifecycle(
    val currentLifeCountMinutes: Long,
    val warmupDurationMinutes: Int = DEFAULT_WARMUP_DURATION_MINUTES,
    val wearDurationMinutes: Int? = null,
) {
    companion object {
        const val DEFAULT_WARMUP_DURATION_MINUTES = 60
    }

    private val warmup: Int = maxOf(0, warmupDurationMinutes)
    private val wear: Int? = wearDurationMinutes?.let { maxOf(0, it) }

    val elapsedMinutes: Long
        get() = maxOf(0L, currentLifeCountMinutes)

    val remainingWarmupMinutes: Long
        get() = maxOf(0L, warmup.toLong() - elapsedMinutes)

    val remainingWearMinutes: Long?
        get() = wear?.let { maxOf(0L, it.toLong() - elapsedMinutes) }

    val isWarmupComplete: Boolean
        get() = elapsedMinutes >= warmup

    val isWarmingUp: Boolean
        get() = !isWarmupComplete && !isExpired

    val isExpired: Boolean
        get() = wear != null && elapsedMinutes >= wear!!

    val isActive: Boolean
        get() = phase == SensorLifecyclePhase.ACTIVE

    val phase: SensorLifecyclePhase
        get() = when {
            isExpired -> SensorLifecyclePhase.EXPIRED
            !isWarmupComplete -> SensorLifecyclePhase.WARMUP
            else -> SensorLifecyclePhase.ACTIVE
        }
}
