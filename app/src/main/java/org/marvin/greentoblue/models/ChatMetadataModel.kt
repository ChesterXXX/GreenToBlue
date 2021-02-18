package org.marvin.greentoblue.models

import android.os.Parcel
import android.os.Parcelable
import org.marvin.greentoblue.ChatDatabaseAdapter

data class ChatMetadataModel(
    val chatID: String,
    var chatName: String,
    val chatCount : Int,
    val mediaCount : Int,
    var mediaFound : Int,
    var chatSource: String,
    var isSelected : Boolean = false
) : Parcelable{

    var chatParticipants = mutableMapOf<String, String>()

    constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        parcel.readString() ?: "",
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readString() ?: ChatSources.SOURCE_WHATSAPP
    ) {
        parcel.readMap(chatParticipants, null)
    }

    override fun toString(): String {
        return "ChatID = $chatID, Name = $chatName, Chat Count = $chatCount, Media Count = $mediaCount, Media Found = $mediaFound, Participants = $chatParticipants"
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(chatID)
        parcel.writeString(chatName)
        parcel.writeInt(chatCount)
        parcel.writeInt(mediaCount)
        parcel.writeInt(mediaFound)
        parcel.writeString(chatSource)
        parcel.writeMap(chatParticipants)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ChatMetadataModel> {
        override fun createFromParcel(parcel: Parcel): ChatMetadataModel {
            return ChatMetadataModel(parcel)
        }

        override fun newArray(size: Int): Array<ChatMetadataModel?> {
            return arrayOfNulls(size)
        }
    }

    fun isGroup(): Boolean{
        return chatParticipants.isNotEmpty() // Might return false positives!
    }

}
