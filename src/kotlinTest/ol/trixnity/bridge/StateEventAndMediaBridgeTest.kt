package ol.trixnity.bridge

import de.connect2x.trixnity.client.media.MediaService
import de.connect2x.trixnity.client.media.PlatformMedia
import de.connect2x.trixnity.client.media.okio.OkioPlatformMedia
import de.connect2x.trixnity.clientserverapi.model.media.FileTransferProgress
import de.connect2x.trixnity.clientserverapi.model.media.ThumbnailResizingMethod
import de.connect2x.trixnity.core.model.events.m.room.AvatarEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedFile
import de.connect2x.trixnity.core.model.events.m.room.NameEventContent
import de.connect2x.trixnity.core.model.events.m.room.TopicEventContent
import de.connect2x.trixnity.utils.ByteArrayFlow
import de.connect2x.trixnity.utils.toByteArray
import io.ktor.http.ContentType
import java.lang.reflect.Proxy
import java.nio.file.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath

class StateEventAndMediaBridgeTest {
    @Test
    fun supportedStateEventMapsBuildExpectedUpstreamContent() {
        val parsed = requireStateEventSpec(
            mapOf(
                BridgeSchema.SendStateEventRequest.stateEvent to mapOf(
                    BridgeSchema.type to "m.room.avatar",
                    BridgeSchema.stateKey to "",
                    BridgeSchema.url to "mxc://example.org/avatar",
                ),
            ),
            BridgeSchema.SendStateEventRequest.stateEvent,
        )

        assertEquals("", parsed.stateKey)
        val content = parsed.toEventContent()
        val avatar = assertIs<AvatarEventContent>(content)
        assertEquals("mxc://example.org/avatar", avatar.url)
    }

    @Test
    fun unsupportedStateEventTypesThrowADescriptiveException() {
        val error = assertFailsWith<IllegalArgumentException> {
            requireStateEventSpec(
                mapOf(
                    BridgeSchema.SendStateEventRequest.stateEvent to mapOf(
                        BridgeSchema.type to "m.room.unknown",
                    ),
                ),
                BridgeSchema.SendStateEventRequest.stateEvent,
            )
        }

        assertContains(error.message ?: "", "m.room.unknown")
    }

    @Test
    fun prepareUploadMediaReadsBytesAndForwardsMimeType() = runTest {
        val recorder = MediaRecorder()
        val source = createTempFile("trixnity-media-", ".png")
        source.writeBytes("image-data".toByteArray())

        val cacheUri = prepareUploadMedia(
            mediaService = mediaService(recorder),
            sourcePath = source,
            mimeType = ContentType.Image.PNG,
        )

        assertEquals("upload://plain/1", cacheUri)
        assertContentEquals("image-data".toByteArray(), recorder.prepared.single())
        assertEquals(ContentType.Image.PNG, recorder.contentTypes.single())
    }

    @Test
    fun uploadPreparedMediaDelegatesKeepInCacheFlag() = runTest {
        val recorder = MediaRecorder()

        val mxcUri = uploadPreparedMedia(
            mediaService = mediaService(recorder),
            cacheUri = "upload://plain/1",
            keepInCache = false,
        )

        assertEquals("mxc://example.org/plain/1", mxcUri)
        assertEquals(listOf("upload://plain/1" to false), recorder.uploaded)
    }

    @Test
    fun uploadPreparedMediaWithProgressReportsSnapshotsAndFinalSuccessFromOneOperation() = runTest {
        val recorder = MediaRecorder()
        val seenProgress = mutableListOf<Map<*, *>>()

        val mxcUri =
            uploadPreparedMediaWithProgress(
                mediaService(recorder),
                cacheUri = "upload://plain/1",
                keepInCache = false,
                onProgress = { value: Any? -> seenProgress += assertIs<Map<*, *>>(value) },
            )

        assertEquals("mxc://example.org/plain/1", mxcUri)
        assertEquals(1, recorder.uploaded.size)
        assertNotNull(recorder.uploadProgress.single())
        assertEquals(
            listOf<Map<*, *>>(
                mapOf(
                    BridgeSchema.transferred to 0L,
                    BridgeSchema.total to 4L,
                ),
                mapOf(
                    BridgeSchema.transferred to 4L,
                    BridgeSchema.total to 4L,
                ),
            ),
            seenProgress,
        )
    }

    @Test
    fun getMediaReturnsAStreamFirstHandle() = runTest {
        val recorder = MediaRecorder()

        val handle =
            getMedia(
                scope = backgroundScope,
                mediaService = mediaService(recorder),
                uri = "mxc://example.org/plain",
            )

        val stream = assertIs<java.io.InputStream>(handle[BridgeSchema.inputStream])
        assertEquals("plain-bytes", stream.reader().readText())
        assertIs<PlatformMedia>(handle[BridgeSchema.raw])
        assertEquals(listOf("mxc://example.org/plain"), recorder.downloadedPlain)
    }

