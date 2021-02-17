package org.marvin.greentoblue.listitemadapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.marvin.greentoblue.R

class ParticipantAdapter(private val participants : Map<String, String>) : RecyclerView.Adapter<ParticipantAdapter.ParticipantViewHolder>() {
    class ParticipantViewHolder(itemView : View) : RecyclerView.ViewHolder(itemView){
        val txtParticipantID : TextView = itemView.findViewById(R.id.txtParticipantID)
        val txtEditParticipantName : EditText = itemView.findViewById(R.id.editTxtParticipantName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParticipantViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(
            R.layout.listviewitem_participant,
            parent,
            false
        )
        return ParticipantViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ParticipantViewHolder, position: Int) {
        val currentParticipantID = participants.keys.toList()[position]
        val currentParticipantName = participants[currentParticipantID]

        holder.txtParticipantID.text = currentParticipantID
        holder.txtEditParticipantName.setText(currentParticipantName)
    }

    override fun getItemCount(): Int {
        return participants.size
    }
}