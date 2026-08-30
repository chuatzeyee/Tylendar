package com.chuatzeyee.tylendar.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotspotSheet(initial: String, onSave: (String) -> Boolean, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    var rejected by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Paper) {
        Column(Modifier.padding(24.dp)) {
            Text("HOTSPOT NAME", style = LabelStyle)
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
                    unfocusedBorderColor = Ink,
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
            Chip("SAVE", modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)) {
                if (onSave(value)) onDismiss() else rejected = true
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoemSheet(p: com.chuatzeyee.tylendar.Poem, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Paper) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            Text("TODAY'S POEM", style = LabelStyle, color = Seal)
            Text(
                p.titleEn ?: p.title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "${p.title}  ${p.author}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (p.authorRoman.isNotEmpty()) {
                Text(
                    if (p.authorDates.isEmpty()) p.authorRoman
                    else "${p.authorRoman}, ${p.authorDates}",
                    style = LabelStyle,
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
}
