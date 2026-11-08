package com.moody.player.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.moody.player.R

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int, vararg extra: FontVariation.Setting) = Font(
    resId = res,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), *extra),
)

@OptIn(ExperimentalTextApi::class)
val Fraunces = FontFamily(
    variable(R.font.fraunces, 400, FontVariation.Setting("opsz", 96f)),
    variable(R.font.fraunces, 500, FontVariation.Setting("opsz", 96f)),
    variable(R.font.fraunces, 600, FontVariation.Setting("opsz", 96f)),
)

val Hanken = FontFamily(
    variable(R.font.hanken_grotesk, 400),
    variable(R.font.hanken_grotesk, 500),
    variable(R.font.hanken_grotesk, 600),
)

val DmMono = FontFamily(
    Font(R.font.dm_mono_regular, FontWeight.Normal),
    Font(R.font.dm_mono_medium, FontWeight.Medium),
)

@Immutable
data class MoodyType(
    val hero: TextStyle = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.W500, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.015).em),
    val display: TextStyle = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.W500, fontSize = 27.sp, lineHeight = 31.sp, letterSpacing = (-0.015).em),
    val title: TextStyle = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.W500, fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = (-0.01).em),
    val heading: TextStyle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W500, fontSize = 16.sp, lineHeight = 22.sp),
    val body: TextStyle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 15.sp, lineHeight = 22.sp),
    val bodyStrong: TextStyle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W500, fontSize = 14.sp, lineHeight = 19.sp),
    val label: TextStyle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W500, fontSize = 13.sp, lineHeight = 17.sp),
    val caption: TextStyle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 12.sp, lineHeight = 16.sp),
    /** Section labels: sentence case, muted, no wide tracking. */
    val section: TextStyle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W500, fontSize = 13.sp, lineHeight = 18.sp),
    val mono: TextStyle = TextStyle(fontFamily = DmMono, fontWeight = FontWeight.W400, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.02.em),
    val monoSmall: TextStyle = TextStyle(fontFamily = DmMono, fontWeight = FontWeight.W400, fontSize = 10.5.sp, lineHeight = 13.sp, letterSpacing = 0.03.em),
)
