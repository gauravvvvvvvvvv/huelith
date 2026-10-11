package cc.gvrv.huelith

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var store: AndroidPhotoStore
    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        lifecycleScope.launch { uris.forEach { store.importPhoto(it) } }
    }

    override fun onStop() {
        store.finishAdjustment()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AndroidPhotoStore(applicationContext)
        setContent {
            val selected by store.openPhoto.collectAsState()
            BackHandler(enabled = selected != null) { store.close() }
            HuelithApp(store) { picker.launch(arrayOf("image/*", "application/octet-stream")) }
        }
    }
}
