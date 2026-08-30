package com.chuatzeyee.tylendar

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

sealed interface Gate {
    data object Loading : Gate
    data class Locked(val error: String? = null) : Gate
    data object Checking : Gate
    data object Open : Gate
}

/* The frame's ESP32 wakes at fixed Singapore times; the app only
   reports them, the schedule lives in the firmware. */
private val WAKES = listOf(0 to 20, 7 to 30, 13 to 0, 19 to 0)
private val SGT = ZoneId.of("Asia/Singapore")

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private var token: String? = null
    private val gh = Github { token }

    var gate by mutableStateOf<Gate>(Gate.Loading)
        private set
    var settings by mutableStateOf<RepoSettings?>(null)
        private set
    var status by mutableStateOf("")
        private set
    var rendering by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var previewStamp by mutableStateOf(System.currentTimeMillis())
        private set

    /* Snappy page switching: the moment a page is picked, the preview
       flips to the committed docs/previews thumbnail of that page and
       only swaps to the live output/preview.png once the GitHub render
       lands. Nothing waits on the two minute render. */
    var previewOverride by mutableStateOf<String?>(null)
        private set

    private var watchJob: Job? = null

    val previewUrl: String
        get() = previewOverride ?: "$RAW/output/preview.png?t=$previewStamp"
    val previewIsLive: Boolean
        get() = previewOverride == null

    init {
        viewModelScope.launch {
            token = TokenStore.flow(getApplication()).first()
            if (token == null) gate = Gate.Locked() else unlock()
        }
    }

    fun saveToken(t: String) {
        val v = t.trim()
        if (!Regex("^(github_pat_|ghp_)[A-Za-z0-9_]{20,}$").matches(v)) {
            gate = Gate.Locked("That does not look like a GitHub token.")
            return
        }
        viewModelScope.launch {
            token = v
            TokenStore.set(getApplication(), v)
            unlock()
        }
    }

    fun removeToken() {
        viewModelScope.launch {
            token = null
            TokenStore.set(getApplication(), null)
            gate = Gate.Locked()
        }
    }

    private suspend fun unlock() {
        gate = Gate.Checking
        try {
            gh.validate(token!!)
            gh.probeGrants()
            gate = Gate.Open
            refresh()
        } catch (e: Exception) {
            /* Keep the stored token on a network blip; only a rejected
               token clears it, mirroring the portal. */
            if (e is GithubException && e.message?.contains("rejected") == true) {
                TokenStore.set(getApplication(), null)
                token = null
            }
            gate = Gate.Locked(e.message ?: "Could not reach GitHub.")
        }
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                settings = gh.settings()
                val run = gh.latestRun()
                status = when {
                    run == null -> ""
                    run.status != "completed" -> "RENDERING"
                    run.conclusion == "success" -> "RENDERED"
                    else -> "LAST RENDER FAILED"
                }
                previewStamp = System.currentTimeMillis()
            } catch (e: Exception) {
                error = e.message
            }
        }
    }

    fun setPage(page: String) {
        val s = settings ?: return
        if (page == s.page) return
        previewOverride = optimisticPreview(page, s.mode)
        commit("page", page, "portal: switch page to $page")
    }

    fun setMode(mode: String) {
        val s = settings ?: return
        if (mode == s.mode) return
        previewOverride = optimisticPreview(s.page, mode)
        commit("mode", mode, "portal: update settings")
    }

    fun setHotspot(label: String): Boolean {
        val v = label.trim()
        if (v.isEmpty() || v.length > 24 || !Regex("^[\\x20-\\x7E]+$").matches(v)) return false
        commit("hotspot", v, "portal: update settings")
        return true
    }

    /* The committed gallery previews stand in instantly. Dark almanac
       has real dark thumbnails; every other page always renders light,
       exactly like the frame. */
    private fun optimisticPreview(page: String, mode: String): String {
        val name = if (page == "almanac" && mode == "dark") "almanac-dark" else page
        return "$RAW/docs/previews/$name.png"
    }

    private fun commit(key: String, value: String, message: String) {
        viewModelScope.launch {
            error = null
            val baseline = runCatching { gh.latestRun() }.getOrNull()?.createdAt
            try {
                /* Optimistic local echo so the row highlights at once. */
                settings = settings?.let {
                    RepoSettings(
                        kotlinx.serialization.json.JsonObject(
                            it.json.toMutableMap().apply {
                                put(key, kotlinx.serialization.json.JsonPrimitive(value))
                            }
                        ),
                        it.sha
                    )
                }
                gh.putSetting(key, value, message)
                watchRender(baseline)
            } catch (e: Exception) {
                error = e.message
                refresh()
            }
        }
    }

    fun forceRender(mode: String) {
        viewModelScope.launch {
            error = null
            val baseline = runCatching { gh.latestRun() }.getOrNull()?.createdAt
            try {
                gh.dispatchRender(mode)
                watchRender(baseline)
            } catch (e: Exception) {
                error = e.message
            }
        }
    }

    /* Poll until a run NEWER than the pre-commit baseline completes,
       then swap the optimistic thumbnail for the fresh live render. */
    private fun watchRender(baseline: String?) {
        watchJob?.cancel()
        watchJob = viewModelScope.launch {
            rendering = true
            status = "RENDERING"
            val deadline = System.currentTimeMillis() + 8 * 60_000
            while (System.currentTimeMillis() < deadline) {
                delay(5_000)
                val run = runCatching { gh.latestRun() }.getOrNull() ?: continue
                if (run.createdAt != baseline && run.status == "completed") {
                    rendering = false
                    if (run.conclusion == "success") {
                        status = "RENDERED"
                        /* The bot commit lands moments after the run
                           completes; small grace before fetching. */
                        delay(3_000)
                        previewOverride = null
                        previewStamp = System.currentTimeMillis()
                    } else {
                        status = "LAST RENDER FAILED"
                    }
                    return@launch
                }
            }
            rendering = false
            status = "STILL RENDERING"
        }
    }

    fun nextWake(): Pair<String, String> {
        val now = ZonedDateTime.now(SGT)
        val candidates = WAKES.map { (h, m) ->
            now.withHour(h).withMinute(m).withSecond(0).withNano(0)
        }.map { if (it.isAfter(now)) it else it.plusDays(1) }
        val next = candidates.min()
        val d = Duration.between(now, next)
        val inText = when {
            d.toHours() > 0 -> "IN ${d.toHours()}H ${d.toMinutes() % 60}M"
            else -> "IN ${d.toMinutes()}M"
        }
        return "%02d:%02d".format(next.hour, next.minute) to inText
    }
}
