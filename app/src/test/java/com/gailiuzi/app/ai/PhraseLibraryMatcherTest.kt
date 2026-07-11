package com.gailiuzi.app.ai

import com.gailiuzi.app.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhraseLibraryMatcherTest {
    private val phrases = AiSettingsRepository.defaultPhrases()

    @Test
    fun `price question prioritizes price template`() {
        val match = PhraseLibraryMatcher.select(
            input = "请问这个项目多少钱，有团购套餐吗？",
            phrases = phrases,
            platform = Platform.DOUYIN,
        )

        assertEquals("default_price", match?.phrase?.id)
    }

    @Test
    fun `intent hint outweighs generic keyword score`() {
        val match = PhraseLibraryMatcher.select(
            input = "我想咨询一下",
            phrases = phrases,
            platform = Platform.MEITUAN,
            intentHint = "APPOINTMENT_QUERY",
        )

        assertEquals("default_appointment", match?.phrase?.id)
    }

    @Test
    fun `disabled phrase is ignored`() {
        val match = PhraseLibraryMatcher.select(
            input = "多少钱",
            phrases = phrases.map { if (it.id == "default_price") it.copy(enabled = false) else it },
        )

        assertTrue(match?.phrase?.id != "default_price")
    }

    @Test
    fun `empty library has no match`() {
        assertNull(PhraseLibraryMatcher.select("多少钱", emptyList()))
    }

    @Test
    fun `prompt includes preferred phrase when library is enabled`() {
        val prompt = ReplyPromptComposer.compose(
            profile = AssistantProfile(),
            phraseLibraryEnabled = true,
            phrases = phrases,
            userContent = "你们门店在哪里？",
            platform = Platform.XIAOHONGSHU,
            intentHint = "LOCATION_QUERY",
        )

        assertEquals("default_location", prompt.preferredPhraseId)
        assertTrue(prompt.instructions.contains("预置话术库已开启"))
        assertTrue(prompt.instructions.contains("门店位置咨询"))
    }

    @Test
    fun `prompt excludes phrases when library is disabled`() {
        val prompt = ReplyPromptComposer.compose(
            profile = AssistantProfile(),
            phraseLibraryEnabled = false,
            phrases = phrases,
            userContent = "多少钱？",
            platform = Platform.DOUYIN,
        )

        assertNull(prompt.preferredPhraseId)
        assertTrue(!prompt.instructions.contains("预置话术库已开启"))
    }
}
