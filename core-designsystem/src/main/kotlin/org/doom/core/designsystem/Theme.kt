package org.doom.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val palette = darkColorScheme(
    primary = Color(0xFFC9B4FF), onPrimary = Color(0xFF35245C),
    background = Color(0xFF131217), onBackground = Color(0xFFE8E4ED),
    surface = Color(0xFF19181E), onSurface = Color(0xFFE8E4ED),
    surfaceVariant = Color(0xFF292730), onSurfaceVariant = Color(0xFFCDCAD5)
)

@Composable
fun DoomTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) palette else lightColorScheme(primary = Color(0xFF6744A6)), content = content)
}

@Composable
fun Page(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        content()
    }
}
