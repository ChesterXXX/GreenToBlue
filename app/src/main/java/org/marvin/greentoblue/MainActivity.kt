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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.marvin.greentoblue.listitemadapters.ChatMetadataAdapter
import org.marvin.greentoblue.listitemadapters.SelectDirectoryAdapter
import org.marvin.greentoblue.models.ChatDataModel
import org.marvin.greentoblue.models.ChatMetadataModel
import org.marvin.greentoblue.models.ChatSources
import org.marvin.greentoblue.models.MediaModel
import java.io.File
import java.security.MessageDigest
import java.sql.Timestamp


class MainActivity : AppCompatActivity() {
    companion object{
        private const val MSGSTORE_LOCATION_REQUEST_CODE = 1
        private const val WA_LOCATION_REQUEST_CODE = 2
        private const val REQUEST_PERMISSION_REQUEST_CODE = 3

        private const val CHAT_METADATA_RESULT_CODE = 100

        private const val INTENT_CHAT_METADATA = "chatmetadata"

        private const val PREF_MEDIA_LOCATION = "medialocation"
        private const val PREF_FB_CHAT_LOCATION = "fbchatlocation"
        private val MEDIA_LOCATION_DEFAULT = Environment.getExternalStorageDirectory().absolutePath + "/Whatsapp/Media/"

        private const val DATABASE_MSGSTORE = "msgstore.db"
        private const val DATABASE_WA = "wa.db"
    }

    class FBChatData(val chatMetadata: ChatMetadataModel, val chats: List<ChatDataModel>)

    //region Private Variables

    private lateinit var chatDatabase: ChatDatabaseAdapter
    private lateinit var adapter : ChatMetadataAdapter

    private var mediaFolderLocation = ""
    private var fbChatFolderLocation = ""

    private var mediaFiles = mutableListOf<MediaModel>()
    private var chatMetadataList = mutableListOf<ChatMetadataModel>()

    private var chatSelected = false

    private var scanning = false

    //endregion

    //region Initialization

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mediaFolderLocation = PreferenceManager.getDefaultSharedPreferences(this).getString(
            PREF_MEDIA_LOCATION,
            MEDIA_LOCATION_DEFAULT
        )!!
        fbChatFolderLocation = PreferenceManager.getDefaultSharedPreferences(this).getString(
            PREF_FB_CHAT_LOCATION,
            Environment.getExternalStorageDirectory().absolutePath
        )!!

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

    private fun addOnClickListeners() {
        addOnClickListenersWhatsapp()
        addOnClickListenersFB()
        addOnClickListenersEditChatMetadata()
    }

    private fun addOnClickListenersWhatsapp() {
        findViewById<Button>(R.id.btnMsgStore).setOnClickListener {
            val msgStoreIntent = Intent().setType("application/*").setAction(Intent.ACTION_GET_CONTENT)

            startActivityForResult(Intent.createChooser(msgStoreIntent, "Select msgstore.db"),MSGSTORE_LOCATION_REQUEST_CODE)
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
            dialog.setTitle("Select Media Directory")
            dialog.setCancelable(true)
            dialog.setContentView(R.layout.dialog_select_directory)

            val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
            val height = (resources.displayMetrics.heightPixels * 0.90).toInt()

            dialog.window?.setLayout(width, height)

            val directoryAdapter = SelectDirectoryAdapter(mediaFolderLocation)

            val lstDirectory: RecyclerView = dialog.findViewById(R.id.lstDirectory)
            lstDirectory.adapter = directoryAdapter
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

            directoryAdapter.listFolders()
        }

        findViewById<Button>(R.id.btnScanMedia).setOnClickListener {
            if(scanning){
                Toast.makeText(applicationContext, "Scanning In Progress! Please Be Patient!", Toast.LENGTH_SHORT).show()
            }else {
                if (hasPermission()) {
                    scanMedia()
                } else {
                    askPermissions()
                }
            }
        }

        findViewById<Button>(R.id.btnScanDB).setOnClickListener {
            if(scanning){
                Toast.makeText(applicationContext, "Scanning In Progress! Please Be Patient!", Toast.LENGTH_SHORT).show()
            } else {
                if (mediaFiles.isEmpty()) {
                    Toast.makeText(this, "Please Scan Media First!", Toast.LENGTH_SHORT).show()
                } else if (databasesExist()) {
                    scanWhatsappDatabase()
                }
            }
        }

    }

