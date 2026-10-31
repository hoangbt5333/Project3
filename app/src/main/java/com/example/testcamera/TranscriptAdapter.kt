package com.example.testcamera

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.testcamera.data.TranscriptEntry

class TranscriptAdapter(
    private var items: List<TranscriptEntry> = emptyList()
) : RecyclerView.Adapter<TranscriptAdapter.TranscriptViewHolder>() {

    class TranscriptViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val layoutRow: View = itemView.findViewById(R.id.layoutTranscriptRow)
        val tvSemester: TextView = itemView.findViewById(R.id.tvSemester)
        val tvSubjectCode: TextView = itemView.findViewById(R.id.tvSubjectCode)
        val tvSubjectName: TextView = itemView.findViewById(R.id.tvSubjectName)
        val tvCredits: TextView = itemView.findViewById(R.id.tvCredits)
        val tvScore: TextView = itemView.findViewById(R.id.tvScore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TranscriptViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transcript_row, parent, false)
        return TranscriptViewHolder(view)
    }

    override fun onBindViewHolder(holder: TranscriptViewHolder, position: Int) {
        val item = items[position]

        holder.tvSemester.text = item.semester
        holder.tvSubjectCode.text = item.subjectCode
        holder.tvSubjectName.text = item.subjectName
        holder.tvCredits.text = item.credits.toString()
        holder.tvScore.text = item.letterGrade

        // Alternating row background for enhanced readability on touch kiosk
        if (position % 2 == 1) {
            holder.layoutRow.setBackgroundColor(0xFFF9FAFB.toInt())
        } else {
            holder.layoutRow.setBackgroundColor(Color.WHITE)
        }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<TranscriptEntry>) {
        this.items = newItems
        notifyDataSetChanged()
    }
}
