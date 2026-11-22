package com.blayter.mantel.ui.theme

/**
 * The main-screen layouts. A layout is code (`ui/layout`, and the Hive screen
 * in `ui/DashboardScreen`); a theme file picks one with its `layout` key.
 * The ids must match the `layout` enum in docs/design/themes/theme.schema.json,
 * which the build checks theme files against.
 */
enum class MantelLayout(val id: String, val label: String) {
    /** Honeycomb cells and craftsman cards: the approved design. */
    HIVE("hive", "Hive"),

    /** A network-operations wall: severity counts, a container table, an event list. */
    NOC("noc", "NOC"),

    /** Cyberpunk: angular tiles with lit segment bars, a CPU gauge, a deploy log. */
    NEON("neon", "Neon"),
}
