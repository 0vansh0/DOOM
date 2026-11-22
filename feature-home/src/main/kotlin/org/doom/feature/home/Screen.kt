package org.doom.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import org.doom.core.designsystem.Page
import androidx.compose.material3.Button

@Composable
fun HomeScreen(count: Int, discover: () -> Unit) = Page("DOOM") {
    Text("$count installed mods")
    Button(onClick = discover) { Text("Browse local examples") }
}
