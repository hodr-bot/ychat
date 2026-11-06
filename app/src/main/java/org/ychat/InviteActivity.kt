package org.ychat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Invitations (100 % programmatique, aucun R.*).
 *
 * - Section "lien unique par contact" : liste des contacts, chacun avec son lien
 *   ([ChatRepository.buildInviteLink]) + bouton copier.
 * - Section "adresse permanente" : [ChatRepository.getOrCreateStaticAddress] + copier.
 * - Section entrante : coller un lien recu ([ChatRepository.parseInviteLink] via
 *   `chatParseUri`), champ surnom, boutons "Accepter" (cree le contact puis ouvre
 *   [ConversationActivity]) / "Refuser" (efface le champ).
 */
class InviteActivity : ComponentActivity() {

    private lateinit var repository: ChatRepository
    private lateinit var contactsBox: LinearLayout
    private lateinit var staticAddrView: TextView
    private lateinit var incomingLink: EditText
    private lateinit var incomingName: EditText
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = (application as YChatApp).repository

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        val scroll = ScrollView(this)
        scroll.addView(root)

        root.addView(section("Liens d'invitation (un par contact)"))
        contactsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(contactsBox)

        // Nouveau contact sortant : cree un contact + son lien unique.
        val newName = EditText(this).apply { hint = "Nom du nouveau contact" }
        val newBtn = Button(this).apply { text = "Creer un lien unique" }
        newBtn.setOnClickListener {
            val name = newName.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                Toast.makeText(this, "Nom requis", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            repository.addContact(displayName = name)
            newName.text?.clear()
            refreshContacts()
        }
        root.addView(newName)
        root.addView(newBtn)

        root.addView(section("Adresse permanente"))
        staticAddrView = TextView(this).apply { textSize = 13f }
        val copyAddr = Button(this).apply { text = "Copier l'adresse" }
        copyAddr.setOnClickListener {
            copy(staticAddrView.text.toString(), "Adresse copiee")
        }
        root.addView(staticAddrView)
        root.addView(copyAddr)

        root.addView(section("Lien recu (scan/collage)"))
        incomingLink = EditText(this).apply { hint = "Coller le lien d'invitation recu" }
        incomingName = EditText(this).apply { hint = "Surnom local (optionnel)" }
        val accept = Button(this).apply { text = "Accepter" }
        val refuse = Button(this).apply { text = "Refuser" }
        accept.setOnClickListener { acceptIncoming() }
        refuse.setOnClickListener {
            incomingLink.text?.clear()
            incomingName.text?.clear()
            Toast.makeText(this, "Invitation refusee", Toast.LENGTH_SHORT).show()
        }
        root.addView(incomingLink)
        root.addView(incomingName)
        root.addView(accept)
        root.addView(refuse)

        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        staticAddrView.text = repository.getOrCreateStaticAddress()
        refreshContacts()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun section(title: String): TextView = TextView(this).apply {
        text = title
        textSize = 17f
        setPadding(0, 24, 0, 8)
    }

    private fun refreshContacts() {
        if (!::contactsBox.isInitialized) return
        contactsBox.removeAllViews()
        val all = repository.getContacts()
        if (all.isEmpty()) {
            contactsBox.addView(TextView(this).apply { text = "Aucun contact pour l'instant." })
            return
        }
        for (c in all) {
            val name = repository.displayNameOf(c)
            contactsBox.addView(TextView(this).apply {
                text = "$name — id ${c.id.take(8)}…"
                textSize = 15f
            })
            val link = TextView(this).apply {
                text = repository.buildInviteLink(c)
                textSize = 12f
            }
            val copyBtn = Button(this).apply { text = "Copier le lien de $name" }
            val linkText = repository.buildInviteLink(c)
            copyBtn.setOnClickListener { copy(linkText, "Lien copie") }
            val open = Button(this).apply { text = "Ouvrir la conversation" }
            open.setOnClickListener {
                startActivity(ConversationActivity.newIntent(this, c.id, name))
            }
            contactsBox.addView(link)
            contactsBox.addView(copyBtn)
            contactsBox.addView(open)
        }
    }

    private fun acceptIncoming() {
        val raw = incomingLink.text?.toString()?.trim().orEmpty()
        if (raw.isEmpty()) {
            Toast.makeText(this, "Lien vide", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            val parsed = withContext(Dispatchers.IO) { repository.parseInviteLink(raw) }
            if (parsed == null) {
                Toast.makeText(this@InviteActivity, "Lien invalide", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val nick = incomingName.text?.toString()?.trim().orEmpty()
            // Accepter = creer le contact (lien entrant conserve comme inviteLink).
            val contact = repository.addContact(
                displayName = nick.ifEmpty { "Contact" },
                inviteLink = parsed,
            )
            Toast.makeText(this@InviteActivity, "Contact ajoute", Toast.LENGTH_SHORT).show()
            startActivity(
                ConversationActivity.newIntent(
                    this@InviteActivity, contact.id, repository.displayNameOf(contact),
                ),
            )
            // Laisse l'ecran d'invitation derriere (retour possible) ; vide les champs.
            incomingLink.text?.clear()
            incomingName.text?.clear()
        }
    }

    private fun copy(text: String, ok: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("ychat", text))
        Toast.makeText(this, ok, Toast.LENGTH_SHORT).show()
    }

    companion object {
        fun newIntent(ctx: Context): Intent = Intent(ctx, InviteActivity::class.java)
    }
}
