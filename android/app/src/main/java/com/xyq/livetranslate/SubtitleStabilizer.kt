package com.xyq.livetranslate

import android.os.Handler

/**
 * 字幕稳定器：把 Live Translate 的碎片输出整理成「确认行 + 当前行」。
 * - 碎片边界重叠合并（服务端偶尔把结尾几个字重发一遍）
 * - 标点只作为内容保留，不立即切行；停顿达到 idleCommitMs 后整段转正
 * - 超过 maxCurrentChars 时才强制转正，避免长对话无限累积（高级设置可调）
 * 所有方法必须在主线程调用（handler 即主线程 Handler）。
 */
class SubtitleStabilizer(
    private val handler: Handler,
    private val idleCommitMs: Long = SettingsStore.DEFAULT_STAB_IDLE_MS.toLong(),
    private val maxCurrentChars: Int = SettingsStore.DEFAULT_STAB_MAX_CHARS,
    private val onRender: (confirmed: String, current: String) -> Unit,
) {
    companion object {
        private val TERMINATORS = charArrayOf('。', '！', '？', '…', '～', '!', '?')
    }

    private val current = StringBuilder()
    private var lastCommitted = ""
    private val idleCommit = Runnable {
        commit(force = true)
        render()
    }

    fun onFragment(t: String) {
        if (t.isEmpty()) return
        appendWithOverlap(t)
        commit(force = current.length >= maxCurrentChars)
        handler.removeCallbacks(idleCommit)
        if (current.isNotEmpty()) handler.postDelayed(idleCommit, idleCommitMs)
        render()
    }

    fun reset() {
        handler.removeCallbacks(idleCommit)
        current.setLength(0)
        lastCommitted = ""
    }

    private fun render() = onRender(lastCommitted, current.toString())

    /** 若新碎片的开头和缓冲区结尾重叠（>=2 字），只追加不重叠的部分。 */
    private fun appendWithOverlap(frag: String) {
        val tail = current.toString()
        var k = minOf(tail.length, frag.length)
        while (k > 0) {
            if (tail.regionMatches(tail.length - k, frag, 0, k)) break
            k--
        }
        if (k < 2) k = 0 // 单字重叠多半是巧合（"…的" + "的…"），不按重叠处理
        current.append(frag, k, frag.length)
    }

    /** 将一个停顿边界内的整段话一次转正；不因句号/问号到来就逐句滚动。 */
    private fun commit(force: Boolean) {
        if (!force) return
        val text = current.toString()
        if (text.isBlank()) return

        val committed = ArrayList<String>()
        for (s in splitSentences(text)) {
            val sentence = s.trim()
            if (sentence.isEmpty()) continue
            // Live 模型偶尔会把非语言呻吟识别/翻译成拟声词；在字幕落盘前再做一道过滤。
            if (SubtitleSpeechFilter.isNonSpeechVocalization(sentence)) continue
            // 连续重复：与上一完整块重复，或被上一完整块包含时丢弃。
            if (sentence == lastCommitted || lastCommitted.contains(sentence)) continue
            committed += s
        }

        current.setLength(0)
        if (committed.isNotEmpty()) {
            // 保留块内原始标点与空格，一次交给历史区，避免每个短句快速顶走上一句。
            lastCommitted = committed.joinToString(separator = "")
        }
    }

    private fun splitSentences(s: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        for (ch in s) {
            cur.append(ch)
            if (ch in TERMINATORS) {
                out.add(cur.toString())
                cur.setLength(0)
            }
        }
        if (cur.isNotBlank()) out.add(cur.toString())
        return out
    }
}
