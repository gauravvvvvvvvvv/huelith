package cc.gvrv.huelith

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

@Composable
actual fun PhotoBytesImage(bytes: ByteArray, modifier: Modifier) {
    val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
    if (bitmap != null) {
        Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Local photo", modifier = modifier, contentScale = ContentScale.Fit)
    } else {
        Text("Image preview unavailable", modifier = modifier)
    }
}
