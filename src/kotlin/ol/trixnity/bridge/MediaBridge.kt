package ol.trixnity.bridge

import de.connect2x.trixnity.client.media
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
}
