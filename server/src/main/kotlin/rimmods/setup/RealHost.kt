package rimmods.setup

import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path

/** The machine the app runs on. Registry values are read with `reg query` (Windows only). */
object RealHost : Host {
    override val os: Os = Os.current()
    override val home: Path = Path(System.getProperty("user.home"))
    override val root: Path = Path("/")
    override fun env(name: String): String? = System.getenv(name)

    override fun registry(key: String, value: String): String? =
        regQuery(listOf(key, "/v", value)).firstNotNullOfOrNull { parseValue(it, value) }

    override fun registrySubkeys(key: String, value: String): List<String> =
        regQuery(listOf(key, "/s", "/v", value)).mapNotNull { parseValue(it, value) }

    /** `    InstallPath    REG_SZ    C:\Program Files (x86)\Steam` → the data. */
    private fun parseValue(line: String, value: String): String? {
        val parts = line.trim().split(Regex("\\s{2,}|\\t"), limit = 3)
        return if (parts.size == 3 && parts[0].equals(value, ignoreCase = true) && parts[1].startsWith("REG_")) parts[2].trim() else null
    }

    private fun regQuery(args: List<String>): List<String> {
        if (os != Os.WINDOWS) return emptyList()
        return runCatching {
            val p = ProcessBuilder(listOf("reg", "query") + args).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readLines()
            if (!p.waitFor(5, TimeUnit.SECONDS)) p.destroy()
            out
        }.getOrDefault(emptyList())
    }
}
