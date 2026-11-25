
package com.monolith

import android.content.Context
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject

class MessageCardBridge(
    context: Context,
    private val actionHandler: (
        action: String,
        messageId: String,
        payload: String
    ) -> Unit
) {

    private var pageReady = false
    private var pendingMessages: List<ChatMessage> = emptyList()
    private var pendingConversationId: String = ""

    // يمنع نتائج العرض القديمة من التأثير على المحادثة الحالية.
    private var displayGeneration = 0

    // يمنع إعادة إرسال بيانات العرض بعد إلغاء صلاحيتها.
    private var hasValidPendingDisplay = false

    // يحتفظ بطلبات حذف تخصيصات المحادثات حتى تصبح صفحة العرض جاهزة.
    private val pendingConversationStateDeletions = LinkedHashSet<String>()

    val webView: WebView = WebView(context)

    init {
        webView.apply {
            setBackgroundColor(Color.TRANSPARENT)

            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = false

            addJavascriptInterface(
                AndroidCallbacks(),
                "MonolithAndroid"
            )

            webViewClient = object : WebViewClient() {

                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {
                    super.onPageFinished(view, url)

                                        if (url?.endsWith("monolith_canvas.html") == true) {
                        pageReady = true
                        flushPendingConversationStateDeletions()
                        sendPendingMessages()
                    }
                }
            }

            loadUrl(
                "file:///android_asset/monolith_canvas.html"
            )
        }
    }

    fun submitMessages(
        messages: List<ChatMessage>,
        conversationId: String = ""
    ) {
        displayGeneration++

        pendingMessages = messages.toList()
        pendingConversationId = conversationId
        hasValidPendingDisplay = true

        val generation = displayGeneration

                webView.post {
            if (pageReady) {
                flushPendingConversationStateDeletions()
            }

            sendPendingMessages(generation)
                }
    }
fun deleteConversationState(conversationId: String) {
    val safeConversationId = conversationId.trim()

    if (safeConversationId.isBlank()) return

    webView.post {
        pendingConversationStateDeletions.add(safeConversationId)

        if (pageReady) {
            flushPendingConversationStateDeletions()
        }
    }
}

private fun flushPendingConversationStateDeletions() {
    if (!pageReady || pendingConversationStateDeletions.isEmpty()) return

        val conversationIds = pendingConversationStateDeletions.toList()

    conversationIds.forEach { conversationId ->
        val javascript =
            "window.MonolithCards && " +
            "typeof window.MonolithCards.deleteConversationState === 'function' " +
            "? window.MonolithCards.deleteConversationState(" +
            "${JSONObject.quote(conversationId)}) : false;"

        webView.evaluateJavascript(javascript) { result ->
            if (result == "true") {
                pendingConversationStateDeletions.remove(conversationId)
            }
        }
    }
}
    // إلغاء صلاحية أي عملية عرض سابقة عند الانتقال بين المحادثات.
    fun invalidatePendingDisplay() {
        displayGeneration++
        hasValidPendingDisplay = false
        webView.visibility = android.view.View.INVISIBLE
    }

    private fun sendPendingMessages(
        generation: Int = displayGeneration
    ) {
        if (!pageReady || !hasValidPendingDisplay) return

        // تجاهل أي عملية أصبحت قديمة قبل تنفيذ JavaScript.
        if (generation != displayGeneration) return

        // استخدام نسخة ثابتة من بيانات العملية الحالية.
        val messages = pendingMessages.toList()
        val conversationId = pendingConversationId

        val cards = JSONArray()

        messages.forEachIndexed { index, message ->

            val senderType = message.senderType

            val entityType = when (senderType) {
                MessageSenderType.SELF -> "self"
                MessageSenderType.PEER -> "peer"
                MessageSenderType.AI -> "ai"
            }

            val owner = when (senderType) {
                MessageSenderType.SELF -> "mine"
                MessageSenderType.PEER -> "peer"
                MessageSenderType.AI -> "ai"
            }

            val title = when (senderType) {
                MessageSenderType.SELF -> "أنت"
                MessageSenderType.PEER -> "PEER"
                MessageSenderType.AI -> message.modelName ?: "MONOLITH"
            }

            val card = JSONObject().apply {
                put("id", "message-${message.id}")
                put("messageId", message.id)
                put("entityType", entityType)
                put("owner", owner)
                put("title", title)

                put("text", message.content)

                put("x", 20)
                put("y", 20 + (index * 175))

                put("bgPreset", "default")
                put("dynState", "off")
                put("dynLockedActive", false)
                put("dynPattern", "pulse")
                put("dynSpeed", 8)
                put("dynIntensity", 20)
                put("hasCustomDyn", false)

                put("role", message.role.name)
                put("timestamp", message.timestamp)
                put("isPinned", message.isPinned)
                put("reactionType", message.reactionType ?: JSONObject.NULL)
                put("isThinking", message.isThinking)
                put("isStreaming", message.isStreaming)
                put("status", message.status.name)

                put(
                    "attachments",
                    JSONArray(
                        message.attachmentUris ?: emptyList<String>()
                    )
                )
            }

            cards.put(card)
        }

        val json = cards.toString()

        val javascript =
            "(function() {" +
            "if (window.MonolithCards && " +
            "typeof window.MonolithCards.setMessages === 'function') {" +
            "window.MonolithCards.setMessages(" +
            "JSON.parse(${JSONObject.quote(json)})," +
            "${JSONObject.quote(conversationId)}" +
            ");" +
            "return true;" +
            "}" +
            "return false;" +
            "})();"

        webView.evaluateJavascript(javascript) { result ->

            // لا نسمح لنتيجة قديمة بإظهار WebView.
            if (
                result == "true" &&
                generation == displayGeneration &&
                hasValidPendingDisplay
            ) {
                webView.visibility = android.view.View.VISIBLE
            }
        }
    }

    private inner class AndroidCallbacks {

        @JavascriptInterface
        fun onCardAction(
            action: String?,
            messageId: String?,
            payload: String?
        ) {
            val safeAction = action ?: return
            val safeMessageId = messageId ?: return
            val safePayload = payload ?: ""

            webView.post {
                actionHandler(
                    safeAction,
                    safeMessageId,
                    safePayload
                )
            }
        }
    }

    fun release() {
        displayGeneration++
        hasValidPendingDisplay = false
        pageReady = false

        webView.stopLoading()
        webView.removeJavascriptInterface("MonolithAndroid")
        webView.loadUrl("about:blank")
        webView.destroy()
    }
}
