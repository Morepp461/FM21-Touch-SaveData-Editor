package com.morepp.fm21touchsaveeditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.morepp.fm21touchsaveeditor.core.FmvContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { EditorHome() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorHome() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var container by remember { mutableStateOf<FmvContainer?>(null) }
    var status by remember { mutableStateOf("Belum ada save yang dibuka") }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            status = "Membaca dan memvalidasi FMV..."
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: error("File tidak dapat dibaca")
                        FmvContainer(bytes)
                    }
                }
                result.onSuccess {
                    container = it
                    status = "Valid: ${it.manifest.saveName}"
                }.onFailure {
                    container = null
                    status = "Gagal membaca save: ${it.message ?: "format tidak dikenali"}"
                }
                busy = false
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("FM21 Touch Save Editor") }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Full Database Editor", style = MaterialTheme.typography.headlineSmall)
                Text("FM21 Touch 2021 · Container inspector")
                Button(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "Memproses..." else "Buka file .fmv") }
                Text(status, style = MaterialTheme.typography.bodyMedium)
            }
            container?.let { parsed ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(parsed.manifest.saveName, style = MaterialTheme.typography.titleLarge)
                            Text("Top-level: ${parsed.manifest.topLevel.size}")
                            Text("Nested: ${parsed.manifest.nested.size}")
                            Text("Total komponen: ${parsed.manifest.members.size}")
                            Text("Footer offset: ${parsed.manifest.footerOffset}")
                        }
                    }
                    Text("Komponen save", style = MaterialTheme.typography.titleMedium)
                }
                items(parsed.manifest.members, key = { it.name }) { member ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(member.name, style = MaterialTheme.typography.titleSmall)
                            Text("Plain: ${member.plainLength} bytes · Stored: ${member.storedLength} bytes",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                Text(
                    "Mode saat ini read-only. Editing dan rebuild baru diaktifkan setelah skema record serta aturan repack tervalidasi.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
