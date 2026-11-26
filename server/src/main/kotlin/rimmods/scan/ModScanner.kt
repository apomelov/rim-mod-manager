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

    fun scan(modsDir: Path): ScanResult {
        require(modsDir.isDirectory()) { "Mods folder not found: $modsDir" }
        val mods = mutableListOf<Mod>()
        val problems = mutableListOf<ScanProblem>()

        for (folder in modsDir.listDirectoryEntries().filter { it.isDirectory() }.sortedBy { it.name }) {
            val about = folder.resolve("About").resolve("About.xml")
            if (!about.exists()) {
                problems += ScanProblem(folder.toString(), "No About/About.xml")
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
            m.copy(
                preview = ImageResolver.preview(Path.of(m.folder))?.toString(),
                icon = icons[m.packageId]?.toString(),
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
