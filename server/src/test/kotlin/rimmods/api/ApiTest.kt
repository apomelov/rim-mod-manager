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
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