    @Test
    fun getEncryptedMediaDelegatesToUpstreamEncryptedDownload() = runTest {
        val recorder = MediaRecorder()
        val encryptedFile =
            EncryptedFile(
                url = "mxc://example.org/encrypted",
                key = EncryptedFile.JWK("secret"),
                initialisationVector = "iv",
                hashes = mapOf("sha256" to "hash"),
            )

        val handle =
            getEncryptedMedia(
                scope = backgroundScope,
                mediaService = mediaService(recorder),
                encryptedFile = encryptedFile,
            )

        val stream = assertIs<java.io.InputStream>(handle[BridgeSchema.inputStream])
        assertEquals("encrypted-bytes", stream.reader().readText())
        assertEquals(listOf(encryptedFile), recorder.downloadedEncrypted)
    }

    @Test
    fun getThumbnailDelegatesExplicitSizingOptions() = runTest {
        val recorder = MediaRecorder()

        val handle =
            getThumbnail(
                scope = backgroundScope,
                mediaService = mediaService(recorder),
                uri = "mxc://example.org/plain",
                width = 320,
                height = 200,
                method = ThumbnailResizingMethod.SCALE,
                animated = true,
            )

        val stream = assertIs<java.io.InputStream>(handle[BridgeSchema.inputStream])
        assertEquals("thumbnail-bytes", stream.reader().readText())
        assertEquals(
            listOf(
                ThumbnailRequest(
                    uri = "mxc://example.org/plain",
                    width = 320,
                    height = 200,
                    method = ThumbnailResizingMethod.SCALE,
                    animated = true,
                ),
            ),
            recorder.thumbnailRequests,
        )
    }

    @Test
    fun temporaryMediaFileDelegatesToUpstreamTemporaryFileSupport() = runTest {
        val path = createTempFile("trixnity-media-", ".tmp")
        path.writeBytes("temp".toByteArray())

        val tempFile =
            temporaryMediaFile(
                platformMedia = TestOkioPlatformMedia(
                    chunks = listOf("temp".toByteArray()),
                    temporaryPath = path,
                ),
            )

        assertEquals(path.toAbsolutePath(), tempFile[BridgeSchema.path])
        assertNotNull(tempFile[BridgeSchema.raw])
    }

    @Test
    fun roomNameAndTopicStateEventsMapToTheirUpstreamClasses() {
        val name = requireStateEventSpec(
            mapOf(
                BridgeSchema.SendStateEventRequest.stateEvent to mapOf(
                    BridgeSchema.type to "m.room.name",
                    BridgeSchema.name to "Ops Bot",
                ),
            ),
            BridgeSchema.SendStateEventRequest.stateEvent,
        )
        val topic = requireStateEventSpec(
            mapOf(
                BridgeSchema.SendStateEventRequest.stateEvent to mapOf(
                    BridgeSchema.type to "m.room.topic",
                    BridgeSchema.topic to "Incident chatter",
                ),
            ),
            BridgeSchema.SendStateEventRequest.stateEvent,
        )

        assertEquals("", name.stateKey)
        assertEquals("", topic.stateKey)
        assertIs<NameEventContent>(name.toEventContent())
        assertIs<TopicEventContent>(topic.toEventContent())
    }

    private data class MediaRecorder(
        val prepared: MutableList<ByteArray> = mutableListOf(),
        val contentTypes: MutableList<ContentType?> = mutableListOf(),
        val uploaded: MutableList<Pair<String, Boolean>> = mutableListOf(),
        val uploadProgress: MutableList<Any?> = mutableListOf(),
        val downloadedPlain: MutableList<String> = mutableListOf(),
        val downloadedEncrypted: MutableList<EncryptedFile> = mutableListOf(),
        val thumbnailRequests: MutableList<ThumbnailRequest> = mutableListOf(),
    )

    private data class ThumbnailRequest(
        val uri: String,
        val width: Long,
        val height: Long,
        val method: ThumbnailResizingMethod,
        val animated: Boolean,
    )

