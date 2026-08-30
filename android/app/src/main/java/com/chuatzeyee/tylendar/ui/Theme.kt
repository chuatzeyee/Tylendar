package com.chuatzeyee.tylendar.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.chuatzeyee.tylendar.R

/* The frame's exact four colors, nothing else. */
val Ink = Color(0xFF0C0C0C)
val Paper = Color(0xFFFFFFFF)
val Seal = Color(0xFFBA2029)
val Gold = Color(0xFFECB70F)

val Canela = FontFamily(
    Font(R.font.canela_regular, FontWeight.Normal),
    Font(R.font.canela_medium, FontWeight.Medium),
    Font(R.font.canela_bold, FontWeight.Bold),
)

/* Letterspaced caps, the portal's and the printed page's label voice. */
val LabelStyle = TextStyle(
    fontFamily = Canela,
    fontSize = 12.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 1.6.sp,
)

/* The app follows the frame, not the OS: paper stays paper. Dark mode
   is a property of the almanac render, not of this remote. */
@Composable
fun TylendarTheme(content: @Composable () -> Unit) {
    isSystemInDarkTheme() // deliberately ignored
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Seal,
            onPrimary = Paper,
            secondary = Ink,
            onSecondary = Paper,
            background = Paper,
            onBackground = Ink,
            surface = Paper,
            onSurface = Ink,
            surfaceVariant = Paper,
            onSurfaceVariant = Ink,
            outline = Ink,
            error = Seal,
        ),
        typography = Typography(
            bodyLarge = TextStyle(fontFamily = Canela, fontSize = 16.sp),
            bodyMedium = TextStyle(fontFamily = Canela, fontSize = 14.sp),
            titleLarge = TextStyle(fontFamily = Canela, fontSize = 28.sp, fontWeight = FontWeight.Bold),
            labelLarge = LabelStyle,
        ),
        content = content,
    )
}
