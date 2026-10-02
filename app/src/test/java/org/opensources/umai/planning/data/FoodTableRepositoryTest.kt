package org.opensources.umai.planning.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.opensources.umai.planning.domain.FoodTableFixtures
import java.io.IOException

class FoodTableRepositoryTest {

    @Test
    fun `the table is read once from the assets, then kept`() = runBlocking {
        val opened = mutableListOf<String>()
        val repository = FoodTableRepository { name ->
            opened += name
            FoodTableFixtures.asset(name).inputStream()
        }

        val first = repository.table()
        val second = repository.table()

        assertNotNull(first)
        assertSame(first, second)
        assertEquals(listOf("ciqual.tsv", "basic_foods.tsv"), opened)
    }

    @Test
    fun `assets that cannot be read give no table, and are tried again`() = runBlocking {
        var readable = false
        val repository = FoodTableRepository { name ->
            if (!readable) throw IOException("missing $name")
            FoodTableFixtures.asset(name).inputStream()
        }

        assertNull(repository.table())
        readable = true
        assertNotNull(repository.table())
    }
}
