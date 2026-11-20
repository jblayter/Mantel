plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * Generates the Hive design tokens from docs/design/tokens.json, the single
 * source of truth: a `HiveTokens` Kotlin object for Compose and a small colors
 * resource for the XML window theme. Nothing generated is checked in.
 */
abstract class GenerateHiveTokens : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val tokensFile: RegularFileProperty

    @get:OutputDirectory
    abstract val kotlinDir: DirectoryProperty

    @get:OutputDirectory
    abstract val resDir: DirectoryProperty

    private val sb = StringBuilder()

    private fun color(v: String): String {
        val c = "androidx.compose.ui.graphics.Color"
        Regex("^#([0-9A-Fa-f]{6})$").find(v)?.let { return "$c(0xFF${it.groupValues[1].uppercase()})" }
        Regex("""^rgba\((\d+),\s*(\d+),\s*(\d+),\s*([\d.]+)\)$""").find(v)?.let {
            val (r, g, b, a) = it.destructured
            return "$c(red = $r, green = $g, blue = $b, alpha = ${Math.round(a.toDouble() * 255)})"
        }
        throw GradleException("tokens.json: unsupported color '$v'")
    }

    private fun isColor(v: Any?) = v is String && (v.startsWith("#") || v.startsWith("rgba("))

    private fun num(v: Any?): String {
        val d = (v as Number).toDouble()
        return if (d % 1.0 == 0.0) d.toInt().toString() else "${d}f"
    }

    private fun quoted(v: Any?) = "\"" + v.toString().replace("\"", "\\\"") + "\""

    /** Lengths become dp; counts and durations stay plain numbers. */
    private fun measure(key: String, v: Any?): String = when {
        isColor(v) -> color(v as String)
        v is String -> quoted(v)
        v is List<*> -> v.joinToString(prefix = "listOf(", postfix = ")") { "${num(it)}.dp" }
        key == "perRow" || key.endsWith("Seconds") -> num(v)
        else -> "${num(v)}.dp"
    }

    private fun camel(key: String) =
        key.split("_").mapIndexed { i, p -> if (i == 0) p else p.replaceFirstChar(Char::uppercase) }.joinToString("")

    private fun obj(name: String, map: Map<*, *>, indent: String, leaf: (String, Any?) -> String) {
        sb.append("$indent" + "object ${camel(name).replaceFirstChar(Char::uppercase)} {\n")
        for ((k, v) in map) {
            k as String
            if (v is Map<*, *>) obj(k, v, "$indent    ", leaf)
            else sb.append("$indent    val ${camel(k)} = ${leaf(k, v)}\n")
        }
        sb.append("$indent}\n")
    }

    @TaskAction
    fun generate() {
        val root = groovy.json.JsonSlurper().parse(tokensFile.get().asFile) as Map<*, *>
        fun section(name: String) =
            root[name] as? Map<*, *> ?: throw GradleException("tokens.json: missing section '$name'")

        sb.append("// Generated from docs/design/tokens.json. Do not edit.\n")
        sb.append("package com.blayter.mantel.ui.theme\n\n")
        sb.append("import androidx.compose.ui.text.font.FontWeight\n")
        sb.append("import androidx.compose.ui.unit.dp\n")
        sb.append("import androidx.compose.ui.unit.sp\n\n")
        sb.append("object HiveTokens {\n")
        val i = "    "
        obj("color", section("color"), i) { _, v -> color(v as String) }
        obj("font", section("font"), i) { _, v -> if (v is Number) "FontWeight(${num(v)})" else quoted(v) }
        obj("typeScale", section("typeScale"), i) { _, v -> "${num(v)}.sp" }
        sb.append("$i" + "object Space {\n")
        for (n in root["space"] as List<*>) sb.append("$i    val s${num(n)} = ${num(n)}.dp\n")
        sb.append("$i}\n")
        for (name in listOf("screen", "card", "hex", "motion", "focus")) obj(name, section(name), i, ::measure)
        obj("thresholds_proposed", section("thresholds_proposed"), i) { _, v -> num(v) }
        sb.append("}\n")

        val kt = kotlinDir.get().asFile.resolve("com/blayter/mantel/ui/theme/HiveTokens.kt")
        kt.parentFile.mkdirs()
        kt.writeText(sb.toString())

        val ground = section("color")["ground"] as String
        val xml = resDir.get().asFile.resolve("values/hive_colors.xml")
        xml.parentFile.mkdirs()
        xml.writeText(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<!-- Generated from docs/design/tokens.json. -->\n" +
                "<resources>\n    <color name=\"hive_ground\">$ground</color>\n</resources>\n",
        )
    }
}

val generateHiveTokens = tasks.register<GenerateHiveTokens>("generateHiveTokens") {
    tokensFile.set(rootProject.layout.projectDirectory.file("docs/design/tokens.json"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.java?.addGeneratedSourceDirectory(generateHiveTokens, GenerateHiveTokens::kotlinDir)
        variant.sources.res?.addGeneratedSourceDirectory(generateHiveTokens, GenerateHiveTokens::resDir)
    }
}

android {
    namespace = "com.blayter.mantel"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.blayter.mantel"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"

        // `./gradlew :app:installDebug -Pmantel.mock=true` builds the all-mock app,
        // for working on the screen without access to the CI box.
        buildConfigField("boolean", "MOCK_DATA", (providers.gradleProperty("mantel.mock").orNull == "true").toString())
        // With the mock build, `-Pmantel.mockMachines=3` adds sample machines beside the main one
        // (one with a warning, one that stops answering), to work on the several-machines screen.
        buildConfigField("int", "MOCK_MACHINES", (providers.gradleProperty("mantel.mockMachines").orNull?.toIntOrNull() ?: 1).toString())
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")

    // Compose for TV. 1.0.1 is the stable release that matches this Compose BOM.
    implementation("androidx.tv:tv-material:1.0.1")

    // JSON as a tree (Glances replies, the pushed config file); no compiler plugin needed.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Encrypted storage for the pushed config. Same version as the KrakenTV board.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-tooling-preview")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
