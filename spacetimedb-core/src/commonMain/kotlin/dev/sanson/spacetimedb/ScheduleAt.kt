package dev.sanson.spacetimedb

import dev.drewhamilton.poko.Poko
import dev.sanson.spacetimedb.protocol.TaggedSumSerializer
import dev.sanson.spacetimedb.protocol.variant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Specifies when a scheduled reducer should execute.
 *
 * Serialized as a BSATN sum type with tag 0 = [Interval], tag 1 = [Time].
 */
@Serializable(with = ScheduleAtSerializer::class)
public abstract class ScheduleAt private constructor() {
    /**
     * Execute the reducer at a repeating interval.
     */
    @Serializable
    @Poko
    public class Interval(public val duration: TimeDuration) : ScheduleAt()

    /**
     * Execute the reducer at a specific point in time.
     */
    @Serializable
    @Poko
    public class Time(public val timestamp: Timestamp) : ScheduleAt()
}

internal object ScheduleAtSerializer : KSerializer<ScheduleAt> by TaggedSumSerializer(
    "ScheduleAt",
    arrayOf(
        variant(ScheduleAt.Interval.serializer()),
        variant(ScheduleAt.Time.serializer()),
    ),
)
