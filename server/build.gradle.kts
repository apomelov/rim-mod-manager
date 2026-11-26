plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "rimmods.MainKt"
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.kaml)
    implementation(libs.logback)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
}

tasks.test {
    useJUnitPlatform()
}

// --- Web client: built by npm, bundled into the fat jar only -------------------------------

val webDir = rootProject.layout.projectDirectory.dir("web")

val npmInstall by tasks.registering(Exec::class) {
    group = "web"
    workingDir = webDir.asFile
    commandLine("npm", "ci")
    inputs.files(webDir.file("package.json"), webDir.file("package-lock.json"))
    outputs.dir(webDir.dir("node_modules"))
}

val webBuild by tasks.registering(Exec::class) {
    group = "web"
    dependsOn(npmInstall)
    workingDir = webDir.asFile
    commandLine("npm", "run", "build")
    inputs.dir(webDir.dir("src"))
    inputs.files(
        webDir.file("index.html"),
        webDir.file("vite.config.ts"),
        webDir.file("tsconfig.json"),
        webDir.file("package-lock.json"),
    )
    outputs.dir(webDir.dir("dist"))
}

tasks.shadowJar {
    archiveFileName = "rimworld-mod-manager.jar"
    from(webBuild) { into("static") }
}
