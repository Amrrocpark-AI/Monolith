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
import android.webkit.WebView
import android.webkit.WebViewClient
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

        if (msg.content.isNotEmpty()) {
            val webView = WebView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                webViewClient = WebViewClient()
            }

            val isUser = msg.role == MessageRole.USER
            val bubbleColor = if (isUser) themeColorHex else "#2A2A2A"
            val textColor = if (isUser) "#000000" else "#FFFFFF"
            
            val htmlContent = buildHtml(msg.content, bubbleColor, textColor)
            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
            messageLayout.addView(webView)
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

    private fun buildHtml(content: String, bubbleColor: String, textColor: String): String {
        return """
            <html>
            <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                body {
                    background-color: $bubbleColor;
                    color: $textColor;
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                    margin: 0;
                    padding: 12px;
                    border-radius: 20px;
                    box-shadow: 0 4px 12px rgba(0,0,0,0.5);
                    word-wrap: break-word;
                    font-size: 16px;
                    line-height: 1.5;
                }
                pre {
                    background-color: #1A1A1A;
                    padding: 12px;
                    border-radius: 8px;
                    overflow-x: auto;
                    color: #00FF00;
                    font-size: 14px;
                }
                code {
                    font-family: 'Courier New', Courier, monospace;
                    background-color: #1A1A1A;
                    padding: 2px 4px;
                    border-radius: 4px;
                }
                table {
                    border-collapse: collapse;
                    width: 100%;
                    margin-top: 10px;
                    margin-bottom: 10px;
                }
                th, td {
                    border: 1px solid #444;
                    padding: 8px;
                    text-align: left;
                }
                th {
                    background-color: #333;
                }
            </style>
            </head>
            <body>
                $content
            </body>
            </html>
        """.trimIndent()
    }

    private fun dpToPx(dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()
}
