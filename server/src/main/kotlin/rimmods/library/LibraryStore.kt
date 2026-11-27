package rimmods.library

import com.charleskorn.kaml.SingleLineStringStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.Serializable
import rimmods.scan.PackageId
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** Reads and writes `library.yaml` (docs/user-files.md). */
object LibraryStore {
    val PREDEFINED_FOLDERS = listOf("Library", "UI", "UX", "QoL", "Tools")

    private val yaml = Yaml(
        configuration = YamlConfiguration(
            encodeDefaults = false,
            singleLineStringStyle = SingleLineStringStyle.PlainExceptAmbiguous,
        ),
    )

    /** The library used when no file exists yet. */
    fun default(): Library = Library(folders = PREDEFINED_FOLDERS.map { Folder(it) })

    /** Parses YAML; normalises packageIds; checks the rules from docs/user-files.md. */
    fun parse(text: String): LoadedLibrary {
        if (text.isBlank()) return LoadedLibrary(Library(), emptyList())
        val dto = try {
            yaml.decodeFromString(LibraryDto.serializer(), text)
        } catch (e: Exception) {
            throw LibraryFileException("Malformed library file: ${e.message}", e)
        }
        val problems = mutableListOf<String>()
        val locations = linkedMapOf<PackageId, MutableList<String>>()
        val library = Library(
            folders = dto.folders.map { it.toFolder(locations, path = "") },
            translations = dto.translations
                .map { (k, v) -> PackageId.of(k) to v.orEmpty().map(PackageId::of).distinct() }
                .toMap(),
            builds = dto.builds.map { Build(it.name, it.mods.map(PackageId::of).distinct()) },
            currentBuild = dto.currentBuild,
        )
        locations.filterValues { it.size > 1 }.forEach { (id, where) ->
            problems += "Mod '$id' is listed in several folders; kept in '${where.first()}', ignored in ${where.drop(1).joinToString { "'$it'" }}"
        }
        checkSiblingNames(library.folders, "", problems)
        return LoadedLibrary(library, problems)
    }

    fun write(library: Library): String = yaml.encodeToString(LibraryDto.serializer(), LibraryDto.of(library)) + "\n"

    /** Loads the file; if it does not exist, creates it from [default]. A malformed file is never overwritten. */
    fun load(file: Path): LoadedLibrary {
        if (!file.exists()) {
            save(file, default())
            return LoadedLibrary(default(), emptyList())
        }
        return parse(file.readText())
    }

    /** Writes atomically: a temp file next to the target, then a rename. */
    fun save(file: Path, library: Library) {
        file.toAbsolutePath().parent.createDirectories()
        val tmp = Files.createTempFile(file.toAbsolutePath().parent, file.fileName.toString(), ".tmp")
        tmp.writeText(write(library))
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    /** Depth-first in display order (subfolders, then mods): the first occurrence of a mod wins. */
    private fun FolderDto.toFolder(locations: MutableMap<PackageId, MutableList<String>>, path: String): Folder {
        val here = "$path/$name"
        val subfolders = folders.map { it.toFolder(locations, here) }
        val ownMods = mods.map(PackageId::of).filter { id ->
            val where = locations.getOrPut(id) { mutableListOf() }
            where += here
            where.size == 1
        }
        return Folder(name, expanded, subfolders, ownMods)
    }

    private fun checkSiblingNames(folders: List<Folder>, path: String, problems: MutableList<String>) {
        folders.groupBy { it.name }.filter { it.value.size > 1 }.keys
            .forEach { problems += "Folder name '$it' is used more than once in '${path.ifEmpty { "/" }}'" }
        folders.forEach { checkSiblingNames(it.folders, "$path/${it.name}", problems) }
    }
}

@Serializable
private data class LibraryDto(
    val folders: List<FolderDto> = emptyList(),
    val translations: Map<String, List<String>?> = emptyMap(),
    val builds: List<BuildDto> = emptyList(),
    val currentBuild: String? = null,
) {
    companion object {
        fun of(l: Library) = LibraryDto(
            folders = l.folders.map(FolderDto::of),
            translations = l.translations.map { (k, v) -> k.value to v.map { it.value } }.toMap(),
            builds = l.builds.map { b -> BuildDto(b.name, b.mods.map { it.value }) },
            currentBuild = l.currentBuild,
        )
    }
}

@Serializable
private data class FolderDto(
    val name: String,
    val expanded: Boolean = false,
    val folders: List<FolderDto> = emptyList(),
    val mods: List<String> = emptyList(),
) {
    companion object {
        fun of(f: Folder): FolderDto = FolderDto(f.name, f.expanded, f.folders.map(::of), f.mods.map { it.value })
    }
}

@Serializable
private data class BuildDto(val name: String, val mods: List<String> = emptyList())
