package rimmods.translations

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir

class ModKeysTest {
    @TempDir
    lateinit var dir: Path

    private fun file(path: String, text: String) {
        val f = dir.resolve(path)
        f.parent.createDirectories()
        f.writeText(text)
    }

    @Test
    fun `provided keys - defNames in Defs anywhere, English Keyed keys`() {
        file("Defs/Things.xml", "<Defs><ThingDef><defName>Gun</defName></ThingDef><ThingDef Name=\"Abstract\" Abstract=\"True\"/></Defs>")
        file("1.6/Defs/More/Things.xml", "<Defs><RecipeDef><defName>MakeGun</defName></RecipeDef></Defs>")
        file("Languages/English/Keyed/UI.xml", "<LanguageData><Mod_Button>Press</Mod_Button></LanguageData>")
        file("Languages/Russian/Keyed/UI.xml", "<LanguageData><Ru_Only>Жми</Ru_Only></LanguageData>")
        file("Defs/Broken.xml", "<Defs><ThingDef>")
        assertEquals(setOf("def:Gun", "def:MakeGun", "key:Mod_Button"), ModKeys.provided(dir))
    }

    @Test
    fun `translated keys - DefInjected defNames and Keyed keys in non-English languages`() {
        file("Languages/Russian/DefInjected/ThingDef/Things.xml", "<LanguageData><Gun.label>пушка</Gun.label><Gun.description>…</Gun.description></LanguageData>")
        file("1.6/Languages/Russian (Русский)/Keyed/UI.xml", "<LanguageData><Mod_Button>Жми</Mod_Button></LanguageData>")
        file("Languages/English/Keyed/UI.xml", "<LanguageData><English_Only>x</English_Only></LanguageData>")
        assertEquals(setOf("def:Gun", "key:Mod_Button"), ModKeys.translated(dir))
    }
}
