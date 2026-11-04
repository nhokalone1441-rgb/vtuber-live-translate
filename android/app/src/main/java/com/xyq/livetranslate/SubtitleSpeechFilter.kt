package com.xyq.livetranslate

/**
 * Last-line subtitle guard for non-lexical vocalizations.
 *
 * Only suppresses a line when every lexical token is a standalone vocalization.
 * Exclamations attached to actual dialogue (for example "Á! Có ai không?") survive.
 */
internal object SubtitleSpeechFilter {
    private val vocalizations = setOf(
        "ưm", "ưmmm", "um", "umm", "uhm", "uhmm", "hmm", "hmmm", "ah", "aah", "aaa",
        "uh", "uhh", "oh", "ooh", "唔", "嗯", "呃", "啊", "哦", "ớ", "á", "ơ",
    )
    private val standaloneGroans = setOf("ưm", "ưmmm", "um", "umm", "uhm", "uhmm", "aah", "hmmm", "ooh", "aaa", "唔", "á", "ớ", "ơ")

    fun isNonSpeechVocalization(text: String): Boolean {
        val tokens = Regex("""[\p{L}]+""")
            .findAll(text.lowercase())
            .map { it.value }
            .toList()
        if (tokens.isEmpty() || tokens.any { it !in vocalizations }) return false
        if (tokens.size == 1 && tokens.single() in setOf("á", "ớ", "ơ") &&
            text.any { it in "!?！？" }
        ) return false
        return tokens.size >= 2 || tokens.single() in standaloneGroans
    }
}