    private fun mediaService(recorder: MediaRecorder): MediaService =
        proxy(MediaService::class.java) { proxy, method, args ->
            when {
                method.name.startsWith("getMedia") -> {
                    recorder.downloadedPlain += args[0] as String
                    TestPlatformMedia(
                        chunks = listOf("plain-".toByteArray(), "bytes".toByteArray()),
                    )
                }

                method.name.startsWith("getEncryptedMedia") -> {
                    recorder.downloadedEncrypted += args[0] as EncryptedFile
                    TestPlatformMedia(
                        chunks = listOf("encrypted-".toByteArray(), "bytes".toByteArray()),
                    )
                }

                method.name.startsWith("getThumbnail") -> {
                    recorder.thumbnailRequests +=
                        ThumbnailRequest(
                            uri = args[0] as String,
                            width = args[1] as Long,
                            height = args[2] as Long,
                            method = args[3] as ThumbnailResizingMethod,
                            animated = args[4] as Boolean,
                        )
                    TestPlatformMedia(
                        chunks = listOf("thumbnail-".toByteArray(), "bytes".toByteArray()),
                    )
                }

                method.name.startsWith("prepareUploadMedia") -> {
                    val content = runBlocking { requireByteArrayFlow(args[0]).toByteArray() }
                    recorder.prepared += content
                    recorder.contentTypes += args[1] as ContentType?
                    "upload://plain/${recorder.prepared.size}"
                }

                method.name.startsWith("uploadMedia") -> {
                    val cacheUri = args[0] as String
                    val progress = args[1]
                    val keepInCache = args[2] as Boolean
                    recorder.uploadProgress += progress
                    emitProgress(progress, FileTransferProgress(0, 4))
                    emitProgress(progress, FileTransferProgress(4, 4))
                    recorder.uploaded += cacheUri to keepInCache
                    "mxc://example.org/plain/${cacheUri.substringAfterLast('/')}"
                }

                method.name == "toString" -> "MediaServiceProxy"
                method.name == "hashCode" -> System.identityHashCode(proxy)
                method.name == "equals" -> proxy === args[0]
                else -> error("unexpected MediaService method ${method.name}")
            }
        }

    private fun requireByteArrayFlow(value: Any?): Flow<ByteArray> =
        (value as? Flow<*>)?.let { flowValue ->
            flowValue.transformValues { chunk ->
                chunk as? ByteArray ?: error("expected ByteArray chunk, got ${chunk?.javaClass?.name}")
            }
        } ?: error("expected Flow callback value, got ${value?.javaClass?.name}")

    private fun Flow<*>.transformValues(requireValue: (Any?) -> ByteArray): Flow<ByteArray> =
        flow {
            collect { emit(requireValue(it)) }
        }

    private fun emitProgress(progress: Any?, value: FileTransferProgress) {
        if (progress == null) return
        require(progress is MutableStateFlow<*>) {
            "expected MutableStateFlow progress sink, got ${progress.javaClass.name}"
        }
        progress.javaClass.methods.firstOrNull {
            it.name == "tryEmit" && it.parameterCount == 1
        }?.invoke(progress, value) ?: error("progress sink ${progress.javaClass.name} does not expose tryEmit(value)")
    }

    private class TestPlatformMedia(
        private val chunks: List<ByteArray>,
        private val delegate: ByteArrayFlow = flow {
            chunks.forEach { emit(it) }
        },
    ) : PlatformMedia, ByteArrayFlow by delegate {
        override fun transformByteArrayFlow(transformer: (ByteArrayFlow) -> ByteArrayFlow): PlatformMedia =
            TestPlatformMedia(chunks = chunks, delegate = transformer(delegate))

        override suspend fun toByteArray(
            coroutineScope: kotlinx.coroutines.CoroutineScope?,
            expectedSize: Long?,
            maxSize: Long?,
        ): ByteArray? = delegate.toByteArray()
    }

    private class TestOkioPlatformMedia(
        chunks: List<ByteArray>,
        private val temporaryPath: Path,
        private val delegate: ByteArrayFlow = flow {
            chunks.forEach { emit(it) }
        },
    ) : OkioPlatformMedia, ByteArrayFlow by delegate {
        override fun transformByteArrayFlow(transformer: (ByteArrayFlow) -> ByteArrayFlow): OkioPlatformMedia =
            TestOkioPlatformMedia(
                chunks = emptyList(),
                temporaryPath = temporaryPath,
                delegate = transformer(delegate),
            )

        override suspend fun getTemporaryFile(): Result<OkioPlatformMedia.TemporaryFile> =
            Result.success(
                object : OkioPlatformMedia.TemporaryFile {
                    override val path = temporaryPath.toAbsolutePath().toString().toPath()

                    override suspend fun delete() = Unit
                },
            )

        override suspend fun toByteArray(
            coroutineScope: kotlinx.coroutines.CoroutineScope?,
            expectedSize: Long?,
            maxSize: Long?,
        ): ByteArray? = delegate.toByteArray()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> proxy(
        type: Class<T>,
        handler: (Any, java.lang.reflect.Method, Array<out Any?>) -> Any?,
    ): T =
        Proxy.newProxyInstance(
            type.classLoader,
            arrayOf(type),
        ) { proxy, method, args ->
            handler(proxy, method, args ?: emptyArray())
        } as T
}
