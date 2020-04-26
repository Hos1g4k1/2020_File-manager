package com.matf.filemanager

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.webkit.MimeTypeMap
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.matf.filemanager.launcher.ImageFileActivity
import com.matf.filemanager.launcher.TextFileActivity
import com.matf.filemanager.launcher.VideoFileActivity
import com.matf.filemanager.manager.FileManager
import com.matf.filemanager.service.FileActionReceiver
import com.matf.filemanager.service.FileActionService
import com.matf.filemanager.util.*
import java.io.File


class MainActivity : AppCompatActivity(), FileManagerChangeListener {

    private lateinit var lFileEntries: ListView

    private lateinit var bBack: Button
    private lateinit var bForward: Button
    private lateinit var bRefresh: Button

    private lateinit var layoutBottomMenu: LinearLayout
    private lateinit var bCopy: Button
    private lateinit var bCut: Button
    private lateinit var bDelete: Button
    private lateinit var bPaste: Button

    private lateinit var adapter: FileEntryAdapter
    private lateinit var fileActionReceiver: FileActionReceiver

    private fun handleBackClick(){
        when(FileManager.menuMode) {
            MenuMode.OPEN -> {
                if (!FileManager.goBack()) {
                    Toast.makeText(this, "greska", Toast.LENGTH_SHORT).show()
                }
            }
            MenuMode.SELECT -> {
                FileManager.toggleSelectionMode()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        adapter = FileEntryAdapter(this)
        FileManager.setListener(this)

        lFileEntries = findViewById(R.id.lFileEntries)
        lFileEntries.adapter = adapter

        bBack = findViewById(R.id.bBack)
        bForward = findViewById(R.id.bForward)
        bRefresh = findViewById(R.id.bRefresh)

        layoutBottomMenu = findViewById(R.id.layoutBottomMenu)
        bCopy = findViewById(R.id.bCopy)
        bCut = findViewById(R.id.bCut)
        bDelete = findViewById(R.id.bDelete)
        bPaste = findViewById(R.id.bPaste)

        val permission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        if (permission == PackageManager.PERMISSION_GRANTED) {
            initDirectory()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 0
            )
        }

        bBack.setOnClickListener {
            handleBackClick()
        }

        bForward.setOnClickListener {
            if (!FileManager.goForward()) {
                Toast.makeText(this, "greska", Toast.LENGTH_SHORT).show()
            }
        }

        bRefresh.setOnClickListener {
            FileManager.refresh()
        }

        bCopy.setOnClickListener {
            FileManager.moveSelectedToClipboard(ClipboardMode.COPY)
        }

        bCut.setOnClickListener {
            FileManager.moveSelectedToClipboard(ClipboardMode.CUT)
        }

        bDelete.setOnClickListener {
            FileManager.deleteSelected()
        }

        bPaste.setOnClickListener {
            FileManager.paste()
        }

        fileActionReceiver = FileActionReceiver(Handler())
        fileActionReceiver.setReceiver(object: FileActionReceiver.Receiver {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(this@MainActivity, "Done", Toast.LENGTH_SHORT).show()
                    FileManager.refresh()
                }
            }
        })
    }

    private fun initDirectory() {
        FileManager.goTo(
            Environment.getExternalStorageDirectory()
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == 0) {
            if (!grantResults.contains(PackageManager.PERMISSION_DENIED)) {
                initDirectory()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 0
                )
            }
        }
    }

    override fun onEntriesChange() {
        adapter.notifyDataSetChanged()
        bBack.isEnabled = FileManager.canGoBack()
        bForward.isEnabled = FileManager.canGoForward()
    }

    override fun onSelectionModeChange(mode: MenuMode) {
        when(mode) {
            MenuMode.OPEN -> {
                if(FileManager.clipboardMode == ClipboardMode.NONE)
                    layoutBottomMenu.visibility = LinearLayout.GONE
                bCopy.isEnabled = false
                bCut.isEnabled = false
                bDelete.isEnabled = false
            }
            MenuMode.SELECT -> {
                layoutBottomMenu.visibility = LinearLayout.VISIBLE
                if(FileManager.selectionEmpty()) {
                    bCopy.isEnabled = false
                    bCut.isEnabled = false
                    bDelete.isEnabled = false
                } else {
                    bCopy.isEnabled = true
                    bCut.isEnabled = true
                    bDelete.isEnabled = true
                }
            }
        }
    }

    override fun onClipboardChange(mode: ClipboardMode) {
        when(mode) {
            ClipboardMode.COPY, ClipboardMode.CUT -> {
                layoutBottomMenu.visibility = LinearLayout.VISIBLE
                bPaste.isEnabled = true
            }
            else -> {
                layoutBottomMenu.visibility = LinearLayout.GONE
                bPaste.isEnabled = false
            }
        }
    }

    override fun onRequestFileOpen(file: File): Boolean {
        val intent: Intent? = when(getTypeFromExtension(file.extension)) {
            FileTypes.TEXT -> Intent(this, TextFileActivity::class.java)
            FileTypes.IMAGE -> Intent(this, ImageFileActivity::class.java)
            FileTypes.VIDEO -> Intent(this, VideoFileActivity::class.java)
            else -> null
        }
        if(intent != null) {
            intent.putExtra("file_path", file.absolutePath.toString())
            startActivity(intent)
            return true
        }
        return false
    }

    override fun onRequestFileOpenWith(file: File): Boolean {
        val uri = FileProvider.getUriForFile(this, "android.matf", file)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        val mimeType: String = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "*/*"
        intent.setDataAndType(uri, mimeType)
        try {
            startActivity(intent)
        }catch (e: ActivityNotFoundException){
            Toast.makeText(this, "No app can open this file.", Toast.LENGTH_LONG).show()
        }
        return true
    }

    override fun copyFile(targets: List<File>, dest: File) {
        val i = Intent(this, FileActionService::class.java)

        i.putExtra("action", FileActions.COPY)
        i.putExtra("targets", targets.map { f -> f.absolutePath }.toTypedArray())
        i.putExtra("dest", dest.absolutePath)
        i.putExtra("receiver", fileActionReceiver)

        FileActionService.enqueueWork(this, i)
    }

    override fun moveFile(targets: List<File>, dest: File) {
        val i = Intent(this, FileActionService::class.java)

        i.putExtra("action", FileActions.MOVE)
        i.putExtra("targets", targets.map { f -> f.absolutePath }.toTypedArray())
        i.putExtra("dest", dest.absolutePath)
        i.putExtra("receiver", fileActionReceiver)

        FileActionService.enqueueWork(this, i)
    }

    override fun deleteFile(targets: List<File>) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle("Confirm")
        builder.setMessage("Are you sure you want to delete selected files?")
        builder.setCancelable(false)
        builder.setPositiveButton("Yes") { dialog, which ->
            val i = Intent(this, FileActionService::class.java)
            i.putExtra("action", FileActions.DELETE)
            i.putExtra("targets", targets.map { f -> f.absolutePath }.toTypedArray())
            i.putExtra("receiver", fileActionReceiver)

            FileActionService.enqueueWork(this, i)
        }
        builder.setNegativeButton("No") { _, _ -> }

        builder.show()
    }

    override fun onBackPressed() {
        handleBackClick()
    }
}
