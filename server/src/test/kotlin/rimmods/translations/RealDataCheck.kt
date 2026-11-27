package rimmods.translations

import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import rimmods.Settings
import rimmods.library.LibraryStore
import rimmods.library.LibrarySync
import rimmods.scan.ModScanner
import rimmods.scan.ModsConfigReader

/**
 * Manual dev check (docs/translations.md, Validation): runs the finder on the real mods folder against a library
 * whose translation marks are the ground truth, as if none were marked. Read only.
 * `RMM_TRUTH=/path/to/library.yaml ./gradlew :server:test --tests '*RealDataCheck*'`; report in build/translation-check.txt.
 */
@EnabledIfEnvironmentVariable(named = "RMM_TRUTH", matches = ".+")
class RealDataCheck {
    @Test
    fun compare() {
        val settings = Settings.load()
        val cfg = ModsConfigReader.read(settings.modsConfigFile)
        val mods = ModScanner(cfg.gameVersion).scan(settings.modsDir).mods
        val truth = LibraryStore.parse(File(System.getenv("RMM_TRUTH")).readText()).library
        val unmarked = truth.copy(translations = emptyMap())
        val sortout = LibrarySync.view(unmarked, mods).sortout.map { it.packageId }.toSet()

        val started = System.currentTimeMillis()
        val found = TranslationFinder.find(mods, sortout) { ModKeySets(ModKeys.provided(Path.of(it.folder)), ModKeys.translated(Path.of(it.folder))) }
        val ms = System.currentTimeMillis() - started

        val expected = truth.translations.flatMap { (t, os) -> os.map { t to it } }.toSet()
        val suggested = found.flatMap { s -> s.originals.map { s.translation to it.packageId } }.toSet()
        val out = StringBuilder()
        out.appendLine("finder: $ms ms; Sortout ${sortout.size}; suggestions ${found.size} (${found.count { it.originals.isEmpty() }} without original)")
        out.appendLine("pairs: expected ${expected.size}, suggested ${suggested.size}, correct ${(expected intersect suggested).size}")
        out.appendLine("MISSED (expected, not suggested):")
        (expected - suggested).forEach { (t, o) -> out.appendLine("  $t -> $o${if (t in found.map { it.translation }) "" else "   [translation not a candidate]"}") }
        out.appendLine("EXTRA (suggested, not expected):")
        (suggested - expected).forEach { (t, o) -> out.appendLine("  $t -> $o${if (t in truth.translations) "" else "   [not marked at all]"}") }
        out.appendLine("WITHOUT ORIGINAL:")
        found.filter { it.originals.isEmpty() }.forEach { out.appendLine("  ${it.translation} keys=${it.translationKeys}") }
        File("build/translation-check.txt").writeText(out.toString())
    }
}
