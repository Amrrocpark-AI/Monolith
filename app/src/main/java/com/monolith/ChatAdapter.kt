package com.monolith

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tables.TableTheme

/**
 * Monolith Conversation Graph UI
 *
 * This file owns message presentation, relationship visualization,
 * tool cards and message actions.
 *
 * Relationship/tool data is supplied through optional providers.
 * The adapter does not invent database records.
 */

enum class RelationKind(
    val label: String,
    val color: Int
) {
    CONTEXT("سياق", Color.rgb(0, 212, 255)),
    REFERENCE("مرجع", Color.rgb(168, 85, 247)),
    CAUSAL("سبب ونتيجة", Color.rgb(255, 170, 60)),
    DERIVATION("اشتقاق", Color.rgb(60, 220, 160)),
    CONTRAST("تعارض", Color.rgb(255, 90, 120)),
    DEPENDENCY("اعتماد", Color.rgb(120, 150, 255)),
    CONTINUATION("استمرار", Color.rgb(220, 220, 240)),
    CUSTOM("مخصص", Color.rgb(180, 180, 190))
}

data class MessageRelation(
    val id: String,
    val sourceMessageId: String,
    val targetMessageId: String,
    val kind: RelationKind,
    val label: String = "",
    val direction: String = "OUTGOING"
)

