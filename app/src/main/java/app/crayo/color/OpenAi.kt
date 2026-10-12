package app.crayo.color

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

/** Something went wrong while asking for colors. The message is written for the person using the app. */
class ColorError(message: String) : Exception(message)

/** Asks an OpenAI vision model which color every numbered area of a coloring page should get. */
object OpenAi {
    const val DEFAULT_SERVER = "https://api.openai.com/v1/chat/completions"
    const val DEFAULT_MODEL = "gpt-5-mini"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .callTimeout(200, TimeUnit.SECONDS)
        .build()

    /** Where a numbered area is on the page, in whole percent, so the model can find it even if a printed number is hard to read. */
    class Area(val number: Int, val x: Int, val y: Int, val share: Int)

    /** The model's first answer: what the page shows, and for every number which part it is and its color (0xRRGGBB). */
    class Answer(val subject: String, val parts: Map<Int, String>, val colors: Map<Int, Int>)

    private fun prompt(title: String, areas: List<Area>) = """
        The first picture is a children's coloring page. Its title is "$title".
        The second picture is the same page with its areas tinted and numbered 1 to ${areas.size}.
        The tints are only there to show which pixels belong to each number; they are not the real colors.

        Where each number sits (percent across from the left, percent down from the top, and how much of the page it covers).
        Use this list to be sure you have the right area, even when a printed number is small or hard to read:
        ${areas.joinToString("\n        ") { "${it.number}: ${it.x}% across, ${it.y}% down, ${it.share}% of the page" }}

        Work in this order:
        1. Decide what the page shows.
        2. For every number, name the part it is in a few words (for example "left wing", "sky", "belly"), then choose its color.
        3. Make the colors agree. Both eyes match, both wings match, all petals of one flower match, the sky is a sky color, and the ground is a ground color.
        4. Look again at the biggest areas. They set the mood of the page, so make sure none of them was left white by mistake.
        - If it is a known character, flag, logo, vehicle or animal, use its real, recognisable colors.
        - Skin, hair, clothes, eyes and so on get their natural colors. One number can cover several small pieces of the same thing, such as strands of hair.
        - Use clear, cheerful, fairly saturated colors a child would use. Avoid grey unless the thing really is grey.
        - Everything inside the picture is part of the scene and gets a realistic color: ground, grass, sand, floor, sky, water, walls, mountains, rocks. None of these is ever white.
        - Use #FFFFFF only for things that really are white (clouds, snow, white fur, teeth, highlights), and for empty paper around the drawing.
        - Neighbouring areas of different things should get clearly different colors.

        Answer with JSON only, in this shape, with an entry for every number from 1 to ${areas.size}:
        {"subject": "what the page shows", "areas": {"1": {"part": "sky", "color": "#RRGGBB"}, "2": {"part": "dragon body", "color": "#RRGGBB"}}}
    """.trimIndent()

    private fun picture(data: String) = JSONObject().put("type", "image_url")
        .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$data").put("detail", "high"))

    /** Sends one question made of text and pictures, and returns the JSON object the model answered with. */
    private suspend fun ask(server: String, key: String, model: String, text: String, pictures: List<String>): JSONObject {
        val content = JSONArray().put(JSONObject().put("type", "text").put("text", text))
        pictures.forEach { content.put(picture(it)) }
        val body = JSONObject()
            .put("model", model)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        val request = try {
            Request.Builder().url(server).header("Authorization", "Bearer $key")
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        } catch (e: IllegalArgumentException) {
            throw ColorError("The server address in Settings is not valid.")
        }

        val (code, response) = send(request)
        if (code !in 200..299) throw ColorError(explain(code, response))
        return try {
            val answer = JSONObject(response).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            JSONObject(answer.substring(answer.indexOf('{')))
        } catch (e: Exception) {
            throw ColorError("The model's answer could not be read. Try again, or try another model.")
        }
    }

