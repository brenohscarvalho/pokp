package com.pokp.pokedex.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.pokp.pokedex.data.Generation
import com.pokp.pokedex.domain.EvolutionNode
import com.pokp.pokedex.domain.LearnMethod
import com.pokp.pokedex.domain.LearnedMove
import com.pokp.pokedex.domain.Nature
import com.pokp.pokedex.domain.PokemonDetail
import com.pokp.pokedex.domain.PokemonType
import com.pokp.pokedex.domain.StatCalculator
import com.pokp.pokedex.domain.StatType
import com.pokp.pokedex.ui.components.TypeChip
import com.pokp.pokedex.ui.components.dexNumber
import com.pokp.pokedex.ui.theme.colorForType
import androidx.compose.ui.graphics.Color

private val TABS = listOf("About", "Stats", "Evolutions", "Weaknesses", "Moves")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PokemonDetailScreen(
    onPokemonClick: (Int) -> Unit,
    onBack: () -> Unit,
    viewModel: PokemonDetailViewModel = viewModel(factory = PokemonDetailViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveableTab()

    val detail = state.detail
    val accent = detail?.types?.firstOrNull()?.let { colorForType(it) } ?: Color.Gray

    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(
                Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            detail == null -> Box(
                Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { Text("Pokémon not found.") }

            else -> Column(Modifier.padding(padding).fillMaxSize()) {
                Header(detail, accent)
                ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 8.dp) {
                    TABS.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) },
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when (selectedTab) {
                        0 -> AboutTab(detail)
                        1 -> StatsTab(state, viewModel)
                        2 -> EvolutionsTab(state.evolutions, detail.id, onPokemonClick)
                        3 -> WeaknessesTab(state.defense)
                        else -> MovesTab(detail.moves)
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberSaveableTab() = remember { mutableIntStateOf(0) }

@Composable
private fun Header(detail: PokemonDetail, accent: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.18f))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = dexNumber(detail.id),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AsyncImage(
            model = detail.artworkUrl,
            contentDescription = detail.name,
            modifier = Modifier.size(180.dp),
        )
        Text(detail.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            detail.types.forEach { TypeChip(it) }
        }
        Spacer(Modifier.height(4.dp))
        Text(Generation.displayName(detail.generation), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun AboutTab(detail: PokemonDetail) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        if (detail.flavorText.isNotBlank()) {
            Text(detail.flavorText, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
        }
        InfoRow("Height", "%.1f m".format(detail.heightMeters))
        InfoRow("Weight", "%.1f kg".format(detail.weightKilograms))
        InfoRow("Abilities", detail.abilities.joinToString(", ").ifBlank { "—" })
        InfoRow("Generation", Generation.displayName(detail.generation))
        InfoRow("Base stat total", detail.baseStats.total.toString())
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, modifier = Modifier.width(130.dp), fontWeight = FontWeight.SemiBold)
        Text(value, modifier = Modifier.weight(1f))
    }
}

// ---------- Stats + Calculator ----------

@Composable
private fun StatsTab(state: DetailUiState, viewModel: PokemonDetailViewModel) {
    val detail = state.detail ?: return
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text("Base stats", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        StatType.entries.forEach { stat ->
            BaseStatBar(stat.shortName, detail.baseStats[stat])
        }
        InfoRow("Total", detail.baseStats.total.toString())

        Spacer(Modifier.height(24.dp))
        Text("Stat calculator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        CalculatorControls(state, viewModel)
    }
}

@Composable
private fun BaseStatBar(label: String, value: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Text(label, modifier = Modifier.width(44.dp), fontWeight = FontWeight.SemiBold)
        Text(value.toString(), modifier = Modifier.width(40.dp))
        LinearProgressIndicator(
            progress = { (value / 255f).coerceIn(0f, 1f) },
            modifier = Modifier.weight(1f).height(10.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalculatorControls(state: DetailUiState, viewModel: PokemonDetailViewModel) {
    val calc = state.calculator

    // Level
    Text("Level: ${calc.level}", fontWeight = FontWeight.SemiBold)
    Slider(
        value = calc.level.toFloat(),
        onValueChange = { viewModel.setLevel(it.toInt().coerceIn(1, 100)) },
        valueRange = 1f..100f,
    )

    // Nature
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = natureLabel(calc.nature),
            onValueChange = {},
            readOnly = true,
            label = { Text("Nature") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        androidx.compose.material3.ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            Nature.entries.forEach { nature ->
                DropdownMenuItem(
                    text = { Text(natureLabel(nature)) },
                    onClick = {
                        viewModel.setNature(nature)
                        expanded = false
                    },
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    StatType.entries.forEach { stat ->
        CalculatorStatRow(
            stat = stat,
            iv = calc.ivs[stat] ?: StatCalculator.MAX_IV,
            ev = calc.evs[stat] ?: 0,
            computed = state.computedStats[stat] ?: 0,
            onIv = { viewModel.setIv(stat, it) },
            onEv = { viewModel.setEv(stat, it) },
        )
    }
}

@Composable
private fun CalculatorStatRow(
    stat: StatType,
    iv: Int,
    ev: Int,
    computed: Int,
    onIv: (Int) -> Unit,
    onEv: (Int) -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(10.dp)) {
            Row {
                Text(stat.displayName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("= $computed", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Text("IV: $iv", fontSize = 12.sp)
            Slider(
                value = iv.toFloat(),
                onValueChange = { onIv(it.toInt().coerceIn(0, StatCalculator.MAX_IV)) },
                valueRange = 0f..StatCalculator.MAX_IV.toFloat(),
            )
            Text("EV: $ev", fontSize = 12.sp)
            Slider(
                value = ev.toFloat(),
                onValueChange = { onEv((it.toInt() / 4 * 4).coerceIn(0, StatCalculator.MAX_EV)) },
                valueRange = 0f..StatCalculator.MAX_EV.toFloat(),
            )
        }
    }
}

private fun natureLabel(nature: Nature): String {
    val inc = nature.increased
    val dec = nature.decreased
    return if (inc == null || dec == null) "${nature.displayName} (neutral)"
    else "${nature.displayName} (+${inc.shortName} / -${dec.shortName})"
}

// ---------- Evolutions ----------

@Composable
private fun EvolutionsTab(
    nodes: List<EvolutionNode>,
    currentId: Int,
    onPokemonClick: (Int) -> Unit,
) {
    if (nodes.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No evolution data.", textAlign = TextAlign.Center)
        }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        if (nodes.size == 1) {
            Text("This Pokémon does not evolve.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
        }
        nodes.forEach { node ->
            if (node.evolvesFromId != null && node.condition.isNotBlank()) {
                Text(
                    "↓ ${node.condition}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                )
            }
            EvolutionRow(node = node, highlighted = node.id == currentId, onClick = { onPokemonClick(node.id) })
        }
    }
}

@Composable
private fun EvolutionRow(node: EvolutionNode, highlighted: Boolean, onClick: () -> Unit) {
    val bg = if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        AsyncImage(model = node.spriteUrl, contentDescription = node.name, modifier = Modifier.size(72.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(dexNumber(node.id), style = MaterialTheme.typography.labelMedium)
            Text(node.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ---------- Weaknesses ----------

@Composable
private fun WeaknessesTab(defense: Map<PokemonType, Double>) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        MultiplierGroup("Weak to (×4)", defense.filterValues { it == 4.0 }.keys)
        MultiplierGroup("Weak to (×2)", defense.filterValues { it == 2.0 }.keys)
        MultiplierGroup("Resists (×½)", defense.filterValues { it == 0.5 }.keys)
        MultiplierGroup("Resists (×¼)", defense.filterValues { it == 0.25 }.keys)
        MultiplierGroup("Immune (×0)", defense.filterValues { it == 0.0 }.keys)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MultiplierGroup(title: String, types: Set<PokemonType>) {
    if (types.isEmpty()) return
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        types.forEach { TypeChip(it) }
    }
    Spacer(Modifier.height(16.dp))
}

// ---------- Moves ----------

@Composable
private fun MovesTab(moves: List<LearnedMove>) {
    if (moves.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No move data.")
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        items(moves) { move -> MoveRow(move) }
    }
}

@Composable
private fun MoveRow(move: LearnedMove) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(move.info.displayName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                move.info.type?.let { TypeChip(it) }
            }
            Spacer(Modifier.height(4.dp))
            val meta = buildList {
                add(move.info.damageClass.displayName)
                add("Power: ${move.info.power ?: "—"}")
                add("Acc: ${move.info.accuracy?.let { "$it%" } ?: "—"}")
                add("PP: ${move.info.pp ?: "—"}")
            }.joinToString("   ")
            Text(meta, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val learn = when (move.method) {
                LearnMethod.LEVEL_UP -> "Learns at Lv. ${move.levelLearnedAt}"
                else -> move.method.displayName
            }
            Text(learn, fontSize = 12.sp)
        }
    }
}
