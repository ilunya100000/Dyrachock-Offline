package com.example.network

import com.example.ui.roadmapAnnouncement
import com.example.viewmodel.AppLanguage
import org.junit.Assert.*
import org.junit.Test

class RoadmapAnnouncementTest {
    @Test fun `all four languages announce statistics features and full desktop ports`() {
        val terms = mapOf(
            AppLanguage.RU to listOf("статистика", "Динамика", "логи", "Разбор", "интерфейса", "Полноценные порты"),
            AppLanguage.EN to listOf("statistics", "performance", "logs", "analysis", "Interface", "Full Windows"),
            AppLanguage.IT to listOf("Statistiche", "Andamento", "Registri", "Analisi", "interfaccia", "Versioni complete"),
            AppLanguage.UA to listOf("статистика", "Динаміка", "логи", "Розбір", "інтерфейсу", "Повноцінні порти")
        )
        for ((language, words) in terms) {
            val (title, body) = roadmapAnnouncement(language)
            assertTrue(title.contains("0.4"))
            assertTrue(title.startsWith("📊"))
            assertTrue(body.contains("Windows") && body.contains("Linux"))
            for (word in words) assertTrue("$language missing $word", body.contains(word))
            assertEquals(6, body.count { it == '•' })
        }
    }
}
