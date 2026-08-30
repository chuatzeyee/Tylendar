package com.chuatzeyee.tylendar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chuatzeyee.tylendar.AppViewModel
import com.chuatzeyee.tylendar.Gate
import com.chuatzeyee.tylendar.MODES
import com.chuatzeyee.tylendar.OWNER
import com.chuatzeyee.tylendar.PAGES
import com.chuatzeyee.tylendar.REPO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* The gallery wall. */
internal val WallBrush = Brush.verticalGradient(
    0f to WallTop, 0.55f to Paper, 1f to WallBottom,
)

internal val PAGE_ZH = mapOf(
    "almanac" to "黃曆",
    "poem" to "唐詩",
    "character" to "漢字",
    "landscape" to "山水",
    "weather" to "天氣",
    "month" to "月曆",
    "year" to "年曆",
)

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
fun App(vm: AppViewModel) {
    when (val g = vm.gate) {
        is Gate.Locked -> TokenGate(g.error, vm::saveToken)
        Gate.Loading, Gate.Checking -> Splash()
        Gate.Open -> Home(vm)
    }
}

@Composable
private fun Wordmark() {
    Text("TYLENDAR", style = WordmarkStyle)
}

@Composable
private fun Splash() {
    Box(Modifier.fillMaxSize().background(WallBrush), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Wordmark()
            Spacer(Modifier.height(14.dp))
            val pulse = rememberInfiniteTransition(label = "splash")
            val a by pulse.animateFloat(
                0.4f, 1f,
                infiniteRepeatable(tween(900), RepeatMode.Reverse),
                label = "splashAlpha",
            )
            Box(Modifier.size(10.dp).graphicsLayer { alpha = a }.background(Seal))
        }
    }
}

/* A plaque: matted, hairline framed, never pill shaped. Red belongs
   only to the seal and the commit button. */
@Composable
internal fun Chip(
    text: String,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val active = selected || pressed
    val bg by animateColorAsState(if (active) Ink else Mat, tween(160), label = "chipBg")
    val fg by animateColorAsState(if (active) Mat else Ink, tween(160), label = "chipFg")
    Box(
        modifier
            .shadow(1.dp, ambientColor = ShadowUmber, spotColor = ShadowUmber)
            .background(bg)
            .border(1.dp, Hairline)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(text, style = MicroStyle, color = fg)
    }
}

@Composable
private fun TokenGate(error: String?, onSave: (String) -> Unit) {
    val uri = LocalUriHandler.current
    var token by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(WallBrush).safeDrawingPadding()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                Modifier
                    .widthIn(max = 420.dp)
                    .shadow(
                        4.dp, RoundedCornerShape(2.dp),
                        ambientColor = ShadowUmber, spotColor = ShadowUmber,
                    )
                    .background(Mat, RoundedCornerShape(2.dp))
                    .border(1.dp, Hairline, RoundedCornerShape(2.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
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
                        unfocusedBorderColor = Hairline,
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
                CommitButton("UNLOCK") { onSave(token) }
            }
        }
    }
}

