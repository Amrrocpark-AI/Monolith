package com.monolith

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.Selection
import android.text.Spannable
import android.view.ActionMode
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView

class ChatAdapter(
    private val context: Context,
    private var messages: List<ChatMessage>,
    private val onEditClick: (ChatMessage) -> Unit,
    private val onReactionClick: (ChatMessage) -> Unit,
    private val onSpeakClick: (String) -> Unit,
    private val onPinClick: (ChatMessage) -> Unit,
    private val themeColorHex: String
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageLayout: LinearLayout = itemView as LinearLayout
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LinearLayout(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding(0, dpToPx(8), 0, dpToPx(8))
        }
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val msg = messages[position]
        val container = holder.messageLayout
        container.removeAllViews()

        val messageLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        if (!msg.attachmentUris.isNullOrEmpty()) {
            val scrollView = HorizontalScrollView(context)
            val imageContainer = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            val imageSizePx = dpToPx(180)

            for (uriString in msg.attachmentUris) {
                val imageView = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(imageSizePx, imageSizePx).apply { setMargins(0, 0, 16, 16) }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    val clip = GradientDrawable().apply { cornerRadius = dpToPx(16).toFloat(); setColor(Color.TRANSPARENT) }
                    background = clip
                    clipToOutline = true
                }
                Glide.with(context).load(uriString).into(imageView)
                imageContainer.addView(imageView)
            }
            scrollView.addView(imageContainer)
            messageLayout.addView(scrollView)
        }

        val textView = TextView(context)
        textView.text = msg.content
        textView.textSize = 15f
        
        if (msg.isThinking) {
            textView.setTextColor(Color.parseColor("#AAAAAA"))
            textView.setTypeface(null, android.graphics.Typeface.ITALIC)
        } else {
            textView.setTextColor(if (msg.role == MessageRole.USER) Color.BLACK else Color.WHITE)
        }
        
        textView.textDirection = if (msg.role == MessageRole.USER) TextView.TEXT_DIRECTION_RTL else TextView.TEXT_DIRECTION_LOCALE
        textView.textAlignment = TextView.TEXT_ALIGNMENT_VIEW_START
        textView.setLineSpacing(dpToPx(4).toFloat(), 1.1f)
        textView.setTextIsSelectable(true)

        textView.customSelectionActionModeCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                menu?.clear()
                menu?.add(0, 1, 0, "🔊 قراءة")?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                menu?.add(0, 2, 1, "📋 نسخ")?.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                menu?.add(0, 3, 2, "تحديد الكل")?.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                return true
            }
            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false
            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                val start = textView.selectionStart
                val end = textView.selectionEnd
                if (start < 0 || end < 0) return false
                val selectedText = textView.text.substring(start, end)
                
                when (item?.itemId) {
                    1 -> { onSpeakClick(selectedText); mode?.finish(); return true }
                    2 -> { 
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Monolith", selectedText))
                        Toast.makeText(context, "تم النسخ", Toast.LENGTH_SHORT).show()
                        mode?.finish(); return true 
                    }
                    3 -> { 
                        val text = textView.text
                        if (text is Spannable) {
                            Selection.selectAll(text)
                        }
                        return true 
                    }
                }
                return false
            }
            override fun onDestroyActionMode(mode: ActionMode?) {}
        }

        if (msg.content.isNotEmpty()) {
            val textBubble = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
                background = GradientDrawable().apply { 
                    setColor(Color.parseColor(msg.colorHex))
                    cornerRadius = dpToPx(20).toFloat() 
                }
            }
            if (msg.isEdited) {
                val redLine = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dpToPx(4), ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(Color.RED)
                }
                val horizontalLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                horizontalLayout.addView(redLine)
                horizontalLayout.addView(textView)
                textBubble.addView(horizontalLayout)
                
                val eyeIcon = ImageView(context).apply {
                    setImageResource(R.drawable.ic_vision)
                    setColorFilter(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(dpToPx(20), dpToPx(20)).apply { setMargins(dpToPx(8), 0, 0, 0) }
                    setOnClickListener {
                        val dialog = AlertDialog.Builder(context)
                        dialog.setTitle("تفاصيل التعديل")
                        dialog.setMessage("النص الأصلي:\n${msg.originalContent}\n\nسبب التعديل:\n${msg.editReason ?: "غير محدد"}")
                        dialog.setPositiveButton("حسناً", null)
                        dialog.show()
                    }
                }
                textBubble.addView(eyeIcon)
            } else {
                textBubble.addView(textView)
            }
            messageLayout.addView(textBubble)
        }

        if (msg.content.isNotEmpty() && msg.content != "جاري التفكير..." && !msg.isThinking) {
            val actionBar = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpToPx(8), dpToPx(8), dpToPx(8), 0)
            }

            fun createActionIcon(resId: Int, tintColor: Int, onClick: () -> Unit): ImageView {
                return ImageView(context).apply {
                    setImageResource(resId)
                    setColorFilter(tintColor)
                    val params = LinearLayout.LayoutParams(dpToPx(22), dpToPx(22))
                    params.setMargins(0, 0, dpToPx(16), 0)
                    layoutParams = params
                    setOnClickListener { onClick() }
                }
            }

            actionBar.addView(createActionIcon(R.drawable.ic_copy, Color.parseColor("#88FFFFFF")) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Monolith", msg.content))
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

            val heartIcon = createActionIcon(R.drawable.ic_heart, if (msg.reactionColor != null) Color.parseColor(msg.reactionColor) else Color.parseColor("#88FFFFFF")) {
                onReactionClick(msg)
            }
            actionBar.addView(heartIcon)
            
            val pinIcon = createActionIcon(R.drawable.ic_pinned, if (msg.isPinned) Color.parseColor("#00D4FF") else Color.parseColor("#88FFFFFF")) {
                onPinClick(msg)
            }
            actionBar.addView(pinIcon)

            actionBar.addView(createActionIcon(R.drawable.ic_more_vert, Color.parseColor("#88FFFFFF")) {
                val popup = PopupMenu(context, actionBar.getChildAt(5))
                popup.menu.add("🔄 إعادة توليد")
                popup.menu.add("📤 مشاركة")
                popup.menu.add("✏️ تعديل")
                popup.setOnMenuItemClickListener { item ->
                    when(item.title) {
                        "🔄 إعادة توليد" -> Toast.makeText(context, "سيتم التوليد قريباً", Toast.LENGTH_SHORT).show()
                        "📤 مشاركة" -> {
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, msg.content) }
                            context.startActivity(android.content.Intent.createChooser(intent, "مشاركة"))
                        }
                        "✏️ تعديل" -> {
                            onEditClick(msg)
                        }
                    }
                    true
                }
                popup.show()
            })

            if (msg.role == MessageRole.USER) {
                actionBar.gravity = Gravity.END
            } else {
                actionBar.gravity = Gravity.START
            }

            messageLayout.addView(actionBar)
        }

        if (msg.role == MessageRole.USER) { 
            messageLayout.gravity = Gravity.END
        } else { 
            messageLayout.gravity = Gravity.START
        }
        
        container.addView(messageLayout)
    }

    override fun getItemCount(): Int = messages.size

    fun updateMessages(newMessages: List<ChatMessage>) {
        this.messages = newMessages
        notifyDataSetChanged()
    }

    private fun dpToPx(dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()
}
