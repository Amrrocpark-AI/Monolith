package com.monolith

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotesActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var noteAdapter: NoteAdapter
    private var notesList: MutableList<NoteEntity> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notes)

        db = AppDatabase(applicationContext)

        val recyclerViewNotes = findViewById<RecyclerView>(R.id.recyclerViewNotes)
        recyclerViewNotes.layoutManager = LinearLayoutManager(this)
        noteAdapter = NoteAdapter(
            notes = notesList,
            onNoteClick = { note -> showEditNoteDialog(note) },
            onNoteLongClick = { note -> showDeleteNoteDialog(note) }
        )
        recyclerViewNotes.adapter = noteAdapter

        findViewById<ImageButton>(R.id.btnBackNotes).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnAddNote).setOnClickListener { showAddNoteDialog() }

        loadNotes()
    }

    private fun loadNotes() {
        lifecycleScope.launch {
            try {
                // استخدام AppDatabase بدلاً من NoteDao
                val notes = withContext(Dispatchers.IO) { db.getAllNotes() }
                notesList.clear()
                notesList.addAll(notes)
                noteAdapter.updateNotes(notesList)
            } catch (e: Exception) {
                Toast.makeText(this@NotesActivity, "فشل تحميل المذكرات", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAddNoteDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        val etTitle = EditText(this).apply { hint = "العنوان" }
        val etContent = EditText(this).apply { 
            hint = "المحتوى" 
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
        }
        layout.addView(etTitle)
        layout.addView(etContent)

        AlertDialog.Builder(this)
            .setTitle("مذكرة جديدة")
            .setView(layout)
            .setPositiveButton("حفظ") { _, _ ->
                val title = etTitle.text.toString().trim()
                val content = etContent.text.toString().trim()
                if (title.isNotEmpty() || content.isNotEmpty()) {
                    val note = NoteEntity(title = title, content = content)
                    lifecycleScope.launch {
                        // استخدام AppDatabase
                        withContext(Dispatchers.IO) { db.insertNote(note) }
                        loadNotes()
                        Toast.makeText(this@NotesActivity, "تم حفظ المذكرة", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showEditNoteDialog(note: NoteEntity) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        val etTitle = EditText(this).apply { setText(note.title); hint = "العنوان" }
        val etContent = EditText(this).apply { 
            setText(note.content); hint = "المحتوى"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
        }
        layout.addView(etTitle)
        layout.addView(etContent)

        AlertDialog.Builder(this)
            .setTitle("تعديل المذكرة")
            .setView(layout)
            .setPositiveButton("حفظ") { _, _ ->
                val title = etTitle.text.toString().trim()
                val content = etContent.text.toString().trim()
                if (title.isNotEmpty() || content.isNotEmpty()) {
                    val updatedNote = note.copy(title = title, content = content)
                    lifecycleScope.launch {
                        // استخدام AppDatabase
                        withContext(Dispatchers.IO) { db.insertNote(updatedNote) }
                        loadNotes()
                        Toast.makeText(this@NotesActivity, "تم تعديل المذكرة", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showDeleteNoteDialog(note: NoteEntity) {
        AlertDialog.Builder(this)
            .setTitle("حذف المذكرة")
            .setMessage("هل أنت متأكد من حذف '${note.title}'؟")
            .setPositiveButton("حذف") { _, _ ->
                lifecycleScope.launch {
                    // استخدام AppDatabase
                    withContext(Dispatchers.IO) { db.deleteNote(note.id) }
                    loadNotes()
                    Toast.makeText(this@NotesActivity, "تم حذف المذكرة", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
}
