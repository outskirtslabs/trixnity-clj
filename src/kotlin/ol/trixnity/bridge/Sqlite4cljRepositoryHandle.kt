package ol.trixnity.bridge

import kotlinx.serialization.json.Json

data class Sqlite4cljRepositoryHandle(
    val db: Any,
    val json: Json,
)
