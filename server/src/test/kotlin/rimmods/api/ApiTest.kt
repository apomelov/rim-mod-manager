package rimmods.api

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.io.TempDir
import rimmods.Settings

class ApiTest {
    @TempDir
    lateinit var dir: Path

    private val json = Json { ignoreUnknownKeys = true }

    private fun settings(): Settings {
        val mods = dir.resolve("mods")
        for ((folder, id, name) in listOf(Triple("1", "a.mod", "Alpha"), Triple("2", "a.mod.ru", "Alpha RU"))) {
            val about = mods.resolve(folder).resolve("About").createDirectories()
            about.resolve("About.xml").writeText("<ModMetaData><packageId>$id</packageId><name>$name</name></ModMetaData>")
        }
        val modsConfig = dir.resolve("ModsConfig.xml")
        modsConfig.writeText("<ModsConfigData><version>1.6.1 rev1</version><activeMods/></ModsConfigData>")
        return Settings(dir.resolve("cfg"), mods, modsConfig, 0)
    }

    @Test
    fun `edits go through to the file and come back as a library view`() = testApplication {
        val settings = settings()
        application { module(settings) }

        suspend fun post(path: String, body: String) = client.post(path) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        val first = json.decodeFromString<LibraryResponse>(client.get("/api/library").bodyAsText())
        assertEquals(listOf("a.mod", "a.mod.ru"), first.library.sortout.map { it.packageId.value })

        assertEquals(HttpStatusCode.OK, post("/api/library/create-folder", """{"parent":"UI","name":"Tabs"}""").status)
        assertEquals(HttpStatusCode.OK, post("/api/library/move-mods", """{"mods":["A.Mod"],"to":"UI/Tabs"}""").status)
        val last = post("/api/library/mark-translation", """{"translation":"a.mod.ru","originals":["a.mod"]}""")
        val view = json.decodeFromString<LibraryResponse>(last.bodyAsText())

        assertEquals(emptyList(), view.library.sortout)
        val tabs = view.library.folders.first { it.name == "UI" }.folders.single()
        assertEquals("a.mod.ru", tabs.mods.single().translations.single().packageId.value)
        assertEquals(mapOf("a.mod.ru" to listOf("a.mod")), view.translations)

        val file = settings.libraryFile.readText()
        assertTrue("Tabs" in file && "a.mod.ru:" in file, file)
    }

    @Test
    fun `a rejected edit is a 400 with a message and changes nothing`() = testApplication {
        val settings = settings()
        application { module(settings) }
        client.get("/api/library")
        val before = settings.libraryFile.readText()

        val r = client.post("/api/library/create-folder") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"UI"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, r.status)
        assertTrue("UI" in r.bodyAsText())
        assertEquals(before, settings.libraryFile.readText())
    }

    @Test
    fun `a leftover folder with only dds textures can be deleted, nothing else can`() = testApplication {
        val settings = settings()
        val leftover = settings.modsDir.resolve("3").resolve("Textures").createDirectories()
        leftover.resolve("x.dds").writeText("dds")
        application { module(settings) }

        suspend fun delete(folder: String) = client.post("/api/leftovers/delete") {
            contentType(ContentType.Application.Json)
            setBody("""{"folder":"$folder"}""")
        }

        val first = json.decodeFromString<LibraryResponse>(client.get("/api/library").bodyAsText())
        assertEquals(listOf("3"), first.problems.mapNotNull { it.removableFolder })

        assertEquals(HttpStatusCode.BadRequest, delete("1").status) // a real mod
        assertEquals(HttpStatusCode.BadRequest, delete("..").status)
        assertEquals(HttpStatusCode.BadRequest, delete("nope").status)
        assertTrue(settings.modsDir.resolve("1").exists())

        val r = delete("3")
        assertEquals(HttpStatusCode.OK, r.status)
        assertFalse(settings.modsDir.resolve("3").exists())
        assertEquals(emptyList(), json.decodeFromString<LibraryResponse>(r.bodyAsText()).problems)
    }

    @Test
    fun `build tabs - create, select mods, duplicate, switch`() = testApplication {
        val settings = settings()
        application { module(settings) }

        suspend fun post(path: String, body: String): LibraryResponse {
            val r = client.post(path) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
            return json.decodeFromString(r.bodyAsText())
        }

        post("/api/builds/create", """{"name":"Solo"}""")
        post("/api/builds/select", """{"build":"Solo","mods":["A.Mod"],"selected":true}""")
        val dup = post("/api/builds/duplicate", """{"name":"Solo"}""")
        assertEquals(listOf(BuildView("Solo", listOf("a.mod")), BuildView("Solo copy", listOf("a.mod"))), dup.builds)
        assertEquals("Solo copy", dup.currentBuild)

        val switched = post("/api/builds/current", """{"name":"Solo"}""")
        assertEquals("Solo", switched.currentBuild)
        assertTrue("currentBuild: Solo" in settings.libraryFile.readText())
    }

    @Test
    fun `activation - preview, then write the sorted list with a backup`() = testApplication {
        val settings = settings()
        val before = settings.modsConfigFile.readText()
        application { module(settings) }

        suspend fun post(path: String, body: String) = client.post(path) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        post("/api/builds/create", """{"name":"Solo"}""")
        post("/api/builds/select", """{"build":"Solo","mods":["a.mod"],"selected":true}""")
        post("/api/library/mark-translation", """{"translation":"a.mod.ru","originals":["a.mod"]}""")

        val preview = json.decodeFromString<ActivationPreview>(client.get("/api/builds/activation?build=Solo").bodyAsText())
        assertEquals(listOf("ludeon.rimworld", "a.mod", "a.mod.ru"), preview.plan.order.map { it.value })
        assertEquals(before, settings.modsConfigFile.readText()) // preview writes nothing

        val r = post("/api/builds/activate", """{"build":"Solo"}""")
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
        val written = rimmods.scan.ModsConfigReader.read(settings.modsConfigFile)
        assertEquals(listOf("ludeon.rimworld", "a.mod", "a.mod.ru"), written.activeMods.map { it.value })
        assertEquals("1.6.1 rev1", written.version)
        assertEquals(listOf(before), settings.backupDir.toFile().listFiles()!!.map { it.readText() })
    }

    @Test
    fun `a blocked build is not written`() = testApplication {
        val settings = settings()
        settings.modsDir.resolve("3").resolve("About").createDirectories().resolve("About.xml")
            .writeText("<ModMetaData><packageId>needs.lib</packageId><modDependencies><li><packageId>no.lib</packageId></li></modDependencies></ModMetaData>")
        val before = settings.modsConfigFile.readText()
        application { module(settings) }
        client.post("/api/builds/create") { contentType(ContentType.Application.Json); setBody("""{"name":"B"}""") }
        client.post("/api/builds/select") { contentType(ContentType.Application.Json); setBody("""{"build":"B","mods":["needs.lib"],"selected":true}""") }

        val preview = json.decodeFromString<ActivationPreview>(client.get("/api/builds/activation?build=B").bodyAsText())
        assertTrue(preview.blocked)
        val r = client.post("/api/builds/activate") { contentType(ContentType.Application.Json); setBody("""{"build":"B"}""") }
        assertEquals(HttpStatusCode.BadRequest, r.status)
        assertEquals(before, settings.modsConfigFile.readText())
    }
}
