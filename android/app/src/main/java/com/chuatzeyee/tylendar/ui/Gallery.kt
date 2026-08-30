package com.chuatzeyee.tylendar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp as flerp
import coil3.compose.AsyncImage
import com.chuatzeyee.tylendar.AppViewModel
import com.chuatzeyee.tylendar.PAGES
import com.chuatzeyee.tylendar.RAW
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/* The carousel of seven framed works, the gilt rail beneath, the
   exhibition label, and the fixed action slot. The page the frame is
   showing wears the collector's seal; committing stamps it on. */
@Composable
internal fun Gallery(
    vm: AppViewModel,
    pager: PagerState,
    cardHeight: Dp,
    onShowPoem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = vm.settings
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val committedIdx = PAGES.indexOf(s?.page)
    val cardWidth = (cardHeight - 40.dp) * 2f / 3f + 28.dp

    /* THE STAMP. drop: the chop falls onto the card. walk: it shrinks
       and settles into the top-right corner slot, where it lives.
       Resting state (both at 1f) is the plain corner seal, so the
       initial settings load draws it without ceremony. */
    val drop = remember { Animatable(1f) }
    val walk = remember { Animatable(1f) }
    val flex = remember { Animatable(1f) }
    var prevCommitted by remember { mutableIntStateOf(Int.MIN_VALUE) }
    LaunchedEffect(committedIdx) {
        val prev = prevCommitted
        prevCommitted = committedIdx
        if (committedIdx < 0 || prev < 0 || prev == committedIdx) return@LaunchedEffect
        drop.snapTo(0f)
        walk.snapTo(0f)
        drop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium))
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        launch {
            flex.animateTo(0.985f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessHigh))
            flex.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessHigh))
        }
        delay(350)
        walk.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val pad = ((maxWidth - cardWidth) / 2).coerceAtLeast(20.dp)

            /* The spotlight is fixed to the wall; paintings slide
               through it. It breathes while a render is in flight. */
            val spot = rememberInfiniteTransition(label = "spot")
            val breathe by spot.animateFloat(
                0.6f, 1f,
                infiniteRepeatable(tween(1600, easing = EaseInOutSine), RepeatMode.Reverse),
                label = "spotAlpha",
            )
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(cardWidth * 1.35f, cardHeight)
                    .graphicsLayer { alpha = if (vm.rendering) breathe else 1f }
                    .background(Brush.radialGradient(listOf(SpotWarm, Color.Transparent)))
            )

            HorizontalPager(
                state = pager,
                contentPadding = PaddingValues(horizontal = pad),
                pageSpacing = 12.dp,
            ) { i ->
                val page = PAGES[i]
                val committed = i == committedIdx
                val dist = ((pager.currentPage - i) + pager.currentPageOffsetFraction)
                    .absoluteValue.coerceIn(0f, 1f)
                Box(
                    Modifier.height(cardHeight).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .graphicsLayer {
                                val k = flerp(1f, 0.90f, dist) *
                                    (if (committed) flex.value else 1f)
                                scaleX = k
                                scaleY = k
                                alpha = flerp(1f, 0.62f, dist)
                                translationY = flerp(0f, 8.dp.toPx(), dist)
                            }
                            .height(cardHeight)
                            .width(cardWidth)
                            .shadow(
                                lerp(14.dp, 4.dp, dist), RoundedCornerShape(2.dp),
                                ambientColor = ShadowUmber, spotColor = ShadowUmber,
                            )
                            .background(Mat, RoundedCornerShape(2.dp))
                            .border(2.dp, Ink, RoundedCornerShape(2.dp))
                            .clickable { scope.launch { pager.animateScrollToPage(i) } }
                    ) {
                        /* Museum mat, bottom weighted, gilt fillet
                           around the artwork itself. */
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 16.dp)
                                .border(1.dp, Brass)
                                .padding(1.dp)
                                .background(Color.White)
                        ) {
                            AsyncImage(
                                model = if (committed) vm.previewUrl else thumbUrl(page, s?.mode),
                                contentDescription = page,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        if (committed) {
                            SealStamp(drop.value, walk.value)
                        }
                    }
                }
            }
        }

        /* Gilt rail: seven strips of ink, one of seal red. */
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PAGES.indices.forEach { i ->
                val tickWidth by animateDpAsState(
                    if (i == pager.currentPage) 20.dp else 14.dp,
                    tween(220, easing = FastOutSlowInEasing), label = "railW",
                )
                val tickColor by animateColorAsState(
                    when {
                        i == committedIdx -> Seal
                        i == pager.currentPage -> Ink
                        else -> Hairline
                    },
                    tween(220, easing = FastOutSlowInEasing), label = "railC",
                )
                Box(
                    Modifier
                        .width(tickWidth)
                        .height(12.dp)
                        .clickable { scope.launch { pager.animateScrollToPage(i) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.fillMaxWidth().height(2.dp).background(tickColor))
                }
            }
        }

        /* The exhibition label. */
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .widthIn(min = 180.dp)
                .shadow(
                    2.dp, RoundedCornerShape(2.dp),
                    ambientColor = ShadowUmber, spotColor = ShadowUmber,
                )
                .background(Mat, RoundedCornerShape(2.dp))
                .border(1.dp, Hairline, RoundedCornerShape(2.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(pager.currentPage, animationSpec = tween(220), label = "label") { i ->
                val page = PAGES[i]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row {
                        Text(
                            page.replaceFirstChar { it.uppercase() },
                            style = TitleStyle,
                            modifier = Modifier.alignByBaseline(),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            PAGE_ZH[page].orEmpty(),
                            style = CjkStyle,
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Row {
                        Text("NO. ${i + 1} OF ${PAGES.size}", style = MicroStyle, color = InkFaint)
                        if (i == committedIdx) {
                            Spacer(Modifier.width(10.dp))
                            Text("ON VIEW", style = MicroStyle, color = Brass)
                            Spacer(Modifier.width(10.dp))
                            if (vm.previewIsLive) {
                                Text("LIVE", style = MicroStyle, color = Seal)
                            } else {
                                Text("PREVIEW", style = MicroStyle, color = InkFaint)
                            }
                        }
                    }
                }
            }
        }

        /* Fixed-height action slot so the gallery never jumps. */
        Spacer(Modifier.height(10.dp))
        Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(
                    visible = pager.currentPage != committedIdx && s != null,
                    enter = fadeIn(tween(200)) + expandHorizontally(tween(200)),
                    exit = fadeOut(tween(140)),
                ) {
                    CommitButton("SET AS FRAME PAGE") { vm.setPage(PAGES[pager.currentPage]) }
                }
                AnimatedVisibility(
                    visible = PAGES[pager.currentPage] == "poem" && vm.poem != null,
                    enter = fadeIn(tween(200)) + expandHorizontally(tween(200)),
                    exit = fadeOut(tween(140)),
                ) {
                    Chip("IN ENGLISH", onClick = onShowPoem)
                }
            }
        }
    }
}