    private fun hex(value: String): Int? {
        val digits = value.trim().removePrefix("#")
        return if (digits.length == 6) digits.toIntOrNull(16) else null
    }

    /** First pass. Both pictures are JPEG bytes already encoded as base64. */
    suspend fun colors(server: String, key: String, model: String, title: String, areas: List<Area>, pageJpeg: String, numberedJpeg: String): Answer {
        val answer = ask(server, key, model, prompt(title, areas), listOf(pageJpeg, numberedJpeg))
        try {
            val list = answer.getJSONObject("areas")
            val colors = HashMap<Int, Int>()
            val parts = HashMap<Int, String>()
            for (name in list.keys()) {
                val number = name.trim().toIntOrNull() ?: continue
                val entry = list.get(name)
                if (entry is JSONObject) {
                    entry.optString("part").takeIf { it.isNotBlank() }?.let { parts[number] = it }
                    hex(entry.optString("color"))?.let { colors[number] = it }
                } else if (entry is String) {
                    hex(entry)?.let { colors[number] = it }
                }
            }
            if (colors.isEmpty()) throw ColorError("The model answered, but gave no usable colors.")
            return Answer(answer.optString("subject", title), parts, colors)
        } catch (e: ColorError) {
            throw e
        } catch (e: Exception) {
            throw ColorError("The model's answer could not be read. Try again, or try another model.")
        }
    }

    // OpenAI's list has every kind of model. These words mark the ones that cannot look at a picture and answer in text.
    private val unusable = listOf(
        "embedding", "whisper", "tts", "audio", "realtime", "transcribe", "dall-e", "image", "moderation", "instruct",
        "search", "babbage", "davinci", "codex", "computer-use", "deep-research", "gpt-3.5", "sora", "-pro",
    )

    private fun usable(id: String) =
        (id.startsWith("gpt-") || id.startsWith("chatgpt-") || (id.length > 1 && id[0] == 'o' && id[1].isDigit())) &&
            unusable.none { it in id }

    /** The models this key may use, straight from OpenAI, newest first. */
    suspend fun models(server: String, key: String): List<String> {
        val request = try {
            Request.Builder().url(server.substringBefore("/chat/completions").trimEnd('/') + "/models")
                .header("Authorization", "Bearer $key").get().build()
        } catch (e: IllegalArgumentException) {
            throw ColorError("The server address in Settings is not valid.")
        }
        val (code, text) = send(request)
        if (code !in 200..299) throw ColorError(explain(code, text))
        return try {
            val list = JSONObject(text).getJSONArray("data")
            (0 until list.length()).map { list.getJSONObject(it) }
                .filter { usable(it.getString("id")) }
                .sortedByDescending { it.optLong("created") }
                .map { it.getString("id") }
        } catch (e: Exception) {
            throw ColorError("OpenAI's list of models could not be read.")
        }
    }

    private fun explain(code: Int, text: String): String {
        val detail = try { JSONObject(text).getJSONObject("error").getString("message") } catch (e: Exception) { "" }
        val reason = when (code) {
            401 -> "OpenAI did not accept the key. Check it in Settings."
            404 -> "OpenAI does not know this model. Check the model name in Settings."
            429 -> "OpenAI refused the request: too many requests, or no credit left on the account."
            in 500..599 -> "OpenAI is having problems right now. Try again later."
            else -> "OpenAI refused the request (code $code)."
        }
        return if (detail.isBlank()) reason else "$reason\n\n$detail"
    }

    private suspend fun send(request: Request): Pair<Int, String> = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resume(0 to "")
            }

            override fun onResponse(call: Call, response: Response) {
                val result = try { response.use { it.code to it.body.string() } } catch (e: Exception) { 0 to "" }
                if (continuation.isActive) continuation.resume(result)
            }
        })
    }.let { (code, text) ->
        if (code == 0) throw ColorError("Could not reach OpenAI. Check the internet connection.") else code to text
    }
}
