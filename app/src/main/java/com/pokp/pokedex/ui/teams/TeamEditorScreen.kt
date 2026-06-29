package com.pokp.pokedex.ui.teams

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.pokp.pokedex.domain.ImageUrls
import com.pokp.pokedex.domain.Nature
import com.pokp.pokedex.domain.PokemonDetail
import com.pokp.pokedex.domain.PokemonType
import com.pokp.pokedex.domain.team.TeamAnalysis
import com.pokp.pokedex.domain.team.TeamMember
import com.pokp.pokedex.ui.components.TypeChip
import com.pokp.pokedex.ui.components.dexNumber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamEditorScreen(
    onBack: () -> Unit,
    onPokemonClick: (Int) -> Unit,
    viewModel: TeamEditorViewModel = viewModel(factory = TeamEditorViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editar time") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.openOverlay(Overlay.Import) }) {
                        Icon(Icons.Default.Download, contentDescription = "Importar")
                    }
                    IconButton(
                        onClick = { viewModel.buildExport() },
                        enabled = state.members.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = "Exportar")
                    }
                    IconButton(onClick = { viewModel.save() }) {
                        Icon(Icons.Default.Save, contentDescription = "Salvar")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = { Text("Nome do time") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            itemsIndexed(state.members) { index, member ->
                MemberCard(
                    member = member,
                    detail = state.details[member.speciesId],
                    onEdit = { viewModel.openOverlay(Overlay.MemberOptions(index)) },
                    onRemove = { viewModel.removeMember(index) },
                    onOpen = { onPokemonClick(member.speciesId) },
                )
            }

            if (state.members.size < MAX_TEAM_SIZE) {
                item {
                    OutlinedButton(
                        onClick = { viewModel.openOverlay(Overlay.AddMember) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("  Adicionar Pokémon")
                    }
                }
            }

            state.analysis?.let { analysis ->
                if (state.members.isNotEmpty()) {
                    item { AnalysisSection(analysis) }
                }
            }
        }
    }

    when (val overlay = state.overlay) {
        Overlay.None -> Unit
        Overlay.AddMember -> AddMemberSheet(viewModel)
        is Overlay.MemberOptions -> state.members.getOrNull(overlay.index)?.let {
            MemberOptionsSheet(viewModel, overlay.index, it, state.details[it.speciesId])
        }
        is Overlay.MovePicker -> state.members.getOrNull(overlay.index)?.let {
            MovePickerSheet(viewModel, overlay.index, overlay.slot, state.details[it.speciesId])
        }
        Overlay.Import -> ImportDialog(viewModel)
        is Overlay.Export -> ExportDialog(viewModel, overlay.text)
    }
}

@Composable
private fun MemberCard(
    member: TeamMember,
    detail: PokemonDetail?,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = ImageUrls.sprite(member.speciesId),
                contentDescription = member.speciesName,
                modifier = Modifier.size(64.dp).clickable(onClick = onOpen),
            )
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(member.speciesName, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    detail?.types?.forEach { TypeChip(it) }
                }
                val moves = member.moves.filter { it.isNotBlank() }
                Text(
                    if (moves.isEmpty()) "Sem golpes" else moves.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Editar") }
            IconButton(onClick = onRemove) { Icon(Icons.Default.Close, contentDescription = "Remover") }
        }
    }
}

