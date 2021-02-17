package org.marvin.greentoblue.models

import android.net.Uri

data class MediaModel(
    val fileName : String,
    val filePath: String,
    val fileUri: Uri,
    val fileHash: String
)