package com.monolith

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
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
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

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

data class CardBgPreset(
    val id: String,
    val label: String,
    val topColor: Int,
    val bottomColor: Int,
    val chipColor: Int
)

class ChatAdapter(
    context: Context,
    private val onEditClick: (ChatMessage) -> Unit,
    private val onReactionClick: (ChatMessage) -> Unit,
    private val onSpeakClick: (String) -> Unit,
    private val onPinClick: (ChatMessage) -> Unit,
    private val themeColorHex: String,
    private val onRegenerateClick: ((ChatMessage) -> Unit)? = null,

    private val relationProvider:
        ((ChatMessage) -> List<MessageRelation>)? = null,

    private val toolProvider:
        ((ChatMessage) -> List<ToolPresentation>)? = null,

    private val onRelationClick:
        ((MessageRelation) -> Unit)? = null,

    private val onToolClick:
        ((ToolPresentation) -> Unit)? = null,

        private val onPaletteClick: ((ChatMessage) -> Unit)? = null,
    private val onMessagesChanged: ((List<ChatMessage>) -> Unit)? = null

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
    private val textColor = Color.rgb(241, 245, 249)
    private val mutedColor = Color.rgb(142, 161, 189)
    private val accentColor = Color.rgb(56, 189, 248)
    private val purpleColor = Color.rgb(168, 85, 247)

    // ألوان الهوية الأصلية في المعاينة: self = #f8fafc ، peer = #38bdf8 ، ai = #ef4444
    private var identitySelfColor = Color.rgb(248, 250, 252)
    private var identityPeerColor = Color.rgb(56, 189, 248)
    private var identityAiColor = Color.rgb(239, 68, 68)

    // ألوان البطاقة والهيدر والدرع من المعاينة
    private val geminiCardBgTop = Color.rgb(20, 25, 36)
    private val geminiCardBgBottom = Color.rgb(12, 15, 22)
    private val geminiCardBorder = Color.rgb(35, 45, 63)
    private val geminiMsgTitle = Color.rgb(142, 161, 189)
    private val geminiBadgeBg = Color.argb(30, 56, 189, 248)
    private val geminiBadgeBorder = Color.argb(76, 56, 189, 248)
    private val geminiBadgeText = Color.rgb(56, 189, 248)
    private val geminiIconBg = Color.argb(8, 255, 255, 255)
    private val geminiIconBorder = Color.argb(20, 255, 255, 255)
    private val geminiShieldColor = Color.argb(148, 9, 12, 17)

    private val geminiPaletteBg = Color.argb(13, 255, 255, 255)
    private val geminiPaletteBorder = Color.argb(31, 255, 255, 255)
    private val geminiPaletteIconColor = Color.rgb(148, 163, 184)

    // قوالب خلفيات البطاقة المنسوخة من CARD_BG_PRESETS
    private val cardBgPresets = listOf(
        CardBgPreset(
            id = "default",
            label = "افتراضي داكن",
            topColor = Color.rgb(20, 25, 36),
            bottomColor = Color.rgb(12, 15, 22),
            chipColor = Color.rgb(20, 25, 36)
        ),
        CardBgPreset(
            id = "obsidian",
            label = "أسود خالص",
            topColor = Color.rgb(17, 24, 39),
            bottomColor = Color.rgb(3, 7, 18),
            chipColor = Color.rgb(11, 14, 20)
        ),
        CardBgPreset(
            id = "midnight",
            label = "أزرق ليلي عميق",
            topColor = Color.rgb(15, 23, 42),
            bottomColor = Color.rgb(2, 6, 23),
            chipColor = Color.rgb(15, 23, 42)
        ),
        CardBgPreset(
            id = "slate",
            label = "أردوازي صلب",
            topColor = Color.rgb(30, 41, 59),
            bottomColor = Color.rgb(15, 23, 42),
            chipColor = Color.rgb(22, 32, 50)
        ),
        CardBgPreset(
            id = "charcoal",
            label = "فحمي نقي",
            topColor = Color.rgb(28, 25, 23),
            bottomColor = Color.rgb(15, 13, 14),
            chipColor = Color.rgb(28, 25, 23)
        )
    )

    // إعدادات الخلفية الديناميكية والشفافية المطابقة للمعاينة
    private var activeBgPresetId: String = "default"
    private var cardOpacityFloat: Float = 0.98f
    private var dynEnabled: Boolean = true
    private var dynStateMode: String = "on" // "off" | "on" | "locked"
    private var dynPattern: String = "pulse" // "pulse" | "grid" | "sheen" | "chess" | "cyberpunk" | "organic"
    private var dynSpeedSec: Float = 8f
    private var dynIntensityPercent: Int = 20

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

    // مهم: لا يعدل msg.content أبداً. النسخ المؤقتة محصورة في الجلسة الحالية.
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
        private const val TAG_PALETTE = "monolith_palette_btn"

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

    fun applyVisualConfig(
        selfColorHex: String? = null,
        peerColorHex: String? = null,
        aiColorHex: String? = null,
        bgPresetId: String? = null,
        cardOpacity: Float? = null,
        dynamicEnabled: Boolean? = null,
        dynamicState: String? = null,
        dynamicPattern: String? = null,
        dynamicSpeedSec: Float? = null,
        dynamicIntensityPercent: Int? = null
    ) {
        selfColorHex?.let { identitySelfColor = parseColor(it, identitySelfColor) }
        peerColorHex?.let { identityPeerColor = parseColor(it, identityPeerColor) }
        aiColorHex?.let { identityAiColor = parseColor(it, identityAiColor) }
        bgPresetId?.let { activeBgPresetId = it }
        cardOpacity?.let { cardOpacityFloat = it.coerceIn(0.25f, 1.0f) }
        dynamicEnabled?.let { dynEnabled = it }
        dynamicState?.let { dynStateMode = it }
        dynamicPattern?.let { dynPattern = it }
        dynamicSpeedSec?.let { dynSpeedSec = it.coerceIn(2f, 15f) }
        dynamicIntensityPercent?.let { dynIntensityPercent = it.coerceIn(5, 100) }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).senderType == MessageSenderType.SELF) {
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

            val header = LinearLayout(context).apply {
                tag = TAG_HEADER
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(context, 6)
                }
            }

            val badge = TextView(context).apply {
                tag = TAG_BADGE
                text = "#0"
                textSize = 11f
                setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                setTextColor(geminiBadgeText)
                background = roundedBackground(
                    geminiBadgeBg,
                    dp(context, 6).toFloat(),
                    geminiBadgeBorder,
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
                    geminiIconBg,
                    dp(context, 8).toFloat(),
                    geminiIconBorder,
                    dp(context, 1)
                )
            }

            val paletteButton = ImageView(context).apply {
                tag = TAG_PALETTE
                setImageResource(R.drawable.ic_palette)
                setColorFilter(geminiPaletteIconColor)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                contentDescription = "تخصيص الموصلات والبطاقة"
                layoutParams = LinearLayout.LayoutParams(
                    dp(context, 30),
                    dp(context, 30)
                ).apply {
                    marginStart = dp(context, 6)
                }
                setPadding(dp(context, 6), dp(context, 6), dp(context, 6), dp(context, 6))
                background = roundedBackground(
                    geminiPaletteBg,
                    dp(context, 9).toFloat(),
                    geminiPaletteBorder,
                    dp(context, 1)
                )
                isClickable = true
                isFocusable = true
            }

            header.addView(badge)
            header.addView(identityIcon)
            header.addView(paletteButton)
            addView(header)

            val card = CardView(context).apply {
                tag = TAG_CARD
                radius = dp(context, 16).toFloat()
                cardElevation = dp(context, 5).toFloat()
                useCompatPadding = false
                preventCornerOverlap = true
                setCardBackgroundColor(Color.TRANSPARENT)

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
            }

            val cardInner = FrameLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val dynLayer = DynBackgroundView(context).apply {
                tag = TAG_DYN_LAYER
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                isClickable = false
                isFocusable = false
            }

            val shield = View(context).apply {
                tag = TAG_SHIELD
                background = GradientDrawable().apply {
                    setColor(geminiShieldColor)
                    cornerRadius = dp(context, 16).toFloat()
                }
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                isClickable = false
                isFocusable = false
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
                setTextColor(geminiMsgTitle)
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, dp(context, 6))
            }

            val message = TextView(context).apply {
                textSize = 13f
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

    private fun buildIdentityVectorDrawable(entityType: String, color: Int): Drawable {
        return object : Drawable() {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            override fun draw(canvas: Canvas) {
                val b = bounds
                if (b.width() <= 0 || b.height() <= 0) return
                val cx = b.exactCenterX()
                val cy = b.exactCenterY()
                val s = min(b.width(), b.height()).toFloat()

                paint.reset()
                paint.isAntiAlias = true
                paint.color = color

                when (entityType) {
                    "peer" -> {
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = s * 0.095f
                        paint.strokeCap = Paint.Cap.ROUND
                        canvas.drawCircle(cx, cy - s * 0.14f, s * 0.16f, paint)
                        val shoulders = Path().apply {
                            moveTo(cx - s * 0.30f, cy + s * 0.30f)
                            quadTo(cx, cy + s * 0.04f, cx + s * 0.30f, cy + s * 0.30f)
                        }
                        canvas.drawPath(shoulders, paint)
                    }
                    "ai" -> {
                        paint.style = Paint.Style.FILL
                        paint.color = Color.argb(38, Color.red(color), Color.green(color), Color.blue(color))
                        canvas.drawRoundRect(
                            cx - s * 0.28f,
                            cy - s * 0.14f,
                            cx + s * 0.28f,
                            cy + s * 0.28f,
                            s * 0.10f,
                            s * 0.10f,
                            paint
                        )
                        paint.style = Paint.Style.STROKE
                        paint.color = color
                        paint.strokeWidth = s * 0.08f
                        paint.strokeCap = Paint.Cap.ROUND
                        canvas.drawRoundRect(
                            cx - s * 0.28f,
                            cy - s * 0.14f,
                            cx + s * 0.28f,
                            cy + s * 0.28f,
                            s * 0.10f,
                            s * 0.10f,
                            paint
                        )
                        canvas.drawLine(cx, cy - s * 0.34f, cx, cy - s * 0.14f, paint)
                        paint.style = Paint.Style.FILL
                        canvas.drawCircle(cx, cy - s * 0.36f, s * 0.055f, paint)
                        canvas.drawCircle(cx - s * 0.12f, cy + s * 0.04f, s * 0.055f, paint)
                        canvas.drawCircle(cx + s * 0.12f, cy + s * 0.04f, s * 0.055f, paint)
                        paint.style = Paint.Style.STROKE
                        canvas.drawLine(cx - s * 0.12f, cy + s * 0.18f, cx + s * 0.12f, cy + s * 0.18f, paint)
                    }
                    else -> {
                        paint.style = Paint.Style.FILL
                        canvas.drawCircle(cx, cy - s * 0.14f, s * 0.16f, paint)
                        val body = Path().apply {
                            moveTo(cx - s * 0.30f, cy + s * 0.30f)
                            quadTo(cx, cy + s * 0.04f, cx + s * 0.30f, cy + s * 0.30f)
                            close()
                        }
                        canvas.drawPath(body, paint)
                    }
                }
            }

            override fun setAlpha(alpha: Int) {
                paint.alpha = alpha
                invalidateSelf()
            }

            override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
                paint.colorFilter = colorFilter
                invalidateSelf()
            }

            @Deprecated("Deprecated in Java")
            override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
        }
    }

    private inner class DynBackgroundView(context: Context) : View(context) {
        private var pattern: String = dynPattern
        private var tone: Int = identitySelfColor
        private var speedSec: Float = dynSpeedSec
        private var intensity: Float = dynIntensityPercent / 100f
        private var active: Boolean = dynEnabled && dynStateMode != "off"

        fun configure(entityType: String) {
            pattern = dynPattern
            tone = when (entityType) {
                "peer" -> identityPeerColor
                "ai" -> identityAiColor
                else -> identitySelfColor
            }
            speedSec = dynSpeedSec
            intensity = dynIntensityPercent / 100f
            active = dynEnabled && dynStateMode != "off"
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!active) return
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val elapsedSec = (android.os.SystemClock.uptimeMillis() % 600_000L) / 1000f
            val cycle = (elapsedSec % speedSec.coerceAtLeast(0.5f)) / speedSec.coerceAtLeast(0.5f)
            val phase = (cycle * Math.PI * 2.0).toFloat()

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val r = Color.red(tone)
            val g = Color.green(tone)
            val b = Color.blue(tone)

            when (pattern) {
                "pulse" -> {
                    val maxR = hypot(w.toDouble(), h.toDouble()).toFloat() * 0.65f
                    val pulseFactor = 0.5f + 0.5f * sin(phase)
                    val radius = (20f + pulseFactor * (maxR - 20f)).coerceAtLeast(16f)
                    paint.shader = RadialGradient(
                        w / 2f,
                        h / 2f,
                        radius,
                        intArrayOf(
                            Color.argb((intensity * 255f * 0.75f).toInt().coerceIn(0, 255), r, g, b),
                            Color.argb((intensity * 255f * 0.25f).toInt().coerceIn(0, 255), r, g, b),
                            Color.argb(0, 9, 12, 17)
                        ),
                        floatArrayOf(0f, 0.5f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(0f, 0f, w, h, paint)
                }
                "grid" -> {
                    val spacing = 22f
                    val offsetX = (cycle * spacing) % spacing
                    val offsetY = (cycle * spacing * 0.5f) % spacing
                    paint.color = Color.argb((intensity * 255f * 0.35f).toInt().coerceIn(0, 255), r, g, b)
                    paint.strokeWidth = 1f
                    var x = offsetX
                    while (x < w) {
                        canvas.drawLine(x, 0f, x, h, paint)
                        x += spacing
                    }
                    var y = offsetY
                    while (y < h) {
                        canvas.drawLine(0f, y, w, y, paint)
                        y += spacing
                    }
                }
                "sheen" -> {
                    val pos = -w + cycle * (w * 2.5f)
                    paint.shader = LinearGradient(
                        pos,
                        0f,
                        pos + 110f,
                        h,
                        intArrayOf(
                            Color.argb(0, 255, 255, 255),
                            Color.argb((intensity * 255f * 0.4f).toInt().coerceIn(0, 255), 255, 255, 255),
                            Color.argb(0, 255, 255, 255)
                        ),
                        floatArrayOf(0f, 0.5f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(0f, 0f, w, h, paint)
                }
                "chess" -> {
                    val cols = 6
                    val rows = 4
                    val cw = w / cols
                    val ch = h / rows
                    paint.style = Paint.Style.FILL
                    for (row in 0 until rows) {
                        for (col in 0 until cols) {
                            val isEven = (row + col) % 2 == 0
                            val shift = 0.5f + 0.5f * sin(row * 0.4f + col * 0.3f + phase)
                            val alpha = if (isEven) {
                                intensity * 0.12f + shift * (intensity * 0.22f)
                            } else {
                                intensity * 0.04f
                            }
                            paint.color = Color.argb((alpha * 255f).toInt().coerceIn(0, 255), r, g, b)
                            canvas.drawRect(col * cw, row * ch, (col + 1) * cw - 1f, (row + 1) * ch - 1f, paint)
                        }
                    }
                }
                "cyberpunk" -> {
                    paint.color = Color.argb((intensity * 255f * 0.35f).toInt().coerceIn(0, 255), r, g, b)
                    paint.strokeWidth = 1.2f
                    paint.style = Paint.Style.STROKE
                    val trace1 = Path().apply {
                        moveTo(0f, h * 0.3f)
                        lineTo(w * 0.5f, h * 0.3f)
                        lineTo(w * 0.5f, h * 0.5f)
                        lineTo(w, h * 0.5f)
                    }
                    val trace2 = Path().apply {
                        moveTo(w, h * 0.7f)
                        lineTo(w * 0.4f, h * 0.7f)
                        lineTo(w * 0.4f, h * 0.5f)
                        lineTo(0f, h * 0.5f)
                    }
                    canvas.drawPath(trace1, paint)
                    canvas.drawPath(trace2, paint)
                }
                "organic" -> {
                    val cx1 = w * 0.35f + sin(phase) * (w * 0.2f)
                    val cy1 = h * 0.4f + cos(phase) * (h * 0.2f)
                    paint.shader = RadialGradient(
                        cx1,
                        cy1,
                        (w * 0.55f).coerceAtLeast(24f),
                        intArrayOf(
                            Color.argb((intensity * 255f * 0.45f).toInt().coerceIn(0, 255), r, g, b),
                            Color.argb(0, 9, 12, 17)
                        ),
                        null,
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(0f, 0f, w, h, paint)
                }
            }

            if (active && dynStateMode == "on") {
                postInvalidateOnAnimation()
            }
        }
    }

    private fun resolvePresetColors(presetId: String): Pair<Int, Int> {
        val found = cardBgPresets.firstOrNull { it.id == presetId }
        return if (found != null) {
            found.topColor to found.bottomColor
        } else {
            geminiCardBgTop to geminiCardBgBottom
        }
    }

    private fun applyCardBackground(
        card: CardView,
        senderType: MessageSenderType
    ) {
        val sideColor = when (senderType) {
            MessageSenderType.SELF -> identitySelfColor
            MessageSenderType.PEER -> identityPeerColor
            MessageSenderType.AI -> identityAiColor
        }
        val (baseTop, baseBottom) = resolvePresetColors(activeBgPresetId)
        val alphaByte = (cardOpacityFloat * 255f).toInt().coerceIn(64, 255)

        val topWithAlpha = Color.argb(
            alphaByte,
            Color.red(baseTop),
            Color.green(baseTop),
            Color.blue(baseTop)
        )
        val bottomWithAlpha = Color.argb(
            alphaByte,
            Color.red(baseBottom),
            Color.green(baseBottom),
            Color.blue(baseBottom)
        )

        val gradient = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(topWithAlpha, bottomWithAlpha)
        ).apply {
            cornerRadius = dp(appContext, 16).toFloat()
            setStroke(dp(appContext, 1), geminiCardBorder)
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
        card.cardElevation = dp(appContext, 5).toFloat()
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

        private val paletteButton: ImageView =
            header.findViewWithTag<ImageView>(TAG_PALETTE)
                ?: (header.getChildAt(2) as ImageView)

        private val card =
            root.findViewWithTag<CardView>(TAG_CARD)
                ?: (root.getChildAt(1) as CardView)

        private val cardInner = card.getChildAt(0) as FrameLayout

        private val dynLayer =
            cardInner.findViewWithTag<DynBackgroundView>(TAG_DYN_LAYER)
                ?: (cardInner.getChildAt(0) as DynBackgroundView)

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

            paletteButton.setOnClickListener {
                currentMessage?.let { msg -> onPaletteClick?.invoke(msg) }
            }
        }

        fun bind(msg: ChatMessage) {
            if (currentMessage?.id != msg.id) {
                expandedBlockIndex = null
            }
            currentMessage = msg

                        val senderType = msg.senderType

            val entityType = when (senderType) {
                MessageSenderType.SELF -> "self"
                MessageSenderType.PEER -> "peer"
                MessageSenderType.AI -> "ai"
            }

            val idColor = when (senderType) {
                MessageSenderType.SELF -> identitySelfColor
                MessageSenderType.PEER -> identityPeerColor
                MessageSenderType.AI -> identityAiColor
            }

            val isSelf = senderType == MessageSenderType.SELF

            root.gravity = if (isSelf) Gravity.END else Gravity.START

            applyCardBackground(card, senderType)

            val badgeNumber = (msg.id.hashCode() and 0x7FFFFFFF) % 999 + 1
            badge.text = "#$badgeNumber"

            identityIcon.setImageDrawable(
                buildIdentityVectorDrawable(entityType, idColor)
            )
            identityIcon.background = roundedBackground(
                Color.argb(34, Color.red(idColor), Color.green(idColor), Color.blue(idColor)),
                dp(root.context, 8).toFloat(),
                Color.argb(102, Color.red(idColor), Color.green(idColor), Color.blue(idColor)),
                dp(root.context, 1)
            )

            titleText.text = when (senderType) {
                MessageSenderType.SELF -> "YOU"
                MessageSenderType.PEER -> "PEER"
                MessageSenderType.AI -> "MONOLITH"
            }

            dynLayer.configure(entityType)

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
                CodeAdapter.extractCodeBlocks(msg.content)
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
                            Color.argb(30, 56, 189, 248),
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

            addToolChip("1. نسخ الكود كاملاً", accentColor) {
                copyTextToClipboard(
                    context = context,
                    label = "MonolithCodeBlock",
                    text = currentCode,
                    toastMessage = "تم نسخ الكود كاملاً"
                )
            }

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

            addToolChip("3. تحليل الكود") {
                showCodeAnalysisDialog(context, currentLanguage, currentCode)
            }

            addToolChip("4. توسيع مساحة الكود") {
                isExpandedHeight = !isExpandedHeight
                refreshCodePreview()
                Toast.makeText(
                    context,
                    if (isExpandedHeight) "تم توسيع مساحة عرض الكود" else "تم تقليص مساحة عرض الكود",
                    Toast.LENGTH_SHORT
                ).show()
            }

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

            addToolChip("7. أرقام الأسطر") {
                showLineNumbers = !showLineNumbers
                refreshCodePreview()
                Toast.makeText(
                    context,
                    if (showLineNumbers) "تم إظهار أرقام الأسطر" else "تم إخفاء أرقام الأسطر",
                    Toast.LENGTH_SHORT
                ).show()
            }

            addToolChip("8. تحديد ونسخ جزء") {
                showSelectPartialCodeDialog(context, currentCode)
            }

            addToolChip("9. مقارنة نسختين") {
                showCompareVersionsDialog(
                    context = context,
                    originalCode = block.code,
                    currentCode = currentCode
                )
            }

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

            addToolChip("AI: شرح الكود", purpleColor) {
                showUnboundAiActionNotice(
                    context = context,
                    actionName = "شرح الكود",
                    language = currentLanguage,
                    code = currentCode
                )
            }

            addToolChip("AI: إصلاح الأخطاء", purpleColor) {
                showUnboundAiActionNotice(
                    context = context,
                    actionName = "إصلاح الأخطاء",
                    language = currentLanguage,
                    code = currentCode
                )
            }

            addToolChip("AI: تحسين وإعادة هيكلة", purpleColor) {
                showUnboundAiActionNotice(
                    context = context,
                    actionName = "تحسين الكود وإعادة هيكلته",
                    language = currentLanguage,
                    code = currentCode
                )
            }

            addToolChip("فتح في Workspace", accentColor) {
                openInCodeWorkspace(
                    context = context,
                    block = block,
                    message = msg,
                    effectiveCode = currentCode,
                    effectiveLanguage = currentLanguage
                )
            }

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
                "داكن أزرق (افتراضي)" to (Color.rgb(8, 12, 25) to Color.rgb(241, 245, 249)),
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

    override fun onCurrentListChanged(
        previousList: List<ChatMessage>,
        currentList: List<ChatMessage>
    ) {
        super.onCurrentListChanged(previousList, currentList)
        onMessagesChanged?.invoke(currentList)
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
