package com.moody.player.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.moody.player.data.media.AudioItem
import com.moody.player.design.MoodyTheme
import com.moody.player.design.Radii
import com.moody.player.design.icons.MoodyIcons

/**
 * Album art from the file itself (MediaStore thumbnail of the song), with a quiet placeholder:
 * a tinted square with a record icon, tone picked from the album name so albums stay recognisable.
 */
@Composable
fun Artwork(item: AudioItem?, modifier: Modifier = Modifier, shape: Shape = Radii.md, iconSize: Dp = 20.dp, onLoaded: ((android.graphics.drawable.Drawable) -> Unit)? = null, onMissing: (() -> Unit)? = null, requestPx: Int = 512) {
    val c = MoodyTheme.colors
    var failed by remember(item?.uri, item?.cover) { mutableStateOf(false) }
    val tones = listOf(c.raised, c.surface, c.accentWash, c.line2)
    val tone = tones[((item?.album?.hashCode() ?: 0) and 0x7fffffff) % tones.size]
    Box(modifier.clip(shape).background(c.raised), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().background(tone))
        Icon(MoodyIcons.Album, null, Modifier.size(iconSize), tint = c.text3)
        if (item != null && !failed) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(item.cover?.let { java.io.File(it) } ?: item.uri).size(requestPx).allowHardware(onLoaded == null).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { failed = true; onMissing?.invoke() },
                onSuccess = { s -> onLoaded?.invoke(s.result.drawable) },
            )
        }
    }
}

@Composable
fun ArtworkSquare(item: AudioItem?, size: Dp, modifier: Modifier = Modifier) = Artwork(item, modifier.size(size), requestPx = coverRequestPx(size))

@Composable
fun ArtworkFill(item: AudioItem?, modifier: Modifier = Modifier) = Artwork(item, modifier.fillMaxWidth())
