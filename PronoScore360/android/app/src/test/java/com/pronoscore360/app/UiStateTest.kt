package com.pronoscore360.app

import com.pronoscore360.app.vm.UiState
import com.pronoscore360.app.vm.load
import com.pronoscore360.core.api.ApiException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UiStateTest {
    @Test fun `load publica Loading y luego Success`() = runTest {
        val s = MutableStateFlow<UiState<Int>>(UiState.Idle)
        load(s) { 42 }; assertEquals(UiState.Loading, s.value); advanceUntilIdle()
        assertEquals(UiState.Success(42), s.value)
    }
    @Test fun `load convierte ApiException en Error con codigo`() = runTest {
        val s = MutableStateFlow<UiState<Int>>(UiState.Idle)
        load(s) { throw ApiException(429, "quota_exceeded", "Límite alcanzado") }; advanceUntilIdle()
        val e = s.value as UiState.Error
        assertEquals("quota_exceeded", e.code); assertTrue(e.message.contains("Límite"))
    }
}
