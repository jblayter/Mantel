package com.blayter.mantel.domain

/** The port Glances serves its web page and REST API on unless it was changed. */
const val GLANCES_PORT = 61208

/** How many cells the picker's comb holds: a row of four and a row of three. */
const val MAX_PICKER_CELLS = 7

/** A machine that answered as Glances during setup. */
data class FoundMachine(
    val address: String,
    val port: Int,
    val name: String,
    val threads: Int,
    val memoryGb: Int,
    val containers: Int,
) {
    /** Root of its Glances web server, as the config stores it. */
    val url: String get() = "http://$address:$port"

    /** The address as a person would say it: the port only when it is not the usual one. */
    val shownAddress: String get() = if (port == GLANCES_PORT) address else "$address:$port"
}

private fun ipv4Parts(address: String): List<Int>? {
    val parts = address.split('.')
    if (parts.size != 4) return null
    return parts.map { part ->
        if (part.isEmpty() || part.length > 3 || !part.all(Char::isDigit)) return null
        part.toInt().takeIf { it <= 255 } ?: return null
    }
}

fun isIpv4(address: String): Boolean = ipv4Parts(address) != null

/** "192.168.1.23" -> "192.168.1.": the part of an address the TV shares with its neighbors. */
fun networkPrefix(address: String): String? =
    ipv4Parts(address)?.let { "${it[0]}.${it[1]}.${it[2]}." }

/** The 254 addresses to ask, in order: every one that shares the TV's first three numbers. */
fun scanAddresses(ownAddress: String): List<String> {
    val prefix = networkPrefix(ownAddress) ?: return emptyList()
    return (1..254).map { "$prefix$it" }
}

/** Machines in the order the picker shows them: by address, number by number. */
fun sortedForPicker(machines: Collection<FoundMachine>): List<FoundMachine> =
    machines.sortedWith(compareBy<FoundMachine>({ ipv4Parts(it.address)?.fold(0L) { sum, n -> sum * 256 + n } ?: Long.MAX_VALUE }, { it.port }))

