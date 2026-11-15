package com.monolith

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

class MonolithAccessibilityService : AccessibilityService() {

    companion object {
        var instance: MonolithAccessibilityService? = null
            private set
        private const val TAG = "MonolithA11y"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility Service Connected. Agent has hands and eyes.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // يمكننا لاحقاً استخدام هذا لمراقبة تغيرات الشاشة
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    // ==========================================
    // 1. العيون: قراءة الشجرة الكاملة (Surgical Querying)
    // ==========================================
    fun getScreenTree(): String {
        val rootNode = rootInActiveWindow ?: return "[]"
        try {
            val jsonArray = JSONArray()
            
            // *** تصحيح الخطأ هنا: استخدام childCount و getChild بدلاً من children ***
            val childCount = rootNode.childCount
            for (i in 0 until childCount) {
                val child = rootNode.getChild(i)
                if (child != null) {
                    val nodeJson = parseNode(child)
                    if (nodeJson != null) {
                        jsonArray.put(nodeJson)
                    }
                }
            }
            
            return jsonArray.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing tree", e)
            return "[]"
        }
    }

    private fun parseNode(node: AccessibilityNodeInfo?): JSONObject? {
        if (node == null) return null
        try {
            val json = JSONObject()
            
            // النصوص
            if (node.text != null) json.put("text", node.text.toString())
            if (node.contentDescription != null) json.put("desc", node.contentDescription.toString())
            
            // النوع والحالة
            if (node.className != null) json.put("class", node.className.toString())
            if (node.viewIdResourceName != null) json.put("id", node.viewIdResourceName)
            
            json.put("clickable", node.isClickable)
            json.put("editable", node.isEditable)
            json.put("scrollable", node.isScrollable)

            // الإحداثيات الدقيقة (X, Y, Width, Height)
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            val boundsJson = JSONObject()
            boundsJson.put("x", bounds.left)
            boundsJson.put("y", bounds.top)
            boundsJson.put("w", bounds.width())
            boundsJson.put("h", bounds.height())
            json.put("bounds", boundsJson)

            // الأبناء (بشكل متداخل)
            val childrenArray = JSONArray()
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    val childJson = parseNode(child)
                    if (childJson != null) childrenArray.put(childJson)
                }
            }
            if (childrenArray.length() > 0) {
                json.put("children", childrenArray)
            }
            return json
        } catch (e: Exception) {
            return null
        }
    }

    // ==========================================
    // 2. الأيادي: تنفيذ الإجراءات
    // ==========================================

    fun clickByCoordinates(x: Int, y: Int): Boolean {
        try {
            val path = Path()
            path.moveTo(x.toFloat(), y.toFloat())
            val gestureBuilder = GestureDescription.Builder()
            gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            return dispatchGesture(gestureBuilder.build(), null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error clicking coordinates", e)
            return false
        }
    }

    fun clickNodeByText(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val nodes = rootNode.findAccessibilityNodeInfosByText(text)
        if (nodes.isNotEmpty()) {
            val node = nodes[0]
            if (node.isClickable) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            else {
                var parent = node.parent
                while (parent != null) {
                    if (parent.isClickable) return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    parent = parent.parent
                }
            }
        }
        return false
    }

    fun getCoordinatesByText(text: String): Pair<Int, Int>? {
        val rootNode = rootInActiveWindow ?: return null
        val nodes = rootNode.findAccessibilityNodeInfosByText(text)
        if (nodes.isNotEmpty()) {
            val bounds = android.graphics.Rect()
            nodes[0].getBoundsInScreen(bounds)
            return Pair(bounds.centerX(), bounds.centerY())
        }
        return null
    }

    fun typeText(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val focusableNode = findEditableNode(rootNode) ?: return false
        
        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return focusableNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    private fun findEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val result = findEditableNode(node.getChild(i))
            if (result != null) return result
        }
        return null
    }

    fun scroll(forward: Boolean = true): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        return rootNode.performAction(
            if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD 
            else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        )
    }
}
