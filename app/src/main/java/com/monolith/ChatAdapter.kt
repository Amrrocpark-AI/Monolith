package com.monolith

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tables.TableTheme

class ChatAdapter(
    private val context: Context,
    private var messages: List<ChatMessage>,
    private val onEditClick: (ChatMessage) -> Unit,
    private val onReactionClick: (ChatMessage) -> Unit,
    private val onSpeakClick: (String) -> Unit,
    private val onPinClick: (ChatMessage) -> Unit,
    private val themeColorHex: String
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    // إعداد محرك عرض النصوص Markdown مع جداول مخصصة (تم تصحيح الخطأ هنا)
    private val tableTheme = TableTheme.builder()
        .tableCellPadding(16)
        .tableBorderWidth(2)
        .tableBorderColor(Color.parseColor("#444444"))
        .tableHeaderRowBackgroundColor(Color.parseColor("#333333"))
        .tableOddRowBackgroundColor(Color.parseColor("#2A2A2A"))
        .tableEvenRowBackgroundColor(Color.parseColor("#222222"))
        .build()

    private val markwon = Markwon.builder(context)
        .usePlugin(TablePlugin.create(tableTheme))
        .build()

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_AI = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].role == MessageRole.USER) VIEW_TYPE_USER else VIEW_TYPE_AI
    }

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val rootLayout: LinearLayout = itemView as LinearLayout
        val cardView: androidx.cardview.widget.CardView = rootLayout.getChildAt(0) as androidx.cardview.widget.CardView
        val tvMessageContent: TextView = cardView.getChildAt(0) as TextView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val layoutRes = if (viewType == VIEW_TYPE_USER) {
            R.layout.item_message_user
        } else {
            R.layout.item_message_ai
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val msg = messages[position]
        val isUser = msg.role == MessageRole.USER

        // 1. إعداد النص باستخدام Markwon
        if (msg.content.isNotEmpty()) {
            markwon.setMarkdown(holder.tvMessageContent, msg.content)
            holder.tvMessageContent.movementMethod = LinkMovementMethod.getInstance()
            holder.tvMessageContent.visibility = View.VISIBLE
            holder.cardView.visibility = View.VISIBLE
        } else {
            holder.tvMessageContent.visibility = View.GONE
            holder.cardView.visibility = View.GONE
        }

        // 2. إضافة الصور
        holder.rootLayout.removeAllViews()
        holder.rootLayout.addView(holder.cardView)

        if (!msg.attachmentUris.isNullOrEmpty()) {
            val imageContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = if (isUser) Gravity.END else Gravity.START 
                setPadding(0, 0, 0, dpToPx(8))
            }
            val scrollView = HorizontalScrollView(context)
            val imageSizePx = dpToPx(150)

            for (uriString in msg.attachmentUris) {
                val imageView = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(imageSizePx, imageSizePx).apply { setMargins(0, 0, 16, 0) }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    val clip = GradientDrawable().apply { cornerRadius = dpToPx(12).toFloat(); setColor(Color.TRANSPARENT) }
                    background = clip
                    clipToOutline = true
                }
                Glide.with(context).load(uriString).into(imageView)
                imageContainer.addView(imageView)
            }
            scrollView.addView(imageContainer)
            holder.rootLayout.addView(scrollView, 0)
        }

        // 3. إضافة شريط الإجراءات
        if (holder.rootLayout.childCount > 1) {
            for (i in holder.rootLayout.childCount - 1 downTo 1) {
                holder.rootLayout.removeViewAt(i)
            }
        }

        if (msg.content.isNotEmpty() && msg.content != "جاري التفكير..." && !msg.isThinking) {
            val actionBar = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpToPx(4), dpToPx(8), dpToPx(4), 0)
            }

            fun createActionIcon(resId: Int, tintColor: Int, onClick: () -> Unit): ImageView {
                return ImageView(context).apply {
                    setImageResource(resId)
                    setColorFilter(tintColor)
                    val params = LinearLayout.LayoutParams(dpToPx(20), dpToPx(20))
                    params.setMargins(0, 0, dpToPx(16), 0)
                    layoutParams = params
                    setOnClickListener { onClick() }
                }
            }

            actionBar.addView(createActionIcon(R.drawable.ic_copy, Color.parseColor("#88FFFFFF")) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Monolith", msg.content))
                Toast.makeText(context, "تم نسخ الرسالة", Toast.LENGTH_SHORT).show()
            })

            actionBar.addView(createActionIcon(R.drawable.ic_volume_up, Color.parseColor("#88FFFFFF")) {
                onSpeakClick(msg.content)
            })

            val thumbIcon = createActionIcon(R.drawable.ic_thumb_hybrid, Color.parseColor("#88FFFFFF")) {
                onReactionClick(msg)
            }
            when (msg.reactionType) {
                "LIKE" -> { thumbIcon.setColorFilter(Color.BLUE); thumbIcon.rotation = 0f }
                "DISLIKE" -> { thumbIcon.setColorFilter(Color.RED); thumbIcon.rotation = 180f }
            }
            actionBar.addView(thumbIcon)

            val pinIcon = createActionIcon(R.drawable.ic_pinned, if (msg.isPinned) Color.parseColor("#00D4FF") else Color.parseColor("#88FFFFFF")) {
                onPinClick(msg)
            }
            actionBar.addView(pinIcon)

            actionBar.addView(createActionIcon(R.drawable.ic_more_vert, Color.parseColor("#88FFFFFF")) {
                val popup = PopupMenu(context, actionBar.getChildAt(actionBar.childCount - 1))
                popup.menu.add("🔄 إعادة توليد")
                popup.menu.add("📤 مشاركة")
                popup.menu.add("✏️ تعديل")
                popup.setOnMenuItemClickListener { item ->
                    when(item.title) {
                        "🔄 إعادة توليد" -> Toast.makeText(context, "سيتم التوليد قريباً", Toast.LENGTH_SHORT).show()
                        "📤 مشاركة" -> {
                            val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, msg.content) }
                            context.startActivity(Intent.createChooser(intent, "مشاركة"))
                        }
                        "✏️ تعديل" -> onEditClick(msg)
                    }
                    true
                }
                popup.show()
            })

            if (isUser) {
                actionBar.gravity = Gravity.END
            } else {
                actionBar.gravity = Gravity.START
            }

            holder.rootLayout.addView(actionBar)
        }
    }

    override fun getItemCount(): Int = messages.size

    fun updateMessages(newMessages: List<ChatMessage>) {
        this.messages = newMessages
        notifyDataSetChanged()
    }

    private fun dpToPx(dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()
}
