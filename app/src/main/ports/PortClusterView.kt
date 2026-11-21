package com.monolith.graph.ports.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import com.monolith.graph.ports.models.*
import kotlin.math.max
import kotlin.math.roundToInt

class PortClusterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private data class PortEntry(
        val view: PortView,
        val label: String
    )

    private data class ContentBounds(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int
    ) {
        val right: Int
            get() = left + width

        val bottom: Int
            get() = top + height
    }

    var ownerId: String = ""
        set(value) {
            if (field == value) return
            field = value
            refreshIds()
        }

    var config: PortLayoutConfig = PortLayoutConfig()
        set(value) {
            field = value
            updateReservedSpace()
            requestLayout()
            invalidate()
        }

    var onPortSelected: ((PortSpec) -> Unit)? = null

    private val ports = LinkedHashMap<String, PortEntry>()

    private val density = resources.displayMetrics.density

    private fun dp(value: Float): Int =
        (value * density).toInt()

    private fun dpF(value: Float): Float =
        value * density

    /*
     * ChatActivity positions the cluster 17dp
     * outside the card on every side.
     *
     * The padding must match that offset so that
     * the content rectangle corresponds exactly
     * to the card rectangle.
     */
    private val portOutset: Int
        get() = dp(17f)

    // ---------------------------------------------------------
    // Mechanical edge chassis paints
    // ---------------------------------------------------------

    private val railBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF111B29.toInt()
    }

    private val railRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpF(1.15f)
        color = 0xFF697D91.toInt()
    }

    private val railGroovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF050B14.toInt()
    }

    private val railHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpF(0.8f)
        color = 0xFFB5C8D9.toInt()
        alpha = 145
    }

    private val railSeamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF02060D.toInt()
    }

    private val railReflectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        alpha = 45
    }

    private val railOuterRect = RectF()
    private val railInnerRect = RectF()
    private val railGrooveRect = RectF()

    init {
        clipChildren = false
        clipToPadding = false
        setWillNotDraw(false)

        updateReservedSpace()
    }

    // ---------------------------------------------------------
    // Reserved card area
    // ---------------------------------------------------------

    private fun updateReservedSpace() {
        val inset = dp(
            config.edgeInsetDp.coerceAtLeast(0f)
        )

        val reserved = portOutset + inset

        setPadding(
            reserved,
            reserved,
            reserved,
            reserved
        )
    }

    private fun getContentBounds(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): ContentBounds {
        return ContentBounds(
            left = paddingLeft,
            top = paddingTop,
            width = (
                right - left - paddingLeft - paddingRight
            ).coerceAtLeast(0),
            height = (
                bottom - top - paddingTop - paddingBottom
            ).coerceAtLeast(0)
        )
    }

    private fun edgeInsetPx(): Float =
        dpF(config.edgeInsetDp.coerceAtLeast(0f))

    private fun leftEdgeX(content: ContentBounds): Float =
        content.left - edgeInsetPx()

    private fun rightEdgeX(content: ContentBounds): Float =
        content.right + edgeInsetPx()

    private fun topEdgeY(content: ContentBounds): Float =
        content.top - edgeInsetPx()

    private fun bottomEdgeY(content: ContentBounds): Float =
        content.bottom + edgeInsetPx()

    // ---------------------------------------------------------
    // Mechanical edge chassis drawing
    // ---------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (ports.isEmpty()) return

        val content = getContentBounds(
            left = left,
            top = top,
            right = right,
            bottom = bottom
        )

        if (
            content.width <= 0 ||
            content.height <= 0
        ) {
            return
        }

        val cornerClearance = dpF(12f)

        /*
         * Draw only the sides that actually contain
         * ports. Each side gets one continuous rail,
         * rather than independent floating bases.
         */

        if (hasPorts(PortSide.LEFT)) {
            drawVerticalRail(
                canvas = canvas,
                centerX = leftEdgeX(content),
                top = content.top + cornerClearance,
                bottom = content.bottom - cornerClearance,
                outerDirection = -1f
            )
        }

        if (hasPorts(PortSide.RIGHT)) {
            drawVerticalRail(
                canvas = canvas,
                centerX = rightEdgeX(content),
                top = content.top + cornerClearance,
                bottom = content.bottom - cornerClearance,
                outerDirection = 1f
            )
        }

        if (hasPorts(PortSide.TOP)) {
            drawHorizontalRail(
                canvas = canvas,
                centerY = topEdgeY(content),
                left = content.left + cornerClearance,
                right = content.right - cornerClearance,
                outerDirection = -1f
            )
        }

        if (hasPorts(PortSide.BOTTOM)) {
            drawHorizontalRail(
                canvas = canvas,
                centerY = bottomEdgeY(content),
                left = content.left + cornerClearance,
                right = content.right - cornerClearance,
                outerDirection = 1f
            )
        }
    }

    private fun hasPorts(side: PortSide): Boolean =
        ports.values.any {
            it.view.portSide == side
        }

    private fun drawVerticalRail(
        canvas: Canvas,
        centerX: Float,
        top: Float,
        bottom: Float,
        outerDirection: Float
    ) {
        if (bottom <= top) return

        val halfWidth = dpF(4.2f)
        val innerHalfWidth = dpF(2.6f)

        railOuterRect.set(
            centerX - halfWidth,
            top,
            centerX + halfWidth,
            bottom
        )

        // Main structural rail.
        canvas.drawRoundRect(
            railOuterRect,
            dpF(2.2f),
            dpF(2.2f),
            railBodyPaint
        )

        // Metallic outer rim.
        canvas.drawRoundRect(
            railOuterRect,
            dpF(2.2f),
            dpF(2.2f),
            railRimPaint
        )

        // Recessed central channel.
        railInnerRect.set(
            centerX - innerHalfWidth,
            top + dpF(2f),
            centerX + innerHalfWidth,
            bottom - dpF(2f)
        )

        canvas.drawRoundRect(
            railInnerRect,
            dpF(1.2f),
            dpF(1.2f),
            railGroovePaint
        )

        // Narrow internal groove.
        val seamX = centerX +
            outerDirection * dpF(0.7f)

        railGrooveRect.set(
            seamX - dpF(0.45f),
            top + dpF(5f),
            seamX + dpF(0.45f),
            bottom - dpF(5f)
        )

        canvas.drawRoundRect(
            railGrooveRect,
            dpF(0.4f),
            dpF(0.4f),
            railSeamPaint
        )

        // Outer metallic reflection.
        val highlightX = centerX -
            outerDirection * dpF(2.8f)

        canvas.drawLine(
            highlightX,
            top + dpF(4f),
            highlightX,
            bottom - dpF(4f),
            railHighlightPaint
        )

        // Subtle opposite-side reflection.
        val reflectionX = centerX +
            outerDirection * dpF(2.8f)

        canvas.drawLine(
            reflectionX,
            top + dpF(5f),
            reflectionX,
            bottom - dpF(5f),
            railReflectionPaint
        )
    }

    private fun drawHorizontalRail(
        canvas: Canvas,
        centerY: Float,
        left: Float,
        right: Float,
        outerDirection: Float
    ) {
        if (right <= left) return

        val halfHeight = dpF(4.2f)
        val innerHalfHeight = dpF(2.6f)

        railOuterRect.set(
            left,
            centerY - halfHeight,
            right,
            centerY + halfHeight
        )

        // Main structural rail.
        canvas.drawRoundRect(
            railOuterRect,
            dpF(2.2f),
            dpF(2.2f),
            railBodyPaint
        )

        // Metallic outer rim.
        canvas.drawRoundRect(
            railOuterRect,
            dpF(2.2f),
            dpF(2.2f),
            railRimPaint
        )

        // Recessed central channel.
        railInnerRect.set(
            left + dpF(2f),
            centerY - innerHalfHeight,
            right - dpF(2f),
            centerY + innerHalfHeight
        )

        canvas.drawRoundRect(
            railInnerRect,
            dpF(1.2f),
            dpF(1.2f),
            railGroovePaint
        )

        // Narrow internal groove.
        val seamY = centerY +
            outerDirection * dpF(0.7f)

        railGrooveRect.set(
            left + dpF(5f),
            seamY - dpF(0.45f),
            right - dpF(5f),
            seamY + dpF(0.45f)
        )

        canvas.drawRoundRect(
            railGrooveRect,
            dpF(0.4f),
            dpF(0.4f),
            railSeamPaint
        )

        // Metallic reflection.
        val highlightY = centerY -
            outerDirection * dpF(2.8f)

        canvas.drawLine(
            left + dpF(4f),
            highlightY,
            right - dpF(4f),
            highlightY,
            railHighlightPaint
        )

        val reflectionY = centerY +
            outerDirection * dpF(2.8f)

        canvas.drawLine(
            left + dpF(5f),
            reflectionY,
            right - dpF(5f),
            reflectionY,
            railReflectionPaint
        )
    }

    // ---------------------------------------------------------
    // Port creation
    // ---------------------------------------------------------

    fun addPort(
        side: PortSide,
        kind: PortKind = PortKind.CONTEXT,
        state: PortState = PortState.IDLE,
        label: String = "",
        shape: PortShape = PortShape.USB_C,
        enabled: Boolean = true
    ): PortView? {

        val sideCount = ports.values.count {
            it.view.portSide == side
        }

        val maxPorts =
            config.maxPortsPerSide.coerceAtLeast(0)

        if (sideCount >= maxPorts) return null

        val id = createPortId(side, sideCount)

        ports[id]?.let {
            return it.view
        }

        val port = PortView(context).apply {
            portId = id
            portSide = side
            portKind = kind
            this.shape = shape
            this.state = state
            isEnabled = enabled

            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        port.onPortClick = { selected ->
            if (
                selected.isEnabled &&
                selected.state != PortState.DISABLED
            ) {
                onPortSelected?.invoke(
                    PortSpec(
                        id = selected.portId,
                        ownerId = ownerId,
                        side = selected.portSide,
                        index = getSideIndex(selected),
                        kind = selected.portKind,
                        state = selected.state,
                        shape = selected.shape,
                        label = label,
                        enabled = selected.isEnabled
                    )
                )
            }
        }

        ports[id] = PortEntry(
            view = port,
            label = label
        )

        addView(port)

        requestLayout()
        invalidate()

        return port
    }

    fun createDefaultPorts() {
        if (ports.isNotEmpty()) return

        PortSide.values().forEach { side ->
            addPort(side)
        }
    }

    // ---------------------------------------------------------
    // Port removal
    // ---------------------------------------------------------

    fun removePort(portId: String) {
        val entry = ports.remove(portId) ?: return

        entry.view.onPortClick = null

        removeView(entry.view)

        refreshIds()

        requestLayout()
        invalidate()
    }

    fun clearPorts() {
        ports.values.forEach { entry ->
            entry.view.onPortClick = null
        }

        ports.clear()
        removeAllViews()

        requestLayout()
        invalidate()
    }

    // ---------------------------------------------------------
    // Port access
    // ---------------------------------------------------------

    fun getPort(portId: String): PortView? =
        ports[portId]?.view

    fun getPorts(side: PortSide): List<PortView> =
        ports.values
            .filter { it.view.portSide == side }
            .map { it.view }

    fun getAllPorts(): List<PortView> =
        ports.values.map { it.view }

    fun getPortSpecs(): List<PortSpec> {
        val indexes = mutableMapOf<PortSide, Int>()

        return ports.values.map { entry ->
            val port = entry.view
            val index = indexes[port.portSide] ?: 0

            indexes[port.portSide] = index + 1

            PortSpec(
                id = port.portId,
                ownerId = ownerId,
                side = port.portSide,
                index = index,
                kind = port.portKind,
                state = port.state,
                shape = port.shape,
                label = entry.label,
                enabled = port.isEnabled
            )
        }
    }

    // ---------------------------------------------------------
    // Port state and appearance
    // ---------------------------------------------------------

    fun setPortState(
        portId: String,
        state: PortState
    ) {
        ports[portId]?.view?.state = state
    }

    fun setPortKind(
        portId: String,
        kind: PortKind
    ) {
        ports[portId]?.view?.portKind = kind
    }

    fun setPortShape(
        portId: String,
        shape: PortShape
    ) {
        ports[portId]?.view?.shape = shape

        requestLayout()
        invalidate()
    }

    fun setPortEnabled(
        portId: String,
        enabled: Boolean
    ) {
        ports[portId]?.view?.isEnabled = enabled
    }

    fun setAllPortsState(state: PortState) {
        ports.values.forEach {
            it.view.state = state
        }
    }

    fun setAllPortsEnabled(enabled: Boolean) {
        ports.values.forEach {
            it.view.isEnabled = enabled
        }
    }

    // ---------------------------------------------------------
    // Port IDs
    // ---------------------------------------------------------

    private fun refreshIds() {
        if (ports.isEmpty()) return

        val entries = ports.values.toList()

        val rebuilt =
            LinkedHashMap<String, PortEntry>()

        val indexes = mutableMapOf<PortSide, Int>()

        entries.forEach { entry ->
            val port = entry.view

            val index = indexes[port.portSide] ?: 0
            indexes[port.portSide] = index + 1

            val newId = createPortId(
                port.portSide,
                index
            )

            port.portId = newId
            rebuilt[newId] = entry
        }

        ports.clear()
        ports.putAll(rebuilt)

        requestLayout()
        invalidate()
    }

    private fun createPortId(
        side: PortSide,
        index: Int
    ): String {
        return "$ownerId:${side.name.lowercase()}:$index"
    }

    private fun getSideIndex(port: PortView): Int {
        var index = 0

        for (entry in ports.values) {
            val current = entry.view

            if (current.portSide != port.portSide) {
                continue
            }

            if (current === port) return index

            index++
        }

        return -1
    }

    // ---------------------------------------------------------
    // Measurement
    // ---------------------------------------------------------

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        super.onMeasure(
            widthMeasureSpec,
            heightMeasureSpec
        )

        ports.values.forEach { entry ->
            val port = entry.view
            if (port.measuredWidth <= 0 || port.measuredHeight <= 0) {
                measureChild(port, widthMeasureSpec, heightMeasureSpec)
            }
        }
    }

    // ---------------------------------------------------------
    // Layout
    // ---------------------------------------------------------

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        val content = getContentBounds(
            left = left,
            top = top,
            right = right,
            bottom = bottom
        )

        PortSide.values().forEach { side ->
            layoutSide(
                side = side,
                content = content
            )
        }
    }

    private fun getSocketCenter(
        port: PortView
    ): PointF {
        return port.getSocketCenter()
    }

    private fun calculatePortPosition(
        side: PortSide,
        port: PortView,
        content: ContentBounds,
        edgeCenter: Int
    ): Pair<Int, Int> {
        val socketCenter = getSocketCenter(port)

        val targetSocketX: Float
        val targetSocketY: Float

        when (side) {
            PortSide.LEFT -> {
                targetSocketX = leftEdgeX(content)
                targetSocketY = edgeCenter.toFloat()
            }

            PortSide.RIGHT -> {
                targetSocketX = rightEdgeX(content)
                targetSocketY = edgeCenter.toFloat()
            }

            PortSide.TOP -> {
                targetSocketX = edgeCenter.toFloat()
                targetSocketY = topEdgeY(content)
            }

            PortSide.BOTTOM -> {
                targetSocketX = edgeCenter.toFloat()
                targetSocketY = bottomEdgeY(content)
            }
        }

        val portX = (targetSocketX - socketCenter.x).roundToInt()
        val portY = (targetSocketY - socketCenter.y).roundToInt()

        return Pair(portX, portY)
    }

    private fun layoutSide(
        side: PortSide,
        content: ContentBounds
    ) {
        val sidePorts = ports.values
            .map { it.view }
            .filter { it.portSide == side }

        if (sidePorts.isEmpty()) return

        val horizontalEdge =
            side == PortSide.LEFT ||
                side == PortSide.RIGHT

        val availableSpace =
            if (horizontalEdge) content.height else content.width

        if (availableSpace <= 0) {
            sidePorts.forEach {
                it.layout(0, 0, 0, 0)
            }
            return
        }

        val requestedSpacing = dp(
            (
                if (horizontalEdge) {
                    config.verticalSpacingDp
                } else {
                    config.horizontalSpacingDp
                }
            ).coerceAtLeast(0f)
        )

        val portExtents = sidePorts.map { port ->
            if (horizontalEdge) {
                port.measuredHeight
            } else {
                port.measuredWidth
            }
        }

        val largestExtent =
            portExtents.maxOrNull() ?: 0

        val minimumSpacing =
            largestExtent + dp(8f)

        val effectiveSpacing = max(
            requestedSpacing,
            minimumSpacing
        )

        val centers = calculateCenters(
            extents = portExtents,
            availableSpace = availableSpace,
            requestedSpacing = effectiveSpacing
        )

        val edgeOrigin =
            if (horizontalEdge) content.top else content.left

        sidePorts.forEachIndexed { index, port ->
            val portWidth = port.measuredWidth
            val portHeight = port.measuredHeight
            val edgeCenter = edgeOrigin + centers[index]

            val (portX, portY) = calculatePortPosition(
                side = side,
                port = port,
                content = content,
                edgeCenter = edgeCenter
            )

            port.layout(
                portX,
                portY,
                portX + portWidth,
                portY + portHeight
            )
        }
    }

    // ---------------------------------------------------------
    // Port distribution
    // ---------------------------------------------------------

    private fun calculateCenters(
        extents: List<Int>,
        availableSpace: Int,
        requestedSpacing: Int
    ): List<Int> {

        val count = extents.size

        if (count == 0) return emptyList()

        if (count == 1) {
            return listOf(availableSpace / 2)
        }

        val totalExtent = extents.sum()

        val minimumRequired =
            totalExtent +
                requestedSpacing * (count - 1)

        if (minimumRequired <= availableSpace) {
            val totalSpacing =
                requestedSpacing * (count - 1)

            val totalLength =
                totalExtent + totalSpacing

            var cursor =
                (availableSpace - totalLength) / 2f

            return extents.map { extent ->
                val center = cursor + extent / 2f
                cursor += extent + requestedSpacing
                center.toInt()
            }
        }

        /*
         * When the requested spacing does not fit,
         * distribute the centers over the available
         * edge while preserving the first and last
         * connector center offsets.
         */

        val halfFirst = extents.first() / 2f
        val halfLast = extents.last() / 2f

        val usableSpace = (
            availableSpace - halfFirst - halfLast
        ).coerceAtLeast(0f)

        return List(count) { index ->
            val fraction =
                index.toFloat() / (count - 1).toFloat()

            (
                halfFirst + usableSpace * fraction
            ).toInt()
        }
    }
}
