package org.marvin.greentoblue.listitemadapters

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat.startActivity
import androidx.recyclerview.widget.RecyclerView
import org.marvin.greentoblue.ChatExportActivity
import org.marvin.greentoblue.MainActivity
import org.marvin.greentoblue.R
import org.marvin.greentoblue.models.ChatMetadataModel

class ChatMetadataAdapter(private val activity: MainActivity, private val chatMetadataSource: List<ChatMetadataModel>) : RecyclerView.Adapter<ChatMetadataAdapter.ChatMetadataViewHolder>(){

    companion object{
        private var INTENT_CHAT_METADATA = "chatmetadata"
        private var CHAT_METADATA_REQUEST_CODE = 5
    }

    private var selectionMode = false

    class ChatMetadataViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtChatName: TextView = itemView.findViewById(R.id.txtChatName)
        val txtChatDetails: TextView = itemView.findViewById(R.id.txtChatDetails)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatMetadataViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(
            R.layout.listviewitem_chat_metadata,
            parent,
            false
        )
        return ChatMetadataViewHolder(itemView)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ChatMetadataViewHolder, position: Int) {
        val currentChat = chatMetadataSource[position]
        holder.txtChatName.text = currentChat.chatName
        holder.txtChatDetails.text = "${currentChat.chatCount} chats, with ${currentChat.mediaCount} media files, of which ${currentChat.mediaFound} are found"

        if(currentChat.isSelected) holder.itemView.setBackgroundColor(Color.RED)
        else holder.itemView.setBackgroundColor(Color.parseColor("#232323"))

        holder.itemView.setOnClickListener {
            if(!selectionMode) {
                val intent = Intent(activity, ChatExportActivity::class.java)
                intent.putExtra(INTENT_CHAT_METADATA, currentChat)
                activity.startActivityForResult(intent, 0)
            } else {
                currentChat.isSelected = !currentChat.isSelected
                notifyItemChanged(position)
            }
        }

        holder.itemView.setOnLongClickListener{
            if(!selectionMode){
                selectionMode = true
                currentChat.isSelected = selectionMode
                activity.onParticipantSelection(selectionMode)
                notifyItemChanged(position)
            }
            true
        }
    }

    fun updateChatMetadata(chatMetadata : ChatMetadataModel){
        chatMetadataSource.find { chat -> chat.chatID == chatMetadata.chatID }?.let { chat ->
            val position = chatMetadataSource.indexOf(chat)
            chat.chatName = chatMetadata.chatName
            chat.chatParticipants = chatMetadata.chatParticipants
            notifyItemChanged(position)
        }

    }

    fun cancelSelection(){
        selectionMode = false

        chatMetadataSource.forEach{ it ->
            if(it.isSelected){
                it.isSelected = false
                notifyItemChanged(chatMetadataSource.indexOf(it))
            }
        }
        activity.onParticipantSelection(selectionMode)
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getItemCount(): Int {
        return chatMetadataSource.size
    }

    fun getSelectedItems() : List<ChatMetadataModel>{
        val selectedItems = mutableListOf<ChatMetadataModel>()
        chatMetadataSource.forEach {
            if (it.isSelected) {
                selectedItems.add(it)
            }
        }
        return selectedItems
    }
}