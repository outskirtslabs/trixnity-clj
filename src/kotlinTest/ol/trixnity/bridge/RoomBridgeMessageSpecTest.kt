package ol.trixnity.bridge

import de.connect2x.trixnity.client.media.MediaService
import de.connect2x.trixnity.client.room.RoomService
import de.connect2x.trixnity.client.room.message.MessageBuilder
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.client.store.RoomOutboxMessage
import de.connect2x.trixnity.clientserverapi.model.media.FileTransferProgress
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.room.EncryptedFile
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.utils.toByteArray
import io.ktor.http.ContentType
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlin.time.Clock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest

class RoomBridgeMessageSpecTest {
    private data class MediaRecorder(
        val uploadedPlain: MutableList<ByteArray> = mutableListOf(),
        val uploadedEncrypted: MutableList<ByteArray> = mutableListOf(),
        val plainContentTypes: MutableList<ContentType?> = mutableListOf(),
    )

    @Test
    fun emoteMessagesPreserveBodyAndFormattedTextFields() = runTest {
        val parsed = requireMessageSpec(
            mapOf(
                BridgeSchema.SendMessageRequest.message to mapOf(
                    BridgeSchema.MessageSpec.kind to ":emote",
                    BridgeSchema.MessageSpec.body to "/me waves",
                    BridgeSchema.MessageSpec.format to "org.matrix.custom.html",
                    BridgeSchema.MessageSpec.formattedBody to "<em>waves</em>",
                ),
            ),
            BridgeSchema.SendMessageRequest.message,
        )

        val content = buildMessageContent(messageBuilder(encrypted = false, recorder = MediaRecorder()), parsed)

        val emote = assertIs<RoomMessageEventContent.TextBased.Emote>(content)
        assertEquals("/me waves", emote.body)
        assertEquals("org.matrix.custom.html", emote.format)
        assertEquals("<em>waves</em>", emote.formattedBody)
    }

    @Test
    fun audioMessagesPreserveMimeTypeDurationAndFileName() = runTest {
        val source = createAttachment(".ogg", "hello audio".toByteArray())
        val parsed = requireMessageSpec(
            mapOf(
                BridgeSchema.SendMessageRequest.message to mapOf(
                    BridgeSchema.MessageSpec.kind to ":audio",
                    BridgeSchema.MessageSpec.body to "Intro clip",
                    BridgeSchema.MessageSpec.sourcePath to source.absolutePathString(),
                    BridgeSchema.MessageSpec.fileName to "intro.ogg",
                    BridgeSchema.MessageSpec.mimeType to "audio/ogg",
                    BridgeSchema.MessageSpec.sizeBytes to 11L,
                    BridgeSchema.MessageSpec.duration to Duration.ofSeconds(42),
                ),
            ),
            BridgeSchema.SendMessageRequest.message,
        )

        val recorder = MediaRecorder()
        val content = buildMessageContent(messageBuilder(encrypted = false, recorder = recorder), parsed)

        val audio = assertIs<RoomMessageEventContent.FileBased.Audio>(content)
        assertEquals("intro.ogg", audio.fileName)
        assertEquals("audio/ogg", audio.info?.mimeType)
        assertEquals(42_000L, audio.info?.duration)
        assertContentEquals("hello audio".toByteArray(), recorder.uploadedPlain.single())
        assertEquals(ContentType.parse("audio/ogg"), recorder.plainContentTypes.single())
    }

    @Test
    fun imageMessagesPreserveMimeTypeFileNameAndDimensions() = runTest {
        val source = createAttachment(".png", "image-data".toByteArray())
        val parsed = requireMessageSpec(
            mapOf(
                BridgeSchema.SendMessageRequest.message to mapOf(
                    BridgeSchema.MessageSpec.kind to ":image",
                    BridgeSchema.MessageSpec.body to "Poster",
                    BridgeSchema.MessageSpec.sourcePath to source.absolutePathString(),
                    BridgeSchema.MessageSpec.fileName to "poster.png",
                    BridgeSchema.MessageSpec.mimeType to "image/png",
                    BridgeSchema.MessageSpec.sizeBytes to 512L,
                    BridgeSchema.MessageSpec.height to 800L,
                    BridgeSchema.MessageSpec.width to 600L,
                ),
            ),
            BridgeSchema.SendMessageRequest.message,
        )

        val content = buildMessageContent(messageBuilder(encrypted = false, recorder = MediaRecorder()), parsed)

        val image = assertIs<RoomMessageEventContent.FileBased.Image>(content)
        assertEquals("poster.png", image.fileName)
        assertEquals("image/png", image.info?.mimeType)
        assertEquals(800, image.info?.height)
        assertEquals(600, image.info?.width)
    }

