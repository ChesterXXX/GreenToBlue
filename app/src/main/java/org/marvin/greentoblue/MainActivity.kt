package org.marvin.greentoblue

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import org.marvin.greentoblue.listitemadapters.ChatMetadataAdapter
import org.marvin.greentoblue.listitemadapters.SelectDirectoryAdapter
import org.marvin.greentoblue.models.ChatDataModel
import org.marvin.greentoblue.models.ChatMetadataModel
import org.marvin.greentoblue.models.MediaModel
import java.io.File
import java.security.MessageDigest
import java.sql.Timestamp


class MainActivity : AppCompatActivity() {
    companion object{
        private var MSGSTORE_LOCATION_REQUEST_CODE = 1
        private var WA_LOCATION_REQUEST_CODE = 2
        private var MEDIA_LOCATION_REQUEST_CODE = 3
        private var REQUEST_PERMISSION_REQUEST_CODE = 4

        private var CHAT_METADATA_RESULT_CODE = 100

        private var INTENT_CHAT_METADATA = "chatmetadata"

        private var PREF_MEDIA_LOCATION = "medialocation"
        private val MEDIA_LOCATION_DEFAULT = Environment.getExternalStorageDirectory().absolutePath + "/Whatsapp/Media/"

        private var DATABASE_MSGSTORE = "msgstore.db"
        private var DATABASE_WA = "wa.db"
    }

    private lateinit var chatDatabase: ChatDatabaseAdapter
    private lateinit var adapter : ChatMetadataAdapter
    private var mediaFolderLocation = ""
    private var mediaFiles = mutableListOf<MediaModel>()
    private var chatMetadataList = mutableListOf<ChatMetadataModel>()

    private var chatSelected = false

    //region Initialization

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mediaFolderLocation = PreferenceManager.getDefaultSharedPreferences(this).getString(PREF_MEDIA_LOCATION, MEDIA_LOCATION_DEFAULT)!!

        addOnClickListeners()

        populateFromDB()

