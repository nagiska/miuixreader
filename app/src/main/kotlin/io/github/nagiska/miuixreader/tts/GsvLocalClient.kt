package io.github.nagiska.miuixreader.tts

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class GsvLocalClient(
    private val port: Int,
    private val rate: Float,
) : NarrationSynthesizer {
    @Volatile
    private var activeConnection: HttpURLConnection? = null

    suspend fun checkStatus(): GsvEndpointStatus = withContext(Dispatchers.IO) {
        try {
            require(port in 1024..65535) { "GSV port is invalid" }
            val connection = openConnection("/v1/models").apply {
                requestMethod = "GET"
                connectTimeout = STATUS_TIMEOUT_MILLIS
                readTimeout = STATUS_TIMEOUT_MILLIS
            }
            activeConnection = connection
            try {
                val status = connection.responseCode
                if (status !in 200..299) error(readError(connection, status))
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val model = JSONObject(response).optJSONArray("data")?.optJSONObject(0)
                    ?: error("GSV returned no model status")
                GsvEndpointStatus(
                    reachable = true,
                    ready = model.optBoolean("ready", false),
                    backendName = model.optString("backend"),
                    errorMessage = if (model.optBoolean("ready", false)) null else "GSV model is not loaded",
                )
            } finally {
                clearAndDisconnect(connection)
            }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            GsvEndpointStatus(
                reachable = false,
                ready = false,
                errorMessage = error.message ?: error::class.java.simpleName,
            )
        }
    }

    override suspend fun synthesize(text: String, output: File): SynthesizedNarrationAudio =
        withContext(Dispatchers.IO) {
            require(text.isNotBlank()) { "Narration text must not be blank" }
            val request = JSONObject()
                .put("model", "gpt-sovits-local")
                .put("voice", "loaded-artifact")
                .put("input", text)
                .put("response_format", "wav")
                .put("language", "auto")
                .put("speed", rate)
                .toString()
                .toByteArray(Charsets.UTF_8)
            val connection = openConnection("/v1/audio/speech").apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = SYNTHESIS_TIMEOUT_MILLIS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setFixedLengthStreamingMode(request.size)
            }
            activeConnection = connection
            output.parentFile?.mkdirs()
            output.delete()
            try {
                connection.outputStream.use { it.write(request) }
                val status = connection.responseCode
                if (status !in 200..299) error(readError(connection, status))
                output.outputStream().buffered().use { destination ->
                    connection.inputStream.buffered().use { source -> source.copyTo(destination) }
                }
                require(isWaveFile(output)) { "GSV returned invalid WAV audio" }
                SynthesizedNarrationAudio(
                    file = output,
                    backendName = connection.getHeaderField("X-GSV-Backend")
                        ?.takeIf(String::isNotBlank)
                        ?: "GSV Mobile",
                )
            } catch (error: Exception) {
                output.delete()
                throw error
            } finally {
                clearAndDisconnect(connection)
            }
        }

    override fun cancel() {
        activeConnection?.disconnect()
        activeConnection = null
    }

    override fun close() = cancel()

    private fun openConnection(path: String): HttpURLConnection =
        URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection

    private fun clearAndDisconnect(connection: HttpURLConnection) {
        if (activeConnection === connection) activeConnection = null
        connection.disconnect()
    }

    private fun readError(connection: HttpURLConnection, status: Int): String {
        val body = connection.errorStream?.bufferedReader()?.use { it.readText().take(MAX_ERROR_LENGTH) }
        val message = body?.let { raw ->
            runCatching {
                JSONObject(raw).optJSONObject("error")?.optString("message")
            }.getOrNull()
        }
        return message?.takeIf(String::isNotBlank) ?: "GSV request failed (HTTP $status)"
    }

    private fun isWaveFile(file: File): Boolean {
        if (!file.isFile || file.length() < 12L) return false
        val header = ByteArray(12)
        file.inputStream().use { input ->
            if (input.read(header) != header.size) return false
        }
        return String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(header, 8, 4, Charsets.US_ASCII) == "WAVE"
    }

    companion object {
        private const val STATUS_TIMEOUT_MILLIS = 1_500
        private const val CONNECT_TIMEOUT_MILLIS = 3_000
        private const val SYNTHESIS_TIMEOUT_MILLIS = 10 * 60 * 1_000
        private const val MAX_ERROR_LENGTH = 64 * 1024
    }
}