    @Test
    fun fileMessagesPreserveMimeTypeAndFileName() = runTest {
        val source = createAttachment(".pdf", "pdf-data".toByteArray())
        val parsed = requireMessageSpec(
            mapOf(
                BridgeSchema.SendMessageRequest.message to mapOf(
                    BridgeSchema.MessageSpec.kind to ":file",
                    BridgeSchema.MessageSpec.body to "Spec sheet",
                    BridgeSchema.MessageSpec.sourcePath to source.absolutePathString(),
                    BridgeSchema.MessageSpec.fileName to "spec-sheet.pdf",
                    BridgeSchema.MessageSpec.mimeType to "application/pdf",
                    BridgeSchema.MessageSpec.sizeBytes to 2048L,
                ),
            ),
            BridgeSchema.SendMessageRequest.message,
        )

        val content = buildMessageContent(messageBuilder(encrypted = false, recorder = MediaRecorder()), parsed)

        val file = assertIs<RoomMessageEventContent.FileBased.File>(content)
        assertEquals("spec-sheet.pdf", file.fileName)
        assertEquals("application/pdf", file.info?.mimeType)
    }

    @Test
    fun invalidMimeStringsThrowADescriptiveException() {
        val source = createAttachment(".bin", "broken".toByteArray())

        val error = assertFailsWith<IllegalArgumentException> {
            requireMessageSpec(
                mapOf(
                    BridgeSchema.SendMessageRequest.message to mapOf(
                        BridgeSchema.MessageSpec.kind to ":file",
                        BridgeSchema.MessageSpec.body to "Broken file",
                        BridgeSchema.MessageSpec.sourcePath to source.absolutePathString(),
                        BridgeSchema.MessageSpec.mimeType to "not a mime",
                    ),
                ),
                BridgeSchema.SendMessageRequest.message,
            )
        }

        assertContains(error.message ?: "", "not a mime")
        assertContains(error.message ?: "", BridgeSchema.MessageSpec.mimeType.toString())
    }

    @Test
    fun unreadableSourcePathsThrowBeforeBuildingTheUpstreamContent() {
        val missing = createTempFile("trixnity-missing-", ".ogg")
        Files.deleteIfExists(missing)

        val error = assertFailsWith<IllegalArgumentException> {
            requireMessageSpec(
                mapOf(
                    BridgeSchema.SendMessageRequest.message to mapOf(
                        BridgeSchema.MessageSpec.kind to ":audio",
                        BridgeSchema.MessageSpec.body to "Missing clip",
                        BridgeSchema.MessageSpec.sourcePath to missing.absolutePathString(),
                    ),
                ),
                BridgeSchema.SendMessageRequest.message,
            )
        }

        assertContains(error.message ?: "", missing.toAbsolutePath().toString())
    }

    @Test
    fun encryptedRoomAttachmentsUseEncryptedMediaPreparation() = runTest {
        val source = createAttachment(".ogg", "secret audio".toByteArray())
        val parsed = requireMessageSpec(
            mapOf(
                BridgeSchema.SendMessageRequest.message to mapOf(
                    BridgeSchema.MessageSpec.kind to ":audio",
                    BridgeSchema.MessageSpec.body to "Secret clip",
                    BridgeSchema.MessageSpec.sourcePath to source.absolutePathString(),
                    BridgeSchema.MessageSpec.fileName to "secret.ogg",
                ),
            ),
            BridgeSchema.SendMessageRequest.message,
        )

        val recorder = MediaRecorder()
        val content = buildMessageContent(messageBuilder(encrypted = true, recorder = recorder), parsed)

        val audio = assertIs<RoomMessageEventContent.FileBased.Audio>(content)
        assertNotNull(audio.file)
        assertEquals(0, recorder.uploadedPlain.size)
        assertEquals(1, recorder.uploadedEncrypted.size)
        assertContentEquals("secret audio".toByteArray(), recorder.uploadedEncrypted.single())
    }

