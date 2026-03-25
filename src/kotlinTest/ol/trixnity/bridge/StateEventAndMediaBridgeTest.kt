package ol.trixnity.bridge

import de.connect2x.trixnity.client.media.MediaService
import de.connect2x.trixnity.clientserverapi.model.media.FileTransferProgress
import de.connect2x.trixnity.core.model.events.m.room.AvatarEventContent
import de.connect2x.trixnity.core.model.events.m.room.NameEventContent
import de.connect2x.trixnity.core.model.events.m.room.TopicEventContent
import de.connect2x.trixnity.utils.ByteArrayFlow
import de.connect2x.trixnity.utils.toByteArray
import io.ktor.http.ContentType
import java.lang.reflect.Proxy
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempFile
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

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
        val uploadProgress: MutableList<MutableStateFlow<FileTransferProgress?>?> = mutableListOf(),
    )

    private fun mediaService(recorder: MediaRecorder): MediaService =
        proxy(MediaService::class.java) { proxy, method, args ->
            when {
                method.name.startsWith("prepareUploadMedia") -> {
                    val content = runBlocking { (args[0] as ByteArrayFlow).toByteArray() }
                    recorder.prepared += content
                    recorder.contentTypes += args[1] as ContentType?
                    "upload://plain/${recorder.prepared.size}"
                }

                method.name.startsWith("uploadMedia") -> {
                    val cacheUri = args[0] as String
                    val progress = args[1] as MutableStateFlow<FileTransferProgress?>?
                    val keepInCache = args[2] as Boolean
                    recorder.uploadProgress += progress
                    progress?.value = FileTransferProgress(0, 4)
                    progress?.value = FileTransferProgress(4, 4)
                    recorder.uploaded += cacheUri to keepInCache
                    "mxc://example.org/plain/${cacheUri.substringAfterLast('/')}"
                }

                method.name == "toString" -> "MediaServiceProxy"
                method.name == "hashCode" -> System.identityHashCode(proxy)
                method.name == "equals" -> proxy === args[0]
                else -> error("unexpected MediaService method ${method.name}")
            }
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
