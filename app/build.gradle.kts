plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * Generates the Hive design tokens from docs/design/tokens.json and the color
 * themes (every .json in docs/design/themes), the single sources of truth:
 * - `HiveTokens`: sizes, type, motion. `HiveTokens.Color` reads the theme in
 *   use, so changing theme recolors the screen without a restart.
 * - `HiveColors`, `HiveTheme` and `HiveThemes`: every theme in the folder.
 * - a small colors resource for the XML window theme (the default theme's ground).
 * Each theme is checked here: every color role of theme.schema.json, nothing
 * else, a dark ground and readable text. A theme that fails stops the build
 * with every problem listed. Nothing generated is checked in.
 */
abstract class GenerateHiveTokens : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val tokensFile: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val themesDir: DirectoryProperty

    @get:OutputDirectory
    abstract val kotlinDir: DirectoryProperty

    @get:OutputDirectory
    abstract val resDir: DirectoryProperty

    private val sb = StringBuilder()

    /** WCAG relative luminance of the ground, at most. Hive's is 0.015. */
    private val maxGroundLuminance = 0.05

    /** WCAG AA for body text, for every text, kind, status and focus color on every background. */
    private val minContrast = 4.5
    private val colorClass = "androidx.compose.ui.graphics.Color"

    private fun color(v: String): String {
        Regex("^#([0-9A-Fa-f]{6})$").find(v)?.let { return "$colorClass(0xFF${it.groupValues[1].uppercase()})" }
        Regex("""^rgba\((\d+),\s*(\d+),\s*(\d+),\s*([\d.]+)\)$""").find(v)?.let {
            val (r, g, b, a) = it.destructured
            return "$colorClass(red = $r, green = $g, blue = $b, alpha = ${Math.round(a.toDouble() * 255)})"
        }
        throw GradleException("unsupported color '$v'")
    }

    private fun isColor(v: Any?) = v is String && (v.startsWith("#") || v.startsWith("rgba("))

    private fun num(v: Any?): String {
        val d = (v as Number).toDouble()
        return if (d % 1.0 == 0.0) d.toInt().toString() else "${d}f"
    }

    private fun quoted(v: Any?) = "\"" + v.toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""

    /** Lengths become dp; counts and durations stay plain numbers. */
    private fun measure(key: String, v: Any?): String = when {
        isColor(v) -> color(v as String)
        v is String -> quoted(v)
        v is List<*> -> v.joinToString(prefix = "listOf(", postfix = ")") { "${num(it)}.dp" }
        key == "perRow" || key.endsWith("Seconds") -> num(v)
        else -> "${num(v)}.dp"
    }

    private fun camel(key: String) =
        key.split("_", "-").mapIndexed { i, p -> if (i == 0) p else p.replaceFirstChar(Char::uppercase) }.joinToString("")

    private fun type(key: String) = camel(key).replaceFirstChar(Char::uppercase)

    private fun obj(name: String, map: Map<*, *>, indent: String, extra: List<String> = emptyList(), leaf: (String, Any?) -> String) {
        sb.append("$indent" + "object ${type(name)} {\n")
        for ((k, v) in map) {
            k as String
            if (v is Map<*, *>) obj(k, v, "$indent    ", leaf = leaf)
            else sb.append("$indent    val ${camel(k)} = ${leaf(k, v)}\n")
        }
        for (line in extra) sb.append("$indent    $line\n")
        sb.append("$indent}\n")
    }

    // ---- Themes -------------------------------------------------------------

    /** The color roles, as the schema's nested `properties`: a role is a String path, a group a Map. */
    private fun roles(node: Map<*, *>): LinkedHashMap<String, Any> {
        val out = LinkedHashMap<String, Any>()
        for ((k, v) in node["properties"] as Map<*, *>) {
            v as Map<*, *>
            out[k as String] = if (v.containsKey("properties")) roles(v) else "leaf"
        }
        return out
    }

    private fun rgb(hex: String): DoubleArray? {
        val m = Regex("^#([0-9A-Fa-f]{6})$").find(hex) ?: return null
        val n = m.groupValues[1].toInt(16)
        return doubleArrayOf((n shr 16 and 255) / 255.0, (n shr 8 and 255) / 255.0, (n and 255) / 255.0)
    }

    /** WCAG relative luminance. */
    private fun luminance(hex: String): Double? = rgb(hex)?.let { c ->
        val l = c.map { if (it <= 0.04045) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
        0.2126 * l[0] + 0.7152 * l[1] + 0.0722 * l[2]
    }

    private fun contrast(a: String, b: String): Double? {
        val la = luminance(a) ?: return null
        val lb = luminance(b) ?: return null
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** Every problem with one theme, in words a theme author can act on. */
    private fun problems(file: java.io.File, theme: Map<*, *>, roles: Map<String, Any>, layouts: List<String>, maxGroundLuminance: Double, minContrast: Double): List<String> {
        val out = mutableListOf<String>()
        val id = theme["id"] as? String
        if (id == null) out += "\"id\" is missing"
        else if (id != file.nameWithoutExtension) out += "\"id\" is \"$id\" but the file is ${file.name}; they must match"
        else if (!Regex("^[a-z][a-z0-9-]{1,23}$").matches(id)) out += "\"id\" must be 2-24 lowercase letters, digits or dashes, starting with a letter"
        for ((key, max) in listOf("name" to 16, "description" to 80, "author" to 200)) {
            val v = theme[key] as? String
            if (v.isNullOrBlank()) out += "\"$key\" is missing"
            else if (v.length > max) out += "\"$key\" is ${v.length} characters; at most $max fit"
        }
        val layout = theme["layout"]
        if (layout != null && layout !in layouts) out += "\"layout\" is \"$layout\"; it must be one of ${layouts.joinToString(", ")}"
        val known = setOf("\$schema", "id", "name", "description", "author", "layout", "color")
        for (k in theme.keys) if (k !in known) out += "unknown key \"$k\""
        val color = theme["color"] as? Map<*, *> ?: return out + "\"color\" is missing"

        // Every role, nothing else, each a color.
        val flat = LinkedHashMap<String, String>()
        fun walk(prefix: String, want: Map<String, Any>, got: Map<*, *>) {
            for ((k, v) in want) {
                val path = prefix + k
                val value = got[k]
                if (v is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    if (value is Map<*, *>) walk("$path.", v as Map<String, Any>, value) else out += "color \"$path\" is missing (a group)"
                } else when {
                    value == null -> out += "color \"$path\" is missing"
                    value !is String || !isColor(value) -> out += "color \"$path\" is \"$value\"; use #RRGGBB"
                    else -> runCatching { color(value) }.onSuccess { flat[path] = value }.onFailure { out += "color \"$path\" is \"$value\"; use #RRGGBB" }
                }
            }
            for (k in got.keys) if (k !in want) out += "unknown color \"$prefix$k\""
        }
        walk("", roles, color)
        if (out.isNotEmpty()) return out

        for ((path, v) in flat) if (path != "chartFill" && rgb(v) == null) out += "color \"$path\" must be #RRGGBB (only chartFill may use rgba)"

        // A dark ground: the screen is on for hours.
        luminance(flat.getValue("ground"))?.let {
            if (it > maxGroundLuminance) out += "\"ground\" ${flat["ground"]} is too light for a screen that is on all day (luminance ${"%.3f".format(it)}, at most $maxGroundLuminance)"
        }

        // Everything that is read must be readable from across the room.
        val backgrounds = listOf("ground", "surface", "cell.base", "cell.baseMissing", "cell.baseEnded")
        val foregrounds = listOf(
            "text", "textBright", "textSecondary", "textMuted", "textDim", "textSoft", "amber",
            "kind.runner", "kind.database", "kind.job",
            "status.inProgress", "status.success", "status.trouble", "status.inactive", "focus",
        )
        fun check(fg: String, bg: String) {
            val r = contrast(flat.getValue(fg), flat.getValue(bg)) ?: return
            if (r < minContrast) out += "$fg ${flat[fg]} on $bg ${flat[bg]} has contrast ${"%.2f".format(r)}:1; it needs at least $minContrast:1"
        }
        for (bg in backgrounds) for (fg in foregrounds) check(fg, bg)
        check("textOnAmber", "amber")
        return out
    }

    private fun colorClassBody(name: String, roles: Map<String, Any>, indent: String) {
        val params = roles.entries.joinToString(", ") { (k, v) -> "val ${camel(k)}: ${if (v is Map<*, *>) type(k) else colorClass}" }
        sb.append("${indent}class $name($params)")
        val groups = roles.filterValues { it is Map<*, *> }
        if (groups.isEmpty()) { sb.append("\n"); return }
        sb.append(" {\n")
        @Suppress("UNCHECKED_CAST")
        for ((k, v) in groups) colorClassBody(type(k), v as Map<String, Any>, "$indent    ")
        sb.append("$indent}\n")
    }

    private fun colorValue(name: String, roles: Map<String, Any>, values: Map<*, *>): String =
        roles.entries.joinToString(", ", prefix = "$name(", postfix = ")") { (k, v) ->
            @Suppress("UNCHECKED_CAST")
            if (v is Map<*, *>) colorValue("$name.${type(k)}", v as Map<String, Any>, values[k] as Map<*, *>)
            else color(values[k] as String)
        }

    private fun colorGetters(roles: Map<String, Any>, path: String, indent: String) {
        for ((k, v) in roles) {
            @Suppress("UNCHECKED_CAST")
            if (v is Map<*, *>) {
                sb.append("${indent}object ${type(k)} {\n")
                colorGetters(v as Map<String, Any>, "$path.${camel(k)}", "$indent    ")
                sb.append("$indent}\n")
            } else {
                sb.append("${indent}val ${camel(k)}: $colorClass get() = $path.${camel(k)}\n")
            }
        }
    }

    @TaskAction
    fun generate() {
        val slurper = groovy.json.JsonSlurper()
        val root = slurper.parse(tokensFile.get().asFile) as Map<*, *>
        fun section(name: String) =
            root[name] as? Map<*, *> ?: throw GradleException("tokens.json: missing section '$name'")

        val dir = themesDir.get().asFile
        val schema = slurper.parse(dir.resolve("theme.schema.json")) as Map<*, *>
        val colorRoles = roles((schema["properties"] as Map<*, *>)["color"] as Map<*, *>)
        val layouts = ((schema["properties"] as Map<*, *>)["layout"] as Map<*, *>)["enum"] as List<*>
        val layoutIds = layouts.map { it as String }
        val files = dir.listFiles { f -> f.extension == "json" && f.name != "theme.schema.json" }!!.sortedBy { it.name }
        val themes = files.map { f ->
            val theme = runCatching { slurper.parse(f) as Map<*, *> }.getOrElse { e ->
                throw GradleException("Theme ${f.name} is not valid JSON: ${e.message}")
            }
            f to theme
        }
        val problems = themes.flatMap { (f, t) -> problems(f, t, colorRoles, layoutIds, maxGroundLuminance, minContrast).map { "  ${f.name}: $it" } }
        if (problems.isNotEmpty()) {
            throw GradleException("Theme problems (see docs/design/themes/README.md):\n" + problems.joinToString("\n"))
        }
        val defaultId = root["defaultTheme"] as? String ?: throw GradleException("tokens.json: missing 'defaultTheme'")
        val byId = themes.associate { (_, t) -> t["id"] as String to t }
        val default = byId[defaultId] ?: throw GradleException("tokens.json: defaultTheme '$defaultId' is not in docs/design/themes")
        // The default first, then the rest by name: the order settings shows them in.
        val ordered = listOf(defaultId) + byId.keys.filter { it != defaultId }.sortedBy { (byId[it]!!["name"] as String).lowercase() }

        sb.append("// Generated from docs/design/tokens.json and docs/design/themes. Do not edit.\n")
        sb.append("package com.blayter.mantel.ui.theme\n\n")
        sb.append("import androidx.compose.ui.text.font.FontWeight\n")
        sb.append("import androidx.compose.ui.unit.dp\n")
        sb.append("import androidx.compose.ui.unit.sp\n\n")

        sb.append("/** Every color role of a theme, as docs/design/themes/theme.schema.json lists them. */\n")
        colorClassBody("HiveColors", colorRoles, "")
        sb.append("\n/** One theme file from docs/design/themes. */\n")
        sb.append("class HiveTheme(val id: String, val name: String, val description: String, val author: String, val layout: MantelLayout, val colors: HiveColors)\n\n")
        sb.append("object HiveThemes {\n")
        for (id in ordered) {
            val t = byId[id]!!
            val layout = (t["layout"] as? String ?: "hive").uppercase().replace("-", "_")
            sb.append("    val ${camel(id)} = HiveTheme(${quoted(id)}, ${quoted(t["name"])}, ${quoted(t["description"])}, ${quoted(t["author"])}, MantelLayout.$layout, ")
            sb.append(colorValue("HiveColors", colorRoles, t["color"] as Map<*, *>))
            sb.append(")\n")
        }
        sb.append("    /** The theme a new display starts on. */\n")
        sb.append("    val default = ${camel(defaultId)}\n")
        sb.append("    /** The default first, then by name. */\n")
        sb.append("    val all = listOf(${ordered.joinToString(", ") { camel(it) }})\n")
        sb.append("}\n\n")

        sb.append("object HiveTokens {\n")
        val i = "    "
        sb.append("$i/** The colors of the theme in use. Read inside composition or drawing, so a theme change redraws. */\n")
        sb.append("${i}object Color {\n")
        colorGetters(colorRoles, "ActiveTheme.colors", "$i    ")
        sb.append("$i}\n")
        obj("font", section("font"), i) { _, v -> if (v is Number) "FontWeight(${num(v)})" else quoted(v) }
        obj("typeScale", section("typeScale"), i) { _, v -> "${num(v)}.sp" }
        sb.append("$i" + "object Space {\n")
        for (n in root["space"] as List<*>) sb.append("$i    val s${num(n)} = ${num(n)}.dp\n")
        sb.append("$i}\n")
        for (name in listOf("screen", "card", "hex", "motion")) obj(name, section(name), i, leaf = ::measure)
        obj("focus", section("focus"), i, extra = listOf("val color: $colorClass get() = ActiveTheme.colors.focus"), leaf = ::measure)
        obj("thresholds_proposed", section("thresholds_proposed"), i) { _, v -> num(v) }
        sb.append("}\n")

        val kt = kotlinDir.get().asFile.resolve("com/blayter/mantel/ui/theme/HiveTokens.kt")
        kt.parentFile.mkdirs()
        kt.writeText(sb.toString())

        val ground = (default["color"] as Map<*, *>)["ground"] as String
        val xml = resDir.get().asFile.resolve("values/hive_colors.xml")
        xml.parentFile.mkdirs()
        xml.writeText(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<!-- Generated from the default theme in docs/design/themes. -->\n" +
                "<resources>\n    <color name=\"hive_ground\">$ground</color>\n</resources>\n",
        )
    }
}

val generateHiveTokens = tasks.register<GenerateHiveTokens>("generateHiveTokens") {
    tokensFile.set(rootProject.layout.projectDirectory.file("docs/design/tokens.json"))
    themesDir.set(rootProject.layout.projectDirectory.dir("docs/design/themes"))
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
    // Draws the QR codes of on-TV setup. The encoder only; nothing is scanned.
    implementation("com.google.zxing:core:3.5.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-tooling-preview")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
