package dev.sanson.spacetimedb.gradle

/**
 * When to destroy existing data while publishing to an existing database identity.
 *
 * Maps to `spacetime publish --delete-data=<value>`.
 */
public enum class DeleteDataMode(internal val cliValue: String) {
    /** Always destroy data before publishing. */
    ALWAYS("always"),

    /** Only destroy data when breaking schema changes occur. */
    ON_CONFLICT("on-conflict"),

    /** Never destroy data (publish fails instead). */
    NEVER("never"),
}
