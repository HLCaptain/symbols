package io.github.hlcaptain.symbols.font

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.text.font.FontVariation

/** A mounted renderer owns its current run; the immutable base font can be shared. */
internal interface NativeSymbolFont {
    /** Null clears the owned layer; true requests rerecording alpha baked into native content. */
    fun updateLayerProperties(scope: GraphicsLayerScope?): Boolean = false

    fun draw(
        scope: DrawScope,
        text: String,
        fontSize: Float,
        settings: FontVariation.Settings?,
        tint: Color,
    )

    fun close()
}

@Composable
internal expect fun rememberNativeSymbolFont(font: SymbolFont): NativeSymbolFont?

/** A layer-property callback can invalidate content immediately without writing snapshot state. */
internal class SymbolFontDrawState {
    internal var node: SymbolFontDrawNode? = null

    fun invalidate() {
        node?.invalidateDraw()
    }
}

internal fun Modifier.symbolFontDraw(
    state: SymbolFontDrawState,
    onDraw: DrawScope.() -> Unit,
): Modifier = this then SymbolFontDrawElement(state, onDraw)

private data class SymbolFontDrawElement(
    val state: SymbolFontDrawState,
    val onDraw: DrawScope.() -> Unit,
) : ModifierNodeElement<SymbolFontDrawNode>() {
    override fun create() = SymbolFontDrawNode(state, onDraw)

    override fun update(node: SymbolFontDrawNode) {
        if (node.state.node === node) node.state.node = null
        node.state = state
        node.onDraw = onDraw
        if (node.isAttached) state.node = node
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "symbolFontDraw"
    }
}

internal class SymbolFontDrawNode(
    var state: SymbolFontDrawState,
    var onDraw: DrawScope.() -> Unit,
) : Modifier.Node(), DrawModifierNode {
    override fun onAttach() {
        state.node = this
    }

    override fun onDetach() {
        if (state.node === this) state.node = null
    }

    override fun ContentDrawScope.draw() {
        onDraw()
        drawContent()
    }
}
