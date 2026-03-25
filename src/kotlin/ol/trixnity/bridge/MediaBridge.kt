package ol.trixnity.bridge

import de.connect2x.trixnity.client.media
import de.connect2x.trixnity.client.media.PlatformMedia
import java.io.Closeable

object MediaBridge {
    @JvmStatic
    fun prepareUploadMedia(
        client: de.connect2x.trixnity.client.MatrixClient,
        sourcePath: String,
        mimeType: String?,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
    ) {
        prepareUploadMedia(
            mediaService = client.media,
            sourcePath = requireReadablePath(sourcePath, BridgeSchema.MessageSpec.sourcePath),
            mimeType = mimeType?.let { parseContentType(it, BridgeSchema.MessageSpec.mimeType) },
        )
    }

    @JvmStatic
    fun uploadMedia(
        client: de.connect2x.trixnity.client.MatrixClient,
        cacheUri: String,
        keepInCache: Boolean,
        onProgress: Any,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
    ) {
        uploadPreparedMediaWithProgress(
            mediaService = client.media,
            cacheUri = cacheUri,
            keepInCache = keepInCache,
            onProgress = onProgress,
        )
    }

    @JvmStatic
    fun getMedia(
        client: de.connect2x.trixnity.client.MatrixClient,
        uri: String,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
    ) {
        ol.trixnity.bridge.getMedia(
            scope = this,
            mediaService = client.media,
            uri = uri,
        )
    }

    @JvmStatic
    fun getEncryptedMedia(
        client: de.connect2x.trixnity.client.MatrixClient,
        encryptedFile: Map<*, *>,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
    ) {
        ol.trixnity.bridge.getEncryptedMedia(
            scope = this,
            mediaService = client.media,
            encryptedFile = requireEncryptedFile(
                mapOf(BridgeSchema.encryptedFile to encryptedFile),
                BridgeSchema.encryptedFile,
            ),
        )
    }

    @JvmStatic
    fun getThumbnail(
        client: de.connect2x.trixnity.client.MatrixClient,
        uri: String,
        width: Long,
        height: Long,
        method: String?,
        animated: Boolean?,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.clientScope(client),
        onSuccess = onSuccess,
        onFailure = onFailure,
    ) {
        ol.trixnity.bridge.getThumbnail(
            scope = this,
            mediaService = client.media,
            uri = uri,
            width = width,
            height = height,
            method = parseThumbnailMethod(method, BridgeSchema.method),
            animated = animated ?: false,
        )
    }

    @JvmStatic
    fun mediaTemporaryFile(
        platformMedia: Any,
        onSuccess: Any,
        onFailure: Any,
    ): Closeable = submitBridgeTask(
        scope = BridgeAsync.closeScope(),
        onSuccess = onSuccess,
        onFailure = onFailure,
    ) {
        ol.trixnity.bridge.temporaryMediaFile(platformMedia as PlatformMedia)
    }

    @JvmStatic
    fun deleteMediaTemporaryFile(temporaryFile: Any) {
        deleteTemporaryMediaFile(temporaryFile)
    }
}
