package rimmods.scan

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

/** Scans the mods folder (one sub-folder per mod). Never fails as a whole: bad mods become [ScanProblem]s. */
class ModScanner(private val gameVersion: GameVersion) {

    fun scan(modsDir: Path): ScanResult = scan(listOf(modsDir))

    /**
     * Scans mods folders in priority order: for a packageId found in several, the first folder's copy is kept
     * (the local Mods folder before Workshop, as in the game). A missing folder is a problem, not a failure.
     */
    fun scan(modsDirs: List<Path>): ScanResult {
        val mods = mutableListOf<Mod>()
        val problems = mutableListOf<ScanProblem>()
        val folders = modsDirs.flatMap { dir ->
            if (dir.isDirectory()) {
                dir.listDirectoryEntries().filter { it.isDirectory() }.sortedBy { it.name }
            } else {
                problems += ScanProblem(dir.toString(), "Mods folder not found")
                emptyList()
            }
        }

        for (folder in folders) {
            val about = folder.resolve("About").resolve("About.xml")
            if (!about.exists()) {
                problems += if (Leftovers.isTexturesOnly(folder)) {
                    ScanProblem(folder.toString(), Leftovers.TEXTURES_ONLY_MESSAGE, texturesOnly = true)
                } else {
                    ScanProblem(folder.toString(), "No About/About.xml: not a mod, probably leftovers of a removed mod")
                }
                continue
            }
            try {
                val info = about.inputStream().buffered().use { AboutXmlParser.parse(it, gameVersion) }
                mods += Mod(info, workshopId(folder), folder.toString())
            } catch (e: AboutXmlException) {
                problems += ScanProblem(folder.toString(), e.message ?: "Cannot read About.xml")
            }
        }

        // The game loads only the first mod with a given packageId; keep the first by folder order.
        val unique = mutableListOf<Mod>()
        for ((id, group) in mods.groupBy { it.packageId }) {
            unique += group.first()
            if (group.size > 1) {
                group.forEach { problems += ScanProblem(it.folder, "Duplicate packageId '$id'") }
            }
        }
        val icons = ImageResolver.icons(unique)
        val withImages = unique.map { m ->
            val content = Languages.scan(Path.of(m.folder), gameVersion)
            m.copy(
                preview = ImageResolver.preview(Path.of(m.folder))?.toString(),
                icon = icons[m.packageId]?.toString(),
                languages = content.languages,
                content = content.content,
            )
        }
        return ScanResult(withImages, problems)
    }

    private fun workshopId(folder: Path): String? {
        val file = folder.resolve("About").resolve("PublishedFileId.txt")
        val fromFile = if (Files.isRegularFile(file)) file.readText().trim().removePrefix("﻿").takeIf { it.isNotEmpty() } else null
        return fromFile ?: folder.name.takeIf { name -> name.all { it.isDigit() } }
    }
}
