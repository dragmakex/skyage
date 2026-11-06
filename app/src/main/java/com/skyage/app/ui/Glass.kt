package com.skyage.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.skyage.app.ui.theme.Sky
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * Set by a screen whose content should show through the glass panels, e.g. the world map. When it is
 * null the panels fall back to a plain veil, so screens without a source keep working.
 */
val LocalHazeState = compositionLocalOf<HazeState?> { null }

/**
 * The two materials the reference Liquid Glass ships, sized for a phone instead of a web page.
 *
 * [Clear] is for one-off controls floating over a busy backdrop (the pill over the map, icon
 * buttons): barely tinted, rim-lit, the backdrop stays readable through it. [Regular] is for real
 * surfaces (sheet, cards, fields): blurred, given a body, and dark enough for white text on top.
 */
enum class GlassMaterial(val blur: Dp, val tint: Float, val rim: Float) {
    Clear(blur = 14.dp, tint = 0.16f, rim = 1f),
    Regular(blur = 24.dp, tint = 0.46f, rim = 0.82f),
}

/**
 * Liquid Glass in four passes, in the order the reference stacks them:
 *
 * 1. **Body** — a dark veil (`tint`) plus a vertical sheen, so the surface is lighter at the top.
 * 2. **Refraction** — light collects in the thick edge: a screen-blended band along the outline, and
 *    a multiply-blended shade along the bottom, which is what makes the pane read as a solid slab.
 * 3. **Specular rim** — a hairline that is bright along the top arc and fades down the sides, with a
 *    hot spot where a light source above the screen would hit it.
 * 4. **Lift** — the drop shadow, so the pane floats instead of sitting on the backdrop.
 *
 * [blur] turns on real backdrop blurring of the layer marked with `Modifier.hazeSource` (needs
 * [LocalHazeState] in scope) and is progressive: strongest at the panel edge, fading inward.
 *
 * Not reproduced: true refraction (the reference displaces the backdrop through an SDF-derived
 * normal map in a fragment shader). Compose cannot sample what is behind a node without a GPU
 * effect of its own, so the edge is lit rather than bent.
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(26.dp),
    material: GlassMaterial = GlassMaterial.Regular,
    blur: Dp = material.blur,
    tint: Color? = null,
    lift: Dp = 10.dp,
): Modifier {
    val hazeState = LocalHazeState.current
    val blurring = hazeState != null && blur > 0.dp

    val surface = if (blurring) {
        clip(shape).hazeEffect(state = hazeState) {
            blurRadius = blur
            noiseFactor = 0.02f
            progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0.35f)
        }
    } else {
        clip(shape)
    }

    return this
        .shadow(
            elevation = lift,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.55f),
            spotColor = Color.Black.copy(alpha = 0.55f),
        )
        .then(surface)
        .drawBehind { drawGlass(shape, material, tint) }
}

private fun DrawScope.drawGlass(shape: Shape, material: GlassMaterial, tint: Color?) {
    val path = shape.createOutline(size, layoutDirection, this).asPath()
    val rim = material.rim

    drawPath(path, Sky.GlassVeil.copy(alpha = Sky.GlassVeil.alpha * material.tint))
    tint?.let { drawPath(path, it) }
    drawPath(
        path,
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.07f),
            0.55f to Color.White.copy(alpha = 0.02f),
            1f to Color.Transparent,
        ),
    )

    // Half of a centred stroke falls outside the clip, so these bands end up inset by width / 2.
    val edge = 8.dp.toPx()
    drawPath(
        path,
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.10f * rim),
            0.6f to Color.White.copy(alpha = 0.025f),
            1f to Color.White.copy(alpha = 0.01f * rim),
        ),
        style = Stroke(edge),
        blendMode = BlendMode.Screen,
    )
    drawPath(
        path,
        Brush.verticalGradient(
            0.5f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.16f),
        ),
        style = Stroke(edge),
        blendMode = BlendMode.Multiply,
    )

    val hairline = 1.1.dp.toPx()
    drawPath(
        path,
        Brush.verticalGradient(
            0.04f to Color.White.copy(alpha = 0.85f * rim),
            0.45f to Color.White.copy(alpha = 0.10f * rim),
            0.85f to Color.White.copy(alpha = 0.02f),
            1f to Color.White.copy(alpha = 0.16f * rim),
        ),
        style = Stroke(hairline),
    )
    // Light source above the surface: brightest where it meets the top edge and the corners fall off.
    drawPath(
        path,
        Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.38f * rim),
            0.55f to Color.White.copy(alpha = 0.08f * rim),
            1f to Color.Transparent,
            center = Offset(size.width * 0.5f, -size.height * 0.25f),
            radius = size.width * 0.85f,
        ),
        style = Stroke(hairline),
        blendMode = BlendMode.Screen,
    )
}

private fun Outline.asPath(): Path = Path().also { it.addOutline(this) }

/** Aurora behind the screens that have no map, so their glass has something to refract. */
fun Modifier.nightBackdrop(): Modifier = drawBehind {
    drawRect(Sky.Night)
    drawRect(
        Brush.radialGradient(
            0f to Sky.AuroraTop.copy(alpha = 0.44f),
            0.55f to Sky.AuroraTop.copy(alpha = 0.14f),
            1f to Color.Transparent,
            center = Offset(size.width * 0.10f, size.height * 0.02f),
            radius = size.width * 1.15f,
        )
    )
    drawRect(
        Brush.radialGradient(
            0f to Sky.AuroraBlue.copy(alpha = 0.34f),
            0.6f to Color.Transparent,
            center = Offset(size.width * 1.0f, size.height * 0.45f),
            radius = size.width * 1.0f,
        )
    )
    drawRect(
        Brush.radialGradient(
            0f to Sky.AccentDeep.copy(alpha = 0.30f),
            1f to Color.Transparent,
            center = Offset(size.width * 0.45f, size.height * 0.95f),
            radius = size.width * 1.1f,
        )
    )
}

