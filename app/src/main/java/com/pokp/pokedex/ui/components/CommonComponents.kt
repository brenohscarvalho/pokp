package com.pokp.pokedex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokp.pokedex.domain.PokemonType
import com.pokp.pokedex.ui.theme.colorForType

/** Formats a national dex id as "#0025". */
fun dexNumber(id: Int): String = "#%04d".format(id)

/** A coloured pill showing a Pokémon type. */
@Composable
fun TypeChip(
    type: PokemonType,
    modifier: Modifier = Modifier,
) {
    val bg = colorForType(type)
    val textColor = if (bg.luminance() > 0.5f) Color.Black else Color.White
    Text(
        text = type.displayName,
        color = textColor,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(PaddingValues(horizontal = 10.dp, vertical = 3.dp)),
    )
}
