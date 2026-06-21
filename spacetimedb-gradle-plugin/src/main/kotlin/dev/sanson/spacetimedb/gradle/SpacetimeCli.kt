package dev.sanson.spacetimedb.gradle

import java.io.File

/**
 * Resolves the `spacetime` CLI executable without depending on the Gradle daemon's `PATH`.
 *
 * The installer only adds `~/.local/bin` to interactive shells (`.zshrc`), so builds launched from
 * IDEs or reused daemons may not find a bare `spacetime` on the `PATH`.
 */
internal object SpacetimeCli {

    /**
     * Returns the path to the `spacetime` CLI, preferring [explicit] when set, then
     * `~/.local/bin/spacetime`, and finally a bare `spacetime` (resolved via `PATH`).
     */
    fun resolve(explicit: String?): String {
        explicit
            ?.takeIf { it.isNotBlank() }
            ?.let {
                return it
            }

        val home = System.getProperty("user.home")
        val localBin = File("$home/.local/bin/spacetime")
        if (localBin.isFile && localBin.canExecute()) return localBin.absolutePath

        return "spacetime"
    }
}
