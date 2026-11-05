package com.xyq.livetranslate

/**
 * 每模式运行时草稿：语言方向 + 场景引用。
 * 场景库是唯一的可复用配置；语言随时可调，不属于任何库条目。
 */
data class TranslationPlan(
    val mode: TranslationMode,
    val sourceLanguageCode: String = DEFAULT_SOURCE_LANGUAGE,
    val targetLanguageCode: String = DEFAULT_TARGET_LANGUAGE,
    val scenePresetId: String = defaultSceneId(mode),
) {
    fun normalized(): TranslationPlan {
        // Migrate legacy automatic-detection plans to the current Multi selector.
        val source = when {
            sourceLanguageCode.equals("auto", ignoreCase = true) -> "multi"
            TranslationLanguageCatalog.sources.any { it.code.equals(sourceLanguageCode, ignoreCase = true) } ->
                sourceLanguageCode
            else -> DEFAULT_SOURCE_LANGUAGE
        }
        val target = targetLanguageCode.takeIf { code ->
            TranslationLanguageCatalog.targets.any { it.code.equals(code, ignoreCase = true) }
        } ?: DEFAULT_TARGET_LANGUAGE
        val sceneId = scenePresetId.trim().ifEmpty { defaultSceneId(mode) }
        return copy(
            sourceLanguageCode = source,
            targetLanguageCode = target,
            scenePresetId = sceneId,
        )
    }

    val directionLabel: String
        get() = "${TranslationLanguageCatalog.source(sourceLanguageCode).label} → ${TranslationLanguageCatalog.target(targetLanguageCode).label}"

    companion object {
        const val DEFAULT_SOURCE_LANGUAGE = "multi"
        const val DEFAULT_TARGET_LANGUAGE = "vi"

        fun default(mode: TranslationMode): TranslationPlan = TranslationPlan(mode = mode)

        fun defaultSceneId(mode: TranslationMode): String = DefaultSceneCatalog.fallbackId(mode)
    }
}
