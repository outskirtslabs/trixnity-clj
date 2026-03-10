package phase1

import io.ktor.http.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object SynapseSharedSecretRegistration {
    private val httpClient: HttpClient = HttpClient.newBuilder().build()

    suspend fun register(
        baseUrl: Url,
        username: String,
        password: String,
        sharedSecret: String,
        admin: Boolean,
        displayName: String = username,
        userType: String? = null,
    ): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val endpoint = URI.create(baseUrl.toString().trimEnd('/') + "/_synapse/admin/v1/register")
            val nonce = fetchNonce(endpoint)
            val mac = generateMac(
                nonce = nonce,
                username = username,
                password = password,
                admin = admin,
                sharedSecret = sharedSecret,
                userType = userType,
            )

            val payload = buildPostBody(
                nonce = nonce,
                username = username,
                password = password,
                displayName = displayName,
                admin = admin,
                mac = mac,
                userType = userType,
            )

            val response = httpClient.send(
                HttpRequest.newBuilder(endpoint)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )

            if (response.statusCode() !in 200..299) {
                error("shared-secret register failed with status=${response.statusCode()} body=${response.body()}")
            }
        }
    }

    fun generateMac(
        nonce: String,
        username: String,
        password: String,
        admin: Boolean,
        sharedSecret: String,
        userType: String?,
    ): String {
        val role = if (admin) "admin" else "notadmin"
        val payload = buildString {
            append(nonce)
            append('\u0000')
            append(username)
            append('\u0000')
            append(password)
            append('\u0000')
            append(role)
            if (userType != null) {
                append('\u0000')
                append(userType)
            }
        }
        return hmacSha1Hex(sharedSecret, payload)
    }

    private fun fetchNonce(endpoint: URI): String {
        val response = httpClient.send(
            HttpRequest.newBuilder(endpoint).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        if (response.statusCode() !in 200..299) {
            error("failed to fetch shared-secret nonce status=${response.statusCode()} body=${response.body()}")
        }

        val nonce = Regex("\"nonce\"\\s*:\\s*\"([^\"]+)\"")
            .find(response.body())
            ?.groupValues
            ?.getOrNull(1)

        return requireNotNull(nonce) { "nonce missing in response: ${response.body()}" }
    }

    private fun buildPostBody(
        nonce: String,
        username: String,
        password: String,
        displayName: String,
        admin: Boolean,
        mac: String,
        userType: String?,
    ): String {
        fun json(value: String) = "\"${escapeJson(value)}\""

        return buildString {
            append("{")
            append("\"nonce\":")
            append(json(nonce))
            append(",\"username\":")
            append(json(username))
            append(",\"displayname\":")
            append(json(displayName))
            append(",\"password\":")
            append(json(password))
            append(",\"admin\":")
            append(if (admin) "true" else "false")
            if (userType != null) {
                append(",\"user_type\":")
                append(json(userType))
            }
            append(",\"mac\":")
            append(json(mac))
            append("}")
        }
    }

    private fun hmacSha1Hex(secret: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA1"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun escapeJson(value: String): String {
        return buildString(value.length + 8) {
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }
    }
}
