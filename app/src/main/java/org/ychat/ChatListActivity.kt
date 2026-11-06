package org.ychat

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * Liste des conversations (100 % programmatique, aucun R.* — voir [ChatAdapter]).
 *
 * - Sans profil -> [ProfileSetupActivity].
 * - Clic ligne -> [ConversationActivity].
 * - Bouton "Nouveau contact" -> [InviteActivity] (FAB remplace par un Button :
 *   pas de Material/FAB en dependances, gradle intouchable).
 */
class ChatListActivity : ComponentActivity() {

    private lateinit var repository: ChatRepository
    private lateinit var adapter: ChatAdapter
    private lateinit var list: ListView
    private lateinit var empty: TextView
    private var resumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = (application as YChatApp).repository

        if (!repository.hasProfile()) {
            startActivity(Intent(this, ProfileSetupActivity::class.java))
            finish()
            return
        }
        resumed = true

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        empty = TextView(this).apply {
            text = "Aucune conversation.\nInvitez un contact pour commencer."
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        list = ListView(this)
        val newChat = Button(this).apply { text = "+ Nouveau contact" }

        adapter = ChatAdapter(onClick = { row ->
            startActivity(ConversationActivity.newIntent(this, row.contactId, row.name))
        })
        list.adapter = adapter
        list.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
        )
        newChat.setOnClickListener {
            startActivity(Intent(this, InviteActivity::class.java))
        }

        root.addView(empty)
        root.addView(list)
        root.addView(newChat)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        if (!resumed) return
        val rows = repository.getContacts().map { c ->
            ChatRow(
                contactId = c.id,
                name = repository.displayNameOf(c),
                preview = repository.lastMessagePreview(c.id),
            )
        }
        adapter.submit(rows)
        val showEmpty = rows.isEmpty()
        empty.visibility = if (showEmpty) View.VISIBLE else View.GONE
        list.visibility = if (showEmpty) View.GONE else View.VISIBLE
    }
}
