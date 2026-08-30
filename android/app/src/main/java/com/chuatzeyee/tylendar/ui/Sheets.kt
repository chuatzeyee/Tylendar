package com.chuatzeyee.tylendar.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chuatzeyee.tylendar.OWNER
import com.chuatzeyee.tylendar.Poem
import com.chuatzeyee.tylendar.REPO

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotspotSheet(initial: String, onSave: (String) -> Boolean, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    var rejected by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Mat,
        dragHandle = {
            Box(
                Modifier.padding(vertical = 10.dp).width(28.dp).height(2.dp).background(Hairline)
            )
        },
    ) {
        Column(Modifier.padding(24.dp)) {
            Text("HOTSPOT", style = LabelStyle, color = InkFaint)
            OutlinedTextField(
                value = value,
                onValueChange = { v ->
                    /* The frame's Latin font: printable ASCII only, 24 max. */
                    value = v.filter { it.code in 32..126 }.take(24)
                    rejected = false
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Ink,
                    unfocusedBorderColor = Hairline,
                    cursorColor = Seal,
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
            Text(
                "Up to 24 plain characters. The frame's font has no CJK.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (rejected) {
                Text(
                    "That name will not fit on the frame.",
                    color = Seal,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            CommitButton("SAVE", modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)) {
                if (onSave(value)) onDismiss() else rejected = true
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    hotspot: String,
    onEditHotspot: () -> Unit,
    onRemoveToken: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Mat,
        dragHandle = {
            Box(
                Modifier.padding(vertical = 10.dp).width(28.dp).height(2.dp).background(Hairline)
            )
        },
    ) {
        Column(Modifier.padding(24.dp)) {
            Text("SETTINGS", style = LabelStyle, color = InkFaint)
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Hotspot label", style = MaterialTheme.typography.bodyMedium)
                    Text(hotspot, style = MicroStyle, color = InkFaint)
                }
                Chip("EDIT") {
                    onDismiss()
                    onEditHotspot()
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("GitHub token", style = MaterialTheme.typography.bodyMedium)
                    Text("SET ON THIS PHONE", style = MicroStyle, color = InkFaint)
                }
                Chip("REMOVE", onClick = onRemoveToken)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Mat,
        dragHandle = {
            Box(
                Modifier.padding(vertical = 10.dp).width(28.dp).height(2.dp).background(Hairline)
            )
        },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Tylendar", style = WordmarkStyle)
            if (version.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("VERSION $version", style = MicroStyle, color = InkFaint)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Remote control for the Tylendar frame: a wall mounted daily " +
                    "Chinese almanac on e-paper. Pick the page on view, switch " +
                    "modes, or force a render; the frame picks changes up at " +
                    "its next wake.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("SOURCE ON GITHUB") { uri.openUri("https://github.com/$OWNER/$REPO") }
                Chip("WEB PORTAL") { uri.openUri("https://$OWNER.github.io/$REPO/") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/* Today's poem in English, hung on its own wall. */
@Composable
fun PoemOverlay(p: Poem, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Box(
        Modifier
            .fillMaxSize()
            .background(WallBrush)
            /* Consume taps so the gallery beneath stays put. */
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Chip(
            "CLOSE",
            modifier = Modifier.align(Alignment.TopEnd).padding(20.dp),
            onClick = onClose,
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("TODAY'S POEM", style = LabelStyle, color = InkFaint)
            Spacer(Modifier.height(10.dp))
            Text(
                p.titleEn ?: p.title,
                fontFamily = Canela,
                fontSize = 26.sp,
                color = Ink,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text("${p.title}  ${p.author}", style = CjkStyle)
            if (p.authorRoman.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    if (p.authorDates.isEmpty()) p.authorRoman
                    else "${p.authorRoman}, ${p.authorDates}",
                    style = MicroStyle,
                    color = InkFaint,
                )
            }
            p.english?.let { lines ->
                Spacer(Modifier.height(20.dp))
                Column(
                    Modifier.widthIn(max = 340.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    lines.forEachIndexed { i, line ->
                        val text = if (i == 0 && line.isNotEmpty()) {
                            /* Drop accent: the first character in seal red. */
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = Seal)) { append(line.first()) }
                                append(line.drop(1))
                            }
                        } else {
                            buildAnnotatedString { append(line) }
                        }
                        Text(
                            text,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            p.gist?.let {
                Spacer(Modifier.height(16.dp))
                Text(
                    it,
                    fontFamily = Canela,
                    fontSize = 13.sp,
                    color = InkFaint,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
