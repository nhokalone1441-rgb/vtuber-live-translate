package com.xyq.livetranslate

import android.os.Handler
import android.os.SystemClock

/**
 * Keeps model context untouched while presenting live karaoke text.
 * Completed sentence prefixes are promoted as soon as punctuation arrives;
 * the unfinished suffix remains in the live row. Silence is only a fallback
 * boundary for languages/dialogue that do not provide punctuation.
 */
class SubtitleStabilizer(
    private val handler: Handler,
    private val idleCommitMs: Long = SettingsStore.DEFAULT_STAB_IDLE_MS.toLong(),
    private val maxCurrentChars: Int = SettingsStore.DEFAULT_STAB_MAX_CHARS,
    private val onCommit: (text: String) -> Unit = {},
    private val onRender: (confirmed: String, current: String) -> Unit,
) {
    companion object {
        private val TERMINATORS = charArrayOf('。', '！', '？', '…', '～', '!', '?', '.')
        private const val CLOSING_MARKS = "\"'”’」』】）)]}"
        private const val DUPLICATE_WINDOW_MS = 900L
    }

    private val current = StringBuilder()
    private var lastCommitted = ""
    private var lastCommittedAtMs = 0L
    private var currentStartedAtMs = 0L
    private val idleCommit = Runnable {
        commitRemainder()
        render()
    }

    fun onFragment(t: String) {
        if (t.isEmpty()) return
        if (current.isEmpty()) currentStartedAtMs = SystemClock.uptimeMillis()
        appendWithOverlap(t)

        // Punctuation is a display boundary, not an audio/model boundary.
        // The Live session continues receiving the full uninterrupted audio stream.
        commitCompletedSentences()

        if (current.length >= maxCurrentChars) commitRemainder()
        handler.removeCallbacks(idleCommit)
        if (current.isNotEmpty()) handler.postDelayed(idleCommit, idleCommitMs)
        render()
    }

    fun reset() {
        handler.removeCallbacks(idleCommit)
        current.setLength(0)
        lastCommitted = ""
        lastCommittedAtMs = 0L
        currentStartedAtMs = 0L
    }

    private fun render() = onRender(lastCommitted, current.toString())

    private fun appendWithOverlap(frag: String) {
        val tail = current.toString()
        var k = minOf(tail.length, frag.length)
        while (k > 0) {
            if (tail.regionMatches(tail.length - k, frag, 0, k)) break
            k--
        }
        if (k < 2) k = 0
        current.append(frag, k, frag.length)
    }

    private fun commitCompletedSentences() {
        var boundary = -1
        for (i in current.indices) {
            if (current[i] in TERMINATORS) {
                boundary = i + 1
                while (boundary < current.length && current[boundary] in CLOSING_MARKS) boundary++
            }
        }
        if (boundary <= 0) return

        val completed = current.substring(0, boundary)
        current.delete(0, boundary)
        commitText(completed)

        if (current.isEmpty()) {
            currentStartedAtMs = 0L
        }
    }

    private fun commitRemainder() {
        if (current.isEmpty()) return
        val text = current.toString()
        val startedAtMs = currentStartedAtMs
        current.setLength(0)
        currentStartedAtMs = 0L
        commitText(text, startedAtMs)
    }

    private fun commitText(text: String, startedAtMs: Long = currentStartedAtMs) {
        if (text.isBlank()) return
        val now = SystemClock.uptimeMillis()
        val isImmediateReplay = startedAtMs - lastCommittedAtMs in 0..DUPLICATE_WINDOW_MS
        val committed = splitSentences(text).map { it.trim() }.filter { sentence ->
            sentence.isNotEmpty() &&
                !SubtitleSpeechFilter.isNonSpeechVocalization(sentence) &&
                !(isImmediateReplay && sentence == lastCommitted)
        }
        if (committed.isEmpty()) return

        // Commit each completed sentence separately so the overlay can show the
        // two most recent dialogue lines instead of one multi-sentence paragraph.
        committed.forEach { sentence ->
            lastCommitted = sentence
            lastCommittedAtMs = now
            onCommit(sentence)
        }
    }

    private fun splitSentences(s: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            cur.append(ch)
            if (ch in TERMINATORS) {
                while (i + 1 < s.length && s[i + 1] in CLOSING_MARKS) {
                    i++
                    cur.append(s[i])
                }
                out.add(cur.toString())
                cur.setLength(0)
            }
            i++
        }
        if (cur.isNotBlank()) out.add(cur.toString())
        return out
    }
}