enum class ToolExecutionState {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class ToolPresentation(
    val id: String,
    val name: String,
    val description: String = "",
    val state: ToolExecutionState,
    val result: String = "",
    val durationMs: Long? = null
)

class ChatAdapter(
    context: Context,
    private val onEditClick: (ChatMessage) -> Unit,
    private val onReactionClick: (ChatMessage) -> Unit,
    private val onSpeakClick: (String) -> Unit,
    private val onPinClick: (ChatMessage) -> Unit,
    private val themeColorHex: String,
    private val onRegenerateClick: ((ChatMessage) -> Unit)? = null,

    // Optional future graph integration.
    private val relationProvider:
        ((ChatMessage) -> List<MessageRelation>)? = null,

    private val toolProvider:
        ((ChatMessage) -> List<ToolPresentation>)? = null,

    private val onRelationClick:
        ((MessageRelation) -> Unit)? = null,

    private val onToolClick:
        ((ToolPresentation) -> Unit)? = null

) : ListAdapter<ChatMessage, ChatAdapter.ChatViewHolder>(DIFF_CALLBACK) {

    private val appContext = context.applicationContext

    private val userColor = parseColor(
        themeColorHex,
        Color.rgb(15, 75, 105)
    )

    private val aiColor = Color.rgb(19, 22, 48)
    private val surfaceColor = Color.rgb(11, 15, 31)
    private val panelColor = Color.rgb(18, 23, 43)
    private val borderColor = Color.rgb(51, 65, 100)
    private val textColor = Color.rgb(235, 241, 255)
    private val mutedColor = Color.rgb(145, 160, 190)
    private val accentColor = Color.rgb(0, 212, 255)
    private val purpleColor = Color.rgb(168, 85, 247)

    private val markwon: Markwon =
        Markwon.builder(appContext)
            .usePlugin(
                TablePlugin.create { builder: TableTheme.Builder ->
                    builder
                        .tableCellPadding(16)
                        .tableBorderWidth(2)
                        .tableBorderColor(Color.rgb(57, 67, 106))
                        .tableHeaderRowBackgroundColor(
                            Color.rgb(37, 36, 71)
                        )
                        .tableOddRowBackgroundColor(
                            Color.rgb(28, 29, 58)
                        )
                        .tableEvenRowBackgroundColor(
                            Color.rgb(23, 24, 47)
                        )
                }
            )
            .build()

    companion object {
        private const val TYPE_USER = 1
        private const val TYPE_AI = 2

        private const val MENU_REGENERATE = 1
        private const val MENU_REACTION = 2
        private const val MENU_PIN = 3
        private const val MENU_SHARE = 4
        private const val MENU_EDIT = 5

        private const val MAX_VISIBLE_PORTS = 4

        private val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<ChatMessage>() {

                override fun areItemsTheSame(
                    oldItem: ChatMessage,
                    newItem: ChatMessage
                ): Boolean {
                    return oldItem.id == newItem.id
                }

                override fun areContentsTheSame(
                    oldItem: ChatMessage,
                    newItem: ChatMessage
                ): Boolean {
                    return oldItem == newItem
                }
            }

        private fun parseColor(
            value: String,
            fallback: Int
        ): Int {
            return try {
                Color.parseColor(value)
            } catch (_: Exception) {
                fallback
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).role == MessageRole.USER) {
            TYPE_USER
        } else {
            TYPE_AI
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ChatViewHolder {
        return ChatViewHolder(
            createItemView(parent.context, viewType)
        )
    }

    private fun createItemView(
        context: Context,
        viewType: Int
    ): LinearLayout {

        val isUser = viewType == TYPE_USER

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (isUser) Gravity.END else Gravity.START

            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            setPadding(
                dp(context, 10),
                dp(context, 8),
                dp(context, 10),
                dp(context, 8)
            )

            // Identity header.
            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(context, 5)
                }
            }

            val identityDot = View(context).apply {
                background = circleDrawable(
                    if (isUser) accentColor else purpleColor
                )
                layoutParams = LinearLayout.LayoutParams(
                    dp(context, 7),
                    dp(context, 7)
                )
            }

            val identity = TextView(context).apply {
                text = if (isUser) "YOU" else "MONOLITH"
                textSize = 10f
                letterSpacing = 0.12f
                setTextColor(mutedColor)
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(context, 7), 0, 0, 0)
            }

            header.addView(identityDot)
            header.addView(identity)
            addView(header)

            // Message card.
            val card = CardView(context).apply {
                radius = dp(context, 19).toFloat()
                cardElevation = dp(context, 5).toFloat()
                useCompatPadding = false
                preventCornerOverlap = true

                setCardBackgroundColor(
                    if (isUser) userColor else aiColor
                )

                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (isUser) {
                        marginStart = dp(context, 32)
                    } else {
                        marginEnd = dp(context, 32)
                    }
                }

                val message = TextView(context).apply {
                    textSize = 15.5f
                    setTextColor(textColor)

                    setPadding(
                        dp(context, 17),
                        dp(context, 14),
                        dp(context, 17),
                        dp(context, 14)
                    )

                    maxWidth = (
                        context.resources.displayMetrics.widthPixels * 0.84f
                    ).toInt()

                    setTextIsSelectable(true)

                    movementMethod =
                        android.text.method.LinkMovementMethod.getInstance()

                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

                addView(message)
            }

            addView(card)

            // Attachments.
            val imageScroll = HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                visibility = View.GONE

                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(context, 8)
                }
            }

            val imageContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = if (isUser) Gravity.END else Gravity.START
            }

            imageScroll.addView(imageContainer)
            addView(imageScroll)

            // Graph node panel.
            val graphPanel = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE

                background = roundedBackground(
                    panelColor,
                    dp(context, 14).toFloat(),
                    borderColor,
                    dp(context, 1)
                )

                setPadding(
                    dp(context, 10),
                    dp(context, 8),
                    dp(context, 10),
                    dp(context, 8)
                )

                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(context, 7)
                }
            }

            addView(graphPanel)

            // Tool execution cards.
            val toolsContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE

                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(context, 7)
                }
            }

            addView(toolsContainer)

            // Action bar.
            val actions = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = if (isUser) Gravity.END else Gravity.START
                visibility = View.GONE

                setPadding(
                    dp(context, 4),
                    dp(context, 4),
                    dp(context, 4),
                    dp(context, 4)
                )

                background = roundedBackground(
                    surfaceColor,
                    dp(context, 22).toFloat(),
                    borderColor,
                    dp(context, 1)
                )

                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(context, 7)
                }
            }

            actions.addView(
                createActionButton(
                    context,
                    R.drawable.ic_copy,
                    "نسخ الرسالة"
                )
            )

            actions.addView(
                createActionButton(
                    context,
                    R.drawable.ic_volume_up,
                    "قراءة الرسالة صوتياً"
                )
            )

            actions.addView(
                createActionButton(
                    context,
                    R.drawable.ic_more_vert,
                    "خيارات الرسالة"
                )
            )

            addView(actions)

            // Relationship legend.
            val legend = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                visibility = View.GONE

                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(context, 5)
                }
            }

            addView(legend)
        }
    }

    private fun createActionButton(
        context: Context,
        drawable: Int,
        description: String
    ): ImageView {
        return ImageView(context).apply {
            setImageResource(drawable)
            setColorFilter(textColor)
            contentDescription = description
            scaleType = ImageView.ScaleType.CENTER_INSIDE

            layoutParams = LinearLayout.LayoutParams(
                dp(context, 42),
                dp(context, 40)
            )

            setPadding(
                dp(context, 10),
                dp(context, 9),
                dp(context, 10),
                dp(context, 9)
            )

            isClickable = true
            isFocusable = true

            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(
                    Color.parseColor("#334A78FF")
                ),
                null,
                null
            )
        }
    }

    inner class ChatViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        private val root = itemView as LinearLayout

        private val header =
            root.getChildAt(0) as LinearLayout

        private val card =
            root.getChildAt(1) as CardView

        private val messageText =
            card.getChildAt(0) as TextView

        private val imageScroll =
            root.getChildAt(2) as HorizontalScrollView

        private val imageContainer =
            imageScroll.getChildAt(0) as LinearLayout

        private val graphPanel =
            root.getChildAt(3) as LinearLayout

        private val toolsContainer =
            root.getChildAt(4) as LinearLayout

        private val actionBar =
            root.getChildAt(5) as LinearLayout

        private val copyButton =
            actionBar.getChildAt(0) as ImageView

        private val speakButton =
            actionBar.getChildAt(1) as ImageView

        private val moreButton =
            actionBar.getChildAt(2) as ImageView

        private val legend =
            root.getChildAt(6) as LinearLayout

        private var currentMessage: ChatMessage? = null
        private var renderedContent: String? = null

        init {
            copyButton.setOnClickListener {
                currentMessage?.let { copyMessage(it) }
            }

            speakButton.setOnClickListener {
                currentMessage?.let {
                    onSpeakClick(it.content)
                }
            }

            moreButton.setOnClickListener {
                showMenu()
            }
        }

        fun bind(msg: ChatMessage) {
            currentMessage = msg

            val isUser = msg.role == MessageRole.USER

            root.gravity = if (isUser) Gravity.END else Gravity.START

            val dot = header.getChildAt(0)
            val identity = header.getChildAt(1) as TextView

            dot.background = circleDrawable(
                if (isUser) accentColor else purpleColor
            )

            identity.text = if (isUser) "YOU" else "MONOLITH"

            card.setCardBackgroundColor(
                if (isUser) userColor else aiColor
            )

            // Message rendering.
            if (renderedContent != msg.content) {
                if (msg.content.isEmpty()) {
                    messageText.text = ""
                } else {
                    markwon.setMarkdown(messageText, msg.content)
                }

                renderedContent = msg.content
            }

            card.visibility = if (msg.content.isNotEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }

            bindImages(msg)
            bindRelations(msg)
            bindTools(msg)

            val canShowActions =
                msg.content.isNotBlank() &&
                msg.content != "جاري التفكير..." &&
                !msg.isThinking

            actionBar.visibility =
                if (canShowActions) View.VISIBLE else View.GONE

            val hasState =
                msg.isPinned ||
                msg.reactionType == "LIKE" ||
                msg.reactionType == "DISLIKE"

            moreButton.setColorFilter(
                if (hasState) accentColor else textColor
            )

            copyButton.isEnabled = msg.content.isNotEmpty()
            speakButton.isEnabled = msg.content.isNotEmpty()

            copyButton.alpha =
                if (copyButton.isEnabled) 1f else 0.4f

            speakButton.alpha =
                if (speakButton.isEnabled) 1f else 0.4f
        }

        private fun bindRelations(msg: ChatMessage) {
            graphPanel.removeAllViews()
            legend.removeAllViews()

            val relations = try {
                relationProvider?.invoke(msg).orEmpty()
            } catch (_: Exception) {
                emptyList()
            }

            val visibleRelations =
                relations.take(MAX_VISIBLE_PORTS)

            if (visibleRelations.isEmpty()) {
                graphPanel.visibility = View.GONE
                legend.visibility = View.GONE
                return
            }

            graphPanel.visibility = View.VISIBLE

            val title = TextView(root.context).apply {
                text = "MESSAGE CONNECTIONS"
                textSize = 9f
                letterSpacing = 0.13f
                setTextColor(mutedColor)
                setTypeface(null, Typeface.BOLD)
            }

            graphPanel.addView(title)

            visibleRelations.forEachIndexed { index, relation ->
                graphPanel.addView(
                    createRelationRow(
                        root.context,
                        index + 1,
                        relation
                    )
                )
            }

            // Show legend only for kinds actually present.
            val kinds = visibleRelations
                .map { it.kind }
                .distinct()

            kinds.forEach { kind ->
                val item = LinearLayout(root.context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(
                        dp(root.context, 5),
                        dp(root.context, 3),
                        dp(root.context, 8),
                        dp(root.context, 3)
                    )
                }

                val dot = View(root.context).apply {
                    background = circleDrawable(kind.color)
                    layoutParams = LinearLayout.LayoutParams(
                        dp(root.context, 6),
                        dp(root.context, 6)
                    )
                }

                val label = TextView(root.context).apply {
                    text = kind.label
                    textSize = 9f
                    setTextColor(mutedColor)
                    setPadding(dp(root.context, 4), 0, 0, 0)
                }

                item.addView(dot)
                item.addView(label)
                legend.addView(item)
            }

            legend.visibility =
                if (kinds.isNotEmpty()) View.VISIBLE else View.GONE
        }

        private fun createRelationRow(
            context: Context,
            portNumber: Int,
            relation: MessageRelation
        ): View {

            return LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL

                setPadding(
                    0,
                    dp(context, 7),
                    0,
                    dp(context, 3)
                )

                val port = TextView(context).apply {
                    text = "P$portNumber"
                    textSize = 9f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(relation.kind.color)

                    background = roundedBackground(
                        Color.argb(28, 255, 255, 255),
                        dp(context, 6).toFloat(),
                        relation.kind.color,
                        dp(context, 1)
                    )

                    setPadding(
                        dp(context, 7),
                        dp(context, 4),
                        dp(context, 7),
                        dp(context, 4)
                    )
                }

                val line = View(context).apply {
                    setBackgroundColor(relation.kind.color)
                    layoutParams = LinearLayout.LayoutParams(
                        dp(context, 22),
                        dp(context, 2)
                    ).apply {
                        marginStart = dp(context, 5)
                        marginEnd = dp(context, 5)
                    }
                }

                val details = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }

                val relationLabel = TextView(context).apply {
                    text = relation.label.ifBlank {
                        relation.kind.label
                    }
                    textSize = 11f
                    setTextColor(textColor)
                    maxLines = 1
                }

                val target = TextView(context).apply {
                    text = relation.targetMessageId.take(12)
                    textSize = 9f
                    setTextColor(mutedColor)
                    maxLines = 1
                }

                details.addView(relationLabel)
                details.addView(target)

                val direction = TextView(context).apply {
                    text = if (
                        relation.direction.equals(
                            "INCOMING",
                            ignoreCase = true
                        )
                    ) "IN" else "OUT"

                    textSize = 8f
                    setTextColor(relation.kind.color)
                    setTypeface(null, Typeface.BOLD)
                }

                addView(port)
                addView(line)
                addView(details)
                addView(direction)

                isClickable = onRelationClick != null
                isFocusable = onRelationClick != null

                setOnClickListener {
                    onRelationClick?.invoke(relation)
                }
            }
        }

        private fun bindTools(msg: ChatMessage) {
            toolsContainer.removeAllViews()

            val tools = try {
                toolProvider?.invoke(msg).orEmpty()
            } catch (_: Exception) {
                emptyList()
            }

            if (tools.isEmpty()) {
                toolsContainer.visibility = View.GONE
                return
            }

            toolsContainer.visibility = View.VISIBLE

            tools.forEach { tool ->
                toolsContainer.addView(
                    createToolCard(root.context, tool)
                )
            }
        }

        private fun createToolCard(
            context: Context,
            tool: ToolPresentation
        ): View {

            val stateColor = when (tool.state) {
                ToolExecutionState.QUEUED ->
                    Color.rgb(150, 160, 185)

                ToolExecutionState.RUNNING ->
                    accentColor

                ToolExecutionState.COMPLETED ->
                    Color.rgb(60, 220, 160)

                ToolExecutionState.FAILED ->
                    Color.rgb(255, 90, 110)

                ToolExecutionState.CANCELLED ->
                    Color.rgb(180, 150, 150)
            }

            val stateText = when (tool.state) {
                ToolExecutionState.QUEUED -> "QUEUED"
                ToolExecutionState.RUNNING -> "RUNNING"
                ToolExecutionState.COMPLETED -> "COMPLETED"
                ToolExecutionState.FAILED -> "FAILED"
                ToolExecutionState.CANCELLED -> "CANCELLED"
            }

            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL

                background = roundedBackground(
                    Color.rgb(14, 20, 39),
                    dp(context, 13).toFloat(),
                    stateColor,
                    dp(context, 1)
                )

                setPadding(
                    dp(context, 12),
                    dp(context, 10),
                    dp(context, 12),
                    dp(context, 10)
                )

                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(context, 7)
                }

                val heading = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val name = TextView(context).apply {
                    text = tool.name
                    textSize = 12f
                    setTextColor(textColor)
                    setTypeface(null, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }

                val state = TextView(context).apply {
                    text = stateText
                    textSize = 9f
                    setTextColor(stateColor)
                    setTypeface(null, Typeface.BOLD)
                }

                heading.addView(name)
                heading.addView(state)
                addView(heading)

                if (tool.description.isNotBlank()) {
                    addView(
                        TextView(context).apply {
                            text = tool.description
                            textSize = 10f
                            setTextColor(mutedColor)
                            setPadding(0, dp(context, 5), 0, 0)
                        }
                    )
                }

                if (tool.result.isNotBlank()) {
                    addView(
                        TextView(context).apply {
                            text = tool.result
                            textSize = 11f
                            setTextColor(textColor)
                            maxLines = 8
                            setPadding(
                                dp(context, 8),
                                dp(context, 7),
                                dp(context, 8),
                                dp(context, 7)
                            )

                            background = roundedBackground(
                                Color.rgb(8, 12, 25),
                                dp(context, 8).toFloat()
                            )

                            layoutParams =
                                LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    topMargin = dp(context, 7)
                                }
                        }
                    )
                }

                tool.durationMs?.let { duration ->
                    addView(
                        TextView(context).apply {
                            text = "${duration} ms"
                            textSize = 9f
                            setTextColor(mutedColor)
                            setPadding(0, dp(context, 5), 0, 0)
                        }
                    )
                }

                isClickable = onToolClick != null
                isFocusable = onToolClick != null

                setOnClickListener {
                    onToolClick?.invoke(tool)
                }
            }
        }

        private fun bindImages(msg: ChatMessage) {
            val uris = msg.attachmentUris.orEmpty()

            imageScroll.visibility =
                if (uris.isEmpty()) View.GONE else View.VISIBLE

            while (imageContainer.childCount > uris.size) {
                val index = imageContainer.childCount - 1
                val image =
                    imageContainer.getChildAt(index) as ImageView

                Glide.with(image).clear(image)
                imageContainer.removeViewAt(index)
            }

            uris.forEachIndexed { index, uri ->
                val image: ImageView =
                    if (index < imageContainer.childCount) {
                        imageContainer.getChildAt(index) as ImageView
                    } else {
                        ImageView(root.context).apply {
                            layoutParams =
                                LinearLayout.LayoutParams(
                                    dp(root.context, 148),
                                    dp(root.context, 148)
                                ).apply {
                                    marginEnd = dp(root.context, 8)
                                }

                            scaleType =
                                ImageView.ScaleType.CENTER_CROP

                            background = roundedBackground(
                                Color.TRANSPARENT,
                                dp(root.context, 13).toFloat()
                            )

                            clipToOutline = true
                            contentDescription = "Message attachment"

                            imageContainer.addView(this)
                        }
                    }

                Glide.with(image)
                    .load(uri)
                    .centerCrop()
                    .into(image)
            }
        }

        private fun copyMessage(msg: ChatMessage) {
            val clipboard = root.context.getSystemService(
                Context.CLIPBOARD_SERVICE
            ) as ClipboardManager

            clipboard.setPrimaryClip(
                ClipData.newPlainText("Monolith", msg.content)
            )

            Toast.makeText(
                root.context,
                "تم نسخ الرسالة",
                Toast.LENGTH_SHORT
            ).show()
        }

        private fun showMenu() {
            val msg = currentMessage ?: return

            val popup = PopupMenu(root.context, moreButton)

            if (
                msg.role == MessageRole.AI &&
                !msg.isThinking &&
                onRegenerateClick != null
            ) {
                popup.menu.add(
                    0,
                    MENU_REGENERATE,
                    0,
                    "إعادة توليد الرد"
                )
            }

            popup.menu.add(
                0,
                MENU_REACTION,
                1,
                "التفاعل مع الرسالة"
            )

            popup.menu.add(
                0,
                MENU_PIN,
                2,
                if (msg.isPinned) {
                    "إلغاء تثبيت الرسالة"
                } else {
                    "تثبيت الرسالة"
                }
            )

            popup.menu.add(
                0,
                MENU_SHARE,
                3,
                "مشاركة الرسالة"
            )

            popup.menu.add(
                0,
                MENU_EDIT,
                4,
                "تعديل الرسالة"
            )

            popup.setOnMenuItemClickListener { item ->
                val current = currentMessage
                    ?: return@setOnMenuItemClickListener false

                if (current.id != msg.id) {
                    return@setOnMenuItemClickListener false
                }

                when (item.itemId) {
                    MENU_REGENERATE ->
                        onRegenerateClick?.invoke(current)

                    MENU_REACTION ->
                        onReactionClick(current)

                    MENU_PIN ->
                        onPinClick(current)

                    MENU_SHARE ->
                        shareMessage(current)

                    MENU_EDIT ->
                        onEditClick(current)

                    else ->
                        return@setOnMenuItemClickListener false
                }

                true
            }

            popup.show()
        }

        private fun shareMessage(msg: ChatMessage) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, msg.content)
            }

            val chooser = Intent.createChooser(
                intent,
                "مشاركة الرسالة"
            )

            if (root.context !is android.app.Activity) {
                chooser.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }

            root.context.startActivity(chooser)
        }

        fun recycle() {
            currentMessage = null

            for (index in 0 until imageContainer.childCount) {
                val image =
                    imageContainer.getChildAt(index) as ImageView

                Glide.with(image).clear(image)
                image.setImageDrawable(null)
            }

            graphPanel.removeAllViews()
            toolsContainer.removeAllViews()
            legend.removeAllViews()

            renderedContent = null
            messageText.text = ""

            imageScroll.visibility = View.GONE
            graphPanel.visibility = View.GONE
            toolsContainer.visibility = View.GONE
            legend.visibility = View.GONE
            card.visibility = View.GONE
            actionBar.visibility = View.GONE

            moreButton.setColorFilter(textColor)
            copyButton.alpha = 1f
            speakButton.alpha = 1f
        }
    }

    override fun onBindViewHolder(
        holder: ChatViewHolder,
        position: Int
    ) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: ChatViewHolder) {
        holder.recycle()
        super.onViewRecycled(holder)
    }

    fun updateMessages(messages: List<ChatMessage>) {
        submitList(messages.toList())
    }

    private fun roundedBackground(
        color: Int,
        radius: Float,
        strokeColor: Int = Color.TRANSPARENT,
        strokeWidth: Int = 0
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius

            if (strokeWidth > 0) {
                setStroke(strokeWidth, strokeColor)
            }
        }
    }

    private fun circleDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun dp(
        context: Context,
        value: Int
    ): Int {
        return (
            value * context.resources.displayMetrics.density
        ).toInt()
    }
}
