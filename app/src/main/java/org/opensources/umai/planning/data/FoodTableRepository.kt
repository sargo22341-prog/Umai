package org.opensources.umai.planning.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.opensources.umai.planning.domain.FoodTable
import java.io.IOException
import java.io.InputStream

/**
 * The table of basic foods shipped with the app, read once, off the main
 * thread, the first time a food is typed, then kept: a few thousand lines,
 * searched at every key.
 *
 * [open] opens a file of the app's assets by its name.
 */
class FoodTableRepository(private val open: (String) -> InputStream) {

    private val mutex = Mutex()
    private var table: FoodTable? = null

    /** The table; `null` when its files cannot be read, which the next call tries again. */
    suspend fun table(): FoodTable? = mutex.withLock {
        table ?: withContext(Dispatchers.IO) { read() }?.also { table = it }
    }

    private fun read(): FoodTable? = try {
        FoodTable.read(lines(CIQUAL), lines(USUAL_FOODS))
    } catch (_: IOException) {
        null
    }

    private fun lines(name: String): List<String> = open(name).bufferedReader().use { it.readLines() }

    private companion object {
        const val CIQUAL = "ciqual.tsv"
        const val USUAL_FOODS = "basic_foods.tsv"
    }
}
