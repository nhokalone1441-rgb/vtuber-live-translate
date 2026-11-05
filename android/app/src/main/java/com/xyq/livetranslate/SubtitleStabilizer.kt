package com.xyq.livetranslate

import android.os.Handler
import android.os.SystemClock

/**
 * Buffers streaming translation fragments into readable dialogue blocks.
 * No partial text is rendered: a block is committed after a short quiet window,
 * or at a natural boundary when it grows beyond the three-line subtitle budget.
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
        private const val MIN_CHUNK_BOUNDARY_RATIO = 0.55
    }

    private val current = StringBuilder()
    private var lastCommitted = ""
    private var lastCommittedAtMs = 0L
    private var currentStartedAtMs = 0L
    private val idleCommit = Runnable {
        commitRemainder()
        render()
    }

    fun onFragment(fragment: String) {
        if (fragment.isEmpty()) return
        if (current.isEmpty()) currentStartedAtMs = SystemClock.uptimeMillis()
        appendWithOverlap(fragment)
        forceCommitAtNaturalBoundary()
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
        render()
    }

    // Deliberately hide unfinished fragments; the overlay is a subtitle box, not karaoke.
    private fun render() = onRender(lastCommitted, "")

    private fun appendWithOverlap(fragment: String) {
        val tail = current.toString()
        when {
            tail.isEmpty() -> current.append(fragment)
            fragment.startsWith(tail) && fragment.length > tail.length -> {
                // Some Live transcription events are cumulative snapshots, not deltas.
                current.setLength(0)
                current.append(fragment)
            }
            tail.startsWith(fragment) -> Unit // Ignore a stale/shorter snapshot.
            else -> {
                var overlap = minOf(tail.length, fragment.length)
                while (overlap > 0) {
                    if (tail.regionMatches(tail.length - overlap, fragment, 0, overlap)) break
                    overlap--
                }
                if (overlap < 2) overlap = 0
                current.append(fragment, overlap, fragment.length)
            }
        }
    }

    private fun forceCommitAtNaturalBoundary() {
        val limit = maxCurrentChars.coerceAtLeast(40)
        if (current.length < limit) return

        val searchEnd = minOf(limit, current.length)
        var boundary = -1
        for (index in 0 until searchEnd) {
            if (current[index] in TERMINATORS) {
                boundary = index + 1
                while (boundary < current.length && current[boundary] in CLOSING_MARKS) boundary++
            }
        }
        if (boundary < limit * MIN_CHUNK_BOUNDARY_RATIO) {
            for (index in searchEnd - 1 downTo (limit * MIN_CHUNK_BOUNDARY_RATIO).toInt()) {
                if (current[index].isWhitespace()) {
                    boundary = index + 1
                    break
                }
            }
        }
        if (boundary <= 0 && current.length < limit * 2) return
        if (boundary <= 0) boundary = limit

        val startedAt = currentStartedAtMs
        val block = current.substring(0, boundary).trim()
        current.delete(0, boundary)
        currentStartedAtMs = if (current.isEmpty()) 0L else SystemClock.uptimeMillis()
        commitText(block, startedAt)
    }

    private fun commitRemainder() {
        if (current.isEmpty()) return
        val text = current.toString().trim()
        val startedAt = currentStartedAtMs
        current.setLength(0)
        currentStartedAtMs = 0L
        commitText(text, startedAt)
    }

    private fun commitText(text: String, startedAtMs: Long) {
        if (text.isBlank() || SubtitleSpeechFilter.isNonSpeechVocalization(text)) return
        val now = SystemClock.uptimeMillis()
        val isImmediateReplay = startedAtMs - lastCommittedAtMs in 0..DUPLICATE_WINDOW_MS
        if (isImmediateReplay && text == lastCommitted) return
        lastCommitted = text
        lastCommittedAtMs = now
        onCommit(text)
    }
}
