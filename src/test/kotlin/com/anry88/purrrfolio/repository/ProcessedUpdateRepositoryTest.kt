package com.anry88.purrrfolio.repository

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.verify
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.jdbc.core.JdbcTemplate

class ProcessedUpdateRepositoryTest {

    @Test
    fun `processing claim is marked complete only once`() {
        val jdbc = mock(JdbcTemplate::class.java)
        `when`(jdbc.update(anyString(), eq(10L))).thenReturn(1, 0)
        val repository = ProcessedUpdateRepository(jdbc)

        assertTrue(repository.markProcessed(10L))
        assertFalse(repository.markProcessed(10L))
    }

    @Test
    fun `failed processing releases only an active claim`() {
        val jdbc = mock(JdbcTemplate::class.java)
        val repository = ProcessedUpdateRepository(jdbc)

        repository.releaseClaim(10L)

        verify(jdbc).update(anyString(), eq(10L))
    }
}
