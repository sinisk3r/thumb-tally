package com.srik.shortstracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val CardShape = RoundedCornerShape(22.dp)
val ButtonShape = RoundedCornerShape(14.dp)

/** A white (or dark) panel with a hairline border, like the promo page's cards. */
@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, extraColors.line, CardShape)
            .padding(18.dp),
        content = content,
    )
}

/** Small mono capitals above a title or inside a card. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(), modifier = modifier, style = MaterialTheme.typography.labelMedium, color = color)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Eyebrow(text, modifier.padding(bottom = 12.dp))
}

/** Eyebrow + big condensed capitals, used at the top of every screen. */
@Composable
fun ScreenTitle(title: String, eyebrow: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 12.dp, bottom = 2.dp)) {
        Eyebrow(eyebrow)
        Text(title.uppercase(), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Text(
        text.uppercase(),
        modifier = modifier.clip(CircleShape).background(container).padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = content,
    )
}

@Composable
fun Dot(color: Color, size: Dp = 9.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** Ink-filled, the main action on a screen. */
@Composable
fun PrimaryButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier.pressBounce(interaction),
        interactionSource = interaction,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        content = content,
    )
}

@Composable
fun GhostButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.pressBounce(interaction),
        interactionSource = interaction,
        shape = ButtonShape,
        border = BorderStroke(1.5.dp, extraColors.line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        content = content,
    )
}

/** A whole row that squashes when tapped. */
fun Modifier.bouncyClick(onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    pressBounce(interaction).clickable(interactionSource = interaction, indication = null, onClick = onClick)
}
