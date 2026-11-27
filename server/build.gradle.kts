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

/** npm is a .cmd script on Windows, which Exec does not resolve by itself. */
val npm = if (System.getProperty("os.name").lowercase().startsWith("windows")) "npm.cmd" else "npm"

val npmInstall by tasks.registering(Exec::class) {
    group = "web"
    workingDir = webDir.asFile
    commandLine(npm, "ci")
    inputs.files(webDir.file("package.json"), webDir.file("package-lock.json"))
    outputs.dir(webDir.dir("node_modules"))
}

val webBuild by tasks.registering(Exec::class) {
    group = "web"
    dependsOn(npmInstall)
    workingDir = webDir.asFile
    commandLine(npm, "run", "build")
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

// --- Distribution: the fat jar plus start scripts, see dist/ ----------------------------------

val dist by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Builds build/dist/rimworld-mod-manager.zip: the jar (client included) and start scripts."
    archiveFileName = "rimworld-mod-manager.zip"
    destinationDirectory = layout.buildDirectory.dir("dist")
    into("rimworld-mod-manager") {
        from(tasks.shadowJar)
        from(rootProject.layout.projectDirectory.dir("dist"))
        filesMatching("run.sh") { permissions { unix("rwxr-xr-x") } }
    }
}

// The application plugin's own distributions (thin jar + lib/, generated scripts) are not used.
listOf("distZip", "distTar", "installDist", "shadowDistZip", "shadowDistTar", "installShadowDist").forEach {
    tasks.named(it) { enabled = false }
}

// --- Native packages: the app with a trimmed Java runtime, built by jpackage for the current OS -------
// jpackage cannot cross-build: CI builds one per OS (.github/workflows/package.yml).

/** Package version. macOS requires the first number to be at least 1. */
val appVersion = "1.0.0"
val currentOs = System.getProperty("os.name").lowercase().let {
    when {
        it.startsWith("windows") -> "windows"
        it.startsWith("mac") -> "macos"
        else -> "linux"
    }
}

/** From `jdeps --print-module-deps` on the fat jar, plus java.xml (DOM parsing; implied by java.desktop today). */
val runtimeModules = listOf(
    "java.base", "java.desktop", "java.instrument", "java.logging", "java.management", "java.naming", "java.xml", "jdk.unsupported",
)

val jpackageImage by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Builds build/jpackage/<image>: the app with a trimmed Java runtime for this OS."
    val jar = tasks.shadowJar.flatMap { it.archiveFile }
    val input = layout.buildDirectory.dir("jpackage-input")
    val output = layout.buildDirectory.dir("jpackage")
    val jdk = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }.map { it.metadata.installationPath }
    inputs.file(jar)
    outputs.dir(output)
    doFirst {
        delete(input, output)
        copy { from(jar); into(input) }
    }
    executable = jdk.get().file("bin/jpackage" + if (currentOs == "windows") ".exe" else "").asFile.path
    args(
        "--type", "app-image",
        "--name", "RimWorldModManager",
        "--app-version", appVersion,
        "--vendor", "RimWorld Mod Manager",
        "--input", input.get().asFile.path,
        "--main-jar", "rimworld-mod-manager.jar",
        "--main-class", "rimmods.MainKt",
        "--java-options", "-Drmm.openBrowser=true",
        "--add-modules", runtimeModules.joinToString(","),
        "--jlink-options", "--strip-debug --no-man-pages --no-header-files --strip-native-commands",
        "--dest", output.get().asFile.path,
    )
    // A console window shows the address; closing it stops the server.
    if (currentOs == "windows") args("--win-console")
}

val jpackageZip by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Zips the native app image for this OS into build/dist/."
    dependsOn(jpackageImage)
    archiveFileName = "rimworld-mod-manager-$appVersion-$currentOs.zip"
    destinationDirectory = layout.buildDirectory.dir("dist")
    from(layout.buildDirectory.dir("jpackage"))
    from(rootProject.layout.projectDirectory.file("dist/README-native.txt"))
    // Keep the launcher and the runtime's executables runnable (zip stores Unix permissions).
    filesMatching(listOf("**/bin/**", "**/MacOS/**", "**/lib/jspawnhelper")) { permissions { unix("rwxr-xr-x") } }
}

