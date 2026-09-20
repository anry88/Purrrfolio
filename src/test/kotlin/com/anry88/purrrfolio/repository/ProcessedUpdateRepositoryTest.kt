package com.anry88.purrrfolio.repository

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate

class ProcessedUpdateRepositoryTest {

    @Test
    fun `duplicate update is the only suppressed database error`() {
        val jdbc = mock(JdbcTemplate::class.java)
        `when`(jdbc.update("INSERT INTO processed_telegram_updates (update_id) VALUES (?)", 10L))
            .thenThrow(DuplicateKeyException("duplicate"))

        assertFalse(ProcessedUpdateRepository(jdbc).recordUpdate(10L))
    }

    @Test
    fun `database outage is not misclassified as a duplicate`() {
        val jdbc = mock(JdbcTemplate::class.java)
        `when`(jdbc.update("INSERT INTO processed_telegram_updates (update_id) VALUES (?)", 10L))
            .thenThrow(DataAccessResourceFailureException("offline"))

        assertThrows(DataAccessResourceFailureException::class.java) {
            ProcessedUpdateRepository(jdbc).recordUpdate(10L)
        }
    }
}
