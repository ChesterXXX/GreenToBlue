package org.marvin.greentoblue.models

import android.net.Uri
import java.sql.Timestamp

data class ChatDataModel (
    val chatID : String,
    val timestamp : Timestamp,
    val chatData : String,
    val chatFromMe : Boolean = false,
    val participantID : String = "",
    val hasMedia : Boolean = false,
    val mediaName : String = "",
    val mediaCaption : String = "",
    val mediaFound : Boolean = false,
    val mediaURI : Uri = Uri.EMPTY,
    val chatSource : String
)