package com.example.agent

import com.example.agent.core.memory.AgentMemory
import com.example.agent.domain.model.MemoryEntry
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AgentMemoryTest {

    private val memory = AgentMemory(
        listOf(
            MemoryEntry("whatsapp", "com.whatsapp"),
            MemoryEntry("chrome", "com.android.chrome"),
        )
    )

    @Test
    fun `resolves an exact alias`() {
        assertThat(memory.resolve("chrome")).isEqualTo("com.android.chrome")
    }

    @Test
    fun `resolves an alias inside a longer phrase`() {
        assertThat(memory.resolve("open whatsapp please")).isEqualTo("com.whatsapp")
    }

    @Test
    fun `returns null for an unknown alias`() {
        assertThat(memory.resolve("telegram")).isNull()
    }

    @Test
    fun `empty memory is empty`() {
        assertThat(AgentMemory.EMPTY.isEmpty()).isTrue()
    }
}
