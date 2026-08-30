package com.chuatzeyee.tylendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.chuatzeyee.tylendar.AppViewModel
import com.chuatzeyee.tylendar.Gate
import com.chuatzeyee.tylendar.MODES
import com.chuatzeyee.tylendar.OWNER
import com.chuatzeyee.tylendar.PAGES
import com.chuatzeyee.tylendar.Poem
import com.chuatzeyee.tylendar.RAW
import com.chuatzeyee.tylendar.REPO
import kotlinx.coroutines.delay

@Composable
fun App(vm: AppViewModel) {
    when (val g = vm.gate) {
        is Gate.Locked -> TokenGate(g.error, vm::saveToken)
        Gate.Loading, Gate.Checking -> Splash()
        Gate.Open -> Home(vm)
    }
}

@Composable
private fun Wordmark() {
    Text("TYLENDAR", style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 4.sp))
}

@Composable
private fun Splash() {
    Box(Modifier.fillMaxSize().background(Paper), contentAlignment = Alignment.Center) {
        Wordmark()
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = LabelStyle, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
}

@Composable
internal fun Chip(
    text: String,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .border(if (selected) 2.dp else 1.dp, if (selected) Seal else Ink)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, style = LabelStyle, color = if (selected) Seal else Ink)
    }
}

@Composable
private fun TokenGate(error: String?, onSave: (String) -> Unit) {
    val uri = LocalUriHandler.current
    var token by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Wordmark()
        Text(
            "This remote needs a fine grained GitHub token with Contents and Actions write access on $OWNER/$REPO. It stays on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
        )
        Chip("CREATE A TOKEN") {
            uri.openUri("https://github.com/settings/personal-access-tokens/new")
        }
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            singleLine = true,
            placeholder = { Text("github_pat_...") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Ink,
                unfocusedBorderColor = Ink,
                cursorColor = Seal,
            ),
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        )
        if (error != null) {
            Text(
                error,
                color = Seal,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
        Button(
            onClick = { onSave(token) },
            colors = ButtonDefaults.buttonColors(containerColor = Seal, contentColor = Paper),
        ) {
            Text("UNLOCK", style = LabelStyle)
        }
    }
}

private val PAGE_KEYS = mapOf(
    Key.A to "almanac",
    Key.P to "poem",
    Key.C to "character",
    Key.L to "landscape",
    Key.W to "weather",
    Key.M to "month",
    Key.Y to "year",
)

@Composable
private fun Home(vm: AppViewModel) {
    val s = vm.settings
    var showHotspot by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    var wake by remember { mutableStateOf(vm.nextWake()) }
    LaunchedEffect(Unit) {
        while (true) {
            wake = vm.nextWake()
            delay(30_000)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .safeDrawingPadding()
            .focusRequester(focus)
            .focusable()
            /* Titan 2 hardware QWERTY. Bubbling onKeyEvent, not preview,
               so the hotspot text field keeps its own keys.
               ponytail: verify key delivery on the physical device */
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyUp) return@onKeyEvent false
                val page = PAGE_KEYS[e.key]
                when {
                    page != null -> { vm.setPage(page); true }
                    e.key == Key.R -> { vm.forceRender("auto"); true }
                    else -> false
                }
            }
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Wordmark()
            Chip("TOKEN SET") { vm.removeToken() }
        }

        Spacer(Modifier.height(20.dp))
        PreviewFrame(vm)

        Spacer(Modifier.height(12.dp))
        if (vm.status.isNotEmpty()) {
            Text(
                vm.status,
                style = LabelStyle,
                color = if (vm.status.contains("FAILED")) Seal else Ink,
            )
        }
        if (vm.rendering) {
            LinearProgressIndicator(
                color = Seal,
                trackColor = Paper,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(2.dp),
            )
        }
        Text(
            "NEXT WAKE ${wake.first} (${wake.second})",
            style = LabelStyle,
            modifier = Modifier.padding(top = 8.dp),
        )
        vm.error?.let {
            Text(
                it,
                color = Seal,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp).clickable { vm.error = null },
            )
        }

        if (s?.page == "poem") {
            vm.poem?.let { PoemCard(it) }
        }

        Label("PAGE")
        PAGES.chunked(3).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { page ->
                    val selected = page == s?.page
                    Column(
                        Modifier
                            .weight(1f)
                            .border(if (selected) 2.dp else 1.dp, if (selected) Seal else Ink)
                            .clickable { vm.setPage(page) }
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AsyncImage(
                            model = "$RAW/docs/previews/$page.png",
                            contentDescription = page,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                        )
                        Text(
                            page.uppercase(),
                            style = LabelStyle.copy(fontSize = 10.sp),
                            color = if (selected) Seal else Ink,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        Label("MODE")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MODES.forEach { m ->
                Chip(m.uppercase(), selected = m == s?.mode) { vm.setMode(m) }
            }
        }

        Label("HOTSPOT")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                s?.hotspot ?: "",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Chip("EDIT") { showHotspot = true }
        }

        Label("RENDER NOW")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MODES.forEach { m ->
                Chip(m.uppercase()) { vm.forceRender(m) }
            }
        }

        Text(
            "The frame picks changes up at its next wake. Press EN on the frame to fetch immediately.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 24.dp, bottom = 16.dp),
        )
    }

    if (showHotspot) {
        HotspotSheet(
            initial = s?.hotspot ?: "",
            onSave = vm::setHotspot,
            onDismiss = { showHotspot = false },
        )
    }
}

@Composable
private fun PoemCard(p: Poem) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .border(1.dp, Ink)
            .padding(14.dp)
    ) {
        Text("TODAY'S POEM", style = LabelStyle, color = Seal)
        Text(
            p.titleEn ?: p.title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "${p.title}  ${p.author}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (p.authorRoman.isNotEmpty()) {
            Text(
                if (p.authorDates.isEmpty()) p.authorRoman else "${p.authorRoman}, ${p.authorDates}",
                style = LabelStyle.copy(fontSize = 10.sp),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        p.english?.let { lines ->
            Spacer(Modifier.height(12.dp))
            lines.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun PreviewFrame(vm: AppViewModel) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().border(2.dp, Ink).background(Paper).padding(10.dp)) {
            AsyncImage(
                model = vm.previewUrl,
                contentDescription = "Frame preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
            )
        }
        Text(
            if (vm.previewIsLive) "LIVE" else "PREVIEW",
            style = LabelStyle,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
