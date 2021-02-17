package org.marvin.greentoblue

import android.annotation.SuppressLint
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.marvin.greentoblue.listitemadapters.ChatMetadataAdapter
import org.marvin.greentoblue.listitemadapters.ChunkAdapter
import org.marvin.greentoblue.listitemadapters.ParticipantAdapter
import org.marvin.greentoblue.models.ChatMetadataModel
import org.marvin.greentoblue.models.ChunkDataModel

class ChatExportActivity : AppCompatActivity() {
    companion object {
        var PREF_MY_NAME = "myname"
        private var PREF_CHUNK_SIZE = "chunksize"

        private const val CHAT_METADATA_RESULT_CODE = 100

        private var DEFAULT_CHUNK_SIZE = 200
        private var DEFAULT_MY_NAME = "Green To Blue"

        private var INTENT_CHAT_METADATA = "chatmetadata"
    }

    private var chunkSize = 0

    private var chunks = mutableListOf<ChunkDataModel>()

    private var myName : String = ""
    private lateinit var adapter : ChunkAdapter
    private lateinit var chatDatabase: ChatDatabaseAdapter
    private lateinit var chatMetadata : ChatMetadataModel

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_export)

        chatMetadata = intent.getParcelableExtra(INTENT_CHAT_METADATA)!!

        chunkSize = PreferenceManager.getDefaultSharedPreferences(this)
                .getInt(PREF_CHUNK_SIZE, DEFAULT_CHUNK_SIZE)

        myName = PreferenceManager.getDefaultSharedPreferences(this)
            .getString(PREF_MY_NAME, DEFAULT_MY_NAME)!!

        initViews()

        populateFromDB()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun populateFromDB() {
        GlobalScope.launch {
            chatDatabase = ChatDatabaseAdapter.getInstance(applicationContext)
            chatDatabase.getChunks(chatMetadata.chatID, chunks)

            withContext(Dispatchers.Main){
                adapter.notifyDataSetChanged()
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun initViews() {
        findViewById<TextView>(R.id.txtChatHeader)?.let{
            it.text = if(chatMetadata.isGroup()) "Group Name" else "Participant Name"
        }

        findViewById<ConstraintLayout>(R.id.layoutParticipantBLock).let {
            it.visibility = if(chatMetadata.isGroup()) ConstraintLayout.VISIBLE else ConstraintLayout.GONE
        }

        findViewById<RecyclerView>(R.id.lstParticipants).let {
            it.adapter = ParticipantAdapter(chatMetadata.chatParticipants)
            it.layoutManager = LinearLayoutManager(this)
            it.setHasFixedSize(true)
        }

        findViewById<EditText>(R.id.editTxtChatName)?.setText(chatMetadata.chatName)

        findViewById<EditText>(R.id.editTxtMyName)?.setText(myName)

        findViewById<EditText>(R.id.editTxtChunkSize)?.setText(chunkSize.toString())

        findViewById<Button>(R.id.btnSaveParticipants)?.setOnClickListener {
            findViewById<TextView>(R.id.editTxtChatName)?.let {
                chatMetadata.chatName = it.text.toString()
            }
            if(chatMetadata.isGroup()) {
                findViewById<RecyclerView>(R.id.lstParticipants).layoutManager?.let {
                    val itemCount = it.itemCount
                    for (i in 0 until itemCount) {
                        it.getChildAt(i)?.let { listItem ->
                            val participantID =
                                listItem.findViewById<TextView>(R.id.txtParticipantID).text.toString()
                            val participantName =
                                listItem.findViewById<EditText>(R.id.editTxtParticipantName).text.toString()
                            chatMetadata.chatParticipants[participantID] = participantName
                        }
                    }
                }
            }
            chatDatabase.updateParticipant(chatMetadata)
        }

        findViewById<Button>(R.id.btnMakeChunks)?.setOnClickListener {
            chunkSize = findViewById<EditText>(R.id.editTxtChunkSize)?.text.toString().toInt()
            GlobalScope.launch {
                withContext(Dispatchers.Main){
                    val btn = it as Button
                    btn.isEnabled = false
                    btn.text = "Making Chunks..."
                    chunks.clear()
                }

                makeChunks()

                withContext(Dispatchers.Main){
                    val btn = it as Button
                    btn.isEnabled = true
                    btn.text = "Make Export Chunks"
                    adapter.notifyDataSetChanged()
                    Toast.makeText(applicationContext, "Done!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<RecyclerView>(R.id.lstChunks).let {
            adapter = ChunkAdapter(this, chunks)
            it.adapter = adapter
            it.layoutManager = LinearLayoutManager(this)
            it.setHasFixedSize(true)
        }
    }

    private fun makeChunks() {
        chatDatabase.makeChunks(chatMetadata, myName, chunkSize, chunks)
        chatDatabase.clearChunks(chatMetadata.chatID)
        chatDatabase.writeChunks(chatMetadata, chunks)
    }

    override fun onBackPressed() {
        chunkSize = findViewById<EditText>(R.id.editTxtChunkSize)?.text.toString().toInt()
        myName = findViewById<EditText>(R.id.editTxtMyName)?.text.toString()

        PreferenceManager.getDefaultSharedPreferences(this).edit().putInt(PREF_CHUNK_SIZE, chunkSize).apply()
        PreferenceManager.getDefaultSharedPreferences(this).edit().putString(PREF_MY_NAME, myName).apply()

        val intent = Intent()
        intent.putExtra(INTENT_CHAT_METADATA, chatMetadata)
        setResult(CHAT_METADATA_RESULT_CODE, intent)

        super.onBackPressed()
    }


    private fun shareToTelegram(){
        //val testIntent = Intent(Intent.ACTION_SEND_MULTIPLE)
        //testIntent.action = Intent.ACTION_SEND_MULTIPLE*/
        //testIntent.type = "*/*"
        //testIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, fileUris)
        //startActivity(testIntent)
        TODO()
    }

}