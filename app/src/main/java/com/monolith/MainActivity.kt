
package com.monolith

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        replaceCardIcon(
            R.id.cardChat,
            MonolithIcon.CORE,
            0xFF36DDF5.toInt()
        )

        replaceCardIcon(
            R.id.cardFileManager,
            MonolithIcon.FILES,
            0xFF9A75FF.toInt()
        )

        replaceCardIcon(
            R.id.cardImageEditor,
            MonolithIcon.IMAGE,
            0xFF36DDF5.toInt()
        )

        replaceCardIcon(
            R.id.cardDispersion,
            MonolithIcon.DISPERSION,
            0xFFB66CFF.toInt()
        )

        replaceCardIcon(
            R.id.cardSettings,
            MonolithIcon.CONTROL,
            0xFF36DDF5.toInt()
        )

        replaceCardIcon(
            R.id.cardSurgicalLens,
            MonolithIcon.VISION,
            0xFF9A75FF.toInt()
        )

        findViewById<MaterialCardView>(R.id.cardChat)
            .setOnClickListener {
                startActivity(
                    Intent(this, ChatActivity::class.java)
                )
            }

        findViewById<MaterialCardView>(R.id.cardFileManager)
            .setOnClickListener {
                startActivity(
                    Intent(this, FileManagerActivity::class.java)
                )
            }

        findViewById<MaterialCardView>(R.id.cardImageEditor)
            .setOnClickListener {
                startActivity(
                    Intent(this, ImageEditorActivity::class.java)
                )
            }

        findViewById<MaterialCardView>(R.id.cardDispersion)
            .setOnClickListener {
                startActivity(
                    Intent(this, DispersionEditorActivity::class.java)
                )
            }

        findViewById<MaterialCardView>(R.id.cardSettings)
            .setOnClickListener {
                MonolithControlPopup.show(this)
            }

        findViewById<MaterialCardView>(R.id.cardSurgicalLens)
            .setOnClickListener {
                startActivity(
                    Intent(this, SurgicalLensActivity::class.java)
                )
            }
    }

    /**
     * استبدال صورة Android الافتراضية داخل البطاقة
     * برمز Monolith مرسوم برمجياً.
     */
    private fun replaceCardIcon(
        cardId: Int,
        icon: MonolithIcon,
        color: Int
    ) {
        val card = findViewById<MaterialCardView>(cardId)
        val oldIcon = findFirstImageView(card) ?: return
        val parent = oldIcon.parent as? ViewGroup ?: return

        val index = parent.indexOfChild(oldIcon)
        val oldParams = oldIcon.layoutParams

        val customIcon = MonolithIconView(
            this,
            icon,
            color
        ).apply {
            contentDescription = icon.name
            scaleX = 1f
            scaleY = 1f
        }

        parent.removeViewAt(index)
        parent.addView(
            customIcon,
            index,
            oldParams
        )
    }

    /**
     * البحث عن أول ImageView داخل البطاقة
     * حتى لا نحتاج إلى تعديل ملف XML.
     */
    private fun findFirstImageView(
        view: View
    ): ImageView? {

        if (view is ImageView) {
            return view
        }

        if (view !is ViewGroup) {
            return null
        }

        for (index in 0 until view.childCount) {
            val result = findFirstImageView(
                view.getChildAt(index)
            )

            if (result != null) {
                return result
            }
        }

        return null
    }
}
