package com.example.ojtlog

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.ojtlog.models.LogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogAdapter(private var logsList: List<LogEntry>) :
    RecyclerView.Adapter<LogAdapter.MessageViewHolder>() {

    private val dateFormatter = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
//        val textView: TextView = view.findViewById(R.id.itemTextView)
        val dateTxt: TextView = view.findViewById<TextView>(R.id.itemMessage_DateTxt)
        val timeInTxt: TextView = view.findViewById<TextView>(R.id.itemMessage_TimeInTxt)
        val timeOutTxt: TextView = view.findViewById<TextView>(R.id.itemMessage_TimeOutTxt)
        val workHrsTxt: TextView = view.findViewById<TextView>(R.id.itemMessage_WorkHrsTxt)
    }

    // This creates the "box" for the data
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    // This puts the actual text into the "box"
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val log = logsList[position]

        val date = log.savedDate?.toDate()
        holder.dateTxt.text = if (date != null) dateFormatter.format(date) else "No Date"

        holder.timeInTxt.text = log.timeIn
        holder.timeOutTxt.text = log.timeOut
        holder.workHrsTxt.text = log.calculatedWorkHrs.toString()

        when (log.entryType) {
            "regular" -> {
                holder.itemView.setBackgroundResource(R.drawable.item_message_regular_shape)
            }
            "absent" -> {
                holder.itemView.setBackgroundResource(R.drawable.item_message_absent_shape)

            }
            "holiday" -> {
                holder.itemView.setBackgroundResource(R.drawable.item_message_holiday_shape)
            }
            else -> {
                holder.itemView.setBackgroundResource(R.drawable.item_message_regular_shape)
            }
        }


    }

    override fun getItemCount():Int = logsList.size

    fun updateData(newList: List<LogEntry>) {
        this.logsList = newList
        notifyDataSetChanged()
    }
}