    private fun addOnClickListenersFB() {
        findViewById<Button>(R.id.btnFBChatBackup).setOnClickListener {
            val dialog = Dialog(this)
            dialog.setTitle("Select FB Chat Backup Directory")
            dialog.setCancelable(true)
            dialog.setContentView(R.layout.dialog_select_directory)

            val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
            val height = (resources.displayMetrics.heightPixels * 0.90).toInt()

            dialog.window?.setLayout(width, height)

            val directoryAdapter = SelectDirectoryAdapter(fbChatFolderLocation)

            val lstDirectory: RecyclerView = dialog.findViewById(R.id.lstDirectory)
            lstDirectory.adapter = directoryAdapter
            lstDirectory.layoutManager = LinearLayoutManager(this)
            lstDirectory.setHasFixedSize(true)

            val btnSelectDirectory: Button = dialog.findViewById(R.id.btnSelectDirectory)
            val btnCancelDirectorySelection: Button =
                dialog.findViewById(R.id.btnCancelDirectorySelection)

            btnCancelDirectorySelection.setOnClickListener {
                dialog.dismiss()
            }

            btnSelectDirectory.setOnClickListener {
                val selectedFBDirectory =
                    (lstDirectory.adapter as SelectDirectoryAdapter).currentDirectory
                fbChatFolderLocation = selectedFBDirectory

                if(fbChatFolderLocation.endsWith("messages")){
                    fbChatFolderLocation = File(fbChatFolderLocation).parent!!
                }
                println(fbChatFolderLocation)
                PreferenceManager.getDefaultSharedPreferences(this)
                    .edit()
                    .putString(PREF_FB_CHAT_LOCATION, fbChatFolderLocation)
                    .apply()
                dialog.dismiss()
            }

            dialog.show()

            directoryAdapter.listFolders()
        }

        findViewById<Button>(R.id.btnScanFB).setOnClickListener {
            if(scanning){
                Toast.makeText(applicationContext, "Scanning In Progress! Please Be Patient!", Toast.LENGTH_SHORT).show()
            } else {
                val dialog = Dialog(this)
                dialog.setTitle("Select FB Chat Backup Directory")
                dialog.setCancelable(false)
                dialog.setContentView(R.layout.dialog_fb_my_name)

                val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
                val height = (resources.displayMetrics.heightPixels * 0.15).toInt()

                dialog.window?.setLayout(width, height)

                dialog.findViewById<Button>(R.id.btnOkMyNameFB).setOnClickListener {
                    val myName =
                        dialog.findViewById<EditText>(R.id.editTxtMyNameFB).text.toString().trim()
                    if (myName.isNotEmpty()) {
                        dialog.dismiss()
                        scanFB(myName)
                    } else {
                        Toast.makeText(this, "Name Cannot Be Empty!", Toast.LENGTH_SHORT).show()
                    }
                }

                dialog.findViewById<Button>(R.id.btnCancelMyNameFB).setOnClickListener {
                    Toast.makeText(
                        this,
                        "Cannot Scan FB Chat Without Your Name!",
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                }

                dialog.show()
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged", "SetTextI18n")
    private fun addOnClickListenersEditChatMetadata() {
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

    //endregion

    //region Scan Media Folder

    @SuppressLint("SetTextI18n")
    private fun scanMedia() {
        GlobalScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                val btn = findViewById<Button>(R.id.btnScanMedia)
                btn.isEnabled = false
                btn.text = "Scanning..."
                scanning = true
            }
            val mediaFolder = File(mediaFolderLocation)

            scanMediaDir(mediaFolder, mediaFiles)

            chatDatabase.clearMedia()

            chatDatabase.addMediaFiles(mediaFiles)

            withContext(Dispatchers.Main) {
                val btn = findViewById<Button>(R.id.btnScanMedia)
                btn.text = "Scan Media"
                btn.isEnabled = true
                Toast.makeText(
                    applicationContext,
                    "Scanned ${mediaFiles.size} Media Files!",
                    Toast.LENGTH_SHORT
                ).show()
                scanning = false
            }
        }
    }

    private fun scanMediaDir(mediaLoc: File, mediaFiles: MutableList<MediaModel>) {
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

    private fun getMediaModel(file: File): MediaModel {
        val hash = hashFile(file)
        val uri = GenericFileProvider.getUriForFile(this, applicationContext.packageName, file)
        return MediaModel(file.name, file.absolutePath, uri, hash)
    }

    //endregion

    //region Create ChatMetadata : Whatsapp

    @SuppressLint("NotifyDataSetChanged", "SetTextI18n")
    private fun scanWhatsappDatabase() {
        GlobalScope.launch {
            withContext(Dispatchers.Main) {
                val btn = findViewById<Button>(R.id.btnScanDB)
                btn.isEnabled = false
                btn.text = "Scanning..."
                scanning = true
            }

            val map = createParticipantsMap()

            createChatMetadataModelsWhatsapp(map)

            chatDatabase.clearChatWhatsapp()

            chatMetadataList
                .filter { it.chatSource == ChatSources.SOURCE_WHATSAPP }
                .forEach {
                    chatDatabase.addChatMetadata(it)

                    val chatData = getChatData(it)

                    runBlocking {
                        chatDatabase.addChatData(chatData)
                    }
                }

            withContext(Dispatchers.Main) {
                val btn = findViewById<Button>(R.id.btnScanDB)
                btn.text = "Scan DB"
                btn.isEnabled = true
                adapter.notifyDataSetChanged()
                Toast.makeText(
                    applicationContext,
                    "Database Created!",
                    Toast.LENGTH_SHORT
                ).show()
                scanning = false
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
                                mediaURI,
                                ChatSources.SOURCE_WHATSAPP
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

    private fun createChatMetadataModelsWhatsapp(participants: Map<String, String>){
        val query =
            "SELECT " +
                    "key_remote_jid, " +
                    "COUNT(key_remote_jid) AS chat_count, " +
                    "SUM(CASE WHEN media_hash IS NOT NULL THEN 1 ELSE 0 END) AS media_count " +
                    "FROM messages " +
                    "GROUP BY key_remote_jid " +
                    "" +
                    "ORDER BY COUNT(key_remote_jid) DESC"

        chatMetadataList.filter { it.chatSource == ChatSources.SOURCE_WHATSAPP }.forEach { chatMetadataList.remove(it) }

        SQLiteDatabase.openOrCreateDatabase(File(filesDir, DATABASE_MSGSTORE), null).use { msgstoreDB ->
            msgstoreDB.rawQuery(query, null).use {
                if (it.moveToFirst()) {
                    do {
                        val chatID = it.getString(0)
                        val chatCount = it.getInt(1)
                        val mediaCount = it.getInt(2)
                        val chatName = participants[chatID] ?: getPhoneNumberOrID(chatID)
                        val chat =
                            ChatMetadataModel(chatID, chatName, chatCount, mediaCount, 0, ChatSources.SOURCE_WHATSAPP)

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
                        chatMetadataList.add(chat)
                    } while (it.moveToNext())
                }
            }
        }
    }

    //endregion

    //region Create ChatMetadata : FB

    @SuppressLint("NotifyDataSetChanged", "SetTextI18n")
    private fun scanFB(myFBName: String){
        var folderScanSuccess = false

        GlobalScope.launch {
            withContext(Dispatchers.Main) {
                val btn = findViewById<Button>(R.id.btnScanFB)
                btn.isEnabled = false
                btn.text = "Scanning..."
                scanning = true
            }

            val chatFolders = scanFBChatFolder()

            val chatList = mutableListOf<FBChatData>()

            if(chatFolders.isNotEmpty()){
                chatFolders.forEach{ chatFolder ->
                    val chatFiles = scanFBChat(chatFolder)
                    val (chatMetadata, chats) = createChatMetadataFB(chatFiles, myFBName)
                    chatList.add(FBChatData(chatMetadata, chats))
                }

                chatMetadataList.filter { it.chatSource == ChatSources.SOURCE_FB }.forEach { chatMetadataList.remove(it) }

                chatList.forEach{
                    chatMetadataList.add(it.chatMetadata)
                }
                chatMetadataList.sortByDescending { it.chatCount }

                folderScanSuccess = true
            }

            withContext(Dispatchers.Main) {
                if(folderScanSuccess){
                    adapter.notifyDataSetChanged()
                    Toast.makeText(
                        applicationContext,
                        "FB Data Scanned. Writing to Database!",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        applicationContext,
                        "Please Select FB Chat Folder!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            var chatAddedToDBSuccess = false

            if(folderScanSuccess){

                chatDatabase.clearChatWhatsapp()

                chatList.forEach{
                    chatDatabase.addChatMetadata(it.chatMetadata)
                    chatDatabase.addChatData(it.chats)
                }

                chatAddedToDBSuccess = true
            }

            withContext(Dispatchers.Main){
                val btn = findViewById<Button>(R.id.btnScanFB)
                btn.text = "Scan FB"
                btn.isEnabled = true
                if(chatAddedToDBSuccess){
                    Toast.makeText(
                        applicationContext,
                        "FB Chat Scanning Done!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                scanning = false
            }
        }
    }

    private fun scanFBChatFolder() : List<File> {
        val chatFolders = arrayListOf<File>()
        File(fbChatFolderLocation)
            .listFiles()
            ?.find { it.name == "messages" }
            ?.listFiles()
            ?.filter { it.name == "inbox" || it.name == "archived_threads" }
            ?.forEach { file -> file.listFiles()?.also { chatFolders.addAll(it) } }
        return chatFolders
    }

    private fun scanFBChat(chatFolder: File) : List<Map<String, Any?>> {
        val chats = mutableListOf<Map<String, Any?>>()
        chatFolder.listFiles()?.forEach { file ->
            if (file.extension == "json") {
                val fileContent = file.reader(Charsets.ISO_8859_1).readText()
                chats.add(JSONObject(fileContent).toMap())
            }
        }
        return chats
    }

    private fun createChatMetadataFB(chats: List<Map<String, Any?>>, myFBName: String) : Pair<ChatMetadataModel, List<ChatDataModel>> {
        val chatList = mutableListOf<ChatDataModel>()

        val sampleChat = chats[0]
        val (chatID) = Regex(".*/(.*)$").find(sampleChat["thread_path"] as String)!!.destructured
        val chatName = sampleChat["title"] as String
        val chatParticipants = mutableMapOf<String, String>()
        (sampleChat["participants"] as List<Any?>).forEach { participant ->
            val participantName = (participant as Map<*, *>)["name"] as String
            if (participantName != myFBName) {
                val participantKey = participantName + "_" + chatID
                chatParticipants[participantKey] = participantName
            }
        }

        var chatCount = 0
        var mediaCount = 0
        var mediaFoundCount = 0

        chats.forEach{ chatMap ->
            (chatMap["messages"] as List<*>)
                .map { message -> message as Map<*, *> }
                .forEach { message ->
                    val sender = message["sender_name"] as String
                    val participantKey = "$sender@$chatID"
                    chatParticipants[participantKey] = sender

                    val chatContent = if ("content" in message.keys) { (message["content"] as String).getDecodedContent() } else { "" }
                    val timestamp = Timestamp(message["timestamp_ms"] as Long)

                    val mediaKey = when {
                        "audio_files" in message.keys -> "audio_files"
                        "files" in message.keys -> "files"
                        "gifs" in message.keys -> "gifs"
                        "photos" in message.keys -> "photos"
                        "sticker" in message.keys -> "sticker"
                        "videos" in message.keys -> "videos"
                        else -> ""
                    }
                    val hasMedia = mediaKey.isNotEmpty()

                    if(hasMedia) {
                        val mediaList =
                            if (message[mediaKey] is List<*>)
                                message[mediaKey] as List<*>
                            else listOf(message[mediaKey])
                        mediaList.map { it as Map<*, *> }.forEach { media ->
                            val mediaFile = File(fbChatFolderLocation, media["uri"] as String)

                            chatCount += 1
                            mediaCount += 1

                            if(mediaFile.exists()) {
                                val mediaFound = true
                                mediaFoundCount += 1

                                val mediaUri = GenericFileProvider.getUriForFile(
                                    this,
                                    applicationContext.packageName,
                                    mediaFile
                                )
                                val mediaName = mediaFile.name

                                chatList.add(
                                    ChatDataModel(
                                        chatID,
                                        timestamp,
                                        "",
                                        myFBName == sender,
                                        participantKey,
                                        hasMedia,
                                        mediaName,
                                        chatContent, //same for each media file
                                        mediaFound,
                                        mediaUri,
                                        ChatSources.SOURCE_FB
                                    )
                                )
                            }
                            else {
                                chatList.add(
                                    ChatDataModel(
                                        chatID,
                                        timestamp,
                                        "",
                                        myFBName == sender,
                                        participantKey,
                                        hasMedia,
                                        "",
                                        chatContent,
                                        false,
                                        Uri.EMPTY,
                                        ChatSources.SOURCE_FB
                                    )
                                )
                            }
                        }
                    }
                    else{
                        chatCount += 1
                        chatList.add(
                            ChatDataModel(
                                chatID,
                                timestamp,
                                chatContent,
                                myFBName == sender,
                                participantKey,
                                false,
                                "",
                                "",
                                false,
                                Uri.EMPTY,
                                ChatSources.SOURCE_FB
                            )
                        )
                    }
                }
            }

        val chatMetadata = ChatMetadataModel(
            chatID,
            chatName,
            chatCount,
            mediaCount,
            mediaFoundCount,
            ChatSources.SOURCE_FB
        )
        if(chatParticipants.size > 1) {
            chatMetadata.chatParticipants = chatParticipants
        }

        return Pair(chatMetadata, chatList)
    }

    private fun String.getDecodedContent() : String{
        return String(this.toByteArray(Charsets.ISO_8859_1))
    }


    //endregion

    //region Edit ChatMetadata (Merge and Delete)

    fun onParticipantSelection(selectionMode: Boolean) {
        chatSelected = selectionMode
        toggleSelectionOperations()
    }

    fun isScanning() : Boolean {
        return scanning
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

    //region Helper Methods

    private fun JSONObject.toMap() : Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        this.keys().forEach { key ->
            var obj = this.get(key)
            if(obj is JSONArray){
                obj = obj.toList()
            } else if (obj is JSONObject){
                obj = obj.toMap()
            }
            map[key] = obj
        }
        return map
    }

    private fun JSONArray.toList() : List<Any?>{
        val list = mutableListOf<Any?>()
        for(i in 0 until this.length()){
            var obj = this.get(i)
            if(obj is JSONArray){
                obj = obj.toList()
            } else if(obj is JSONObject){
                obj = obj.toMap()
            }
            list.add(obj)
        }
        return list
    }

    private fun hashFile(file: File): String {
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

    private fun getPhoneNumberOrID(jid: String): String {
        val re = Regex("(.*)@(.*)")
        var sender = jid
        re.find(jid)?.let { result ->
            sender = result.groupValues[1]
        }
        return sender
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
}