package org.ychat

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Liste des conversations — adaptateur ListView programmatique.
 *
 * ECART (voir .worker-report.md) : mission demandait RecyclerView sur `item_chat`
 * (ychat1). Impossible ici : `androidx.recyclerview` absent des dependances et
 * `build.gradle.kts` intouchable + `res/layout/` absent de ce worktree (aucun
 * fichier neuf hors `java/org/ychat/`). Equivalent ListView 100 % programmatique
 * (aucun R.*), meme role : nom + apercu + clic -> conversation.
 */
class ChatAdapter(
    private var items: List<ChatRow> = emptyList(),
    private val onClick: (ChatRow) -> Unit = {},
) : BaseAdapter() {

    fun submit(rows: List<ChatRow>) {
        items = rows
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): ChatRow = items[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = items[position]
        val ctx = parent.context
        val root = (convertView as? LinearLayout) ?: LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 24, 24, 24)
        }
        root.removeAllViews()

        val avatar = TextView(ctx).apply {
            text = row.name.firstOrNull()?.uppercase() ?: "?"
            textSize = 20f
            gravity = Gravity.CENTER
            contentDescription = row.name
        }
        avatar.layoutParams = LinearLayout.LayoutParams(120, 120)

        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        col.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val name = TextView(ctx).apply {
            text = row.name
            textSize = 16f
        }
        val preview = TextView(ctx).apply {
            text = row.preview
            textSize = 14f
            maxLines = 1
        }
        col.addView(name)
        col.addView(preview)

        root.addView(avatar)
        root.addView(col)
        root.setOnClickListener { onClick(row) }
        return root
    }
}

/** Ligne de conversation pour l'adaptateur. */
data class ChatRow(
    val contactId: String,
    val name: String,
    val preview: String,
)
