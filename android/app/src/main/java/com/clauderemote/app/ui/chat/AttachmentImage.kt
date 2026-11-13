package com.clauderemote.app.ui.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 附件的缩略图。
 *
 * 不用 Coil 加载 data URI：Coil 2 的 UriFetcher 只认 http/file/content 等真实
 * scheme，`data:image/png;base64,...` 会静默失败，预览区域就只剩一个删除按钮。
 * 这里直接把 base64（或 data URI 里的 base64）解码成 Bitmap，预览和消息气泡共用。
 */
@Composable
fun AttachmentImage(
    base64: String,
    preview: String,
    contentDescription: String?,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(base64, preview) {
        bitmap = withContext(Dispatchers.IO) { decode(base64, preview) }
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
        )
    }
}

private fun decode(base64: String, preview: String, maxSide: Int = 1280): Bitmap? {
    val bytes = when {
        // 预览是专为界面生成的小图，优先用它；没有再退回原图 base64
        preview.startsWith("data:") && preview.contains(',') ->
            runCatching { Base64.decode(preview.substringAfter(','), Base64.DEFAULT) }.getOrNull()
        base64.isNotBlank() -> runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull()
        else -> null
    } ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) {
        sample *= 2
    }
    return BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample },
    )
}