// ---------------- Analysis ----------------

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AnalysisSection(analysis: TeamAnalysis) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Fraquezas do time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val weakRows = analysis.defense.filter { it.weak > 0 }.sortedByDescending { it.weak }
        if (weakRows.isEmpty()) {
            Text("Nenhuma fraqueza compartilhada.", style = MaterialTheme.typography.bodyMedium)
        } else {
            weakRows.forEach { row ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypeChip(row.type)
                    Text(
                        "${row.weak} fraco(s)" + if (row.resist > 0) " · ${row.resist} resiste(m)" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Text(
            "Cobertura ofensiva (${analysis.covered.size}/18)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        if (analysis.uncovered.isEmpty()) {
            Text("Cobertura completa! Você acerta todos os tipos com eficácia.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("Tipos sem golpe super eficaz:", style = MaterialTheme.typography.bodySmall)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PokemonType.entries.filter { it in analysis.uncovered }.forEach { TypeChip(it) }
            }
        }

        Text("Sugestões", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        analysis.suggestions.forEach { s ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(s.title, fontWeight = FontWeight.SemiBold)
                    Text(s.detail, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ---------------- Add member ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMemberSheet(viewModel: TeamEditorViewModel) {
    val query by viewModel.pickerQuery.collectAsStateWithLifecycle()
    val results by viewModel.pickerResults.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = { viewModel.closeOverlay() }) {
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            Text("Adicionar Pokémon", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.pickerQuery.value = it },
                placeholder = { Text("Buscar") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(results, key = { it.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable { viewModel.addMember(p.id) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(model = p.spriteUrl, contentDescription = p.name, modifier = Modifier.size(48.dp))
                        Text("${dexNumber(p.id)}  ${p.name}", Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

// ---------------- Member options ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberOptionsSheet(
    viewModel: TeamEditorViewModel,
    index: Int,
    member: TeamMember,
    detail: PokemonDetail?,
) {
    ModalBottomSheet(onDismissRequest = { viewModel.closeOverlay() }) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(member.speciesName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            DropdownField(
                label = "Natureza",
                value = member.nature,
                options = Nature.entries.map { it.displayName },
                onSelect = { viewModel.setNature(index, it) },
            )

            val abilityOptions = detail?.abilities ?: emptyList()
            if (abilityOptions.isNotEmpty()) {
                DropdownField(
                    label = "Habilidade",
                    value = member.ability ?: abilityOptions.first(),
                    options = abilityOptions,
                    onSelect = { viewModel.setAbility(index, it) },
                )
            }

            OutlinedTextField(
                value = member.item.orEmpty(),
                onValueChange = { viewModel.setItem(index, it) },
                label = { Text("Item") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = member.level.toString(),
                onValueChange = { viewModel.setLevel(index, it.toIntOrNull() ?: member.level) },
                label = { Text("Nível") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            DropdownField(
                label = "Tera Tipo",
                value = member.teraType ?: "Nenhum",
                options = listOf("Nenhum") + PokemonType.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                onSelect = { viewModel.setTera(index, if (it == "Nenhum") null else it) },
            )

            Text("Golpes", fontWeight = FontWeight.SemiBold)
            for (slot in 0 until MAX_MOVES) {
                val moveName = member.moves.getOrNull(slot)
                OutlinedButton(
                    onClick = { viewModel.openOverlay(Overlay.MovePicker(index, slot)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(moveName ?: "Adicionar golpe ${slot + 1}", Modifier.weight(1f))
                    if (moveName != null) {
                        IconButton(onClick = { viewModel.clearMove(index, slot) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remover golpe")
                        }
                    }
                }
            }
            TextButton(onClick = { viewModel.closeOverlay() }, modifier = Modifier.fillMaxWidth()) {
                Text("Concluído")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
    }
}

// ---------------- Move picker ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MovePickerSheet(
    viewModel: TeamEditorViewModel,
    index: Int,
    slot: Int,
    detail: PokemonDetail?,
) {
    val query by viewModel.moveQuery.collectAsStateWithLifecycle()
    val moves = remember(detail, query) {
        (detail?.moves ?: emptyList())
            .distinctBy { it.info.name }
            .filter { query.isBlank() || it.info.displayName.contains(query, true) }
            .sortedBy { it.info.displayName }
    }
    ModalBottomSheet(onDismissRequest = { viewModel.closeOverlay() }) {
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            Text("Escolher golpe", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.moveQuery.value = it },
                placeholder = { Text("Buscar golpe") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            LazyColumn(Modifier.heightIn(max = 440.dp)) {
                items(moves, key = { it.info.name }) { move ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { viewModel.setMove(index, slot, move.info.displayName) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(move.info.displayName, Modifier.weight(1f))
                        move.info.type?.let { TypeChip(it) }
                        Text(
                            "P:${move.info.power ?: "—"}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ---------------- Import / Export ----------------

@Composable
private fun ImportDialog(viewModel: TeamEditorViewModel) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { viewModel.closeOverlay() },
        title = { Text("Importar do Showdown") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Cole o time no formato Showdown") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
            )
        },
        confirmButton = { TextButton(onClick = { viewModel.import(text) }) { Text("Importar") } },
        dismissButton = { TextButton(onClick = { viewModel.closeOverlay() }) { Text("Cancelar") } },
    )
}

@Composable
private fun ExportDialog(viewModel: TeamEditorViewModel, text: String) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = { viewModel.closeOverlay() },
        title = { Text("Exportar (Showdown)") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 360.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(text))
                viewModel.closeOverlay()
            }) { Text("Copiar") }
        },
        dismissButton = { TextButton(onClick = { viewModel.closeOverlay() }) { Text("Fechar") } },
    )
}
