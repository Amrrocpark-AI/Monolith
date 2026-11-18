
package com.monolith

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
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

class ChatAdapter(
    context: Context,
    private val onEditClick: (ChatMessage) -> Unit,
    private val onReactionClick: (ChatMessage) -> Unit,
    private val onSpeakClick: (String) -> Unit,
    private val onPinClick: (ChatMessage) -> Unit,
    private val themeColorHex: String,
    private val onRegenerateClick: ((ChatMessage) -> Unit)? = null
) : ListAdapter<ChatMessage, ChatAdapter.ChatViewHolder>(DIFF_CALLBACK) {

    private val appContext = context.applicationContext

    private val userBubbleColor = runCatching {
        Color.parseColor(themeColorHex)
    }.getOrDefault(Color.rgb(8, 48, 78))

    private val aiBubbleColor = Color.rgb(20, 19, 48)

    private val surfaceColor = Color.rgb(12, 15, 35)

    private val iconColor = Color.parseColor("#DCE7FF")

    private val accentColor = Color.parseColor("#00D4FF")

    private val purpleColor = Color.parseColor("#A855F7")

    private val markwon: Markwon = Markwon.builder(appContext)
        .usePlugin(
            TablePlugin.create { builder: TableTheme.Builder ->
                builder
                    .tableCellPadding(16)
                    .tableBorderWidth(2)
                    .tableBorderColor(Color.parseColor("#39436A"))
                    .tableHeaderRowBackgroundColor(
                        Color.parseColor("#252447")
                    )
                    .tableOddRowBackgroundColor(
                        Color.parseColor("#1C1D3A")
                    )
                    .tableEvenRowBackgroundColor(
                        Color.parseColor("#17182F")
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

            gravity = if (isUser) {
                Gravity.END
            } else {
                Gravity.START
            }

            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            setPadding(
                dp(context, 12),
                dp(context, 7),
                dp(context, 12),
                dp(context, 7)
            )

            // Message card

            val card = CardView(context).apply {
                radius = dp(context, 19).toFloat()
                cardElevation = dp(context, 3).toFloat()
                useCompatPadding = false
                preventCornerOverlap = true

                setCardBackgroundColor(
                    if (isUser) userBubbleColor else aiBubbleColor
                )

                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (isUser) {
                        marginStart = dp(context, 35)
                    } else {
                        marginEnd = dp(context, 35)
                    }
                }

                val message = TextView(context).apply {
                    textSize = 15.5f
                    setTextColor(Color.WHITE)

                    setPadding(
                        dp(context, 17),
                        dp(context, 13),
                        dp(context, 17),
                        dp(context, 13)
                    )

                    maxWidth = (
                        context.resources.displayMetrics.widthPixels * 0.80f
                    ).toInt()

                    setTextIsSelectable(true)

                    movementMethod =
                        android.text.method.LinkMovementMethod.getInstance()

                    layoutParams = CardView.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

                addView(message)
            }

            addView(card)

            // Image attachments

            val imageScroll = HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                visibility = View.GONE

                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(context, 9)
                }
            }

            val imageContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL

                gravity = if (isUser) {
                    Gravity.END
                } else {
                    Gravity.START
                }
            }

            imageScroll.addView(imageContainer)
            addView(imageScroll)

            // Compact action bar:
            // Copy, Speak, More.

            val actions = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL

                gravity = if (isUser) {
                    Gravity.END
                } else {
                    Gravity.START
                }

                visibility = View.GONE

                setPadding(
                    dp(context, 5),
                    dp(context, 5),
                    dp(context, 5),
                    dp(context, 5)
                )

                background = roundedBackground(
                    surfaceColor,
                    dp(context, 24).toFloat(),
                    Color.parseColor("#35416B"),
                    dp(context, 1)
                )

                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(context, 7)
                    if (isUser) {
                        marginEnd = dp(context, 4)
                    } else {
                        marginStart = dp(context, 4)
                    }
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
                    "المزيد من خيارات الرسالة"
                )
            )

            addView(actions)
        }
    }

    private fun createActionButton(
        context: Context,
        drawable: Int,
        description: String
    ): ImageView {
        return ImageView(context).apply {
            setImageResource(drawable)
            setColorFilter(iconColor)

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

        private val card = root.getChildAt(0) as CardView

        private val messageText =
            card.getChildAt(0) as TextView

        private val imageScroll =
            root.getChildAt(1) as HorizontalScrollView

        private val imageContainer =
            imageScroll.getChildAt(0) as LinearLayout

        private val actionBar =
            root.getChildAt(2) as LinearLayout

        private val copyButton =
            actionBar.getChildAt(0) as ImageView

        private val speakButton =
            actionBar.getChildAt(1) as ImageView

        private val moreButton =
            actionBar.getChildAt(2) as ImageView

        private var currentMessage: ChatMessage? = null

        private var renderedContent: String? = null

        init {
            copyButton.setOnClickListener {
                val message = currentMessage
                    ?: return@setOnClickListener

                copyMessage(message)
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

            root.gravity = if (isUser) {
                Gravity.END
            } else {
                Gravity.START
            }

            card.setCardBackgroundColor(
                if (isUser) userBubbleColor else aiBubbleColor
            )

            // Render Markdown only when content changes.

            if (renderedContent != msg.content) {
                if (msg.content.isEmpty()) {
                    messageText.text = ""
                } else {
                    markwon.setMarkdown(
                        messageText,
                        msg.content
                    )
                }

                renderedContent = msg.content
            }

            card.visibility = if (msg.content.isNotEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }

            bindImages(msg)

            val canShowActions =
                msg.content.isNotBlank() &&
                msg.content != "جاري التفكير..." &&
                !msg.isThinking

            actionBar.visibility = if (canShowActions) {
                View.VISIBLE
            } else {
                View.GONE
            }

            // Show a subtle indication when the message
            // has a reaction or is pinned.

            val hasMessageState =
                msg.isPinned ||
                msg.reactionType == "LIKE" ||
                msg.reactionType == "DISLIKE"

            moreButton.setColorFilter(
                if (hasMessageState) {
                    accentColor
                } else {
                    iconColor
                }
            )

            copyButton.isEnabled = msg.content.isNotEmpty()

            speakButton.isEnabled = msg.content.isNotEmpty()

            copyButton.alpha = if (copyButton.isEnabled) {
                1f
            } else {
                0.4f
            }

            speakButton.alpha = if (speakButton.isEnabled) {
                1f
            } else {
                0.4f
            }
        }

        private fun bindImages(msg: ChatMessage) {
            val uris = msg.attachmentUris.orEmpty()

            imageScroll.visibility = if (uris.isEmpty()) {
                View.GONE
            } else {
                View.VISIBLE
            }

            // Remove excess recycled image views.

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

                            layoutParams = LinearLayout.LayoutParams(
                                dp(root.context, 150),
                                dp(root.context, 150)
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

                            contentDescription =
                                "Message attachment"

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
                ClipData.newPlainText(
                    "Monolith",
                    msg.content
                )
            )

            Toast.makeText(
                root.context,
                "تم نسخ الرسالة",
                Toast.LENGTH_SHORT
            ).show()
        }

        private fun showMenu() {
            val msg = currentMessage ?: return

            val popup = PopupMenu(
                root.context,
                moreButton
            )

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

                // Prevent actions on a recycled holder.

                val current = currentMessage
                    ?: return@setOnMenuItemClickListener false

                if (current.id != msg.id) {
                    return@setOnMenuItemClickListener false
                }

                when (item.itemId) {

                    MENU_REGENERATE -> {
                        onRegenerateClick?.invoke(current)
                    }

                    MENU_REACTION -> {
                        onReactionClick(current)
                    }

                    MENU_PIN -> {
                        onPinClick(current)
                    }

                    MENU_SHARE -> {
                        shareMessage(current)
                    }

                    MENU_EDIT -> {
                        onEditClick(current)
                    }

                    else -> return@setOnMenuItemClickListener false
                }

                true
            }

            popup.show()
        }

        private fun shareMessage(msg: ChatMessage) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"

                putExtra(
                    Intent.EXTRA_TEXT,
                    msg.content
                )
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

            // Clear image requests before reuse.

            for (index in 0 until imageContainer.childCount) {
                val image =
                    imageContainer.getChildAt(index) as ImageView

                Glide.with(image).clear(image)

                image.setImageDrawable(null)
            }

            renderedContent = null

            messageText.text = ""

            imageScroll.visibility = View.GONE

            card.visibility = View.GONE

            actionBar.visibility = View.GONE

            moreButton.setColorFilter(iconColor)

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

    override fun onViewRecycled(
        holder: ChatViewHolder
    ) {
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

    private fun dp(
        context: Context,
        value: Int
    ): Int {
        return (
            value * context.resources.displayMetrics.density
        ).toInt()
    }
}
