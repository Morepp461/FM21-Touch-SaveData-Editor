package com.morepp.fm21touchsaveeditor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { EditorHome() } }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorHome() {
    val modules = listOf(
        "FMU Container Parser", "Component Extractor", "Player Record Mapping",
        "CA / PA Editor", "Player Attributes Editor", "Club Finance Editor",
        "Prize Money Editor", "League Reputation Editor", "FMU Rebuilder"
    )
    Scaffold(topBar = { TopAppBar(title = { Text("FM21 Touch Save Editor") }) }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Full Database Editor", style = MaterialTheme.typography.headlineSmall)
                Text("Alpha 0.3 development scaffold")
                Text("Original saves are never overwritten automatically.")
            }
            items(modules.size) { index ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(modules[index], style = MaterialTheme.typography.titleMedium)
                        Text("Awaiting verified FM21 Touch format integration", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