/* The collector's seal. drop 0..1 is the chop falling; walk 0..1 is the
   settle from 96dp centered to 30dp half over the frame's corner. */
@Composable
private fun BoxScope.SealStamp(drop: Float, walk: Float) {
    val sealSize = lerp(96.dp, 30.dp, walk)
    Box(
        Modifier
            .align(BiasAlignment(walk, -walk))
            .offset(x = 6.dp * walk, y = (-6).dp * walk)
            .size(sealSize)
            .graphicsLayer {
                alpha = drop
                val k = flerp(2.4f, 1f, drop)
                scaleX = k
                scaleY = k
                rotationZ = flerp(-20f, -4f, drop)
            }
            .shadow(
                4.dp, RoundedCornerShape(3.dp),
                ambientColor = ShadowUmber, spotColor = ShadowUmber,
            )
            .background(Seal, RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "曆",
            color = Mat,
            fontFamily = FontFamily.Default,
            fontSize = (sealSize.value / 2f).sp,
        )
    }
}

@Composable
internal fun CommitButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .shadow(6.dp, RectangleShape, ambientColor = ShadowUmber, spotColor = ShadowUmber)
            .height(38.dp)
            .background(if (pressed) SealPress else Seal)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MicroStyle, color = Mat, maxLines = 1, overflow = TextOverflow.Clip)
    }
}

/* The committed gallery thumbnails; dark almanac is the one page with a
   real dark thumbnail, everything else always renders light. */
internal fun thumbUrl(page: String, mode: String?): String {
    val name = if (page == "almanac" && mode == "dark") "almanac-dark" else page
    return "$RAW/docs/previews/$name.png"
}