        findViewById<RecyclerView>(R.id.lstChatMetadata).let {
            adapter = ChatMetadataAdapter(this, chatMetadataList)
            it.adapter = adapter
            it.layoutManager = LinearLayoutManager(this)
            it.setHasFixedSize(true)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun populateFromDB(){
        GlobalScope.launch {
            chatDatabase = ChatDatabaseAdapter.getInstance(applicationContext)
            chatDatabase.getChatMetadata(chatMetadataList)
            chatDatabase.getMediaFiles(mediaFiles)
            withContext(Dispatchers.Main){
                adapter.notifyDataSetChanged()
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged", "SetTextI18n")
    private fun addOnClickListeners() {

        findViewById<Button>(R.id.btnMsgStore).setOnClickListener {
            val msgStoreIntent =
                Intent().setType("application/*").setAction(Intent.ACTION_GET_CONTENT)

            startActivityForResult(
                Intent.createChooser(msgStoreIntent, "Select msgstore.db"),
                MSGSTORE_LOCATION_REQUEST_CODE
            )
        }

        findViewById<Button>(R.id.btnWA).setOnClickListener {
            val waIntent = Intent().setType("application/*").setAction(Intent.ACTION_GET_CONTENT)

            startActivityForResult(
                Intent.createChooser(waIntent, "Select wa.db"),
                WA_LOCATION_REQUEST_CODE
            )
        }

        findViewById<Button>(R.id.btnMedia).setOnClickListener {
            val dialog = Dialog(this)
            //dialog.requestWindowFeature(Window.)
            dialog.setTitle("Select Media Directory")
            dialog.setCancelable(true)
            dialog.setContentView(R.layout.dialog_select_directory)

            val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
            val height = (resources.displayMetrics.heightPixels * 0.90).toInt()

            dialog.window?.setLayout(width, height)

            val adapter = SelectDirectoryAdapter(mediaFolderLocation)

            val lstDirectory: RecyclerView = dialog.findViewById(R.id.lstDirectory)
            lstDirectory.adapter = adapter
            lstDirectory.layoutManager = LinearLayoutManager(this)
            lstDirectory.setHasFixedSize(true)

            val btnSelectDirectory: Button = dialog.findViewById(R.id.btnSelectDirectory)
            val btnCancelDirectorySelection: Button =
                dialog.findViewById(R.id.btnCancelDirectorySelection)

            btnCancelDirectorySelection.setOnClickListener {
                dialog.dismiss()
            }

            btnSelectDirectory.setOnClickListener {
                val selectedMediaDirectory =
                    (lstDirectory.adapter as SelectDirectoryAdapter).currentDirectory
                mediaFolderLocation = selectedMediaDirectory
                PreferenceManager.getDefaultSharedPreferences(this)
                    .edit()
                    .putString(PREF_MEDIA_LOCATION, mediaFolderLocation)
                    .apply()
                dialog.dismiss()
            }

            dialog.show()

            adapter.listFolders()


        }

        findViewById<Button>(R.id.btnScanMedia).setOnClickListener {
            if (hasPermission()) {
                GlobalScope.launch(Dispatchers.IO) {
                    withContext(Dispatchers.Main) {
                        val btn = it as Button
                        btn.isEnabled = false
                        btn.text = "Scanning..."
                    }

                    scanMedia()

                    withContext(Dispatchers.Main) {
                        val btn = it as Button
                        btn.text = "Scan Media"
                        btn.isEnabled = true
                        Toast.makeText(
                            applicationContext,
                            "Scanned ${mediaFiles.size} Media Files!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                askPermissions()
            }
        }

        findViewById<Button>(R.id.btnScanDB).setOnClickListener {
            if (mediaFiles.isEmpty()) {
                Toast.makeText(this, "Please Scan Media First!", Toast.LENGTH_SHORT).show()
            } else if (databasesExist()) {
                GlobalScope.launch {
                    withContext(Dispatchers.Main) {
                        val btn = it as Button
                        btn.isEnabled = false
                        btn.text = "Scanning..."
                    }

                    scanDatabase()

                    withContext(Dispatchers.Main) {
                        val btn = it as Button
                        btn.text = "Scan DB"
                        btn.isEnabled = true
                        adapter.notifyDataSetChanged()
                        Toast.makeText(
                            applicationContext,
                            "Database Created!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        findViewById<Button>(R.id.btnCancelChatSelection).setOnClickListener {
            adapter.cancelSelection()
        }

        findViewById<Button>(R.id.btnDeleteChats).setOnClickListener {
            val selectedItems = adapter.getSelectedItems()
            if (selectedItems.isNotEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle("Delete Chats")
                    .setMessage("Do you want to delete ${selectedItems.size} chats?")
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setCancelable(false)
                    .setPositiveButton("Yes") { _, _ ->
                        GlobalScope.launch {
                            chatDatabase.deleteChats(selectedItems)

                            chatDatabase.getChatMetadata(chatMetadataList)

                            withContext(Dispatchers.Main) {
                                adapter.cancelSelection()
                                adapter.notifyDataSetChanged()
                            }
                        }
                    }
                    .setNegativeButton("No") { _, _ -> }
                    .show()
            } else {
                Toast.makeText(this, "Please select chats to delete!", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnMergeChats).setOnClickListener {
            val selectedItems = adapter.getSelectedItems()
            if (selectedItems.size <= 1) {
                Toast.makeText(this, "Please select at least 2 chats to merge!", Toast.LENGTH_SHORT)
                    .show()
            } else {
                if (selectedItems.isNotEmpty()) {
                    AlertDialog.Builder(this)
                        .setTitle("Delete Chats")
                        .setMessage("Do you want to delete ${selectedItems.size} chats?")
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setCancelable(false)
                        .setPositiveButton("Yes") { _, _ ->
                            GlobalScope.launch {

                                chatDatabase.mergeChats(selectedItems)

                                chatDatabase.getChatMetadata(chatMetadataList)

                                withContext(Dispatchers.Main) {
                                    adapter.cancelSelection()
                                    adapter.notifyDataSetChanged()
                                }
                            }
                        }
                        .setNegativeButton("No") { _, _ -> }
                        .show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                MSGSTORE_LOCATION_REQUEST_CODE -> {
                    data?.data?.let { selectedFile -> copyDBFile(selectedFile, DATABASE_MSGSTORE) }
                }
                WA_LOCATION_REQUEST_CODE -> {
                    data?.data?.let { selectedFile -> copyDBFile(selectedFile, DATABASE_WA) }
                }
                MEDIA_LOCATION_REQUEST_CODE -> {
                    data?.data?.let {
                        DocumentFile.fromTreeUri(this, it)?.let { mediaDir ->
                            if (mediaDir.canRead()) {
                                val takeFlags = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
                                contentResolver.takePersistableUriPermission(it, takeFlags)
                                PreferenceManager.getDefaultSharedPreferences(this)
                                    .edit()
                                    .putString(PREF_MEDIA_LOCATION, it.toString())
                                    .apply()
                            }
                        }
                    }
                }
                REQUEST_PERMISSION_REQUEST_CODE -> {
                    Log.d("PERMISSION", "GRANTED!")
                }
                else -> {
                    Log.d("REQUEST CODE", "Unknown Request Code : $requestCode")
                }
            }
        } else if( resultCode == CHAT_METADATA_RESULT_CODE ){
            data?.getParcelableExtra<ChatMetadataModel>(INTENT_CHAT_METADATA)?.let {
                adapter.updateChatMetadata(it)
            }
        }
    }

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PackageManager.PERMISSION_GRANTED == checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            true
        }
    }

    private fun askPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val externalPerms = arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            requestPermissions(externalPerms, REQUEST_PERMISSION_REQUEST_CODE)
        }
    }

    private fun copyDBFile(selectedFile: Uri, fileName: String) {
        openFileOutput(fileName, Context.MODE_PRIVATE).use { outputStream ->
            application.contentResolver.openInputStream(selectedFile)?.let { inputStream ->
                val buff = ByteArray(2048)
                var read: Int
                while (inputStream.read(buff, 0, buff.size).also { read = it } > 0) {
                    outputStream.write(buff, 0, read)
                }
            }
        }
    }

    //endregion

    //region Scan Media Folder

    private fun scanMedia() {
        val mediaFolder = File(mediaFolderLocation)

        scanMediaDir(mediaFolder, mediaFiles)

        chatDatabase.clearMedia()

        chatDatabase.addMediaFiles(mediaFiles)

        Log.d("FILES", "Got ${mediaFiles.size} many media files!")
    }

    private fun scanMediaDir(mediaLoc: File, mediaFiles: MutableList<MediaModel>) {
        fun hashFile(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(1024)
            file.inputStream().buffered(1024).use {
                while (true) {
                    val bytesRead = it.read(buffer)
                    if (bytesRead < 0) break
                    md.update(buffer, 0, bytesRead)
                }
            }
            return Base64.encodeToString(md.digest(), Base64.DEFAULT)
        }

        fun getMediaModel(file: File): MediaModel {
            val hash = hashFile(file)
            val uri = GenericFileProvider.getUriForFile(this, applicationContext.packageName, file)
            return MediaModel(file.name, file.absolutePath, uri, hash)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mediaLoc.walk()
                .toList()
                .filter { it.isFile }
                .map { mediaFiles.add(getMediaModel(it)) }
                .count()
        } else {
            mediaLoc.walk()
                .filter { it.isFile }
                .map { mediaFiles.add(getMediaModel(it)) }
        }
    }

    //endregion

    //region Create ChatMetadata

    private fun scanDatabase() {
        val map = createParticipantsMap()

        createChatMetadataModels(map, chatMetadataList)

        chatDatabase.clearChat()

        chatMetadataList.forEach {
            chatDatabase.addChatMetadata(it)

            val chatData = getChatData(it)

            runBlocking {
                chatDatabase.addChatData(chatData)
            }
        }
    }

    private fun getChatData(chatMetadata: ChatMetadataModel) : List<ChatDataModel> {
        val chatID = chatMetadata.chatID
        val query = "SELECT " +
                "timestamp, " +
                "data, " +
                "media_name, " +
                "media_caption, " +
                "media_hash, " +
                "key_from_me, " +
                "remote_resource " +
                "FROM messages " +
                "WHERE key_remote_jid='$chatID'"

        val chats = mutableListOf<ChatDataModel>()
        var mediaFoundCount = 0

        SQLiteDatabase.openOrCreateDatabase(File(filesDir, DATABASE_MSGSTORE), null).use { db ->
            db.rawQuery(query, null).use { curr ->
                if (curr.moveToFirst()) {
                    do {
                        val timestamp = curr.getLong(0)
                        val chatData = curr.getString(1)
                        var mediaName = curr.getString(2)
                        val mediaCaption = curr.getString(3)
                        val mediaHash = curr.getString(4)?.toString() ?: ""
                        val keyFromMe = curr.getInt(5)
                        val participantID = curr.getString(6)
                        var hasMedia = false
                        var mediaFound = false
                        var mediaURI = Uri.EMPTY
                        if (mediaHash.isNotEmpty()) {
                            hasMedia = true
                            mediaFiles.find { it.fileHash.trim() == mediaHash.trim() }?.let {
                                mediaFound = true
                                mediaFoundCount += 1
                                mediaURI = it.fileUri
                                mediaName = it.fileName
                            }
                        }

                        chats.add(
                            ChatDataModel(
                                chatID,
                                Timestamp(timestamp),
                                chatData?.toString() ?: "",
                                keyFromMe != 0,
                                participantID?.toString() ?: "",
                                hasMedia,
                                mediaName ?: "",
                                mediaCaption?.toString() ?: "",
                                mediaFound,
                                mediaURI
                            )
                        )
                    } while (curr.moveToNext())
                }
            }
        }
        chatDatabase.updateMediaFoundCount(chatID, mediaFoundCount)
        chatMetadata.mediaFound = mediaFoundCount
        return chats
    }

    private fun databasesExist(): Boolean {
        return if (File(filesDir, DATABASE_MSGSTORE).exists()) {
            if (File(filesDir, DATABASE_WA).exists()) {
                true
            } else {
                Log.d("ERROR", "$DATABASE_WA Doesn't Exist")
                Toast.makeText(this, "Please locate $DATABASE_WA", Toast.LENGTH_SHORT).show()
                false
            }
        } else {
            Log.d("ERROR", "$DATABASE_MSGSTORE Doesn't Exist")
            Toast.makeText(this, "Please locate $DATABASE_MSGSTORE", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun createParticipantsMap(): MutableMap<String, String> {
        val participantsMap = mutableMapOf<String, String>()
        SQLiteDatabase.openOrCreateDatabase(File(filesDir, DATABASE_WA), null).use { db ->
            val query = "SELECT jid, display_name, wa_name FROM wa_contacts"
            db.rawQuery(query, null).use {
                if (it.moveToFirst()) {
                    do {
                        val jid = it.getString(0)
                        val senderDisplayName = it.getString(1)
                        val senderWhatsappName = it.getString(2)

                        participantsMap[jid] =
                            if (!senderDisplayName.isNullOrEmpty()) senderDisplayName
                            else if (!senderWhatsappName.isNullOrEmpty()) senderWhatsappName
                            else getPhoneNumberOrID(jid)
                    } while (it.moveToNext())
                }
            }
        }
        return participantsMap
    }

    private fun getPhoneNumberOrID(jid: String): String {
        val re = Regex("(.*)@(.*)")
        var sender = jid
        re.find(jid)?.let { result ->
            sender = result.groupValues[1]
        }
        return sender
    }

    private fun createChatMetadataModels(
        participants: Map<String, String>,
        chatModels: MutableList<ChatMetadataModel>
    ){
        chatModels.clear()
        val query =
            "SELECT " +
                    "key_remote_jid, " +
                    "COUNT(key_remote_jid) AS chat_count, " +
                    "SUM(CASE WHEN media_hash IS NOT NULL THEN 1 ELSE 0 END) AS media_count " +
                    "FROM messages " +
                    "GROUP BY key_remote_jid " +
                    "" +
                    "ORDER BY COUNT(key_remote_jid) DESC"

        SQLiteDatabase.openOrCreateDatabase(File(filesDir, DATABASE_MSGSTORE), null).use { msgstoreDB ->
            msgstoreDB.rawQuery(query, null).use {
                if (it.moveToFirst()) {
                    do {
                        val chatID = it.getString(0)
                        val chatCount = it.getInt(1)
                        val mediaCount = it.getInt(2)
                        val chatName = participants[chatID] ?: getPhoneNumberOrID(chatID)
                        val chat =
                            ChatMetadataModel(chatID, chatName, chatCount, mediaCount, 0)

                        val participantsQuery =
                            "SELECT DISTINCT(remote_resource) FROM messages WHERE key_remote_jid='$chatID' AND remote_resource != ''"

                        msgstoreDB.rawQuery(participantsQuery, null).use { curr ->
                            if (curr.moveToFirst()) {
                                do {
                                    val participantID = curr.getString(0)
                                    val participantName = participants[participantID] ?: getPhoneNumberOrID(
                                        participantID
                                    )
                                    chat.chatParticipants[participantID] =
                                        participantName
                                } while (curr.moveToNext())
                            }
                        }
                        chatModels.add(chat)
                    } while (it.moveToNext())
                }
            }
        }
    }

    //endregion

    //region Edit ChatMetadata (Merge and Delete)

    fun onParticipantSelection(selectionMode: Boolean) {
        chatSelected = selectionMode
        toggleSelectionOperations()
    }

    private fun toggleSelectionOperations(){
        findViewById<LinearLayout>(R.id.layoutChatSelection).visibility = if (chatSelected) LinearLayout.VISIBLE else LinearLayout.GONE
    }

    override fun onBackPressed() {
        if(chatSelected){
            adapter.cancelSelection()
            toggleSelectionOperations()
        }
        else {
            super.onBackPressed()
        }
    }

    //endregion
}