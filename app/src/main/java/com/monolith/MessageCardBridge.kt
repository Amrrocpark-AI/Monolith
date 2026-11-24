
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
                        sendPendingMessages()
                    }
                }
            }

            loadUrl(
                "file:///android_asset/monolith_canvas.html"
            )
        }
    }

    fun submitMessages(messages: List<ChatMessage>) {
        pendingMessages = messages.toList()

        webView.post {
            sendPendingMessages()
        }
    }

    private fun sendPendingMessages() {
        if (!pageReady) return

        val cards = JSONArray()

        pendingMessages.forEachIndexed { index, message ->

            val isUser = message.role == MessageRole.USER

            val card = JSONObject().apply {
                put("id", "message-${message.id}")
                put("messageId", message.id)

                put(
                    "entityType",
                    if (isUser) "self" else "ai"
                )

                put(
                    "owner",
                    if (isUser) "mine" else "ai"
                )

                put("title", if (isUser) "أنت" else {
                    message.modelName ?: "MONOLITH"
                })

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
            "JSON.parse(${JSONObject.quote(json)})" +
            ");" +
            "return true;" +
            "}" +
            "return false;" +
            "})();"

        webView.evaluateJavascript(javascript) { result ->
            if (result == "true") {
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
        pageReady = false

        webView.stopLoading()
        webView.removeJavascriptInterface("MonolithAndroid")
        webView.loadUrl("about:blank")
        webView.destroy()
    }
}
