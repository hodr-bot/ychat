package org.ychat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Toast
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Conversation 1:1 (100 % programmatique, aucun R.*).
 *
 * Ligne de saisie + bouton envoyer. Envoi via [ChatRepository.sendMessage]
 * -> [SimplexJni.sendCmd] sur IO (NOTE mission : "SimplexJni.send" ; l'API
 * reelle du worktree expose `sendCmd` — ecart consigne en rapport).
 *
 * Pas de `lifecycleScope` : `lifecycle-runtime-ktx` absent des dependances et
 * gradle intouchable -> scope manuel annule dans [onDestroy].
 */
class ConversationActivity : ComponentActivity() {

    private lateinit var repository: ChatRepository
    private lateinit var adapter: MessageAdapter
    private lateinit var list: ListView
    private lateinit var input: EditText
    private var contactId: String = ""
    private var sending = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = (application as YChatApp).repository
        contactId = intent.getStringExtra(EXTRA_CONTACT_ID) ?: ""
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""

        if (contactId.isEmpty() || repository.getContact(contactId) == null) {
            Toast.makeText(this, "Conversation inconnue", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        setTitle(title)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        list = ListView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
            )
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
        }
        input = EditText(this).apply {
            hint = "Message"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val send = Button(this).apply { text = "Envoyer" }
        send.setOnClickListener { sendCurrent() }

        adapter = MessageAdapter()
        list.adapter = adapter
        row.addView(input)
        row.addView(send)
        root.addView(list)
        root.addView(row)
        setContentView(root)
        refresh()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun refresh() {
        if (!::adapter.isInitialized) return
        val msgs = repository.getMessages(contactId)
        adapter.submit(msgs)
        if (msgs.isNotEmpty()) list.setSelection(msgs.size - 1)
    }

    private fun sendCurrent() {
        if (sending) return
        val text = input.text?.toString()?.trim() ?: ""
        if (text.isEmpty()) return
        sending = true
        scope.launch {
            try {
                val res = repository.sendMessage(contactId, text)
                input.text?.clear()
                refresh()
                if (!res.sent) {
                    Toast.makeText(
                        this@ConversationActivity,
                        "Backend indisponible : message garde en local",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@ConversationActivity,
                    "Envoi impossible : ${e.message}",
                    Toast.LENGTH_SHORT,
                ).show()
            } finally {
                sending = false
            }
        }
    }

    companion object {
        private const val EXTRA_CONTACT_ID = "contact_id"
        private const val EXTRA_TITLE = "title"

        fun newIntent(ctx: Context, contactId: String, title: String): Intent =
            Intent(ctx, ConversationActivity::class.java)
                .putExtra(EXTRA_CONTACT_ID, contactId)
                .putExtra(EXTRA_TITLE, title)
    }
}
