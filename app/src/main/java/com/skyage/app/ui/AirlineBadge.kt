package com.skyage.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skyage.app.ui.theme.Sky

/**
 * The airline's IATA code in a glass tile. Airline marks themselves are trademarks and the app
 * ships no third-party logo art, so the code is the honest stand-in.
 */
@Composable
fun AirlineBadge(iata: String?, size: Dp = 28.dp, modifier: Modifier = Modifier) {
    val code = iata?.trim()?.uppercase().orEmpty()
    if (code.isEmpty()) return

    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Sky.Fill)
            .border(1.dp, Sky.TileBorder, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.labelSmall,
            fontSize = (size.value * 0.36f).sp,
            fontWeight = FontWeight.SemiBold,
            color = Sky.SecondaryLabel,
            maxLines = 1,
        )
    }
}
