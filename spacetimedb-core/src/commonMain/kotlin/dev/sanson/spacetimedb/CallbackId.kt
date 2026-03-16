package dev.sanson.spacetimedb

import kotlin.jvm.JvmInline

/**
 * Opaque handle for a registered callback.
 *
 * Returned by callback registration methods on [Table], [TableWithPrimaryKey], and [EventTable].
 * Pass to the corresponding `remove*` method to deregister.
 */
@JvmInline public value class CallbackId internal constructor(internal val id: Long)