private val CountWords = listOf("No", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve")

/** "Three" for 3; digits beyond twelve. */
fun countWord(n: Int): String = CountWords.getOrNull(n) ?: n.toString()

/** The sentence under the picker's title. */
fun pickerSentence(found: Int, searching: Boolean): String = when {
    found == 0 && searching -> "Looking for machines on your network."
    found == 0 -> "Nothing has answered yet."
    found > MAX_PICKER_CELLS ->
        "${countWord(found)} answered on your network; the first ${countWord(MAX_PICKER_CELLS).lowercase()} are shown. Press OK on a cell to watch it or stop."
    else -> "${countWord(found)} answered on your network. Press OK on a cell to watch it or stop."
}

/** The main button's words, or null when nothing is picked and there is nothing to start. */
fun watchLabel(watched: Int): String? = when (watched) {
    0 -> null
    1 -> "Watch 1 machine"
    else -> "Watch $watched machines"
}

/** Which part of the address the number pad is typing into. */
enum class AddressField { LastNumber, Whole, Port }

/**
 * The address being typed on the number pad. To begin with only the last
 * number is asked for: [prefix] is the TV's own network and stays as it is.
 * "Change the first part" opens the whole address; "Change the port" moves
 * the pad to the port. A key that would make the address impossible is ignored.
 */
data class AddressEntry(
    val prefix: String,
    val typed: String = "",
    val port: String = GLANCES_PORT.toString(),
    val field: AddressField = if (prefix.isEmpty()) AddressField.Whole else AddressField.LastNumber,
    /** Where the pad goes back to after the port. */
    private val addressField: AddressField = field,
) {
    val address: String get() = prefix + typed

    val portNumber: Int? get() = port.toIntOrNull()?.takeIf { it in 1..65535 }

    /** Ready to be checked. */
    val isComplete: Boolean get() = isIpv4(address) && portNumber != null

    fun press(key: Char): AddressEntry = when (field) {
        AddressField.LastNumber -> if (key.isDigit() && fitsNumber(typed + key, 255)) copy(typed = typed + key) else this
        AddressField.Whole -> if (fitsAddress(typed + key)) copy(typed = typed + key) else this
        AddressField.Port -> if (key.isDigit() && fitsNumber(port + key, 65535)) copy(port = port + key) else this
    }

    fun delete(): AddressEntry = when (field) {
        AddressField.Port -> copy(port = port.dropLast(1))
        else -> copy(typed = typed.dropLast(1))
    }

    /** Unlocks the first three numbers, for a machine on another network. */
    fun changeFirstPart(): AddressEntry =
        copy(prefix = "", typed = prefix + typed, field = AddressField.Whole, addressField = AddressField.Whole)

    /** Moves the pad to the port, or back to the address. */
    fun togglePort(): AddressEntry =
        if (field == AddressField.Port) copy(field = addressField) else copy(field = AddressField.Port)

    private fun fitsNumber(text: String, max: Int) =
        text.length <= max.toString().length && !(text.length > 1 && text[0] == '0') && text.toInt() <= max

    /** True while [text] can still grow into an IPv4 address. */
    private fun fitsAddress(text: String): Boolean {
        if (!text.all { it.isDigit() || it == '.' }) return false
        val parts = text.split('.')
        if (parts.size > 4) return false
        // Only the part being typed may be empty.
        if (parts.dropLast(1).any { it.isEmpty() }) return false
        return parts.all { it.isEmpty() || fitsNumber(it, 255) }
    }
}

/**
 * The machines to store after the picker was opened from settings. The ones
 * already watched keep their order, so the first machine stays the first;
 * new ones follow in the picker's order. A watched machine the search did
 * not find cannot be unpicked there, so it stays.
 */
fun machinesAfterPicking(stored: List<String>, watched: Set<String>, pickerOrder: List<String>): List<String> =
    stored.filter { it in watched } + pickerOrder.filter { it in watched && it !in stored }

/** How long the sign-in code still works, as the sentence beside the spinner ends. */
fun codeTimeLeft(seconds: Long): String = when (val minutes = seconds / 60) {
    0L -> "This code works for less than a minute more."
    1L -> "This code works for 1 more minute."
    else -> "This code works for $minutes more minutes."
}

/** The sentence under "Ready to start". */
fun readySentence(machineNames: List<String>, liveDeploys: Boolean): String {
    val machines = when (machineNames.size) {
        1 -> "Mantel shows ${machineNames[0]}."
        else -> "Mantel takes turns between the ${countWord(machineNames.size).lowercase()} machines."
    }
    return if (liveDeploys) "$machines Deploys stay on screen the whole time." else machines
}

/** "Watching 2 machines". */
fun watchingHeading(machines: Int): String = if (machines == 1) "Watching 1 machine" else "Watching $machines machines"

/** The heading of the Ready screen's deploys card. */
fun deploysHeading(repos: Int, login: String): String = when (repos) {
    0 -> "Signed in as $login · no repositories yet"
    1 -> "Deploys from 1 repository · signed in as $login"
    else -> "Deploys from $repos repositories · signed in as $login"
}

/** How many lines a Ready card lists before it says "and N more". */
const val MAX_READY_LINES = 10

/** The lines of a Ready card: all of [names] if they fit, or one fewer and how many are left out. */
fun readyLines(names: List<String>): Pair<List<String>, Int> =
    if (names.size <= MAX_READY_LINES) names to 0 else names.take(MAX_READY_LINES - 1) to names.size - (MAX_READY_LINES - 1)

/** "Mantel reads 3 repositories now." */
fun repositoriesNow(repos: Int): String = when (repos) {
    0 -> "Mantel reads no repositories yet."
    1 -> "Mantel reads 1 repository now."
    else -> "Mantel reads $repos repositories now."
}
