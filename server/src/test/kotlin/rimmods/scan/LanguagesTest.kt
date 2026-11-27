package rimmods.scan

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir

class LanguagesTest {
    @TempDir
    lateinit var dir: Path

    private val v16 = GameVersion.parse("1.6")

    private fun lang(vararg path: String) {
        path.fold(dir) { p, s -> p.resolve(s) }.createDirectories()
    }

    @Test
    fun `languages from Languages folders anywhere in the mod, normalised and sorted`() {
        lang("Languages", "Russian (Русский)")
        lang("Common", "Languages", "German")
        lang("Biotech", "Languages", "TraditionalChinese")
        lang("1.6", "Languages", "PortugueseBrazil")
        lang("1.6", "Biotech", "Languages", "Korean (한국어)")
        lang("v1.6", "languages", "French")
        lang("Languages", "Russian")
        assertEquals(
            listOf("ChineseTraditional", "French", "German", "Korean", "PortugueseBrazilian", "Russian"),
            Languages.scan(dir, v16).languages,
        )
    }

    @Test
    fun `folders of other game versions are ignored`() {
        lang("1.4", "Languages", "Japanese")
        lang("v1.5", "Languages", "Polish")
        lang("1.6", "Languages", "Czech")
        assertEquals(listOf("Czech"), Languages.scan(dir, v16).languages)
    }

    @Test
    fun `no Languages folder and no content, no languages`() {
        lang("About")
        assertEquals(emptyList(), Languages.scan(dir, v16).languages)
    }

    @Test
    fun `own content means English, a pure translation has none`() {
        lang("Languages", "Russian")
        lang("About")
        assertEquals(ModContent(listOf("Russian"), emptyList()), Languages.scan(dir, v16))

        lang("1.6", "Defs")
        lang("Assemblies")
        lang("1.4", "Patches") // other version: ignored
        assertEquals(ModContent(listOf("English", "Russian"), listOf("Assemblies", "Defs")), Languages.scan(dir, v16))
    }
}
