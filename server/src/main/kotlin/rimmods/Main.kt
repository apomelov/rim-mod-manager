package rimmods

import io.ktor.server.application.ApplicationStarted
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.awt.Desktop
import java.net.URI
import rimmods.api.AppState
import rimmods.api.module
import rimmods.setup.Os

fun main() {
    val configDir = Settings.defaultConfigDir()
    // null: the welcome page asks for the paths (also when settings.yaml cannot be read; saving there replaces it)
    val settings = runCatching { Settings.load(configDir) }.onFailure { println("Settings ignored: ${it.message}") }.getOrNull()
    // -Drmm.port overrides the port, e.g. to run a second copy while developing
    val port = System.getProperty("rmm.port")?.toIntOrNull() ?: settings?.port ?: Settings.port(configDir)
    val url = "http://127.0.0.1:$port/"
    val server = embeddedServer(Netty, port = port, host = "127.0.0.1") { module(AppState(configDir, settings)) }
    server.monitor.subscribe(ApplicationStarted) {
        println("RimWorld Mod Manager: $url (config: $configDir${if (settings == null) ", not set up yet" else ""})")
        if (System.getProperty("rmm.openBrowser") == "true") openBrowser(url)
    }
    server.start(wait = true)
}

private fun openBrowser(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
            return
        }
    }
    val command = when (Os.current()) {
        Os.WINDOWS -> listOf("rundll32", "url.dll,FileProtocolHandler", url)
        Os.MAC -> listOf("open", url)
        Os.LINUX -> listOf("xdg-open", url)
    }
    runCatching { ProcessBuilder(command).start() }.onFailure { println("Open $url in a browser") }
}
