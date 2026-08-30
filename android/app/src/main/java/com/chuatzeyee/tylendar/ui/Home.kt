package com.chuatzeyee.tylendar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import com.chuatzeyee.tylendar.AppViewModel
import com.chuatzeyee.tylendar.Gate
import com.chuatzeyee.tylendar.MODES
import com.chuatzeyee.tylendar.OWNER
import com.chuatzeyee.tylendar.PAGES
import com.chuatzeyee.tylendar.RAW
import com.chuatzeyee.tylendar.REPO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

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
    Text(text, style = LabelStyle, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
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

/* One screen, nothing scrolls. The hero is a swipeable carousel of the
   seven pages with the neighbours peeking in; the page the frame is
   actually showing wears a red seal. On the Titan 2's square screen the
   controls sit beside the carousel, on a tall phone below it. */
@Composable
private fun Home(vm: AppViewModel) {
    val s = vm.settings
    var showHotspot by remember { mutableStateOf(false) }
    var showPoem by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    var wake by remember { mutableStateOf(vm.nextWake()) }
    LaunchedEffect(Unit) {
        while (true) {
            wake = vm.nextWake()
            delay(30_000)
        }
    }

    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(
        initialPage = PAGES.indexOf(s?.page).coerceAtLeast(0)
    ) { PAGES.size }
    /* Settings usually arrive after first composition; snap the carousel
       to the committed page once, then leave the user's browsing alone. */
    var synced by remember { mutableStateOf(s != null) }
    LaunchedEffect(s?.page) {
        if (!synced && s != null) {
            synced = true
            pager.scrollToPage(PAGES.indexOf(s.page).coerceAtLeast(0))
        }
    }

    BoxWithConstraints(
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
                    page != null -> {
                        vm.setPage(page)
                        scope.launch { pager.animateScrollToPage(PAGES.indexOf(page)) }
                        true
                    }
                    e.key == Key.R -> { vm.forceRender("auto"); true }
                    else -> false
                }
            }
            .padding(16.dp)
    ) {
        val availH = maxHeight
        val wide = maxWidth >= availH
        if (wide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Carousel(
                    vm, pager,
                    cardHeight = availH - 116.dp,
                    onShowPoem = { showPoem = true },
                    modifier = Modifier.weight(11f).fillMaxHeight(),
                )
                Column(Modifier.weight(9f).verticalScroll(rememberScrollState())) {
                    Controls(vm, wake, header = true, onEditHotspot = { showHotspot = true })
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Wordmark()
                    Chip("TOKEN SET") { vm.removeToken() }
                }
                Spacer(Modifier.height(8.dp))
                Carousel(
                    vm, pager,
                    cardHeight = (availH - 380.dp).coerceAtLeast(180.dp),
                    onShowPoem = { showPoem = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Controls(vm, wake, header = false, onEditHotspot = { showHotspot = true })
                }
            }
        }
    }

    if (showHotspot) {
        HotspotSheet(
            initial = s?.hotspot ?: "",
            onSave = vm::setHotspot,
            onDismiss = { showHotspot = false },
        )
    }
    if (showPoem) {
        vm.poem?.let { PoemSheet(it) { showPoem = false } }
    }
}

@Composable
private fun Carousel(
    vm: AppViewModel,
    pager: androidx.compose.foundation.pager.PagerState,
    cardHeight: Dp,
    onShowPoem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = vm.settings
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val committedIdx = PAGES.indexOf(s?.page)
    val cardWidth = cardHeight * 2f / 3f

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val pad = ((maxWidth - cardWidth) / 2).coerceAtLeast(20.dp)
            HorizontalPager(
                state = pager,
                contentPadding = PaddingValues(horizontal = pad),
                pageSpacing = 10.dp,
            ) { i ->
                val page = PAGES[i]
                val committed = i == committedIdx
                val dist = ((pager.currentPage - i) + pager.currentPageOffsetFraction)
                    .absoluteValue.coerceIn(0f, 1f)
                Box(
                    Modifier
                        .height(cardHeight)
                        .fillMaxWidth()
                        .graphicsLayer {
                            val k = lerp(1f, 0.92f, dist)
                            scaleX = k
                            scaleY = k
                            alpha = lerp(1f, 0.45f, dist)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .height(cardHeight)
                            .width(cardWidth)
                            .border(if (committed) 2.dp else 1.dp, Ink)
                            .background(Paper)
                            .clickable { scope.launch { pager.animateScrollToPage(i) } }
                            .padding(6.dp)
                    ) {
                        AsyncImage(
                            model = if (committed) vm.previewUrl else thumbUrl(page, s?.mode),
                            contentDescription = page,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (committed) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                                    .background(Seal),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("曆", color = Paper, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        val current = PAGES[pager.currentPage]
        val caption = if (pager.currentPage == committedIdx) {
            "${current.uppercase()}  ${if (vm.previewIsLive) "LIVE" else "PREVIEW"}"
        } else {
            current.uppercase()
        }
        Text(caption, style = LabelStyle, modifier = Modifier.padding(top = 8.dp))

        Row(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PAGES.indices.forEach { i ->
                Box(
                    Modifier
                        .size(7.dp)
                        .then(
                            when {
                                i == committedIdx -> Modifier.background(Seal)
                                i == pager.currentPage -> Modifier.background(Ink)
                                else -> Modifier.border(1.dp, Ink)
                            }
                        )
                        .clickable { scope.launch { pager.animateScrollToPage(i) } }
                )
            }
        }

        /* Fixed-height action slot so the carousel never jumps. */
        Box(Modifier.height(52.dp).padding(top = 10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnimatedVisibility(visible = pager.currentPage != committedIdx && s != null) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.setPage(PAGES[pager.currentPage])
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Seal, contentColor = Paper),
                    ) {
                        Text("SET AS FRAME PAGE", style = LabelStyle)
                    }
                }
                AnimatedVisibility(visible = current == "poem" && vm.poem != null) {
                    Chip("IN ENGLISH", onClick = onShowPoem)
                }
            }
        }
    }
}

/* The committed gallery thumbnails; dark almanac is the one page with a
   real dark thumbnail, everything else always renders light. */
private fun thumbUrl(page: String, mode: String?): String {
    val name = if (page == "almanac" && mode == "dark") "almanac-dark" else page
    return "$RAW/docs/previews/$name.png"
}

@Composable
private fun Controls(
    vm: AppViewModel,
    wake: Pair<String, String>,
    header: Boolean,
    onEditHotspot: () -> Unit,
) {
    val s = vm.settings
    if (header) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Wordmark()
            Chip("TOKEN SET") { vm.removeToken() }
        }
        Spacer(Modifier.height(12.dp))
    }

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
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(2.dp),
        )
    }
    Text(
        "NEXT WAKE ${wake.first} (${wake.second})",
        style = LabelStyle,
        modifier = Modifier.padding(top = 6.dp),
    )
    vm.error?.let {
        Text(
            it,
            color = Seal,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp).clickable { vm.error = null },
        )
    }

    Label("MODE")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MODES.forEach { m ->
            Chip(m.uppercase(), selected = m == s?.mode) { vm.setMode(m) }
        }
    }

    Label("RENDER NOW")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MODES.forEach { m ->
            Chip(m.uppercase()) { vm.forceRender(m) }
        }
    }

    Label("HOTSPOT")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            s?.hotspot ?: "",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Chip("EDIT", onClick = onEditHotspot)
    }

    Text(
        "The frame picks changes up at its next wake, or press EN on the frame.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}
