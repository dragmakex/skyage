package com.skyage.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.skyage.app.data.Airport
import com.skyage.app.data.FlightPhase
import com.skyage.app.ui.theme.Sky

private val CardShape = RoundedCornerShape(20.dp)

/** Content card. Glass, so it reads the same on the map, the sheet and the detail screen. */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.liquidGlass(CardShape).padding(16.dp)) { content() }
}

@Composable
fun PhaseBadge(phase: FlightPhase) {
    val (label, color) = when (phase) {
        FlightPhase.AIRBORNE -> "IN AIR" to Sky.OnTime
        FlightPhase.ON_GROUND -> "ON GROUND" to Sky.Warn
        FlightPhase.NO_DATA -> "NO SIGNAL" to Sky.TertiaryLabel
        FlightPhase.LANDED -> "LANDED" to Sky.Accent
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(color))
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 5.dp),
        )
    }
}

/** Airport-to-airport header. Codes use the dataset's IATA where available.
 * [compact] trims it for list rows. Renders nothing when neither end is known. */
@Composable
fun RouteHeader(
    origin: Airport?,
    originCode: String?,
    dest: Airport?,
    destCode: String?,
    compact: Boolean = false,
) {
    val left = origin?.code ?: originCode
    val right = dest?.code ?: destCode
    if (left == null && right == null) return
    val style = if (compact) {
        MaterialTheme.typography.titleLarge
    } else {
        MaterialTheme.typography.displaySmall
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        left?.let { Text(it, style = style, color = Sky.Label, fontWeight = FontWeight.Bold) }
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(Sky.TileBorder)
        )
        right?.let { Text(it, style = style, color = Sky.Label, fontWeight = FontWeight.Bold) }
    }
}

/** Thin progress rail. Null renders an empty track. */
@Composable
fun ProgressRail(fraction: Double?, modifier: Modifier = Modifier) {
    val f = (fraction ?: 0.0).coerceIn(0.0, 1.0).toFloat()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0x33FFFFFF)),
    ) {
        if (f > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.horizontalGradient(listOf(Sky.Accent, Color(0xFF6FA8FF)))
                    )
            )
        }
    }
}

@Composable
fun StatCell(label: String, value: String, sub: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Sky.TertiaryLabel,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = Sky.Label,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (sub != null) {
            Text(
                text = sub,
                style = MaterialTheme.typography.bodyMedium,
                color = Sky.SecondaryLabel,
            )
        }
    }
}

@Composable
fun CenteredMessage(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Sky.Label,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = Sky.SecondaryLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
