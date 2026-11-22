package com.monolith

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
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
import java.util.LinkedHashMap

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

data class ExtractedCodeBlock(
    val index: Int,
    val language: String,
    val code: String,
    val isClosed: Boolean = true
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

    // ألوان الهوية من Gemini
    private val identitySelfColor = Color.rgb(248, 250, 252)
    private val identityAiColor = Color.rgb(239, 68, 68)

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

    // Session-scoped working copies/snippets keyed by messageId + block index (never mutates msg.content).
    private val workingCodeOverrides = mutableMapOf<String, String>()
    private val workingLanguageOverrides = mutableMapOf<String, String>()
    private val savedSnippets = LinkedHashMap<String, ExtractedCodeBlock>()

    companion object {
        private const val TYPE_USER = 1
        private const val TYPE_AI = 2

        private const val MENU_REGENERATE = 1
        private const val MENU_REACTION = 2
        private const val MENU_PIN = 3
        private const val MENU_SHARE = 4
        private const val MENU_EDIT = 5

        private const val MAX_VISIBLE_PORTS = 4

        private const val TAG_HEADER = "monolith_header"
        private const val TAG_CARD = "monolith_card"
        private const val TAG_IMAGE_SCROLL = "monolith_image_scroll"
        private const val TAG_GRAPH_PANEL = "monolith_graph_panel"
        private const val TAG_TOOLS_CONTAINER = "monolith_tools_container"
        private const val TAG_CODE_BLOCKS_CONTAINER = "monolith_code_blocks_container"
        private const val TAG_ACTIONS = "monolith_actions"
        private const val TAG_LEGEND = "monolith_legend"
        private const val TAG_BADGE = "monolith_badge"
        private const val TAG_IDENTITY_ICON = "monolith_identity_icon"
        private const val TAG_DYN_LAYER = "monolith_dyn_layer"
        private const val TAG_SHIELD = "monolith_contrast_shield"
        private const val TAG_TITLE = "monolith_title"

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

            // 1. Identity header + رقم الرسالة + أيقونة الهوية.
            val header = LinearLayout(context).apply {
                tag = TAG_HEADER
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(context, 5)
                }
            }

            val badge = TextView(context).apply {
                tag = TAG_BADGE
                text = "#0"
                textSize = 11f
                setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                setTextColor(accentColor)
                background = roundedBackground(
                    Color.argb(30, 56, 189, 248),
                    dp(context, 6).toFloat(),
                    Color.argb(76, 56, 189, 248),
                    dp(context, 1)
                )
                setPadding(dp(context, 7), dp(context, 2), dp(context, 7), dp(context, 2))
            }

            val identityIcon = ImageView(context).apply {
                tag = TAG_IDENTITY_ICON
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                layoutParams = LinearLayout.LayoutParams(
                    dp(context, 28),
                    dp(context, 28)
                ).apply {
                    marginStart = dp(context, 8)
                }
                background = roundedBackground(
                    Color.argb(8, 255, 255, 255),
                    dp(context, 8).toFloat(),
                    Color.argb(20, 255, 255, 255),
                    dp(context, 1)
                )
            }

            header.addView(badge)
            header.addView(identityIcon)
            addView(header)

            // 2. Message card with dynamic background + contrast shield + title + text.
            val card = CardView(context).apply {
                tag = TAG_CARD
                radius = dp(context, 16).toFloat()
                cardElevation = dp(context, 8).toFloat()
                useCompatPadding = false
                preventCornerOverlap = true
                setCardBackgroundColor(Color.TRANSPARENT)

                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (isUser) {
                        marginStart = dp(context, 8)
                    } else {
                        marginEnd = dp(context, 8)
                    }
                }
            }

            val cardInner = FrameLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val dynLayer = View(context).apply {
                tag = TAG_DYN_LAYER
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            val shield = View(context).apply {
                tag = TAG_SHIELD
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            val contentColumn = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    dp(context, 17),
                    dp(context, 14),
                    dp(context, 17),
                    dp(context, 14)
                )
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val title = TextView(context).apply {
                tag = TAG_TITLE
                textSize = 12.5f
                setTextColor(mutedColor)
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, dp(context, 6))
            }

            val message = TextView(context).apply {
                textSize = 15.5f
                setTextColor(textColor)
                setTextIsSelectable(true)
                movementMethod = android.text.method.LinkMovementMethod.getInstance()
                maxWidth = (
                    context.resources.displayMetrics.widthPixels * 0.84f
                ).toInt()
            }

            contentColumn.addView(title)
            contentColumn.addView(message)
            cardInner.addView(dynLayer)
            cardInner.addView(shield)
            cardInner.addView(contentColumn)
            card.addView(cardInner)
            addView(card)

            // 3. Attachments.
            val imageScroll = HorizontalScrollView(context).apply {
                tag = TAG_IMAGE_SCROLL
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

            // 4. Graph node panel.
            val graphPanel = LinearLayout(context).apply {
                tag = TAG_GRAPH_PANEL
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

            // 5. Tool execution cards.
            val toolsContainer = LinearLayout(context).apply {
                tag = TAG_TOOLS_CONTAINER
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

            // 6. Code blocks interactive zones & contextual toolbars (hidden until interaction).
            val codeBlocksContainer = LinearLayout(context).apply {
                tag = TAG_CODE_BLOCKS_CONTAINER
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE

                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(context, 7)
                }
            }

            addView(codeBlocksContainer)

            // 7. Action bar.
            val actions = LinearLayout(context).apply {
                tag = TAG_ACTIONS
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

            // 8. Relationship legend.
            val legend = LinearLayout(context).apply {
                tag = TAG_LEGEND
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

    // =====================================================================
    // تصميم Gemini: خلفية البطاقة (تدرج + حافة جانبية + ظل)
    // =====================================================================
    private fun applyCardBackground(card: CardView, isUser: Boolean) {
        val baseColor = if (isUser) userColor else aiColor
        val darkColor = shade(baseColor, 0.72f)
        val sideColor = if (isUser) identitySelfColor else identityAiColor

        val gradient = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(baseColor, darkColor)
        ).apply {
            cornerRadius = dp(appContext, 16).toFloat()
            setStroke(dp(appContext, 1), borderColor)
        }

        val sideStripe = GradientDrawable().apply {
            setColor(sideColor)
            cornerRadius = dp(appContext, 16).toFloat()
        }

        val layer = LayerDrawable(arrayOf(gradient, sideStripe)).apply {
            setLayerInset(1, dp(appContext, 4), 0, 0, 0)
        }

        card.background = layer
        card.setCardBackgroundColor(Color.TRANSPARENT)
        card.cardElevation = dp(appContext, 8).toFloat()
    }

    // =====================================================================
    // تصميم Gemini: طبقة الخلفية الديناميكية (تدرج ثابت)
    // =====================================================================
    private fun applyDynBackground(dynView: View, isUser: Boolean) {
        val top = if (isUser) userColor else aiColor
        val bottom = shade(top, 0.55f)
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(top, bottom)
        ).apply {
            cornerRadius = dp(appContext, 16).toFloat()
        }
        dynView.background = gradient
    }

    // =====================================================================
    // تصميم Gemini: ظل اللون لخلفية التدرج
    // =====================================================================
    private fun shade(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    // =====================================================================
    // تصميم Gemini: أيقونة الهوية (بديل SVG)
    // =====================================================================
    private fun buildIdentityIcon(isUser: Boolean, color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(appContext, 1), Color.argb(80, 255, 255, 255))
        }
    }

    /**
     * Markdown fenced code block parser tracking fence character (` or ~),
     * opening fence length, matching closing fence (>= opening length),
     * optional language info string, full block content in order, and
     * unclosed fences through the end of the message without mutating input.
     */
    private fun extractCodeBlocks(content: String): List<ExtractedCodeBlock> {
        if (content.isEmpty() || (!content.contains("```") && !content.contains("~~~"))) {
            return emptyList()
        }

        val lines = content.split('\n')
        val result = mutableListOf<ExtractedCodeBlock>()

        var inFence = false
        var fenceChar = '`'
        var fenceLength = 0
        var currentLanguage = "Text"
        val currentLines = mutableListOf<String>()

        for (rawLine in lines) {
            val line = rawLine.trimEnd('\r')

            if (!inFence) {
                val openInfo = parseOpeningFence(line)
                if (openInfo != null) {
                    inFence = true
                    fenceChar = openInfo.first
                    fenceLength = openInfo.second
                    currentLanguage = openInfo.third.ifEmpty { "Text" }
                    currentLines.clear()
                }
            } else {
                if (isClosingFence(line, fenceChar, fenceLength)) {
                    result.add(
                        ExtractedCodeBlock(
                            index = result.size + 1,
                            language = currentLanguage,
                            code = currentLines.joinToString("\n"),
                            isClosed = true
                        )
                    )
                    inFence = false
                    fenceLength = 0
                    currentLanguage = "Text"
                    currentLines.clear()
                } else {
                    currentLines.add(line)
                }
            }
        }

        if (inFence) {
            result.add(
                ExtractedCodeBlock(
                    index = result.size + 1,
                    language = currentLanguage,
                    code = currentLines.joinToString("\n"),
                    isClosed = false
                )
            )
        }

        return result
    }

    private fun parseOpeningFence(line: String): Triple<Char, Int, String>? {
        var idx = 0
        var spaces = 0
        while (idx < line.length && spaces < 3 && (line[idx] == ' ' || line[idx] == '\t')) {
            idx++
            spaces++
        }
        if (idx >= line.length) return null

        val candidateChar = line[idx]
        if (candidateChar != '`' && candidateChar != '~') return null

        var count = 0
        while (idx < line.length && line[idx] == candidateChar) {
            count++
            idx++
        }
        if (count < 3) return null

        val infoString = line.substring(idx).trim()
        // CommonMark backtick info strings cannot contain backticks.
        if (candidateChar == '`' && infoString.contains('`')) return null

        val language = infoString.split(Regex("\\s+")).firstOrNull()?.trim().orEmpty()
        return Triple(candidateChar, count, language)
    }

    private fun isClosingFence(
        line: String,
        fenceChar: Char,
        minLength: Int
    ): Boolean {
        var idx = 0
        var spaces = 0
        while (idx < line.length && spaces < 3 && (line[idx] == ' ' || line[idx] == '\t')) {
            idx++
            spaces++
        }
        if (idx >= line.length || line[idx] != fenceChar) return false

        var count = 0
        while (idx < line.length && line[idx] == fenceChar) {
            count++
            idx++
        }
        if (count < minLength) return false

        while (idx < line.length) {
            if (line[idx] != ' ' && line[idx] != '\t') return false
            idx++
        }
        return true
    }

    private fun blockKey(messageId: String, blockIndex: Int): String {
        return "${messageId}::block::$blockIndex"
    }

    private fun resolveBlockCode(messageId: String, block: ExtractedCodeBlock): String {
        return workingCodeOverrides[blockKey(messageId, block.index)] ?: block.code
    }

    private fun resolveBlockLanguage(messageId: String, block: ExtractedCodeBlock): String {
        return workingLanguageOverrides[blockKey(messageId, block.index)] ?: block.language
    }

    private fun copyTextToClipboard(context: Context, label: String, text: String, toastMessage: String) {
        val clipboard = context.getSystemService(
            Context.CLIPBOARD_SERVICE
        ) as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText(label, text)
        )

        Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
    }

    private fun openInCodeWorkspace(
        context: Context,
        block: ExtractedCodeBlock,
        message: ChatMessage? = null,
        effectiveCode: String = block.code,
        effectiveLanguage: String = block.language
    ) {
        val title = buildString {
            append(effectiveLanguage)
            append(" #")
            append(block.index)
            message?.id?.take(6)?.let { shortId ->
                if (shortId.isNotBlank()) {
                    append(" • ")
                    append(shortId)
                }
            }
        }

        val intent = Intent(context, CodeWorkspaceActivity::class.java).apply {
            putExtra(CodeWorkspaceActivity.EXTRA_CODE, effectiveCode)
            putExtra(CodeWorkspaceActivity.EXTRA_LANGUAGE, effectiveLanguage)
            putExtra(CodeWorkspaceActivity.EXTRA_TITLE, title)
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        context.startActivity(intent)
    }

    inner class ChatViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        private val root = itemView as LinearLayout

        private val header =
            root.findViewWithTag<LinearLayout>(TAG_HEADER)
                ?: (root.getChildAt(0) as LinearLayout)

        private val badge =
            header.findViewWithTag<TextView>(TAG_BADGE)
                ?: (header.getChildAt(0) as TextView)

        private val identityIcon =
            header.findViewWithTag<ImageView>(TAG_IDENTITY_ICON)
                ?: (header.getChildAt(1) as ImageView)

        private val card =
            root.findViewWithTag<CardView>(TAG_CARD)
                ?: (root.getChildAt(1) as CardView)

        private val cardInner = card.getChildAt(0) as FrameLayout

        private val dynLayer =
            cardInner.findViewWithTag<View>(TAG_DYN_LAYER)
                ?: (cardInner.getChildAt(0) as View)

        private val shield =
            cardInner.findViewWithTag<View>(TAG_SHIELD)
                ?: (cardInner.getChildAt(1) as View)

        private val contentColumn = cardInner.getChildAt(2) as LinearLayout

        private val titleText =
            contentColumn.findViewWithTag<TextView>(TAG_TITLE)
                ?: (contentColumn.getChildAt(0) as TextView)

        private val messageText =
            contentColumn.getChildAt(1) as TextView

        private val imageScroll =
            root.findViewWithTag<HorizontalScrollView>(TAG_IMAGE_SCROLL)
                ?: (root.getChildAt(2) as HorizontalScrollView)

        private val imageContainer =
            imageScroll.getChildAt(0) as LinearLayout

        private val graphPanel =
            root.findViewWithTag<LinearLayout>(TAG_GRAPH_PANEL)
                ?: (root.getChildAt(3) as LinearLayout)

        private val toolsContainer =
            root.findViewWithTag<LinearLayout>(TAG_TOOLS_CONTAINER)
                ?: (root.getChildAt(4) as LinearLayout)

        private val codeBlocksContainer =
            root.findViewWithTag<LinearLayout>(TAG_CODE_BLOCKS_CONTAINER)

        private val actionBar =
            root.findViewWithTag<LinearLayout>(TAG_ACTIONS)

        private val copyButton =
            actionBar.getChildAt(0) as ImageView

        private val speakButton =
            actionBar.getChildAt(1) as ImageView

        private val moreButton =
            actionBar.getChildAt(2) as ImageView

        private val legend =
            root.findViewWithTag<LinearLayout>(TAG_LEGEND)

        private var currentMessage: ChatMessage? = null
        private var renderedContent: String? = null
        private var expandedBlockIndex: Int? = null

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
            if (currentMessage?.id != msg.id) {
                expandedBlockIndex = null
            }
            currentMessage = msg

            val isUser = msg.role == MessageRole.USER

            root.gravity = if (isUser) Gravity.END else Gravity.START

            // تطبيق تصميم Gemini
            applyCardBackground(card, isUser)
            applyDynBackground(dynLayer, isUser)

            // شارة الرقم (مشتقة من msg.id لضمان ثبات الهوية)
            val badgeNumber = (msg.id.hashCode() and 0x7FFFFFFF) % 999 + 1
            badge.text = "#$badgeNumber"
            badge.setTextColor(if (isUser) identitySelfColor else identityAiColor)

            // أيقونة الهوية
            identityIcon.setImageDrawable(
                buildIdentityIcon(
                    isUser,
                    if (isUser) identitySelfColor else identityAiColor
                )
            )

            // عنوان البطاقة
            titleText.text = if (isUser) "YOU" else "MONOLITH"

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
            bindCodeBlocks(msg)

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

        private fun bindCodeBlocks(msg: ChatMessage) {
            codeBlocksContainer.removeAllViews()

            val blocks = if (msg.isThinking || msg.content.isBlank()) {
                expandedBlockIndex = null
                emptyList()
            } else {
                extractCodeBlocks(msg.content)
            }

            if (blocks.isEmpty()) {
                expandedBlockIndex = null
                codeBlocksContainer.visibility = View.GONE
                return
            }

            if (expandedBlockIndex != null && blocks.none { it.index == expandedBlockIndex }) {
                expandedBlockIndex = null
            }

            codeBlocksContainer.visibility = View.VISIBLE

            blocks.forEach { block ->
                val isExpanded = expandedBlockIndex == block.index
                codeBlocksContainer.addView(
                    createCodeBlockItem(
                        context = root.context,
                        msg = msg,
                        block = block,
                        isExpanded = isExpanded,
                        onToggleExpand = {
                            expandedBlockIndex = if (expandedBlockIndex == block.index) {
                                null
                            } else {
                                block.index
                            }
                            currentMessage?.let { bindCodeBlocks(it) }
                        },
                        onCloseTools = {
                            if (expandedBlockIndex == block.index) {
                                expandedBlockIndex = null
                                currentMessage?.let { bindCodeBlocks(it) }
                            }
                        }
                    )
                )
            }
        }

        private fun createCodeBlockItem(
            context: Context,
            msg: ChatMessage,
            block: ExtractedCodeBlock,
            isExpanded: Boolean,
            onToggleExpand: () -> Unit,
            onCloseTools: () -> Unit
        ): View {
            val effectiveLanguage = resolveBlockLanguage(msg.id, block)
            val effectiveCode = resolveBlockCode(msg.id, block)

            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(context, 6)
                }

                // Interaction trigger / compact preview zone (does not open Workspace directly).
                val triggerRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL

                    background = roundedBackground(
                        panelColor,
                        dp(context, 12).toFloat(),
                        if (isExpanded) accentColor else borderColor,
                        dp(context, 1)
                    )

                    setPadding(
                        dp(context, 12),
                        dp(context, 8),
                        dp(context, 12),
                        dp(context, 8)
                    )

                    val badge = TextView(context).apply {
                        text = effectiveLanguage
                        textSize = 10f
                        setTextColor(accentColor)
                        setTypeface(null, Typeface.BOLD)
                        background = roundedBackground(
                            Color.argb(30, 0, 212, 255),
                            dp(context, 6).toFloat()
                        )
                        setPadding(
                            dp(context, 7),
                            dp(context, 3),
                            dp(context, 7),
                            dp(context, 3)
                        )
                    }

                    val label = TextView(context).apply {
                        val lineCount = effectiveCode.split('\n').size
                        text = "كتلة كود #${block.index} ($lineCount سطر)"
                        textSize = 12f
                        setTextColor(textColor)
                        maxLines = 1
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                        ).apply {
                            marginStart = dp(context, 8)
                            marginEnd = dp(context, 8)
                        }
                    }

                    val toggleHint = TextView(context).apply {
                        text = if (isExpanded) "إخفاء الأدوات" else "أدوات الكود"
                        textSize = 11f
                        setTextColor(if (isExpanded) accentColor else mutedColor)
                        setTypeface(null, Typeface.BOLD)
                    }

                    addView(badge)
                    addView(label)
                    addView(toggleHint)

                    isClickable = true
                    isFocusable = true
                    setOnClickListener { onToggleExpand() }
                }

                addView(triggerRow)

                // Contextual toolbar remains strictly hidden until this specific block is interacted with.
                if (isExpanded) {
                    addView(
                        createCodeBlockToolsPanel(
                            context = context,
                            msg = msg,
                            block = block,
                            onCloseTools = onCloseTools
                        )
                    )
                }
            }
        }

        private fun createCodeBlockToolsPanel(
            context: Context,
            msg: ChatMessage,
            block: ExtractedCodeBlock,
            onCloseTools: () -> Unit
        ): View {
            var currentCode = resolveBlockCode(msg.id, block)
            var currentLanguage = resolveBlockLanguage(msg.id, block)
            var showLineNumbers = false
            var fontSizeSp = 12f
            var codeTextColor = textColor
            var codeBgColor = Color.rgb(8, 12, 25)
            var isExpandedHeight = false

            val panel = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = roundedBackground(
                    surfaceColor,
                    dp(context, 12).toFloat(),
                    accentColor,
                    dp(context, 1)
                )
                setPadding(
                    dp(context, 10),
                    dp(context, 10),
                    dp(context, 10),
                    dp(context, 10)
                )
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(context, 6)
                }
            }

            val codePreview = TextView(context).apply {
                textSize = fontSizeSp
                setTextColor(codeTextColor)
                typeface = Typeface.MONOSPACE
                setPadding(
                    dp(context, 10),
                    dp(context, 8),
                    dp(context, 10),
                    dp(context, 8)
                )
                background = roundedBackground(
                    codeBgColor,
                    dp(context, 8).toFloat(),
                    borderColor,
                    dp(context, 1)
                )
                maxLines = 8
                setTextIsSelectable(true)
            }

            fun refreshCodePreview() {
                codePreview.textSize = fontSizeSp
                codePreview.setTextColor(codeTextColor)
                codePreview.background = roundedBackground(
                    codeBgColor,
                    dp(context, 8).toFloat(),
                    borderColor,
                    dp(context, 1)
                )
                codePreview.maxLines = if (isExpandedHeight) Int.MAX_VALUE else 8
                codePreview.text = if (showLineNumbers) {
                    currentCode.split('\n').mapIndexed { idx, line ->
                        "${idx + 1} | $line"
                    }.joinToString("\n")
                } else {
                    currentCode
                }
            }

            refreshCodePreview()
            panel.addView(codePreview)

            val chipsScroll = HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(context, 8)
                }
            }

            val chipsRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            fun addToolChip(title: String, tint: Int = textColor, onClick: () -> Unit) {
                val chip = TextView(context).apply {
                    text = title
                    textSize = 11f
                    setTextColor(tint)
                    setTypeface(null, Typeface.BOLD)
                    background = roundedBackground(
                        panelColor,
                        dp(context, 8).toFloat(),
                        borderColor,
                        dp(context, 1)
                    )
                    setPadding(
                        dp(context, 10),
                        dp(context, 6),
                        dp(context, 10),
                        dp(context, 6)
                    )
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginEnd = dp(context, 6)
                    }
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { onClick() }
                }
                chipsRow.addView(chip)
            }

            // 1. نسخ الكود كاملاً
            addToolChip("1. نسخ الكود كاملاً", accentColor) {
                copyTextToClipboard(
                    context = context,
                    label = "MonolithCodeBlock",
                    text = currentCode,
                    toastMessage = "تم نسخ الكود كاملاً"
                )
            }

            // 2. تحرير الكود
            addToolChip("2. تحرير الكود") {
                showEditCodeBlockDialog(
                    context = context,
                    initialCode = currentCode,
                    onSave = { edited ->
                        currentCode = edited
                        workingCodeOverrides[blockKey(msg.id, block.index)] = edited
                        refreshCodePreview()
                        Toast.makeText(context, "تم تحديث نسخة الكود المحلية", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // 3. تحليل الكود
            addToolChip("3. تحليل الكود") {
                showCodeAnalysisDialog(context, currentLanguage, currentCode)
            }

            // 4. توسيع مساحة الكود
            addToolChip("4. توسيع مساحة الكود") {
                isExpandedHeight = !isExpandedHeight
                refreshCodePreview()
                Toast.makeText(
                    context,
                    if (isExpandedHeight) "تم توسيع مساحة عرض الكود" else "تم تقليص مساحة عرض الكود",
                    Toast.LENGTH_SHORT
                ).show()
            }

            // 5. تغيير لغة البرمجة
            addToolChip("5. تغيير لغة البرمجة") {
                showChangeLanguageDialog(
                    context = context,
                    currentLanguage = currentLanguage,
                    onLanguageSelected = { newLang ->
                        currentLanguage = newLang.ifBlank { "Text" }
                        workingLanguageOverrides[blockKey(msg.id, block.index)] = currentLanguage
                        currentMessage?.let { bindCodeBlocks(it) }
                        Toast.makeText(context, "اللغة: $currentLanguage", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // 6. تخصيص حجم الخط والألوان
            addToolChip("6. حجم الخط والألوان") {
                showAppearanceDialog(
                    context = context,
                    currentFontSizeSp = fontSizeSp,
                    currentBgColor = codeBgColor,
                    currentFgColor = codeTextColor,
                    onApply = { newSize, newBg, newFg ->
                        fontSizeSp = newSize
                        codeBgColor = newBg
                        codeTextColor = newFg
                        refreshCodePreview()
                    }
                )
            }

            // 7. إظهار أرقام الأسطر
            addToolChip("7. أرقام الأسطر") {
                showLineNumbers = !showLineNumbers
                refreshCodePreview()
                Toast.makeText(
                    context,
                    if (showLineNumbers) "تم إظهار أرقام الأسطر" else "تم إخفاء أرقام الأسطر",
                    Toast.LENGTH_SHORT
                ).show()
            }

            // 8. تحديد جزء من الكود ونسخه
            addToolChip("8. تحديد ونسخ جزء") {
                showSelectPartialCodeDialog(context, currentCode)
            }

            // 9. مقارنة نسختين من الكود
            addToolChip("9. مقارنة نسختين") {
                showCompareVersionsDialog(
                    context = context,
                    originalCode = block.code,
                    currentCode = currentCode
                )
            }

            // 10. حفظ الكود كمقتطف مستقل
            addToolChip("10. حفظ كمقتطف") {
                val key = blockKey(msg.id, block.index)
                savedSnippets[key] = ExtractedCodeBlock(
                    index = block.index,
                    language = currentLanguage,
                    code = currentCode,
                    isClosed = block.isClosed
                )
                Toast.makeText(context, "تم حفظ الكود كمقتطف مستقل", Toast.LENGTH_SHORT).show()
            }

            // AI 1. شرح الكود (غير مربوط بمحرك فعلي في المحول)
            addToolChip("AI: شرح الكود", purpleColor) {
                showUnboundAiActionNotice(
                    context = context,
                    actionName = "شرح الكود",
                    language = currentLanguage,
                    code = currentCode
                )
            }

            // AI 2. إصلاح الأخطاء (غير مربوط بمحرك فعلي في المحول)
            addToolChip("AI: إصلاح الأخطاء", purpleColor) {
                showUnboundAiActionNotice(
                    context = context,
                    actionName = "إصلاح الأخطاء",
                    language = currentLanguage,
                    code = currentCode
                )
            }

            // AI 3. تحسين الكود وإعادة هيكلته (غير مربوط بمحرك فعلي في المحول)
            addToolChip("AI: تحسين وإعادة هيكلة", purpleColor) {
                showUnboundAiActionNotice(
                    context = context,
                    actionName = "تحسين الكود وإعادة هيكلته",
                    language = currentLanguage,
                    code = currentCode
                )
            }

            // فتح في CodeWorkspaceActivity
            addToolChip("فتح في Workspace", accentColor) {
                openInCodeWorkspace(
                    context = context,
                    block = block,
                    message = msg,
                    effectiveCode = currentCode,
                    effectiveLanguage = currentLanguage
                )
            }

            // إغلاق الأدوات
            addToolChip("إغلاق", mutedColor) {
                onCloseTools()
            }

            chipsScroll.addView(chipsRow)
            panel.addView(chipsScroll)

            return panel
        }

        private fun showEditCodeBlockDialog(
            context: Context,
            initialCode: String,
            onSave: (String) -> Unit
        ) {
            val input = EditText(context).apply {
                setText(initialCode)
                setTextColor(Color.WHITE)
                typeface = Typeface.MONOSPACE
                textSize = 12f
                minLines = 6
                gravity = Gravity.TOP or Gravity.START
                setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12))
            }

            AlertDialog.Builder(context)
                .setTitle("تحرير كتلة الكود")
                .setView(input)
                .setPositiveButton("حفظ") { _, _ ->
                    onSave(input.text.toString())
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }

        private fun showCodeAnalysisDialog(
            context: Context,
            language: String,
            code: String
        ) {
            val lines = code.split('\n')
            val nonEmptyLines = lines.count { it.isNotBlank() }
            val charCount = code.length
            val summary = buildString {
                append("اللغة: ").append(language).append('\n')
                append("إجمالي الأسطر: ").append(lines.size).append('\n')
                append("الأسطر غير الفارغة: ").append(nonEmptyLines).append('\n')
                append("عدد الأحرف: ").append(charCount)
            }

            AlertDialog.Builder(context)
                .setTitle("تحليل الكود")
                .setMessage(summary)
                .setPositiveButton("حسناً", null)
                .show()
        }

        private fun showChangeLanguageDialog(
            context: Context,
            currentLanguage: String,
            onLanguageSelected: (String) -> Unit
        ) {
            val presets = arrayOf("Text", "kotlin", "java", "python", "xml", "json", "javascript", "sql")
            val input = EditText(context).apply {
                setText(currentLanguage)
                setTextColor(Color.WHITE)
                hint = "اسم اللغة (مثال: kotlin أو Text)"
                setSingleLine(true)
            }

            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 8))
                addView(input)
            }

            AlertDialog.Builder(context)
                .setTitle("تغيير لغة البرمجة")
                .setView(container)
                .setItems(presets) { _, which ->
                    onLanguageSelected(presets[which])
                }
                .setPositiveButton("تطبيق") { _, _ ->
                    onLanguageSelected(input.text.toString().trim())
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }

        private fun showAppearanceDialog(
            context: Context,
            currentFontSizeSp: Float,
            currentBgColor: Int,
            currentFgColor: Int,
            onApply: (Float, Int, Int) -> Unit
        ) {
            val sizes = arrayOf("11 sp", "12 sp", "14 sp", "16 sp")
            val sizeValues = floatArrayOf(11f, 12f, 14f, 16f)
            val themes = arrayOf(
                "داكن أزرق (افتراضي)" to (Color.rgb(8, 12, 25) to Color.rgb(235, 241, 255)),
                "داكن محايد" to (Color.rgb(18, 18, 20) to Color.rgb(240, 240, 240)),
                "تباين عالٍ" to (Color.BLACK to Color.WHITE)
            )

            var chosenSize = currentFontSizeSp
            var chosenBg = currentBgColor
            var chosenFg = currentFgColor

            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 8))
            }

            val sizeLabel = TextView(context).apply {
                text = "حجم الخط: ${chosenSize.toInt()} sp"
                setTextColor(textColor)
                setPadding(0, 0, 0, dp(context, 6))
            }
            layout.addView(sizeLabel)

            sizes.forEachIndexed { idx, label ->
                layout.addView(
                    Button(context).apply {
                        text = label
                        setOnClickListener {
                            chosenSize = sizeValues[idx]
                            sizeLabel.text = "حجم الخط: ${chosenSize.toInt()} sp"
                        }
                    }
                )
            }

            val themeLabel = TextView(context).apply {
                text = "نظام الألوان"
                setTextColor(textColor)
                setPadding(0, dp(context, 10), 0, dp(context, 6))
            }
            layout.addView(themeLabel)

            themes.forEach { (name, pair) ->
                layout.addView(
                    Button(context).apply {
                        text = name
                        setOnClickListener {
                            chosenBg = pair.first
                            chosenFg = pair.second
                        }
                    }
                )
            }

            AlertDialog.Builder(context)
                .setTitle("تخصيص حجم الخط والألوان")
                .setView(layout)
                .setPositiveButton("تطبيق") { _, _ ->
                    onApply(chosenSize, chosenBg, chosenFg)
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }

        private fun showSelectPartialCodeDialog(
            context: Context,
            code: String
        ) {
            val input = EditText(context).apply {
                setText(code)
                setTextColor(Color.WHITE)
                typeface = Typeface.MONOSPACE
                textSize = 12f
                minLines = 5
                setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12))
            }

            val copySelectionBtn = Button(context).apply {
                text = "نسخ التحديد الحالي"
                setOnClickListener {
                    val start = input.selectionStart.coerceAtLeast(0)
                    val end = input.selectionEnd.coerceAtLeast(0)
                    val lo = minOf(start, end)
                    val hi = maxOf(start, end)
                    val selected = if (hi > lo && hi <= input.text.length) {
                        input.text.subSequence(lo, hi).toString()
                    } else {
                        input.text.toString()
                    }
                    copyTextToClipboard(
                        context = context,
                        label = "MonolithPartialCode",
                        text = selected,
                        toastMessage = "تم نسخ الجزء المحدد"
                    )
                }
            }

            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(input)
                addView(copySelectionBtn)
            }

            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) = Unit
            })

            AlertDialog.Builder(context)
                .setTitle("تحديد جزء من الكود ونسخه")
                .setView(layout)
                .setPositiveButton("إغلاق", null)
                .show()
        }

        private fun showCompareVersionsDialog(
            context: Context,
            originalCode: String,
            currentCode: String
        ) {
            val isIdentical = originalCode == currentCode
            val comparisonText = if (isIdentical) {
                "النسخة الحالية مطابقة تماماً للنسخة الأصلية المستخرجة من الرسالة.\n\n--- الكود ---\n$currentCode"
            } else {
                "تم تعديل النسخة الحالية عن النسخة الأصلية.\n\n--- النسخة الأصلية ---\n$originalCode\n\n--- النسخة الحالية ---\n$currentCode"
            }

            val scroll = ScrollView(context).apply {
                addView(
                    TextView(context).apply {
                        text = comparisonText
                        textSize = 11f
                        setTextColor(textColor)
                        typeface = Typeface.MONOSPACE
                        setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12))
                    }
                )
            }

            AlertDialog.Builder(context)
                .setTitle("مقارنة نسختين من الكود")
                .setView(scroll)
                .setPositiveButton("حسناً", null)
                .show()
        }

        private fun showUnboundAiActionNotice(
            context: Context,
            actionName: String,
            language: String,
            code: String
        ) {
            AlertDialog.Builder(context)
                .setTitle(actionName)
                .setMessage(
                    "إجراء «$actionName» غير مربوط بمحرك ذكاء اصطناعي فعلي داخل ChatAdapter ولم يتم تنفيذه.\n" +
                        "اللغة: $language • طول الكود: ${code.length} حرف."
                )
                .setPositiveButton("حسناً", null)
                .show()
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
            expandedBlockIndex = null

            for (index in 0 until imageContainer.childCount) {
                val image =
                    imageContainer.getChildAt(index) as ImageView

                Glide.with(image).clear(image)
                image.setImageDrawable(null)
            }

            graphPanel.removeAllViews()
            toolsContainer.removeAllViews()
            codeBlocksContainer.removeAllViews()
            legend.removeAllViews()

            renderedContent = null
            messageText.text = ""
            titleText.text = ""

            imageScroll.visibility = View.GONE
            graphPanel.visibility = View.GONE
            toolsContainer.visibility = View.GONE
            codeBlocksContainer.visibility = View.GONE
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
