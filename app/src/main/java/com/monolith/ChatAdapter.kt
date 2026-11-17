
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
import android.widget.FrameLayout
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
    }.getOrDefault(Color.rgb(45, 85, 125))

    private val aiBubbleColor = Color.parseColor("#222222")
    private val iconColor = Color.parseColor("#B3FFFFFF")
    private val accentColor = Color.parseColor("#00D4FF")

    private val markwon: Markwon = Markwon.builder(appContext)
        .usePlugin(
            TablePlugin.create { builder: TableTheme.Builder ->
                builder
                    .tableCellPadding(16)
                    .tableBorderWidth(2)
                    .tableBorderColor(Color.parseColor("#444444"))
                    .tableHeaderRowBackgroundColor(Color.parseColor("#333333"))
                    .tableOddRowBackgroundColor(Color.parseColor("#2A2A2A"))
                    .tableEvenRowBackgroundColor(Color.parseColor("#222222"))
            }
        )
        .build()

    companion object {
        private const val TYPE_USER = 1
        private const val TYPE_AI = 2

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
            gravity = if (isUser) Gravity.END else Gravity.START

            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            setPadding(dp(context, 8), dp(context, 5),
                dp(context, 8), dp(context, 5))

            // Message card
            val card = CardView(context).apply {
                radius = dp(context, 14).toFloat()
                cardElevation = dp(context, 2).toFloat()
                useCompatPadding = true

                setCardBackgroundColor(
                    if (isUser) userBubbleColor else aiBubbleColor
                )

                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )

                val message = TextView(context).apply {
                    textSize = 15f
                    setTextColor(Color.WHITE)
                    setPadding(
                        dp(context, 14), dp(context, 10),
                        dp(context, 14), dp(context, 10)
                    )

                    maxWidth = (
                        context.resources.displayMetrics.widthPixels * 0.82f
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

            // Image area
            val imageScroll = HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                visibility = View.GONE

                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(context, 6)
                }
            }

            val imageContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = if (isUser) Gravity.END else Gravity.START
            }

            imageScroll.addView(imageContainer)
            addView(imageScroll)

            // Action bar
            val actions = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = if (isUser) Gravity.END else Gravity.START
                visibility = View.GONE

                setPadding(
                    dp(context, 4), dp(context, 8),
                    dp(context, 4), 0
                )
            }

            val iconResources = intArrayOf(
                R.drawable.ic_copy,
                R.drawable.ic_volume_up,
                R.drawable.ic_thumb_hybrid,
                R.drawable.ic_pinned,
                R.drawable.ic_more_vert
            )

            iconResources.forEach { resource ->
                actions.addView(
                    ImageView(context).apply {
                        setImageResource(resource)
                        setColorFilter(iconColor)
                        contentDescription = "Message action"
                        scaleType = ImageView.ScaleType.CENTER_INSIDE

                        layoutParams = LinearLayout.LayoutParams(
                            dp(context, 24),
                            dp(context, 24)
                        ).apply {
                            marginEnd = dp(context, 16)
                        }

                        isClickable = true
                        isFocusable = true
                    }
                )
            }

            addView(actions)
        }
    }

    inner class ChatViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        private val root = itemView as LinearLayout

        private val card = root.getChildAt(0) as CardView
        private val messageText = card.getChildAt(0) as TextView

        private val imageScroll =
            root.getChildAt(1) as HorizontalScrollView

        private val imageContainer =
            imageScroll.getChildAt(0) as LinearLayout

        private val actionBar = root.getChildAt(2) as LinearLayout

        private val copyButton = actionBar.getChildAt(0) as ImageView
        private val speakButton = actionBar.getChildAt(1) as ImageView
        private val reactionButton = actionBar.getChildAt(2) as ImageView
        private val pinButton = actionBar.getChildAt(3) as ImageView
        private val moreButton = actionBar.getChildAt(4) as ImageView

        private var currentMessage: ChatMessage? = null
        private var renderedContent: String? = null

        init {
            copyButton.setOnClickListener {
                val msg = currentMessage ?: return@setOnClickListener
                copyMessage(msg)
            }

            speakButton.setOnClickListener {
                currentMessage?.let {
                    onSpeakClick(it.content)
                }
            }

            reactionButton.setOnClickListener {
                currentMessage?.let(onReactionClick)
            }

            pinButton.setOnClickListener {
                currentMessage?.let(onPinClick)
            }

            moreButton.setOnClickListener {
                showMenu()
            }
        }

        fun bind(msg: ChatMessage) {
            currentMessage = msg

            val isUser = msg.role == MessageRole.USER

            // Reset alignment and background.
            root.gravity = if (isUser) Gravity.END else Gravity.START

            card.setCardBackgroundColor(
                if (isUser) userBubbleColor else aiBubbleColor
            )

            // Render Markdown only when text changes.
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

            val canShowActions =
                msg.content.isNotBlank() &&
                msg.content != "جاري التفكير..." &&
                !msg.isThinking

            actionBar.visibility =
                if (canShowActions) View.VISIBLE else View.GONE

            // Reset all recycled icon states.
            reactionButton.rotation = 0f
            reactionButton.setColorFilter(iconColor)

            when (msg.reactionType) {
                "LIKE" -> {
                    reactionButton.setColorFilter(Color.BLUE)
                }

                "DISLIKE" -> {
                    reactionButton.setColorFilter(Color.RED)
                    reactionButton.rotation = 180f
                }
            }

            pinButton.setColorFilter(
                if (msg.isPinned) accentColor else iconColor
            )

            // Hide content actions for empty messages.
            copyButton.isEnabled = msg.content.isNotEmpty()
            speakButton.isEnabled = msg.content.isNotEmpty()
        }

        private fun bindImages(msg: ChatMessage) {
            val uris = msg.attachmentUris.orEmpty()

            imageScroll.visibility =
                if (uris.isEmpty()) View.GONE else View.VISIBLE

            // Remove only excess image views.
            while (imageContainer.childCount > uris.size) {
                val index = imageContainer.childCount - 1
                val image = imageContainer.getChildAt(index) as ImageView

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

                            scaleType = ImageView.ScaleType.CENTER_CROP

                            background = GradientDrawable().apply {
                                cornerRadius = dp(
                                    root.context, 12
                                ).toFloat()

                                setColor(Color.TRANSPARENT)
                            }

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

            if (!msg.isThinking && msg.role == MessageRole.AI &&
                onRegenerateClick != null
            ) {
                popup.menu.add(0, 1, 0, "إعادة توليد")
            }

            popup.menu.add(0, 2, 1, "مشاركة")
            popup.menu.add(0, 3, 2, "تعديل")

            popup.setOnMenuItemClickListener { item ->
                // Verify that the holder still represents the same message.
                val current = currentMessage
                    ?: return@setOnMenuItemClickListener false

                if (current.id != msg.id) {
                    return@setOnMenuItemClickListener false
                }

                when (item.itemId) {
                    1 -> onRegenerateClick?.invoke(current)

                    2 -> {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, current.content)
                        }

                        val chooser = Intent.createChooser(
                            intent, "مشاركة الرسالة"
                        )

                        if (root.context !is android.app.Activity) {
                            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }

                        root.context.startActivity(chooser)
                    }

                    3 -> onEditClick(current)
                }

                true
            }

            popup.show()
        }

        fun recycle() {
            currentMessage = null

            // Clear requests before the holder is reused.
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

            reactionButton.rotation = 0f
            reactionButton.setColorFilter(iconColor)
            pinButton.setColorFilter(iconColor)
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

    private fun dp(context: Context, value: Int): Int {
        return (
            value * context.resources.displayMetrics.density
        ).toInt()
    }
}
