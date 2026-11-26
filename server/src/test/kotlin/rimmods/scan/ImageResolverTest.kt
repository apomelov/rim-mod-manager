package rimmods.scan

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir

class ImageResolverTest {
    @TempDir
    lateinit var dir: Path

    private fun file(relative: String): Path {
        val f = dir.resolve(relative)
        f.parent.createDirectories()
        f.writeText("png")
        return f
    }

    private fun about(packageId: String, iconPath: String? = null, deps: List<String> = emptyList()) = AboutInfo(
        packageId = PackageId.of(packageId), packageIdOriginal = packageId, name = packageId, authors = emptyList(),
        description = "", supportedVersions = emptyList(),
        dependencies = deps.map { Dependency(PackageId.of(it)) },
        loadAfter = emptyList(), loadBefore = emptyList(), forceLoadAfter = emptyList(),
        forceLoadBefore = emptyList(), incompatibleWith = emptyList(), modIconPath = iconPath,
    )

    private fun mod(folder: String, packageId: String, iconPath: String? = null, deps: List<String> = emptyList()): Mod {
        dir.resolve(folder).createDirectories()
        return Mod(about(packageId, iconPath, deps), null, dir.resolve(folder).toString())
    }

    @Test
    fun `preview is About_Preview_png, case-insensitively`() {
        val upper = file("m1/About/Preview.png")
        val lower = file("m2/About/preview.png")
        dir.resolve("m3/About").createDirectories()
        assertEquals(upper, ImageResolver.preview(dir.resolve("m1")))
        assertEquals(lower, ImageResolver.preview(dir.resolve("m2")))
        assertNull(ImageResolver.preview(dir.resolve("m3")))
        assertNull(ImageResolver.preview(dir.resolve("nowhere")))
    }

    @Test
    fun `icon from modIconPath in any Textures folder of the mod, case-insensitively`() {
        val a = file("a/Textures/UI/Logo.png")
        val b = file("b/1.6/Textures/ui/logo.PNG")
        val c = file("c/Common/Textures/Icon.png")
        val icons = ImageResolver.icons(
            listOf(mod("a", "a.mod", "UI/Logo"), mod("b", "b.mod", "UI/Logo"), mod("c", "c.mod", "Icon")),
        )
        assertEquals(mapOf(PackageId.of("a.mod") to a, PackageId.of("b.mod") to b, PackageId.of("c.mod") to c), icons)
    }

    @Test
    fun `modIconPath must match whole path segments`() {
        file("a/Textures/OtherUI/Logo.png")
        assertEquals(emptyMap(), ImageResolver.icons(listOf(mod("a", "a.mod", "UI/Logo"))))
    }

    @Test
    fun `icon from modIconPath falls back to the mod's dependencies`() {
        val logo = file("core/Textures/UI/VELogo.png")
        val icons = ImageResolver.icons(
            listOf(mod("core", "ve.core"), mod("ve", "ve.something", "UI/VELogo", deps = listOf("VE.Core"))),
        )
        assertEquals(logo, icons[PackageId.of("ve.something")])
    }

    @Test
    fun `dependency fallback is transitive, nearest first, and survives cycles`() {
        val far = file("core/Textures/UI/VELogo.png")
        val near = file("mid2/Textures/UI/VELogo.png")
        val icons = ImageResolver.icons(
            listOf(
                mod("core", "ve.core", deps = listOf("upgrades")),
                mod("mid", "vve", deps = listOf("ve.core")),
                mod("mid2", "other", deps = listOf("ve.core")),
                mod("upgrades", "upgrades", "UI/VELogo", deps = listOf("vve")),
                mod("tier3", "tier3", "UI/VELogo", deps = listOf("vve", "other")),
            ),
        )
        assertEquals(far, icons[PackageId.of("upgrades")])
        assertEquals(near, icons[PackageId.of("tier3")])
    }

    @Test
    fun `About_ModIcon_png is used when modIconPath is absent or not found`() {
        val plain = file("a/About/ModIcon.png")
        val fallback = file("b/About/modicon.png")
        val icons = ImageResolver.icons(listOf(mod("a", "a.mod"), mod("b", "b.mod", "UI/Nowhere")))
        assertEquals(plain, icons[PackageId.of("a.mod")])
        assertEquals(fallback, icons[PackageId.of("b.mod")])
    }

    @Test
    fun `modIconPath wins over About_ModIcon_png`() {
        val texture = file("a/Textures/Logo.png")
        file("a/About/ModIcon.png")
        assertEquals(texture, ImageResolver.icons(listOf(mod("a", "a.mod", "Logo")))[PackageId.of("a.mod")])
    }

    @Test
    fun `no icon at all`() {
        assertEquals(emptyMap(), ImageResolver.icons(listOf(mod("a", "a.mod"))))
    }
}
