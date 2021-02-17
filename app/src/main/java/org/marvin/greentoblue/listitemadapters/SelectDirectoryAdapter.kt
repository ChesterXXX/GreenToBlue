package org.marvin.greentoblue.listitemadapters

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.marvin.greentoblue.R
import java.io.File

class SelectDirectoryAdapter(var currentDirectory : String) : RecyclerView.Adapter<SelectDirectoryAdapter.SelectDirectoryViewHolder>(){

    data class Folder(
        val folderName : String,
        val folderPath : String,
        val isParent : Boolean = false
    )

    private val folders = mutableListOf<Folder>()

    class SelectDirectoryViewHolder(itemView : View) : RecyclerView.ViewHolder(itemView){
        val txtDirectoryName : TextView = itemView.findViewById(R.id.txtDirectoryName)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun listFolders(){
        folders.clear()
        folders.add(Folder("(Go Up)", "", true))
        File(currentDirectory).listFiles()?.forEach {
            if(it.isDirectory){
                folders.add(Folder(it.name, it.absolutePath))
            }
        }
        notifyDataSetChanged()
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectDirectoryViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(
            R.layout.listviewitem_directory,
            parent,
            false
        )
        return SelectDirectoryViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: SelectDirectoryViewHolder, position: Int) {
        holder.txtDirectoryName.text = folders[position].folderName
        holder.itemView.setOnClickListener{
            val folder = folders[position]
            if(folder.isParent){
                val parent = File(currentDirectory).parent
                if(!parent.isNullOrEmpty()) {
                    currentDirectory = parent
                    listFolders()
                }
            } else {
                currentDirectory = folders[position].folderPath
                listFolders()
            }

        }
    }

    override fun getItemCount(): Int {
        return folders.size
    }
}