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

/** Which typeface the app draws with: Moody's own (Geist) or whatever the phone uses. */
enum class FontMode { APP, SYSTEM }

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) = Font(
    resId = res,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Geist: a plain, even sans that stays out of the way of the pictures. OFL. */
val Geist = FontFamily(variable(R.font.geist, 400), variable(R.font.geist, 500), variable(R.font.geist, 600))

/** Geist Mono for times, sizes and counts, so digits line up. OFL. */
val GeistMono = FontFamily(variable(R.font.geist_mono, 400), variable(R.font.geist_mono, 500))

private const val TABULAR = "tnum"

@Immutable
data class MoodyType(
    val hero: TextStyle,
    val display: TextStyle,
    val title: TextStyle,
    val heading: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    /** Section labels: sentence case, muted, no wide tracking. */
    val section: TextStyle,
    val mono: TextStyle,
    val monoSmall: TextStyle,
)

/**
 * One sans family carries everything; hierarchy comes from size and colour, not weight.
 * With [FontMode.SYSTEM] the phone's own font is used and figures switch to tabular digits instead of a mono face.
 */
fun moodyType(mode: FontMode = FontMode.APP): MoodyType {
    val sans = if (mode == FontMode.APP) Geist else FontFamily.Default
    val mono = if (mode == FontMode.APP) GeistMono else FontFamily.Default
    fun s(size: Float, line: Float, weight: Int, tracking: Float = 0f, family: FontFamily = sans, features: String? = null) = TextStyle(
        fontFamily = family, fontWeight = FontWeight(weight), fontSize = size.sp, lineHeight = line.sp,
        letterSpacing = tracking.em, fontFeatureSettings = features,
    )
    return MoodyType(
        hero = s(32f, 38f, 600, -0.025f),
        display = s(25f, 31f, 600, -0.02f),
        title = s(19f, 25f, 600, -0.01f),
        heading = s(16f, 22f, 500, -0.005f),
        body = s(15f, 22f, 400),
        bodyStrong = s(15f, 20f, 500),
        label = s(14f, 18f, 500),
        caption = s(12.5f, 17f, 400),
        section = s(13f, 18f, 500),
        mono = s(12.5f, 16f, 400, family = mono, features = TABULAR),
        monoSmall = s(11f, 14f, 400, 0.01f, family = mono, features = TABULAR),
    )
}
