package rimmods.setup

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlinx.serialization.Serializable
import rimmods.library.LibraryStore

/** The paths the welcome page edits; empty strings mean "not set". */
@Serializable
data class SetupPaths(
    val gameDir: String = "",
    val gameConfigDir: String = "",
    val workshopDir: String = "",
    val localModsDir: String = "",
    val libraryFile: String = "",
)

@Serializable
data class Check(val ok: Boolean, val message: String)

@Serializable
data class SetupChecks(
    val gameDir: Check,
    val gameConfigDir: Check,
    val workshopDir: Check,
    val localModsDir: Check,
    val libraryFile: Check,
) {
    val ok: Boolean get() = listOf(gameDir, gameConfigDir, workshopDir, localModsDir, libraryFile).all { it.ok }
}

/** Checks each path as docs/paths.md says. Read only. */
object PathChecks {
    fun check(p: SetupPaths): SetupChecks = SetupChecks(
        gameDir = path(p.gameDir)?.let { dir ->
            when {
                !dir.isDirectory() -> Check(false, "Not a folder")
                !PathDetector.isContent(dir) -> Check(false, "No Data/Core/About/About.xml here: not the game's folder")
                else -> Check(true, dir.resolve("Version.txt").takeIf { it.isRegularFile() }?.let { "RimWorld ${it.readText().trim()}" } ?: "RimWorld found")
            }
        } ?: Check(false, "Required"),
        gameConfigDir = path(p.gameConfigDir)?.let { dir ->
            when {
                !dir.isDirectory() -> Check(false, "Not a folder")
                !dir.resolve("ModsConfig.xml").isRegularFile() -> Check(false, "No ModsConfig.xml here: start the game once, or pick its Config folder")
                else -> Check(true, "ModsConfig.xml found")
            }
        } ?: Check(false, "Required"),
        workshopDir = modsFolder(p.workshopDir, "Not used (no Steam Workshop)"),
        localModsDir = modsFolder(p.localModsDir, "Not used"),
        libraryFile = path(p.libraryFile)?.let { file ->
            when {
                file.isDirectory() -> Check(false, "A folder: give a file name, e.g. library.yaml")
                file.exists() -> runCatching { LibraryStore.parse(file.readText()) }.fold(
                    { Check(true, "Existing library: ${it.library.folders.size} top-level folder(s), ${it.library.builds.size} build(s)") },
                    { Check(false, "Cannot read: ${it.message}") },
                )
                file.parent?.let { it.isDirectory() || !it.exists() } == true -> Check(true, "Will be created")
                else -> Check(false, "Its folder cannot be created")
            }
        } ?: Check(false, "Required"),
    )

    private fun modsFolder(s: String, unused: String): Check = path(s)?.let { dir ->
        if (!dir.isDirectory()) Check(false, "Not a folder")
        else Check(true, "${runCatching { dir.listDirectoryEntries().count { it.isDirectory() } }.getOrDefault(0)} mod folder(s)")
    } ?: Check(true, unused)

    private fun path(s: String): Path? = s.trim().takeIf { it.isNotEmpty() }?.let { runCatching { Path(it) }.getOrNull() }
}
