package com.morepp.fm21touchsaveeditor

import android.os.Bundle
import java.io.File
import java.io.InputStream
import java.io.ByteArrayOutputStream
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.morepp.fm21touchsaveeditor.core.FmvContainer
import com.morepp.fm21touchsaveeditor.core.FmvRebuilder
import com.morepp.fm21touchsaveeditor.core.FmvSaveComparator
import com.morepp.fm21touchsaveeditor.core.PersonRecordIndex
import com.morepp.fm21touchsaveeditor.core.PersonRecordMapper
import com.morepp.fm21touchsaveeditor.core.PersonRecordDiffAnalyzer
import com.morepp.fm21touchsaveeditor.core.PersonSnapshotDiff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val colors = darkColorScheme(
                primary = Color(0xFF70C47A),
                secondary = Color(0xFF9BCB9F),
                background = Color(0xFF101713),
                onBackground = Color(0xFFF1F5F1),
                surface = Color(0xFF1B241E),
                onSurface = Color(0xFFF1F5F1),
                surfaceVariant = Color(0xFF26332A),
                onSurfaceVariant = Color(0xFFA0ADA2)
            )
            window.statusBarColor = android.graphics.Color.rgb(16, 23, 19)
            window.navigationBarColor = android.graphics.Color.rgb(16, 23, 19)
            MaterialTheme(colorScheme = colors) {
                Surface(Modifier.fillMaxSize(), color = colors.background) { EditorHome() }
            }
        }
    }
}

