package com.chuatzeyee.tylendar.ui

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

/* The gallery palette. Every pixel is one of these or an alpha of Ink;
   the e-paper renders stay the brightest objects on screen. */
val Ink = Color(0xFF1A1A1A)
val Paper = Color(0xFFF7F3EA)
val Mat = Color(0xFFFFFDF8)
val Seal = Color(0xFFBA2029)
val SealPress = Color(0xFF9A1B22)
val Gold = Color(0xFFECB70F)
val Brass = Color(0xFF8F6E1A)
val InkFaint = Color(0x9E1A1A1A)
val InkGhost = Color(0x0F1A1A1A)
val Hairline = Color(0x2E1A1A1A)
val ShadowUmber = Color(0xFF3A2E1C)
val WallTop = Color(0xFFFAF6EE)
val WallBottom = Color(0xFFEFE7D8)
val SpotWarm = Color(0x30FFF6DC)

val Canela = FontFamily(
    Font(R.font.canela_regular, FontWeight.Normal),
    Font(R.font.canela_medium, FontWeight.Medium),
    Font(R.font.canela_bold, FontWeight.Bold),
)

val WordmarkStyle = TextStyle(
    fontFamily = Canela,
    fontWeight = FontWeight.Medium,
    fontSize = 15.sp,
    letterSpacing = 6.sp,
    color = Ink,
)

/* Eyebrow caps: MODE, RENDER NOW, HOTSPOT. */
val LabelStyle = TextStyle(
    fontFamily = Canela,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    letterSpacing = 2.2.sp,
)

/* Plaque text: buttons, chips, NO. 4 OF 7, ON VIEW. */
val MicroStyle = TextStyle(
    fontFamily = Canela,
    fontWeight = FontWeight.Medium,
    fontSize = 10.sp,
    letterSpacing = 1.8.sp,
)

/* Exhibition title on the label card. */
val TitleStyle = TextStyle(
    fontFamily = Canela,
    fontWeight = FontWeight.Normal,
    fontSize = 24.sp,
    color = Ink,
)

val WakeHeroStyle = TextStyle(
    fontFamily = Canela,
    fontWeight = FontWeight.Bold,
    fontSize = 44.sp,
    letterSpacing = (-0.5).sp,
    color = Ink,
)

/* CJK companions never get Canela; it has no CJK glyphs. */
val CjkStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.W600,
    fontSize = 15.sp,
    color = InkFaint,
)

@Composable
fun TylendarTheme(content: @Composable () -> Unit) {
    /* Deliberately no isSystemInDarkTheme(): paper stays paper. The
       remote follows the frame, not the OS. */
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Seal,
            onPrimary = Mat,
            secondary = Ink,
            background = Paper,
            surface = Paper,
            surfaceVariant = Mat,
            onBackground = Ink,
            onSurface = Ink,
            outline = Hairline,
            error = Seal,
        ),
        typography = Typography(
            displayLarge = WakeHeroStyle,
            titleLarge = TitleStyle,
            bodyLarge = TextStyle(
                fontFamily = Canela, fontWeight = FontWeight.Normal,
                fontSize = 16.sp, lineHeight = 27.sp, color = Ink,
            ),
            bodyMedium = TextStyle(
                fontFamily = Canela, fontWeight = FontWeight.Normal,
                fontSize = 14.sp, lineHeight = 20.sp, color = Ink,
            ),
            labelLarge = LabelStyle,
            labelSmall = MicroStyle,
        ),
        content = content,
    )
}
