package com.chuatzeyee.tylendar

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

const val OWNER = "chuatzeyee"
const val REPO = "Tylendar"
const val API = "https://api.github.com/repos/$OWNER/$REPO"
const val RAW = "https://raw.githubusercontent.com/$OWNER/$REPO/main"
const val SETTINGS_PATH = "generator/settings.json"

val PAGES = listOf("almanac", "poem", "character", "landscape", "weather", "month", "year")
val MODES = listOf("auto", "light", "dark")

class GithubException(message: String) : Exception(message)

data class RenderRun(val status: String, val conclusion: String?, val createdAt: String)

data class Poem(
    val title: String,
    val author: String,
    val authorRoman: String,
    val authorDates: String,
    val lines: List<String>,
    val titleEn: String?,
    val english: List<String>?,
)

data class RepoSettings(val json: JsonObject, val sha: String) {
    val page get() = json["page"]?.jsonPrimitive?.contentOrNull ?: "almanac"
    val mode get() = json["mode"]?.jsonPrimitive?.contentOrNull ?: "auto"
    val hotspot get() = json["hotspot"]?.jsonPrimitive?.contentOrNull ?: ""
}

class Github(private val token: () -> String?) {

    private val client = OkHttpClient()
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        prettyPrintIndent = "  "
    }
    private val jsonMedia = "application/json".toMediaType()

    private fun request(path: String, method: String = "GET", body: String? = null): Request {
        val b = Request.Builder()
            .url(API + path)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
        token()?.let { b.header("Authorization", "Bearer $it") }
        if (method != "GET") b.method(method, (body ?: "").toRequestBody(jsonMedia))
        return b.build()
    }

    private suspend fun call(req: Request): Pair<Int, String> = withContext(Dispatchers.IO) {
        client.newCall(req).execute().use { res -> res.code to res.body.string() }
    }

    private fun parse(text: String): JsonObject = json.parseToJsonElement(text).jsonObject

    /* Mirrors the portal gate: the token must exist AND have push
       access to this repo, so a stranger's own token does not unlock
       anything. */
    suspend fun validate(t: String) {
        val (code, body) = call(
            Request.Builder().url(API)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("Authorization", "Bearer $t")
                .build()
        )
        if (code == 401) throw GithubException("GitHub rejected that token.")
        if (code !in 200..299) throw GithubException("GitHub said $code. Try again in a minute.")
        val push = parse(body)["permissions"]?.jsonObject?.get("push")?.jsonPrimitive?.booleanOrNull
        if (push != true) {
            throw GithubException("That token cannot write to this repo. Check its Contents permission.")
        }
    }

    /* GitHub reports push: true for the owner's fine grained token even
       when a permission box was left unticked, so exercise both write
       endpoints for real. The bogus sha and ref make GitHub refuse the
       action itself (409/422) after the permission check has passed,
       so nothing is ever written or dispatched. Same trick as the
       portal. */
    suspend fun probeGrants() {
        val contents = call(
            request(
                "/contents/$SETTINGS_PATH", "PUT",
                """{"message":"portal: permission probe","content":"","sha":"${"0".repeat(40)}"}"""
            )
        )
        if (contents.first in listOf(403, 404, 429)) {
            throw GithubException("That token is missing the Contents permission. Edit it on GitHub, grant Contents, Read and write.")
        }
        val actions = call(
            request(
                "/actions/workflows/render.yml/dispatches", "POST",
                """{"ref":"refs/heads/tylendar-permission-probe"}"""
            )
        )
        if (actions.first in listOf(403, 404, 429)) {
            throw GithubException("That token is missing the Actions permission. Edit it on GitHub, grant Actions, Read and write.")
        }
    }

    suspend fun settings(): RepoSettings {
        val (code, body) = call(request("/contents/$SETTINGS_PATH?ref=main"))
        if (code == 401) throw GithubException("Token rejected. Replace it from the token chip.")
        if (code !in 200..299) throw GithubException("Could not read settings, GitHub said $code.")
        val file = parse(body)
        val content = file["content"]?.jsonPrimitive?.contentOrNull
            ?: throw GithubException("Unexpected settings payload.")
        val decoded = String(Base64.decode(content, Base64.DEFAULT), Charsets.UTF_8)
        return RepoSettings(parse(decoded), file["sha"]!!.jsonPrimitive.content)
    }

    /* Reread the sha right before every PUT: the render bot commits
       output four times a day, so a racing PUT gets a 409 and must
       retry with the fresh sha, same loop the portal and render.yml
       use. Unknown settings keys round-trip untouched. */
    suspend fun putSetting(key: String, value: String, message: String) {
        repeat(3) { attempt ->
            val cur = settings()
            val updated = JsonObject(cur.json.toMutableMap().apply { put(key, JsonPrimitive(value)) })
            val text = json.encodeToString(JsonObject.serializer(), updated) + "\n"
            val encoded = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val payload = JsonObject(
                mapOf(
                    "message" to JsonPrimitive(message),
                    "content" to JsonPrimitive(encoded),
                    "sha" to JsonPrimitive(cur.sha),
                )
            ).toString()
            val (code, _) = call(request("/contents/$SETTINGS_PATH", "PUT", payload))
            when {
                code in 200..299 -> return
                code == 409 && attempt < 2 -> Unit
                else -> throw GithubException("Save failed, GitHub said $code.")
            }
        }
        throw GithubException("Save kept colliding with the render bot. Try again.")
    }

    suspend fun latestRun(): RenderRun? {
        val (code, body) = call(request("/actions/workflows/render.yml/runs?per_page=1"))
        if (code !in 200..299) return null
        val run = parse(body)["workflow_runs"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        return RenderRun(
            status = run["status"]?.jsonPrimitive?.contentOrNull ?: "unknown",
            conclusion = run["conclusion"]?.jsonPrimitive?.contentOrNull,
            createdAt = run["created_at"]?.jsonPrimitive?.contentOrNull ?: "",
        )
    }

    /* Public raw file, no token needed. The generator picks the daily
       poem as toordinal(today) % count; the caller replicates that. */
    suspend fun poems(): List<Poem> {
        val (code, body) = call(
            Request.Builder().url("$RAW/generator/data/poems.json").build()
        )
        if (code !in 200..299) throw GithubException("Could not fetch poems, GitHub said $code.")
        return json.parseToJsonElement(body).jsonArray.map { el ->
            val o = el.jsonObject
            fun text(k: String) = o[k]?.jsonPrimitive?.contentOrNull ?: ""
            fun lines(k: String) = o[k]?.jsonArray?.map { it.jsonPrimitive.content }
            Poem(
                title = text("title"),
                author = text("author"),
                authorRoman = text("author_roman"),
                authorDates = text("author_dates"),
                lines = lines("lines") ?: emptyList(),
                titleEn = o["title_en"]?.jsonPrimitive?.contentOrNull,
                english = lines("english"),
            )
        }
    }

    suspend fun dispatchRender(mode: String) {
        val (code, _) = call(
            request(
                "/actions/workflows/render.yml/dispatches", "POST",
                """{"ref":"main","inputs":{"mode":"$mode"}}"""
            )
        )
        if (code != 204) throw GithubException("Render dispatch failed, GitHub said $code.")
    }
}
