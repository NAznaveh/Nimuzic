package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import android.util.LruCache
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

// Cache both positive results and negative results (empty byte array) to avoid repeated failed JNI calls
private val embeddedArtCache = object : LruCache<String, ByteArray>(20 * 1024) {
    override fun sizeOf(key: String, value: ByteArray): Int = (value.size / 1024).coerceAtLeast(1)
}
private val EMPTY_BYTES = ByteArray(0)

@Composable
fun TrackCoverImage(
    coverUrl: String,
    coverDrawableResName: String = "",
    audioPath: String = "",
    title: String,
    artist: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    cornerRadius: Dp = 8.dp,
    showDetails: Boolean = false
) {
    val context = LocalContext.current
    var embeddedArtBytes by remember(coverUrl, audioPath) {
        val cached = if (coverUrl.isBlank() && audioPath.isNotBlank()) embeddedArtCache.get(audioPath) else null
        mutableStateOf<ByteArray?>(
            if (cached != null && cached.isNotEmpty()) cached else null
        )
    }

    // Asynchronously extract embedded artwork on IO thread to prevent main UI thread scrolling lag
    LaunchedEffect(coverUrl, audioPath) {
        if (coverUrl.isBlank() && audioPath.isNotBlank() && !audioPath.startsWith("http") && !audioPath.startsWith("android.resource") && !audioPath.startsWith("asset:")) {
            val cached = embeddedArtCache.get(audioPath)
            if (cached != null) {
                embeddedArtBytes = if (cached.isNotEmpty()) cached else null
            } else {
                withContext(Dispatchers.IO) {
                    val bytes = com.example.player.AudioArtworkUtils.extractEmbeddedArtworkBytes(audioPath)
                    if (bytes != null) {
                        embeddedArtCache.put(audioPath, bytes)
                        embeddedArtBytes = bytes
                    } else {
                        embeddedArtCache.put(audioPath, EMPTY_BYTES)
                        embeddedArtBytes = null
                    }
                }
            }
        }
    }

    val drawableResId = remember(coverDrawableResName) {
        if (coverDrawableResName.isNotBlank()) {
            context.resources.getIdentifier(coverDrawableResName, "drawable", context.packageName)
        } else 0
    }

    val imageModel = remember(coverUrl, embeddedArtBytes, drawableResId) {
        when {
            coverUrl.isNotBlank() -> coverUrl
            embeddedArtBytes != null -> embeddedArtBytes
            drawableResId != 0 -> drawableResId
            else -> null
        }
    }

    if (imageModel != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageModel)
                .crossfade(false)
                .build(),
            contentDescription = "$title cover",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .clip(RoundedCornerShape(cornerRadius))
        )
    } else {
        val gradientColors = remember(title, artist) {
            getDeterministicGradientColors(title + artist)
        }

        Box(
            modifier = modifier
                .clip(RoundedCornerShape(cornerRadius))
                .background(Brush.linearGradient(gradientColors)),
            contentAlignment = Alignment.Center
        ) {
            if (showDetails) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(iconSize * 1.8f)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Album,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(iconSize * 1.2f)
                        )
                    }
                    Text(
                        text = title.trim().take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        color = Color.White
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Default Music Cover",
                        tint = Color.White,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
    }
}

private fun getDeterministicGradientColors(key: String): List<Color> {
    val hash = abs(key.hashCode())
    val paletteList = listOf(
        listOf(Color(0xFF8E24AA), Color(0xFF3F51B5)),
        listOf(Color(0xFFFF512F), Color(0xFFDD2476)),
        listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
        listOf(Color(0xFF4FACFE), Color(0xFF00F2FE)),
        listOf(Color(0xFFFC466B), Color(0xFF3F5EFB)),
        listOf(Color(0xFFFF8008), Color(0xFFFFC837))
    )
    return paletteList[hash % paletteList.size]
}
