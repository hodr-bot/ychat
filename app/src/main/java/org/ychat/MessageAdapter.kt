package org.ychat

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bulles de conversation — adaptateur ListView programmatique.
 *
 * ECART (voir .worker-report.md) : mission demandait RecyclerView sur
 * `item_message_in` / `item_message_out` (ychat1). Meme contrainte que
 * [ChatAdapter] (pas de recyclerview en dependances, gradle intouchable, pas de
 * res/layout). Deux types de vue (entrant/sortant), meme role : texte + heure.
 * Aucun R.*.
 */
class MessageAdapter(
    private var items: List<ChatMessage> = emptyList(),
) : BaseAdapter() {

    fun submit(messages: List<ChatMessage>) {
        items = messages
        notifyDataSetChanged()
    }

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): ChatMessage = items[position]
    override fun getItemId(position: Int): Long = items[position].id
    override fun getViewTypeCount(): Int = 2
    override fun getItemViewType(position: Int): Int =
        if (items[position].outgoing) TYPE_OUT else TYPE_IN

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val m = items[position]
        val ctx = parent.context
        val outer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (m.outgoing) Gravity.END else Gravity.START
            setPadding(16, 8, 16, 8)
        }
        val bubble = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
        }
        val body = TextView(ctx).apply {
            text = m.text
            textSize = 15f
        }
        val time = TextView(ctx).apply {
            text = timeFmt.format(Date(m.timestamp)) + if (m.outgoing) " ✓✓" else ""
            textSize = 11f
            gravity = Gravity.END
        }
        bubble.addView(body)
        bubble.addView(time)
        outer.addView(bubble)
        return outer
    }

    companion object {
        private const val TYPE_IN = 0
        private const val TYPE_OUT = 1
    }
}
