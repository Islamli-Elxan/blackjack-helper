package az.nexgen.bjhelper

object RankParser {
    /** Returns 2..9, 10 (any ten-value card) or 11 (ace). Null when the text is not a card rank. */
    fun parse(raw: String): Int? {
        val s = raw.trim().uppercase().trim { !it.isLetterOrDigit() }
        return when (s) {
            "A" -> 11
            "2", "3", "4", "5", "6", "7", "8", "9" -> s.toInt()
            "10", "1O", "IO", "LO", "I0", "L0", "J", "Q", "K", "O", "0" -> 10
            else -> null
        }
    }
}