private enum class EditorTab(val title: String) {
    OVERVIEW("Ringkasan"), PEOPLE("Orang"), COMPARE("Perbandingan"), COMPONENTS("Berkas")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorHome() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var container by remember { mutableStateOf<FmvContainer?>(null) }
    var comparisonContainer by remember { mutableStateOf<FmvContainer?>(null) }
    var comparisonResults by remember { mutableStateOf(emptyList<com.morepp.fm21touchsaveeditor.core.MemberDiff>()) }
    var personSnapshotDiff by remember { mutableStateOf<PersonSnapshotDiff?>(null) }
    var personIndex by remember { mutableStateOf<PersonRecordIndex?>(null) }
    var personSearch by remember { mutableStateOf("") }
    var personStatusFilter by remember { mutableStateOf("Semua") }
    var status by remember { mutableStateOf("Belum ada save yang dibuka") }
    var busy by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<File?>(null) }
    var filterText by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(EditorTab.OVERVIEW) }
    var selectedCategory by remember { mutableStateOf("Semua") }
    var componentLimit by remember { mutableIntStateOf(40) }

    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val payload = pendingExport
        if (uri != null && payload != null) {
            runCatching {
                val output = context.contentResolver.openOutputStream(uri)
                    ?: error("Tidak bisa menulis file")
                output.use { sink -> payload.inputStream().use { source -> source.copyTo(sink) } }

                if (payload.extension.equals("fmv", ignoreCase = true)) {
                    val savedBytes = context.contentResolver.openInputStream(uri)?.use { readSaveBytes(it) }
                        ?: error("File hasil ekspor tidak bisa dibaca ulang")
                    val reopened = FmvContainer(savedBytes)
                    val expectedHash = java.security.MessageDigest.getInstance("SHA-256")
                        .digest(payload.readBytes())
                    val actualHash = java.security.MessageDigest.getInstance("SHA-256")
                        .digest(savedBytes)
                    if (!expectedHash.contentEquals(actualHash)) {
                        error("Pemeriksaan SHA-256 gagal. File hasil ekspor tidak identik.")
                    }
                    "Salinan save berhasil disimpan dan diverifikasi: ${reopened.manifest.saveName}"
                } else {
                    "Komponen berhasil diekspor (${payload.length()} byte)"
                }
            }.onSuccess { status = it }
                .onFailure { status = "Ekspor gagal: ${it.message}" }
        }
        payload?.delete()
        pendingExport = null
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            status = "Membaca dan memvalidasi FMV..."
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val input = context.contentResolver.openInputStream(uri)
                            ?: error("File tidak dapat dibaca")
                        val bytes = readSaveBytes(input)
                        FmvContainer(bytes)
                    }
                }
                result.onSuccess {
                    container = it
                    comparisonContainer = null
                    comparisonResults = emptyList()
                    personSnapshotDiff = null
                    personIndex = null
                    personSearch = ""
                    personStatusFilter = "Semua"
                    filterText = ""
                    selectedCategory = "Semua"
                    componentLimit = 40
                    selectedTab = EditorTab.OVERVIEW
                    status = "File save valid: ${it.manifest.saveName}"
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
                        val input = context.contentResolver.openInputStream(uri)
                            ?: error("File pembanding tidak dapat dibaca")
                        val bytes = readSaveBytes(input)
                        FmvContainer(bytes)
                    }
                }
                result.onSuccess {
                    comparisonContainer = it
                    comparisonResults = emptyList()
                    status = "File pembanding valid: ${it.manifest.saveName}"
                }.onFailure {
                    comparisonContainer = null
                    status = "Gagal membaca save pembanding: ${it.message}"
                }
                busy = false
            }
        }
    }

    val parsed = container
    val allMembers = parsed?.manifest?.members.orEmpty()
    val categories = listOf("Semua", "game_db", "comp_", "tc_", "history", "Lainnya")
    val filteredMembers = allMembers.filter { member ->
        val categoryMatch = when (selectedCategory) {
            "Semua" -> true
            "Lainnya" -> !member.name.startsWith("game_db/") &&
                !member.name.startsWith("comp_") && !member.name.startsWith("tc_") &&
                !member.name.contains("history", ignoreCase = true)
            "history" -> member.name.contains("history", ignoreCase = true)
            "game_db" -> member.name.startsWith("game_db/")
            else -> member.name.startsWith(selectedCategory)
        }
        categoryMatch && (filterText.isBlank() ||
            member.name.contains(filterText.trim(), ignoreCase = true) ||
            (member.archive?.contains(filterText.trim(), ignoreCase = true) == true))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Editor Save FM21 Touch", fontWeight = FontWeight.SemiBold)
                        Text("Football Manager Touch 2021", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("FOOTBALL MANAGEMENT SAVE DATA", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(if (parsed == null) "Buka dan analisis save FM21 Touch." else parsed.manifest.saveName,
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("MODE HANYA-BACA", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text("Periksa database, telusuri komponen, bandingkan save, dan ekspor salinan tanpa mengubah file asli.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(status, style = MaterialTheme.typography.bodySmall,
                            color = if (status.startsWith("Gagal")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            onClick = { picker.launch(arrayOf("*/*")) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text(if (busy) "Memproses..." else if (parsed == null) "Buka save FMV" else "Ganti save") }
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }

            if (parsed != null) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        SummaryMetric("Basis Data", parsed.gameVersion ?: "Tidak diketahui", Modifier.weight(1f))
                        SummaryMetric("Komponen", allMembers.size.toString(), Modifier.weight(1f))
                        SummaryMetric("Arsip", parsed.manifest.nested.size.toString(), Modifier.weight(1f))
                    }
                }
                item {
                    ScrollableTabRow(
                        selectedTabIndex = EditorTab.entries.indexOf(selectedTab),
                        edgePadding = 0.dp,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        EditorTab.entries.forEachIndexed { index, tab ->
                            Tab(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                text = { Text(tab.title) }
                            )
                        }
                    }
                }

                when (selectedTab) {
                    EditorTab.OVERVIEW -> {
                        item {
                            SectionHeading("Ruang Kerja Karier", "Pemeriksaan save dan alat yang tersedia")
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Informasi berkas save", style = MaterialTheme.typography.titleMedium)
                                    InfoLine("Save", parsed.manifest.saveName)
                                    InfoLine("Versi basis data", parsed.gameVersion ?: "Tidak terdeteksi")
                                    InfoLine("Komponen utama", parsed.manifest.topLevel.size.toString())
                                    InfoLine("Komponen di dalam arsip", parsed.manifest.nested.size.toString())
                                    InfoLine("Posisi footer", parsed.manifest.footerOffset.toString())
                                }
                            }
                        }
                        item {
                            SectionHeading("Alat basis data", "Analisis hanya-baca sampai struktur data terbukti")
                            ToolCard(
                                title = "Pemetaan data orang",
                                description = personIndex?.let { "${it.scannedNameEntries} nama · ${it.mappedEntries} terhubung · ${it.unresolvedEntries} belum terpetakan" }
                                    ?: "Hubungkan nama dengan data identitas internal",
                                action = if (personIndex == null) "Buat pemetaan" else "Buka pemetaan",
                                enabled = !busy && parsed.manifest.members.any { it.name == "game_db/.dat" },
                                onClick = {
                                    if (personIndex != null) {
                                        selectedTab = EditorTab.PEOPLE
                                    } else {
                                        busy = true
                                        status = "Menyusun pemetaan data orang..."
                                        scope.launch {
                                            val result = withContext(Dispatchers.IO) {
                                                runCatching {
                                                    val member = parsed.manifest.members.first { it.name == "game_db/.dat" }
                                                    val file = File(context.cacheDir, "fm21_components/game_db_person_map.dat")
                                                    try {
                                                        parsed.extractToFile(member, file)
                                                        FileChannel.open(file.toPath(), StandardOpenOption.READ).use { channel ->
                                                            require(channel.size() <= Int.MAX_VALUE) { "Database component terlalu besar untuk pemetaan" }
                                                            val mapped = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
                                                            PersonRecordMapper.map(mapped)
                                                        }
                                                    } finally {
                                                        file.delete()
                                                    }
                                                }
                                            }
                                            result.onSuccess {
                                                personIndex = it
                                                status = "Indeks orang selesai. Klasifikasi pemain dan staf belum diverifikasi."
                                                selectedTab = EditorTab.PEOPLE
                                            }.onFailure { status = "Pemetaan data orang gagal: ${it.message}" }
                                            busy = false
                                        }
                                    }
                                }
                            )
                            ToolCard(
                                title = "Perbandingan save",
                                description = comparisonContainer?.let { "Pembanding: ${it.manifest.saveName}" }
                                    ?: "Bandingkan dua save dari karier yang sama untuk menemukan komponen yang berubah",
                                action = if (comparisonContainer == null) "Pilih save pembanding" else "Buka perbandingan",
                                enabled = !busy,
                                onClick = {
                                    if (comparisonContainer == null) comparisonPicker.launch(arrayOf("*/*"))
                                    else selectedTab = EditorTab.COMPARE
                                }
                            )
                            ToolCard(
                                title = "Penjelajah komponen",
                                description = "Cari dan ekspor komponen dari file save",
                                action = "Jelajahi berkas",
                                enabled = true,
                                onClick = { selectedTab = EditorTab.COMPONENTS }
                            )
                            ToolCard(
                                title = "Salinan save tervalidasi",
                                description = "Periksa seluruh komponen, lalu ekspor salinan FMV identik tanpa mengubah file asli.",
                                action = "Validasi & ekspor salinan",
                                enabled = !busy,
                                onClick = {
                                    busy = true
                                    status = "Memvalidasi seluruh komponen save..."
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            runCatching {
                                                val bytes = FmvRebuilder.validatedUnchangedCopy(parsed)
                                                val safeName = parsed.manifest.saveName
                                                    .replace(Regex("[^A-Za-z0-9._-]+"), "_")
                                                    .take(48)
                                                    .ifBlank { "FM21_Save" }
                                                File(context.cacheDir, "salinan_${safeName}.fmv").apply {
                                                    parentFile?.mkdirs()
                                                    writeBytes(bytes)
                                                }
                                            }
                                        }
                                        result.onSuccess { file ->
                                            pendingExport = file
                                            exportPicker.launch(file.name)
                                            status = "Validasi berhasil. Pilih lokasi untuk menyimpan salinan FMV."
                                        }.onFailure { status = "Validasi save gagal: ${it.message}" }
                                        busy = false
                                    }
                                }
                            )
                        }
                        item {
                            Text("Fitur edit pemain, keuangan, dan penyimpanan ulang masih dikunci sampai setiap field dan proses uji-balik terbukti aman.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    EditorTab.PEOPLE -> {
                        item {
                            SectionHeading("Indeks orang", "Data orang belum dipisahkan menjadi pemain dan staf")
                            if (personIndex == null) {
                                ToolCard(
                                    title = "Buat indeks orang",
                                    description = "Ekstrak game_db/.dat lalu pindai indeks nama",
                                    action = "Mulai pemindaian",
                                    enabled = !busy && parsed.manifest.members.any { it.name == "game_db/.dat" },
                                    onClick = {
                                        busy = true
                                        status = "Menyusun pemetaan data orang..."
                                        scope.launch {
                                            val result = withContext(Dispatchers.IO) {
                                                runCatching {
                                                    val member = parsed.manifest.members.first { it.name == "game_db/.dat" }
                                                    val file = File(context.cacheDir, "fm21_components/game_db_person_map.dat")
                                                    try {
                                                        parsed.extractToFile(member, file)
                                                        FileChannel.open(file.toPath(), StandardOpenOption.READ).use { channel ->
                                                            require(channel.size() <= Int.MAX_VALUE) { "Database component terlalu besar untuk pemetaan" }
                                                            PersonRecordMapper.map(channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size()))
                                                        }
                                                    } finally {
                                                        file.delete()
                                                    }
                                                }
                                            }
                                            result.onSuccess { personIndex = it; status = "Indeks orang selesai" }
                                                .onFailure { status = "Pemetaan data orang gagal: ${it.message}" }
                                            busy = false
                                        }
                                    }
                                )
                            }
                        }
                        if (personIndex != null) {
                            val index = personIndex!!
                            val matches = index.entries.filter { person ->
                                val textMatch = personSearch.isBlank() ||
                                    person.name.contains(personSearch.trim(), ignoreCase = true) ||
                                    person.uniqueId.toString().contains(personSearch.trim()) ||
                                    (person.entityRecordId?.toString()?.contains(personSearch.trim()) == true)
                                val statusMatch = when (personStatusFilter) {
                                    "Terhubung" -> person.isMapped
                                    "Belum terpetakan" -> !person.isMapped
                                    else -> true
                                }
                                textMatch && statusMatch
                            }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SummaryMetric("Nama", index.scannedNameEntries.toString(), Modifier.weight(1f))
                                    SummaryMetric("Terhubung", index.mappedEntries.toString(), Modifier.weight(1f))
                                    SummaryMetric("Belum terpetakan", index.unresolvedEntries.toString(), Modifier.weight(1f))
                                }
                            }
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = personSearch, onValueChange = { personSearch = it },
                                        label = { Text("Cari nama, UID, atau ID internal") }, singleLine = true,
                                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                                    )
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(listOf("Semua", "Terhubung", "Belum terpetakan")) { filter ->
                                            FilterChip(
                                                selected = personStatusFilter == filter,
                                                onClick = { personStatusFilter = filter },
                                                label = { Text(filter) }
                                            )
                                        }
                                    }
                                    Text("Menampilkan ${minOf(matches.size, 100)} dari ${matches.size} hasil · filter: ${personStatusFilter}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    OutlinedButton(
                                        onClick = {
                                            busy = true
                                            scope.launch {
                                                val exported = withContext(Dispatchers.IO) {
                                                    runCatching {
                                                        val file = File(context.cacheDir, "pemetaan_data_orang_fm21.csv")
                                                        file.parentFile?.mkdirs()
                                                        file.bufferedWriter(Charsets.UTF_8).use { writer ->
                                                            writer.write("nama,uid,id_indeks_nama,flag_nama,id_data_internal,offset_data,kode_data,panjang_data_byte")
                                                            writer.newLine()
                                                            index.entries.forEach { person ->
                                                                val cells = listOf(
                                                                    person.name, person.uniqueId.toString(),
                                                                    person.nameIndexId.toString(), person.nameFlags.toString(),
                                                                    person.entityRecordId?.toString().orEmpty(),
                                                                    person.entityRecordOffset?.toString().orEmpty(),
                                                                    person.entityRecordTypeTag.orEmpty(),
                                                                    person.entityRecordLength?.toString().orEmpty()
                                                                )
                                                                writer.write(cells.joinToString(",") { csvEscape(it) })
                                                                writer.newLine()
                                                            }
                                                        }
                                                        file
                                                    }
                                                }
                                                exported.onSuccess {
                                                    pendingExport = it
                                                    exportPicker.launch(it.name)
                                                    status = "File CSV pemetaan orang siap diekspor"
                                                }.onFailure { status = "Gagal menyiapkan ekspor CSV: ${it.message}" }
                                                busy = false
                                            }
                                        },
                                        enabled = !busy,
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Ekspor seluruh pemetaan ke CSV") }
                                }
                            }
                            items(matches.take(100), key = { "person_${it.uniqueId}" }) { person ->
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(person.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                        Text("UID ${person.uniqueId} · ID nama ${person.nameIndexId}",
                                            style = MaterialTheme.typography.bodySmall)
                                        Text("ID data internal: ${person.entityRecordId ?: "Belum terpetakan"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (person.entityRecordTypeTag != null) {
                                            Text("Tag record mentah: ${person.entityRecordTypeTag}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (person.entityRecordLength != null) {
                                            Text("Panjang data yang teramati: ${person.entityRecordLength} byte",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                            item {
                                Text("Pemetaan ini belum membuktikan bahwa data tersebut milik pemain. CA, PA, dan atribut belum tersedia untuk diedit.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    EditorTab.COMPARE -> {
                        item {
                            SectionHeading("Perbandingan save", "Gunakan dua file save dari karier yang sama jika memungkinkan")
                            OutlinedButton(
                                onClick = { comparisonPicker.launch(arrayOf("*/*")) },
                                enabled = !busy, modifier = Modifier.fillMaxWidth()
                            ) { Text(if (comparisonContainer == null) "Pilih save kedua" else "Ganti save kedua") }
                            comparisonContainer?.let { other ->
                                Text("Dibandingkan dengan: ${other.manifest.saveName}", style = MaterialTheme.typography.bodyMedium)
                                Button(
                                    onClick = {
                                        busy = true
                                        status = "Membandingkan komponen save..."
                                        scope.launch {
                                            val result = withContext(Dispatchers.IO) {
                                                runCatching {
                                                    FmvSaveComparator.compare(parsed, other, File(context.cacheDir, "fm21_comparison"))
                                                }
                                            }
                                            result.onSuccess { diffs ->
                                                comparisonResults = diffs
                                                status = "Perbandingan selesai: ${diffs.count { !it.identical }} komponen berubah"
                                            }.onFailure { status = "Perbandingan gagal: ${it.message}" }
                                            busy = false
                                        }
                                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()
                                ) { Text(if (busy) "Membandingkan..." else "Bandingkan save") }
                                OutlinedButton(
                                    onClick = {
                                        busy = true
                                        status = "Menganalisis perubahan record orang..."
                                        scope.launch {
                                            val result = withContext(Dispatchers.IO) {
                                                runCatching {
                                                    val beforeMember = parsed.manifest.members.first { it.name == "game_db/.dat" }
                                                    val afterMember = other.manifest.members.first { it.name == "game_db/.dat" }
                                                    val directory = File(context.cacheDir, "fm21_person_diff").apply { mkdirs() }
                                                    val beforeFile = File(directory, "before_game_db.dat")
                                                    val afterFile = File(directory, "after_game_db.dat")
                                                    try {
                                                        parsed.extractToFile(beforeMember, beforeFile)
                                                        other.extractToFile(afterMember, afterFile)
                                                        PersonRecordDiffAnalyzer.compareFiles(beforeFile, afterFile)
                                                    } finally {
                                                        beforeFile.delete()
                                                        afterFile.delete()
                                                    }
                                                }
                                            }
                                            result.onSuccess {
                                                personSnapshotDiff = it
                                                status = "Analisis record selesai: ${it.changed.size} record berubah dari ${it.compared} record yang dapat dibandingkan"
                                            }.onFailure { status = "Analisis record gagal: ${it.message}" }
                                            busy = false
                                        }
                                    },
                                    enabled = !busy && parsed.manifest.members.any { it.name == "game_db/.dat" } &&
                                        other.manifest.members.any { it.name == "game_db/.dat" },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(if (busy) "Menganalisis..." else "Analisis perubahan data orang") }
                            }
                        }
                        personSnapshotDiff?.let { personDiff ->
                            item {
                                SectionHeading("Perubahan record orang", "Bukti byte mentah berdasarkan UID, bukan interpretasi atribut")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SummaryMetric("Berubah", personDiff.changed.size.toString(), Modifier.weight(1f))
                                    SummaryMetric("Tetap", personDiff.unchanged.toString(), Modifier.weight(1f))
                                    SummaryMetric("Dibandingkan", personDiff.compared.toString(), Modifier.weight(1f))
                                }
                                Text("Terpetakan: ${personDiff.beforeMapped} → ${personDiff.afterMapped} · tanpa panjang record valid: ${personDiff.skippedUnknownLength}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            items(personDiff.changed.take(100), key = { "person_change_${it.uniqueId}" }) { change ->
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(change.name, fontWeight = FontWeight.SemiBold)
                                        Text("UID ${change.uniqueId} · ID internal ${change.entityRecordId}",
                                            style = MaterialTheme.typography.bodySmall)
                                        Text("Offset record ${change.beforeOffset} → ${change.afterOffset} · ${change.recordLength} byte",
                                            style = MaterialTheme.typography.bodySmall)
                                        Text("Byte berubah: ${change.changedByteOffsets.joinToString()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        if (comparisonResults.isNotEmpty()) {
                            val changed = comparisonResults.filter { !it.identical }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SummaryMetric("Berubah", changed.size.toString(), Modifier.weight(1f))
                                    SummaryMetric("Dibandingkan", comparisonResults.size.toString(), Modifier.weight(1f))
                                }
                            }
                            item {
                                OutlinedButton(
                                    onClick = {
                                        busy = true
                                        scope.launch {
                                            val report = withContext(Dispatchers.IO) {
                                                runCatching {
                                                    val file = File(context.cacheDir, "perbandingan_save_fm21.csv")
                                                    file.bufferedWriter(Charsets.UTF_8).use { writer ->
                                                        writer.write("komponen,identik,ukuran_awal_byte,ukuran_akhir_byte,byte_berubah,offset_perbedaan_pertama,sha256_awal,sha256_akhir")
                                                        writer.newLine()
                                                        comparisonResults.forEach { diff ->
                                                            val cells = listOf(
                                                                diff.name, diff.identical.toString(),
                                                                diff.beforeBytes?.toString().orEmpty(),
                                                                diff.afterBytes?.toString().orEmpty(),
                                                                diff.changedBytesInCommonLength.toString(),
                                                                diff.firstDifferenceOffset?.toString().orEmpty(),
                                                                diff.beforeSha256.orEmpty(), diff.afterSha256.orEmpty()
                                                            )
                                                            writer.write(cells.joinToString(",") { csvEscape(it) })
                                                            writer.newLine()
                                                        }
                                                    }
                                                    file
                                                }
                                            }
                                            report.onSuccess {
                                                pendingExport = it
                                                exportPicker.launch(it.name)
                                                status = "File CSV perbandingan siap diekspor"
                                            }.onFailure { status = "Gagal membuat laporan perbandingan: ${it.message}" }
                                            busy = false
                                        }
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Ekspor laporan perbandingan lengkap (CSV)") }
                            }
                            items(changed.take(100), key = { "diff_${it.name}" }) { diff ->
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text(diff.name, fontWeight = FontWeight.SemiBold)
                                        Text("${diff.beforeBytes} → ${diff.afterBytes} byte · ${diff.changedBytesInCommonLength} byte berubah",
                                            style = MaterialTheme.typography.bodySmall)
                                        Text("Perbedaan pertama: ${diff.firstDifferenceOffset ?: "hanya ukuran"}",
                                            style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }

                    EditorTab.COMPONENTS -> {
                        item {
                            SectionHeading("Penjelajah komponen", "${filteredMembers.size} komponen ditemukan · ${allMembers.size} total")
                            OutlinedTextField(
                                value = filterText, onValueChange = { filterText = it; componentLimit = 40 },
                                label = { Text("Cari nama berkas atau arsip") }, singleLine = true,
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(categories) { category ->
                                    FilterChip(
                                        selected = selectedCategory == category,
                                        onClick = { selectedCategory = category; componentLimit = 40 },
                                        label = { Text(category) }
                                    )
                                }
                            }
                            Text("Menampilkan ${minOf(componentLimit, filteredMembers.size)} dari ${filteredMembers.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(filteredMembers.take(componentLimit), key = { it.name }) { member ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(member.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text("Ukuran asli ${member.plainLength} byte · Ukuran tersimpan ${member.storedLength} byte",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    OutlinedButton(
                                        onClick = {
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
                                        },
                                        enabled = !busy
                                    ) { Text("Ekspor komponen") }
                                }
                            }
                        }
                        if (filteredMembers.size > componentLimit) {
                            item {
                                OutlinedButton(
                                    onClick = { componentLimit = (componentLimit + 40).coerceAtMost(filteredMembers.size) },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Muat 40 lainnya") }
                            }
                        }
                    }
                }
            } else {
                item {
                    SectionHeading("Mulai", "Buka file save Football Manager Touch 2021")
                    Text("Aplikasi membaca file save asli tanpa mengubahnya. Komponen yang diekspor disimpan terpisah dan file karier sumber tidak pernah ditimpa.",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("Minimum sistem: Android 8.0 (API 26)",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ToolCard(
    title: String,
    description: String,
    action: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onClick, enabled = enabled) { Text(action) }
        }
    }
}


/**
 * Bounded file read for the current in-memory FMV parser.
 * The source URI is never modified and oversized inputs fail before allocation grows unchecked.
 */
private fun readSaveBytes(input: InputStream, maxBytes: Long = 128L * 1024 * 1024): ByteArray {
    input.use { source ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val count = source.read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) {
                throw IllegalArgumentException("Ukuran save melewati batas aman pemrosesan saat ini, yaitu 128 MB")
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}

private fun csvEscape(value: String): String =
    "\"" + value.replace("\"", "\"\"") + "\""
