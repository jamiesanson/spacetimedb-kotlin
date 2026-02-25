package dev.sanson.spacetimedb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Specifies when a scheduled reducer should execute.
 *
 * Serialized as a BSATN sum type (sealed class) with tag 0 = [Interval], tag 1 = [Time].
 */
@Serializable
public sealed class ScheduleAt {
    /**
     * Execute the reducer at a repeating interval.
     */
    @Serializable
    @SerialName("Interval")
    public data class Interval(val duration: TimeDuration) : ScheduleAt()

    /**
     * Execute the reducer at a specific point in time.
     */
    @Serializable
    @SerialName("Time")
    public data class Time(val timestamp: Timestamp) : ScheduleAt()
}
