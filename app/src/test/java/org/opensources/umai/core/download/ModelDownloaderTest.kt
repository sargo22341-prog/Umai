package org.opensources.umai.core.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

/** The download of model files, with a download manager that answers at once. */
class ModelDownloaderTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private data class Model(val name: String, val files: List<ModelFile>)

    private data class ModelFile(
        override val url: String,
        override val fileName: String,
        override val sizeBytes: Long,
        override val sha256: String?,
    ) : DownloadableFile

    private class MemoryRecord : DownloadRecord<Model> {
        var installed: Model? = null
        var pending: PendingDownload<Model>? = null

        override suspend fun installed() = installed

        override suspend fun pending() = pending

        override suspend fun setInstalled(model: Model?) {
            installed = model
        }

        override suspend fun setPending(pending: PendingDownload<Model>?) {
            this.pending = pending
        }
    }

    /** Writes what [served] holds for a URL as soon as it is asked for; [failing] URLs fail. */
    private class InstantDownloads(private val served: Map<String, ByteArray>, private val failing: Set<String> = emptySet()) :
        FileDownloads {
        private val urls = mutableListOf<String>()
        val removed = mutableListOf<Long>()

        override fun enqueue(url: String, destination: File, title: String, description: String): Long {
            served[url]?.let(destination::writeBytes)
            urls += url
            return urls.size.toLong()
        }

        override fun progress(id: Long): DownloadProgress {
            val url = urls[(id - 1).toInt()]
            val state = if (url in failing) DownloadProgress.State.FAILED else DownloadProgress.State.DONE
            return DownloadProgress(state, downloaded = served[url]?.size?.toLong() ?: 0L, total = 0L)
        }

        override fun remove(id: Long) {
            removed += id
        }
    }

    private val weights = "weights".toByteArray()
    private val record = MemoryRecord()
    private val dir by lazy { folder.newFolder("models") }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun model(name: String, sha: String? = sha256(weights)) =
        Model(name, listOf(ModelFile("https://models.example/$name", "$name.bin", weights.size.toLong(), sha)))

    private fun downloader(downloads: FileDownloads, space: Long = Long.MAX_VALUE) = ModelDownloader(
        downloads = downloads,
        modelsFolder = { dir },
        record = record,
        scope = scope,
        filesOf = Model::files,
        titleOf = Model::name,
        description = { "Downloading" },
        spaceMargin = 0L,
        availableBytes = { space },
    )

    private suspend fun ModelDownloader<Model>.settled(): DownloadState<Model> =
        withTimeout(TIMEOUT_MS) { state.first { it is DownloadState.Idle || it is DownloadState.Failed } }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `a checked download is installed and replaces the previous model`() = runBlocking {
        val previous = model("small")
        dir.resolve("small.bin").writeBytes(weights)
        record.installed = previous
        val downloader = downloader(InstantDownloads(mapOf("https://models.example/large" to weights)))

        downloader.install(model("large"))

        assertEquals(DownloadState.Idle, downloader.settled())
        assertEquals(model("large"), record.installed)
        assertNull(record.pending)
        assertEquals("large.bin", downloader.installedFile(model("large").files.single())?.name)
        assertFalse(dir.resolve("small.bin").exists())
    }

    @Test
    fun `a file whose hash differs is refused and removed`() = runBlocking {
        val downloader = downloader(InstantDownloads(mapOf("https://models.example/large" to weights)))

        downloader.install(model("large", sha = sha256("other".toByteArray())))

        assertEquals(DownloadState.Failed(model("large", sha256("other".toByteArray())), InstallFailure.CORRUPTED), downloader.settled())
        assertNull(record.installed)
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `a failed download is reported and forgotten`() = runBlocking {
        val url = "https://models.example/large"
        val downloads = InstantDownloads(mapOf(url to weights), failing = setOf(url))
        val downloader = downloader(downloads)

        downloader.install(model("large"))

        assertEquals(InstallFailure.DOWNLOAD_FAILED, (downloader.settled() as DownloadState.Failed).failure)
        assertEquals(listOf(1L), downloads.removed)
        assertNull(record.pending)
    }

    @Test
    fun `without room for the files nothing is downloaded`() = runBlocking {
        val downloads = InstantDownloads(emptyMap())
        val downloader = downloader(downloads, space = 3L)

        downloader.install(model("large"))

        assertEquals(InstallFailure.NOT_ENOUGH_SPACE, (downloader.state.value as DownloadState.Failed).failure)
        assertNull(record.pending)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