    @Test
    fun normalizedOutboxMessagesIncludeMediaUploadProgressWhenPresent() {
        val outboxMessage =
            RoomOutboxMessage(
                roomId = RoomId("!room:example.org"),
                transactionId = "txn-123",
                content = RoomMessageEventContent.TextBased.Text("uploading"),
                createdAt = Clock.System.now(),
            )
        outboxMessage.mediaUploadProgress.value = FileTransferProgress(512, 1024)

        val normalized = normalizeRoomOutboxMessage(outboxMessage)

        assertEquals(
            mapOf(
                BridgeSchema.transferred to 512L,
                BridgeSchema.total to 1024L,
            ),
            normalized?.get(BridgeSchema.RoomOutboxMessage.mediaUploadProgress),
        )
    }

    private fun createAttachment(suffix: String, bytes: ByteArray): Path =
        createTempFile("trixnity-attachment-", suffix).also { it.writeBytes(bytes) }

    private fun messageBuilder(encrypted: Boolean, recorder: MediaRecorder): MessageBuilder =
        MessageBuilder(
            roomId = RoomId("!room:example.org"),
            roomService = roomService(encrypted),
            mediaService = mediaService(recorder),
            ownUserId = UserId("@bot:example.org"),
        )

    private fun roomService(encrypted: Boolean): RoomService =
        proxy(RoomService::class.java) { proxy, method, args ->
            when {
                method.name.startsWith("getById") ->
                    flowOf(
                        Room(
                            roomId = when (val roomId = args[0]) {
                                is RoomId -> roomId
                                is String -> RoomId(roomId)
                                else -> error("unexpected room id value $roomId")
                            },
                            encrypted = encrypted,
                        ),
                    )

                method.name == "toString" -> "RoomServiceProxy(encrypted=$encrypted)"
                method.name == "hashCode" -> System.identityHashCode(proxy)
                method.name == "equals" -> proxy === args[0]
                else -> error("unexpected RoomService method ${method.name}")
            }
        }

    private fun mediaService(recorder: MediaRecorder): MediaService =
        proxy(MediaService::class.java) { proxy, method, args ->
            when {
                method.name.startsWith("prepareUploadMedia") -> {
                    val content = runBlocking { requireByteArrayFlow(args[0]).toByteArray() }
                    recorder.uploadedPlain += content
                    recorder.plainContentTypes += args[1] as ContentType?
                    "upload://plain/${recorder.uploadedPlain.size}"
                }

                method.name.startsWith("prepareUploadEncryptedMedia") -> {
                    val content = runBlocking { requireByteArrayFlow(args[0]).toByteArray() }
                    recorder.uploadedEncrypted += content
                    EncryptedFile(
                        url = "upload://encrypted/${recorder.uploadedEncrypted.size}",
                        key = EncryptedFile.JWK("key"),
                        initialisationVector = "iv",
                        hashes = mapOf("sha256" to "hash"),
                    )
                }

                method.name == "toString" -> "MediaServiceProxy"
                method.name == "hashCode" -> System.identityHashCode(proxy)
                method.name == "equals" -> proxy === args[0]
                else -> error("unexpected MediaService method ${method.name}")
            }
        }

    private fun requireByteArrayFlow(value: Any?): Flow<ByteArray> =
        (value as? Flow<*>)?.let { flow ->
            flow.transformValues { chunk ->
                chunk as? ByteArray ?: error("expected ByteArray chunk, got ${chunk?.javaClass?.name}")
            }
        } ?: error("expected Flow callback value, got ${value?.javaClass?.name}")

    private fun Flow<*>.transformValues(requireValue: (Any?) -> ByteArray): Flow<ByteArray> =
        kotlinx.coroutines.flow.flow {
            collect { emit(requireValue(it)) }
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
