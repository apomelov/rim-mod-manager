package rimmods

import io.ktor.server.application.ApplicationStarted
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import rimmods.api.module

fun main() {
    val settings = Settings.load()
    val url = "http://127.0.0.1:${settings.port}/"
    val server = embeddedServer(Netty, port = settings.port, host = "127.0.0.1") { module(settings) }
    server.monitor.subscribe(ApplicationStarted) {
        println("RimWorld Mod Manager: $url (config: ${settings.configDir})")
        if (System.getProperty("rmm.openBrowser") == "true") ProcessBuilder("xdg-open", url).start()
    }
    server.start(wait = true)
}
