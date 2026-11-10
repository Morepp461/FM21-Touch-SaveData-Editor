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
import com.morepp.fm21touchsaveeditor.core.FmvSaveComparator
import com.morepp.fm21touchsaveeditor.core.PersonRecordIndex
import com.morepp.fm21touchsaveeditor.core.PersonRecordMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val colors = darkColorScheme(
                primary = Color(0xFFE0443E),
                secondary = Color(0xFFD7B56D),
                background = Color(0xFF101114),
                onBackground = Color(0xFFF3F1EC),
                surface = Color(0xFF1B1D22),
                onSurface = Color(0xFFF3F1EC),
                surfaceVariant = Color(0xFF24262C),
                onSurfaceVariant = Color(0xFF9A9DA6)
            )
            window.statusBarColor = android.graphics.Color.rgb(16, 17, 20)
            window.navigationBarColor = android.graphics.Color.rgb(16, 17, 20)
            MaterialTheme(colorScheme = colors) {
                Surface(Modifier.fillMaxSize(), color = colors.background) { EditorHome() }
            }
        }
    }
}

private enum class EditorTab(val title: String) {
    OVERVIEW("Overview"), PEOPLE("People"), COMPARE("Compare"), COMPONENTS("Files")
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
    var selectedTab by remember { mutableStateOf(EditorTab.OVERVIEW) }
    var selectedCategory by remember { mutableStateOf("All") }
    var componentLimit by remember { mutableIntStateOf(40) }

    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val payload = pendingExport
        if (uri != null && payload != null) {
            runCatching {
                val output = context.contentResolver.openOutputStream(uri) ?: error("Tidak bisa menulis file")
                output.use { sink -> payload.inputStream().use { source -> source.copyTo(sink) } }
            }.onSuccess { status = "Komponen berhasil diekspor (${payload.length()} bytes)" }
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
                    personIndex = null
                    personSearch = ""
                    filterText = ""
                    selectedCategory = "All"
                    componentLimit = 40
                    selectedTab = EditorTab.OVERVIEW
                    status = "Save tervalidasi: ${it.manifest.saveName}"
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
                    status = "Save pembanding valid: ${it.manifest.saveName}"
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
    val categories = listOf("All", "game_db", "comp_", "tc_", "history", "other")
    val filteredMembers = allMembers.filter { member ->
        val categoryMatch = when (selectedCategory) {
            "All" -> true
            "other" -> !member.name.startsWith("game_db/") &&
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
                        Text("FM21 Touch Save Editor", fontWeight = FontWeight.SemiBold)
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
                        Text("FULL DATABASE EDITOR", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(if (parsed == null) "Your career, clearly organised." else parsed.manifest.saveName,
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
                        SummaryMetric("Database", parsed.gameVersion ?: "Unknown", Modifier.weight(1f))
                        SummaryMetric("Components", allMembers.size.toString(), Modifier.weight(1f))
                        SummaryMetric("Archives", parsed.manifest.nested.size.toString(), Modifier.weight(1f))
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
                            SectionHeading("Career workspace", "Save integrity and available tools")
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Container information", style = MaterialTheme.typography.titleMedium)
                                    InfoLine("Save", parsed.manifest.saveName)
                                    InfoLine("Database version", parsed.gameVersion ?: "Not detected")
                                    InfoLine("Top-level components", parsed.manifest.topLevel.size.toString())
                                    InfoLine("Nested components", parsed.manifest.nested.size.toString())
                                    InfoLine("Footer offset", parsed.manifest.footerOffset.toString())
                                }
                            }
                        }
                        item {
                            SectionHeading("Database tools", "Read-only analysis until record layouts are proven")
                            ToolCard(
                                title = "Person record mapping",
                                description = personIndex?.let { "${it.scannedNameEntries} names · ${it.mappedEntries} linked · ${it.unresolvedEntries} unresolved" }
                                    ?: "Connect names to internal person records",
                                action = if (personIndex == null) "Build mapping" else "Open mapping",
                                enabled = !busy && parsed.manifest.members.any { it.name == "game_db/.dat" },
                                onClick = {
                                    if (personIndex != null) {
                                        selectedTab = EditorTab.PEOPLE
                                    } else {
                                        busy = true
                                        status = "Membangun person record mapping..."
                                        scope.launch {
                                            val result = withContext(Dispatchers.IO) {
                                                runCatching {
                                                    val member = parsed.manifest.members.first { it.name == "game_db/.dat" }
                                                    val file = File(context.cacheDir, "fm21_components/game_db_person_map.dat")
                                                    parsed.extractToFile(member, file)
                                                    PersonRecordMapper.map(file.readBytes())
                                                }
                                            }
                                            result.onSuccess {
                                                personIndex = it
                                                status = "Person index selesai. Player classification belum diverifikasi."
                                                selectedTab = EditorTab.PEOPLE
                                            }.onFailure { status = "Person mapping gagal: ${it.message}" }
                                            busy = false
                                        }
                                    }
                                }
                            )
                            ToolCard(
                                title = "Save comparison",
                                description = comparisonContainer?.let { "Pembanding: ${it.manifest.saveName}" }
                                    ?: "Compare two career saves to locate changed components",
                                action = if (comparisonContainer == null) "Choose comparison save" else "Open comparison",
                                enabled = !busy,
                                onClick = {
                                    if (comparisonContainer == null) comparisonPicker.launch(arrayOf("*/*"))
                                    else selectedTab = EditorTab.COMPARE
                                }
                            )
                            ToolCard(
                                title = "Component browser",
                                description = "Search and export individual save components",
                                action = "Browse files",
                                enabled = true,
                                onClick = { selectedTab = EditorTab.COMPONENTS }
                            )
                        }
                        item {
                            Text("Player editing, finance editing and rebuilding stay locked until each field and round-trip rule is verified.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    EditorTab.PEOPLE -> {
                        item {
                            SectionHeading("People index", "Person records are not yet classified as players or staff")
                            if (personIndex == null) {
                                ToolCard(
                                    title = "Build person index",
                                    description = "Extract game_db/.dat and scan its name index",
                                    action = "Start scan",
                                    enabled = !busy && parsed.manifest.members.any { it.name == "game_db/.dat" },
                                    onClick = {
                                        busy = true
                                        status = "Membangun person record mapping..."
                                        scope.launch {
                                            val result = withContext(Dispatchers.IO) {
                                                runCatching {
                                                    val member = parsed.manifest.members.first { it.name == "game_db/.dat" }
                                                    val file = File(context.cacheDir, "fm21_components/game_db_person_map.dat")
                                                    parsed.extractToFile(member, file)
                                                    PersonRecordMapper.map(file.readBytes())
                                                }
                                            }
                                            result.onSuccess { personIndex = it; status = "Person index selesai" }
                                                .onFailure { status = "Person mapping gagal: ${it.message}" }
                                            busy = false
                                        }
                                    }
                                )
                            }
                        }
                        if (personIndex != null) {
                            val index = personIndex!!
                            val matches = index.entries.filter {
                                personSearch.isBlank() || it.name.contains(personSearch.trim(), ignoreCase = true)
                            }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SummaryMetric("Names", index.scannedNameEntries.toString(), Modifier.weight(1f))
                                    SummaryMetric("Linked", index.mappedEntries.toString(), Modifier.weight(1f))
                                    SummaryMetric("Unresolved", index.unresolvedEntries.toString(), Modifier.weight(1f))
                                }
                            }
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = personSearch, onValueChange = { personSearch = it },
                                        label = { Text("Search person name") }, singleLine = true,
                                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                                    )
                                    Text("Showing ${minOf(matches.size, 100)} of ${matches.size} results",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    OutlinedButton(
                                        onClick = {
                                            busy = true
                                            scope.launch {
                                                val exported = withContext(Dispatchers.IO) {
                                                    runCatching {
                                                        val file = File(context.cacheDir, "fm21_person_record_mapping.csv")
                                                        file.parentFile?.mkdirs()
                                                        file.bufferedWriter(Charsets.UTF_8).use { writer ->
                                                            writer.write("name,unique_id,name_index_id,name_flags,entity_record_id,entity_record_offset,record_tag,record_length_bytes")
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
                                                    status = "Person mapping CSV ready to export"
                                                }.onFailure { status = "CSV export preparation failed: ${it.message}" }
                                                busy = false
                                            }
                                        },
                                        enabled = !busy,
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Export complete mapping as CSV") }
                                }
                            }
                            items(matches.take(100), key = { "person_${it.uniqueId}" }) { person ->
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(person.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                        Text("UID ${person.uniqueId} · Name ID ${person.nameIndexId}",
                                            style = MaterialTheme.typography.bodySmall)
                                        Text("Entity record: ${person.entityRecordId ?: "Unresolved"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (person.entityRecordTypeTag != null) {
                                            Text("Record tag: ${person.entityRecordTypeTag}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (person.entityRecordLength != null) {
                                            Text("Observed adjacent-record span: ${person.entityRecordLength} bytes",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                            item {
                                Text("Mapping ini belum membuktikan bahwa record merupakan pemain. CA, PA dan atribut belum tersedia untuk diedit.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    EditorTab.COMPARE -> {
                        item {
                            SectionHeading("Save comparison", "Use two saves from the same career where possible")
                            OutlinedButton(
                                onClick = { comparisonPicker.launch(arrayOf("*/*")) },
                                enabled = !busy, modifier = Modifier.fillMaxWidth()
                            ) { Text(if (comparisonContainer == null) "Choose second save" else "Change second save") }
                            comparisonContainer?.let { other ->
                                Text("Comparing with: ${other.manifest.saveName}", style = MaterialTheme.typography.bodyMedium)
                                Button(
                                    onClick = {
                                        busy = true
                                        status = "Comparing save components..."
                                        scope.launch {
                                            val result = withContext(Dispatchers.IO) {
                                                runCatching {
                                                    FmvSaveComparator.compare(parsed, other, File(context.cacheDir, "fm21_comparison"))
                                                }
                                            }
                                            result.onSuccess { diffs ->
                                                comparisonResults = diffs
                                                status = "Comparison complete: ${diffs.count { !it.identical }} changed components"
                                            }.onFailure { status = "Comparison failed: ${it.message}" }
                                            busy = false
                                        }
                                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()
                                ) { Text(if (busy) "Comparing..." else "Compare saves") }
                            }
                        }
                        if (comparisonResults.isNotEmpty()) {
                            val changed = comparisonResults.filter { !it.identical }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SummaryMetric("Changed", changed.size.toString(), Modifier.weight(1f))
                                    SummaryMetric("Compared", comparisonResults.size.toString(), Modifier.weight(1f))
                                }
                            }
                            item {
                                OutlinedButton(
                                    onClick = {
                                        busy = true
                                        scope.launch {
                                            val report = withContext(Dispatchers.IO) {
                                                runCatching {
                                                    val file = File(context.cacheDir, "fm21_save_comparison.csv")
                                                    file.bufferedWriter(Charsets.UTF_8).use { writer ->
                                                        writer.write("component,identical,before_bytes,after_bytes,changed_bytes_in_common_length,first_difference_offset,before_sha256,after_sha256")
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
                                                status = "Comparison CSV ready to export"
                                            }.onFailure { status = "Comparison report failed: ${it.message}" }
                                            busy = false
                                        }
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Export full comparison report (CSV)") }
                            }
                            items(changed.take(100), key = { "diff_${it.name}" }) { diff ->
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text(diff.name, fontWeight = FontWeight.SemiBold)
                                        Text("${diff.beforeBytes} → ${diff.afterBytes} bytes · ${diff.changedBytesInCommonLength} bytes changed",
                                            style = MaterialTheme.typography.bodySmall)
                                        Text("First difference: ${diff.firstDifferenceOffset ?: "size only"}",
                                            style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }

                    EditorTab.COMPONENTS -> {
                        item {
                            SectionHeading("Component browser", "${filteredMembers.size} matching components · ${allMembers.size} total")
                            OutlinedTextField(
                                value = filterText, onValueChange = { filterText = it; componentLimit = 40 },
                                label = { Text("Search file or archive name") }, singleLine = true,
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
                            Text("Showing ${minOf(componentLimit, filteredMembers.size)} of ${filteredMembers.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(filteredMembers.take(componentLimit), key = { it.name }) { member ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(member.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text("Plain ${member.plainLength} bytes · Stored ${member.storedLength} bytes",
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
                                    ) { Text("Export component") }
                                }
                            }
                        }
                        if (filteredMembers.size > componentLimit) {
                            item {
                                OutlinedButton(
                                    onClick = { componentLimit = (componentLimit + 40).coerceAtMost(filteredMembers.size) },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Load 40 more") }
                            }
                        }
                    }
                }
            } else {
                item {
                    SectionHeading("Get started", "Open a Football Manager Touch 2021 save file")
                    Text("The editor reads the original save without changing it. Exported components are saved separately; the source career file is never overwritten.",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("Supported minimum: Android 8.0 (API 26)",
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
                throw IllegalArgumentException("Save exceeds the current safe in-memory limit of 128 MB")
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}

private fun csvEscape(value: String): String =
    "\"" + value.replace("\"", "\"\"") + "\""
