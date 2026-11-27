package rimmods.scan

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir

class GameScannerTest {
    @TempDir
    lateinit var dir: Path

    private fun content(folder: String, packageId: String, extra: String = "") {
        val about = dir.resolve("Data").resolve(folder).resolve("About").createDirectories()
        about.resolve("About.xml").writeText("<ModMetaData><packageId>$packageId</packageId>$extra</ModMetaData>")
    }

    @Test
    fun `Core and DLCs come from Data, in release order, named after their folder`() {
        content("Royalty", "Ludeon.RimWorld.Royalty", "<forceLoadAfter><li>Ludeon.RimWorld</li></forceLoadAfter>")
        content("Core", "Ludeon.RimWorld")
        content("Biotech", "Ludeon.RimWorld.Biotech", "<name>Biotech!</name>")
        content("Stray", "not.ludeon")
        dir.resolve("Data").resolve("Place official expansions here.txt").writeText("")

        val game = GameScanner.scan(dir, GameVersion.parse("1.6"))
        assertEquals(listOf("ludeon.rimworld", "ludeon.rimworld.royalty", "ludeon.rimworld.biotech"), game.map { it.packageId.value })
        assertEquals(listOf("Core", "Royalty", "Biotech!"), game.map { it.about.name })
        assertEquals(listOf("ludeon.rimworld"), game[1].about.forceLoadAfter.map { it.value })
    }

    @Test
    fun `Core's icon is the game's own icon`() {
        content("Core", "Ludeon.RimWorld")
        val icon = dir.resolve("RimWorldLinux_Data").resolve("Resources").createDirectories().resolve("UnityPlayer.png")
        icon.writeText("png")
        assertEquals(icon.toString(), GameScanner.scan(dir, GameVersion.parse("1.6")).single().icon)
    }

    @Test
    fun `no game folder means no game content`() {
        assertEquals(emptyList(), GameScanner.scan(dir.resolve("nope"), GameVersion.parse("1.6")))
        assertEquals(emptyList(), GameScanner.scan(null, GameVersion.parse("1.6")))
    }
}
