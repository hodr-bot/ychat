package org.ychat

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity

/**
 * Creation du profil local (100 % programmatique, aucun R.*).
 *
 * - Champ nom + bouton creer : persiste via [ChatRepository.saveProfile] avec les
 *   serveurs par defaut [ServerPresets.defaults] (modifiables ensuite, persistes
 *   en SharedPreferences via [ServerPresets.save]/[load]).
 * - Redirige vers [ChatListActivity] et se termine.
 */
class ProfileSetupActivity : ComponentActivity() {

    private lateinit var repository: ChatRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = (application as YChatApp).repository

        if (repository.hasProfile()) {
            goList()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        val title = TextView(this).apply {
            text = "Bienvenue sur YChat"
            textSize = 24f
            gravity = Gravity.CENTER
        }
        val hint = TextView(this).apply {
            text = "Choisissez un nom d'affichage.\nLes serveurs par defaut seront utilises (modifiables plus tard)."
            gravity = Gravity.CENTER
        }
        val name = EditText(this).apply { this.hint = "Votre nom" }
        val create = Button(this).apply { text = "Creer le profil" }
        create.setOnClickListener {
            val displayName = name.text?.toString()?.trim().orEmpty()
            if (displayName.isEmpty()) {
                Toast.makeText(this, "Nom requis", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            repository.saveProfile(displayName, ServerPresets.defaults())
            Toast.makeText(this, "Profil cree", Toast.LENGTH_SHORT).show()
            goList()
        }
        root.addView(title)
        root.addView(hint)
        root.addView(name)
        root.addView(create)
        setContentView(root)
    }

    private fun goList() {
        startActivity(Intent(this, ChatListActivity::class.java))
        finish()
    }
}
