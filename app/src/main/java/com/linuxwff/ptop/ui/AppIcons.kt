package com.linuxwff.ptop.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

/** Launcher icons for the per-app lists, cached per package. */
object AppIcons {
    private val cache = HashMap<String, ImageBitmap?>(128)

    fun get(ctx: Context, pkg: String?): ImageBitmap? {
        if (pkg.isNullOrEmpty()) return null
        if (cache.containsKey(pkg)) return cache[pkg]
        val v = runCatching {
            ctx.packageManager.getApplicationIcon(pkg).toBitmap(64, 64).asImageBitmap()
        }.getOrNull()
        cache[pkg] = v
        return v
    }
}

@Composable
fun AppIcon(pkg: String?, size: Dp = 16.dp) {
    val ctx = LocalContext.current
    val bmp = remember(pkg) { AppIcons.get(ctx, pkg) }
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            modifier = Modifier.size(size).clip(RoundedCornerShape(4.dp)),
        )
    } else {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(4.dp))
                .background(C.ghost)
        )
    }
}