/* One screen, nothing scrolls. A lit gallery wall: the seven pages hang
   as framed works sliding through a fixed spotlight, the docent panel
   carries the wake clock and the controls. On the Titan 2's square
   screen the panel sits beside the gallery, on a tall phone below it. */
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

    Box(Modifier.fillMaxSize().background(WallBrush)) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
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
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 16.dp)
        ) {
            val availH = maxHeight
            val wide = maxWidth >= availH
            if (wide) {
                Column(Modifier.fillMaxSize()) {
                    HeaderRow(vm)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Gallery(
                            vm, pager,
                            cardHeight = availH - 177.dp,
                            onShowPoem = { showPoem = true },
                            modifier = Modifier.weight(11f).fillMaxHeight(),
                        )
                        Box(Modifier.weight(9f).fillMaxHeight()) {
                            GhostChar(pager, Modifier.align(Alignment.BottomEnd))
                            DocentPanel(
                                vm, wake,
                                compact = availH < 460.dp,
                                onEditHotspot = { showHotspot = true },
                            )
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    HeaderRow(vm)
                    Spacer(Modifier.height(12.dp))
                    Box {
                        GhostChar(pager, Modifier.align(Alignment.BottomEnd))
                        Gallery(
                            vm, pager,
                            cardHeight = (availH - 340.dp).coerceAtLeast(220.dp),
                            onShowPoem = { showPoem = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    DocentPanel(
                        vm, wake,
                        compact = false,
                        onEditHotspot = { showHotspot = true },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showPoem && vm.poem != null,
            enter = slideInVertically(tween(260)) { it / 8 } + fadeIn(tween(260)),
            exit = fadeOut(tween(200)),
        ) {
            vm.poem?.let { PoemOverlay(it) { showPoem = false } }
        }
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
private fun HeaderRow(vm: AppViewModel) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(30.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Wordmark()
            Chip("TOKEN SET") { vm.removeToken() }
        }
        Spacer(Modifier.height(6.dp))
        /* The picture rail. */
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
    }
}

/* The current page's character, engraved into the wall at 6% ink. */
@Composable
private fun GhostChar(pager: PagerState, modifier: Modifier = Modifier) {
    Crossfade(
        pager.currentPage,
        animationSpec = tween(300),
        label = "ghost",
        modifier = modifier,
    ) { i ->
        Text(
            PAGE_ZH[PAGES[i]].orEmpty().take(1),
            fontFamily = FontFamily.Default,
            fontSize = 170.sp,
            color = InkGhost,
        )
    }
}

@Composable
private fun Eyebrow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(12.dp).height(1.dp).background(Brass))
        Spacer(Modifier.width(4.dp))
        Text(text, style = LabelStyle, color = InkFaint)
    }
}

@Composable
private fun DocentPanel(
    vm: AppViewModel,
    wake: Pair<String, String>,
    compact: Boolean,
    onEditHotspot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = vm.settings
    val gap = if (compact) 12.dp else 16.dp
    Column(modifier.fillMaxHeight()) {
        /* The wake hero: when the frame next opens its eyes. */
        Eyebrow("NEXT WAKE")
        Spacer(Modifier.height(6.dp))
        Text(
            wake.first,
            style = if (wake.first.length > 5) WakeHeroStyle.copy(fontSize = 32.sp)
            else WakeHeroStyle,
        )
        Text(
            wake.second.lowercase(),
            fontFamily = Canela,
            fontSize = 12.sp,
            color = InkFaint,
        )
        Spacer(Modifier.height(6.dp))
        /* The incense rule. */
        Box(Modifier.fillMaxWidth().height(2.dp).background(Brass))

        Spacer(Modifier.height(14.dp))
        StatusLine(vm)

        Spacer(Modifier.height(gap))
        Eyebrow("MODE")
        Spacer(Modifier.height(6.dp))
        ModeStrip(s?.mode) { vm.setMode(it) }

        Spacer(Modifier.height(gap))
        Eyebrow("RENDER NOW")
        Spacer(Modifier.height(6.dp))
        RenderStrip { vm.forceRender(it) }

        Spacer(Modifier.height(gap))
        Eyebrow("HOTSPOT")
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                s?.hotspot ?: "",
                fontFamily = Canela,
                fontSize = 16.sp,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Chip("EDIT", onClick = onEditHotspot)
        }

        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
        Spacer(Modifier.height(8.dp))
        Text(
            "The frame picks changes up at its next wake, or press EN on the frame.",
            fontFamily = Canela,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = InkFaint,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusLine(vm: AppViewModel) {
    val pulse = rememberInfiniteTransition(label = "status")
    val a by pulse.animateFloat(
        0.4f, 1f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "statusAlpha",
    )
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val square = when {
                vm.rendering -> Gold
                vm.status.contains("FAILED") -> Seal
                else -> Ink
            }
            Box(
                Modifier
                    .size(8.dp)
                    .graphicsLayer { alpha = if (vm.rendering) a else 1f }
                    .background(square)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                vm.status.ifEmpty { "READY" },
                style = MicroStyle,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (vm.rendering) {
            LinearProgressIndicator(
                color = Gold,
                trackColor = Hairline,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(2.dp),
            )
        }
        vm.error?.let {
            Text(
                it,
                color = Seal,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp).clickable { vm.error = null },
            )
        }
    }
}

/* One inked strip, three cells, a sliding ink fill under the labels. */
@Composable
private fun ModeStrip(selected: String?, onSelect: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(36.dp).border(1.dp, Ink)) {
        val cellW = maxWidth / MODES.size
        val x by animateDpAsState(
            cellW * MODES.indexOf(selected).coerceAtLeast(0),
            tween(240, easing = FastOutSlowInEasing),
            label = "modeX",
        )
        if (selected != null) {
            Box(Modifier.offset(x = x).width(cellW).fillMaxHeight().background(Ink))
        }
        Row(Modifier.fillMaxSize()) {
            MODES.forEachIndexed { i, m ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable { onSelect(m) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        m.uppercase(),
                        style = MicroStyle,
                        color = if (m == selected) Mat else InkFaint,
                    )
                }
                if (i < MODES.size - 1) {
                    Box(Modifier.width(1.dp).fillMaxHeight().background(Hairline))
                }
            }
        }
    }
}

/* Same strip geometry, but momentary: the tapped cell flashes gold and
   fires a render, the same gold as the progress bar it starts. */
@Composable
private fun RenderStrip(onRender: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val flashes = remember { MODES.map { Animatable(0f) } }
    Row(Modifier.fillMaxWidth().height(36.dp).border(1.dp, Ink)) {
        MODES.forEachIndexed { i, m ->
            val flash = flashes[i]
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .drawBehind { drawRect(Gold.copy(alpha = flash.value)) }
                    .clickable {
                        onRender(m)
                        scope.launch {
                            flash.snapTo(1f)
                            flash.animateTo(0f, tween(240))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(m.uppercase(), style = MicroStyle, color = Ink)
            }
            if (i < MODES.size - 1) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(Hairline))
            }
        }
    }
}
