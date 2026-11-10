package com.morepp.fm21touchsaveeditor

import android.os.Bundle
import java.io.File
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
import com.morepp.fm21touchsaveeditor.core.FmvSaveComparator
import com.morepp.fm21touchsaveeditor.core.PersonRecordIndex
import com.morepp.fm21touchsaveeditor.core.PersonRecordMapper
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
    var comparisonContainer by remember { mutableStateOf<FmvContainer?>(null) }
    var comparisonResults by remember { mutableStateOf(emptyList<com.morepp.fm21touchsaveeditor.core.MemberDiff>()) }
    var personIndex by remember { mutableStateOf<PersonRecordIndex?>(null) }
    var personSearch by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Belum ada save yang dibuka") }
    var busy by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<File?>(null) }
    var filterText by remember { mutableStateOf("") }

    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val payload = pendingExport
        if (uri != null && payload != null) {
            runCatching {
                val output = context.contentResolver.openOutputStream(uri) ?: error("Tidak bisa menulis file")
                output.use { sink -> payload.inputStream().use { source -> source.copyTo(sink) } }
            }
                .onSuccess { status = "Komponen berhasil diekspor (${payload.length()} bytes)" }
                .onFailure { status = "Ekspor gagal: ${it.message}" }
        }
        pendingExport = null
    }

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
                    comparisonContainer = null
                    comparisonResults = emptyList()
                    personIndex = null
                    personSearch = ""
                    status = "Valid: ${it.manifest.saveName}"
                }.onFailure {
                    container = null
                    status = "Gagal membaca save: ${it.message ?: "format tidak dikenali"}"
                }
                busy = false
            }
        }
    }


    val comparisonPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            status = "Membaca save pembanding..."
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: error("File pembanding tidak dapat dibaca")
                        FmvContainer(bytes)
                    }
                }
                result.onSuccess {
                    comparisonContainer = it
                    comparisonResults = emptyList()
                    status = "Save pembanding valid: ${it.manifest.saveName}"
                }.onFailure {
                    comparisonContainer = null
                    status = "Gagal membaca save pembanding: ${it.message}"
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
                val visibleMembers = parsed.manifest.members.filter {
                    filterText.isBlank() || it.name.contains(filterText.trim(), ignoreCase = true) ||
                        (it.archive?.contains(filterText.trim(), ignoreCase = true) == true)
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(parsed.manifest.saveName, style = MaterialTheme.typography.titleLarge)
                            Text("Database version: ${parsed.gameVersion ?: "unknown"}")
                            Text("Top-level: ${parsed.manifest.topLevel.size}")
                            Text("Nested: ${parsed.manifest.nested.size}")
                            Text("Total komponen: ${parsed.manifest.members.size}")
                            Text("Footer offset: ${parsed.manifest.footerOffset}")
                        }
                    }
                    Text("Komponen save", style = MaterialTheme.typography.titleMedium)
                }
                item {
                    val gameDbMember = parsed.manifest.members.firstOrNull { it.name == "game_db/.dat" }
                    Button(
                        onClick = {
                            val active = container ?: return@Button
                            val member = active.manifest.members.firstOrNull { it.name == "game_db/.dat" } ?: return@Button
                            busy = true
                            status = "Membangun person record mapping dari game_db..."
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val file = File(context.cacheDir, "fm21_components/game_db_person_map.dat")
                                        active.extractToFile(member, file)
                                        PersonRecordMapper.map(file.readBytes())
                                    }
                                }
                                result.onSuccess { index ->
                                    personIndex = index
                                    status = "Person mapping selesai: ${index.mappedEntries} record terhubung"
                                }.onFailure {
                                    status = "Person mapping gagal: ${it.message}"
                                }
                                busy = false
                            }
                        },
                        enabled = !busy && gameDbMember != null,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (busy) "Memproses..." else if (personIndex == null) "Build Player / Person Record Mapping" else "Rebuild Person Record Mapping") }
                }
                if (personIndex != null) {
                        val index = personIndex!!
                        val matchingCount = index.entries.count {
                            personSearch.isBlank() || it.name.contains(personSearch.trim(), ignoreCase = true)
                        }
                        val visiblePeople = index.entries.filter {
                            personSearch.isBlank() || it.name.contains(personSearch.trim(), ignoreCase = true)
                        }.take(100)
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Person Record Mapping", style = MaterialTheme.typography.titleLarge)
                                    Text("Name index: ${index.scannedNameEntries} · Entity records linked: ${index.mappedEntries} · Unresolved: ${index.unresolvedEntries}")
                                    OutlinedTextField(
                                        value = personSearch,
                                        onValueChange = { personSearch = it },
                                        label = { Text("Search person name") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text("Showing ${visiblePeople.size} of $matchingCount matches (max 100)", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        items(visiblePeople, key = { "person_${it.uniqueId}" }) { person ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(person.name, style = MaterialTheme.typography.titleSmall)
                                    Text("Unique ID: ${person.uniqueId} · Name index ID: ${person.nameIndexId}", style = MaterialTheme.typography.bodySmall)
                                    Text("Entity record ID: ${person.entityRecordId ?: "unresolved"}", style = MaterialTheme.typography.bodySmall)
                                    if (person.entityRecordTypeTag != null) {
                                        Text("Record tag: ${person.entityRecordTypeTag} · Length: ${person.entityRecordLength?.let { it.toString() + " bytes (adjacent ID verified)" } ?: "not inferred"}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        item {
                            Text("Mapping ini menghubungkan nama dan UID ke person entity record. Belum semua record terbukti sebagai pemain; CA/PA dan atribut belum ditafsirkan.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                item {
                    OutlinedButton(
                        onClick = { comparisonPicker.launch(arrayOf("*/*")) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (comparisonContainer == null) "Pilih save pembanding" else "Ganti save pembanding") }
                    comparisonContainer?.let { other ->
                        Text("Pembanding: ${other.manifest.saveName} · ${other.manifest.members.size} komponen")
                        Button(
                            onClick = {
                                val first = container ?: return@Button
                                busy = true
                                status = "Membandingkan seluruh komponen..."
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching {
                                            FmvSaveComparator.compare(
                                                first, other, File(context.cacheDir, "fm21_comparison")
                                            )
                                        }
                                    }
                                    result.onSuccess { diffs ->
                                        comparisonResults = diffs
                                        val changed = diffs.count { !it.identical }
                                        status = "Perbandingan selesai: $changed dari ${diffs.size} komponen berubah"
                                    }.onFailure {
                                        status = "Perbandingan gagal: ${it.message}"
                                    }
                                    busy = false
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (busy) "Memproses..." else "Bandingkan kedua save") }
                    }
                }
                if (comparisonResults.isNotEmpty()) {
                    val changed = comparisonResults.filter { !it.identical }
                    item {
                        Text("Hasil perbandingan: ${changed.size} komponen berubah", style = MaterialTheme.typography.titleMedium)
                        Text("Perbedaan byte dihitung pada panjang data yang sama; perubahan ukuran ditampilkan terpisah.", style = MaterialTheme.typography.bodySmall)
                    }
                    items(changed.take(100), key = { "diff_${it.name}" }) { diff ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(diff.name, style = MaterialTheme.typography.titleSmall)
                                Text("Ukuran: ${diff.beforeBytes} → ${diff.afterBytes} bytes", style = MaterialTheme.typography.bodySmall)
                                Text("Byte berubah: ${diff.changedBytesInCommonLength} · Offset pertama: ${diff.firstDifferenceOffset ?: "ukuran saja"}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = filterText,
                        onValueChange = { filterText = it },
                        label = { Text("Filter component name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("${visibleMembers.size} components found", style = MaterialTheme.typography.bodySmall)
                }
                items(visibleMembers, key = { it.name }) { member ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(member.name, style = MaterialTheme.typography.titleSmall)
                            Text("Plain: ${member.plainLength} bytes · Stored: ${member.storedLength} bytes",
                                style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = {
                                val active = container ?: return@OutlinedButton
                                busy = true
                                scope.launch {
                                    val extracted = withContext(Dispatchers.IO) {
                                        runCatching {
                                            val safeName = member.name.replace("/", "_").replace("\\\\", "_")
                                            val output = File(context.cacheDir, "fm21_components/$safeName")
                                            active.extractToFile(member, output)
                                            output
                                        }
                                    }
                                    extracted.onSuccess { file ->
                                        pendingExport = file
                                        exportPicker.launch(file.name)
                                    }.onFailure { status = "Ekstraksi gagal: ${it.message}" }
                                    busy = false
                                }
                            }, enabled = !busy) { Text("Ekspor komponen") }
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
