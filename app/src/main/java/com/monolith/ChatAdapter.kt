
package com.monolith

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.method.LinkMovementMethod
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
    private val context: Context,
    private val onEditClick: (ChatMessage) -> Unit,
    private val onReactionClick: (ChatMessage) -> Unit,
    private val onSpeakClick: (String) -> Unit,
    private val onPinClick: (ChatMessage) -> Unit,
    private val themeColorHex: String,
    private val onRegenerateClick: ((ChatMessage) -> Unit)? = null
) : ListAdapter<ChatMessage, ChatAdapter.ChatViewHolder>(DIFF_CALLBACK) {

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_AI = 2

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ChatMessage>() {
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

    private val markwon: Markwon = Markwon.builder(context)
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

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).role == MessageRole.USER) {
            VIEW_TYPE_USER
        } else {
            VIEW_TYPE_AI
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ChatViewHolder {
        return ChatViewHolder(
            createMessageLayout(parent.context, viewType)
        )
    }

    private fun createMessageLayout(
        ctx: Context,
        viewType: Int
    ): LinearLayout {

        val isUser = viewType == VIEW_TYPE_USER

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (isUser) Gravity.END else Gravity.START

            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            setPadding(
                dp(8), dp(6), dp(8), dp(6)
            )

            // Message card
            val card = CardView(ctx).apply {
                radius = dp(14).toFloat()
                cardElevation = dp(2).toFloat()
                useCompatPadding = true

                val backgroundColor = if (isUser) {
                    runCatching {
                        Color.parseColor(themeColorHex)
                    }.getOrDefault(Color.rgb(45, 85, 125))
                } else {
                    Color.parseColor("#222222")
                }

                setCardBackgroundColor(backgroundColor)

                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    weight = 0f
                    gravity = if (isUser) Gravity.END else Gravity.START
                }

                val text = TextView(ctx).apply {
                    textSize = 15f
                    setTextColor(Color.WHITE)
                    setPadding(dp(14), dp(10), dp(14), dp(10))
                    setTextIsSelectable(true)
                    movementMethod = LinkMovementMethod.getInstance()
                    layoutParams = CardView.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

                addView(text)
            }

            addView(card)

            // Images container
            val scroll = HorizontalScrollView(ctx).apply {
                isHorizontalScrollBarEnabled = false
                visibility = View.GONE

                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val imageContainer = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = if (isUser) Gravity.END else Gravity.START
            }

            scroll.addView(imageContainer)
            addView(scroll)

            // Action bar: created only once
            val actionBar = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = if (isUser) Gravity.END else Gravity.START
                setPadding(dp(4), dp(8), dp(4), 0)

                visibility = View.GONE
            }

            fun createIcon(resource: Int): ImageView {
                return ImageView(ctx).apply {
                    setImageResource(resource)
                    setColorFilter(Color.parseColor("#88FFFFFF"))

                    val params = LinearLayout.LayoutParams(
                        dp(20), dp(20)
                    ).apply {
                        marginEnd = dp(16)
                    }

                    layoutParams = params
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    contentDescription = "Message action"
                }
            }

            actionBar.addView(createIcon(R.drawable.ic_copy))
            actionBar.addView(createIcon(R.drawable.ic_volume_up))
            actionBar.addView(createIcon(R.drawable.ic_thumb_hybrid))
            actionBar.addView(createIcon(R.drawable.ic_pinned))
            actionBar.addView(createIcon(R.drawable.ic_more_vert))

            addView(actionBar)
        }
    }

    inner class ChatViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        private val root = itemView as LinearLayout
        private val card = root.getChildAt(0) as CardView
        private val messageText = card.getChildAt(0) as TextView
        private val imageScroll = root.getChildAt(1) as HorizontalScrollView
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
            // Listeners are registered once, not on every bind.

            copyButton.setOnClickListener {
                val msg = currentMessage ?: return@setOnClickListener

                val clipboard = context.getSystemService(
                    Context.CLIPBOARD_SERVICE
                ) as ClipboardManager

                clipboard.setPrimaryClip(
                    ClipData.newPlainText("Monolith", msg.content)
                )

                Toast.makeText(
                    context,
                    "تم نسخ الرسالة",
                    Toast.LENGTH_SHORT
                ).show()
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
                showMessageMenu()
            }
        }

        fun bind(msg: ChatMessage) {
            currentMessage = msg

            val isUser = msg.role == MessageRole.USER

            // 1. Markdown: render only when content changes.
            if (renderedContent != msg.content) {
                if (msg.content.isNotEmpty()) {
                    markwon.setMarkdown(messageText, msg.content)
                } else {
                    messageText.text = ""
                }

                renderedContent = msg.content
            }

            card.visibility = if (msg.content.isNotEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }

            // 2. Images: reuse existing ImageViews.
            bindImages(msg)

            // 3. Action bar visibility.
            val showActions =
                msg.content.isNotEmpty() &&
                msg.content != "جاري التفكير..." &&
                !msg.isThinking

            actionBar.visibility = if (showActions) {
                View.VISIBLE
            } else {
                View.GONE
            }

            // 4. Reset reaction state every bind.
            reactionButton.rotation = 0f

            when (msg.reactionType) {
                "LIKE" -> {
                    reactionButton.setColorFilter(Color.BLUE)
                }

                "DISLIKE" -> {
                    reactionButton.setColorFilter(Color.RED)
                    reactionButton.rotation = 180f
                }

                else -> {
                    reactionButton.setColorFilter(
                        Color.parseColor("#88FFFFFF")
                    )
                }
            }

            // 5. Pin state.
            pinButton.setColorFilter(
                if (msg.isPinned) {
                    Color.parseColor("#00D4FF")
                } else {
                    Color.parseColor("#88FFFFFF")
                }
            )

            // 6. Align content.
            root.gravity = if (isUser) {
                Gravity.END
            } else {
                Gravity.START
            }
        }

        private fun bindImages(msg: ChatMessage) {
            val uris = msg.attachmentUris.orEmpty()

            imageScroll.visibility = if (uris.isEmpty()) {
                View.GONE
            } else {
                View.VISIBLE
            }

            // Remove only excess views, never rebuild the entire root.
            while (imageContainer.childCount > uris.size) {
                val index = imageContainer.childCount - 1
                val image = imageContainer.getChildAt(index) as ImageView
                Glide.with(image).clear(image)
                imageContainer.removeViewAt(index)
            }

            uris.forEachIndexed { index, uri ->

                val imageView: ImageView

                if (index < imageContainer.childCount) {
                    imageView = imageContainer.getChildAt(index) as ImageView
                } else {
                    imageView = ImageView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            dp(150),
                            dp(150)
                        ).apply {
                            marginEnd = dp(8)
                        }

                        scaleType = ImageView.ScaleType.CENTER_CROP

                        background = GradientDrawable().apply {
                            cornerRadius = dp(12).toFloat()
                            setColor(Color.TRANSPARENT)
                        }

                        clipToOutline = true
                    }

                    imageContainer.addView(imageView)
                }

                Glide.with(imageView)
                    .load(uri)
                    .centerCrop()
                    .into(imageView)
            }
        }

        private fun showMessageMenu() {
            val msg = currentMessage ?: return

            val popup = PopupMenu(context, moreButton)

            if (onRegenerateClick != null) {
                popup.menu.add(0, 1, 0, "🔄 إعادة توليد")
            }

            popup.menu.add(0, 2, 1, "📤 مشاركة")
            popup.menu.add(0, 3, 2, "✏️ تعديل")

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> onRegenerateClick?.invoke(msg)

                    2 -> {
                        val intent = Intent(
                            Intent.ACTION_SEND
                        ).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, msg.content)
                        }

                        context.startActivity(
                            Intent.createChooser(intent, "مشاركة")
                        )
                    }

                    3 -> onEditClick(msg)
                }

                true
            }

            popup.show()
        }

        fun recycle() {
            currentMessage = null

            for (i in 0 until imageContainer.childCount) {
                val image = imageContainer.getChildAt(i) as ImageView
                Glide.with(image).clear(image)
            }
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

    fun updateMessages(newMessages: List<ChatMessage>) {
        submitList(newMessages.toList())
    }

    private fun dp(value: Int): Int {
        return (
            value * context.resources.displayMetrics.density
        ).toInt()
    }
}