/**
 * Screen frame for the pages without a map: aurora backdrop as the haze source, then a transparent
 * Scaffold so every glass component on top refracts it.
 */
@Composable
fun GlassScaffold(
    topBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val hazeState = rememberHazeState()
    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().nightBackdrop().hazeSource(hazeState))
            Scaffold(containerColor = Color.Transparent, topBar = topBar, content = content)
        }
    }
}

/** A glass slab that lays its content out in a column. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    material: GlassMaterial = GlassMaterial.Regular,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    blur: Dp = material.blur,
    lift: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.liquidGlass(shape, material, blur, lift = lift).padding(contentPadding),
        content = content,
    )
}

/**
 * Top bar on the 16 dp content grid: back tile flush with the content gutter and the actions in a
 * matching tile on the other side, so the circles line up with the cards below.
 */
@Composable
fun GlassTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(64.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            onClick = onBack,
            size = 44.dp,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = Sky.Label,
            maxLines = 1,
            modifier = Modifier.weight(1f).padding(start = 14.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/** Round glass button for one icon — the floating action, back, refresh and delete controls. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .liquidGlass(CircleShape, GlassMaterial.Clear, lift = 6.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Sky.Label,
            modifier = Modifier.size(size * 0.48f),
        )
    }
}

/** Full-width glass button; the primary action carries the accent tint. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .liquidGlass(
                shape = RoundedCornerShape(20.dp),
                material = GlassMaterial.Regular,
                tint = Sky.Accent.copy(alpha = 0.26f),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Sky.Label,
        )
    }
}

/**
 * Text field with the glass body and no Material box stroke: the rim is the border. The caption sits
 * above the field instead of inside it, because Material's floating label jumps 80 px up and shrinks
 * the moment the field takes focus, which reads as the layout shoving itself around.
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (isError) Sky.Alert else Sky.TertiaryLabel,
            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder?.let { { Text(it) } },
            singleLine = singleLine,
            isError = isError,
            keyboardOptions = keyboardOptions,
            trailingIcon = trailingIcon,
            shape = shape,
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(
                    shape = shape,
                    material = GlassMaterial.Clear,
                    tint = if (isError) Sky.Alert.copy(alpha = 0.16f) else null,
                    lift = 4.dp,
                ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                errorContainerColor = Color.Transparent,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                errorBorderColor = Color.Transparent,
                focusedTextColor = Sky.Label,
                unfocusedTextColor = Sky.Label,
                errorTextColor = Sky.Label,
                focusedPlaceholderColor = Sky.TertiaryLabel,
                unfocusedPlaceholderColor = Sky.TertiaryLabel,
                focusedTrailingIconColor = Sky.SecondaryLabel,
                unfocusedTrailingIconColor = Sky.SecondaryLabel,
                cursorColor = Sky.Accent,
            ),
        )
    }
}
