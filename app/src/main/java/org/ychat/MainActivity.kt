package org.ychat

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Aiguillage : sans profil -> [ProfileSetupActivity], sinon [ChatListActivity].
 * (Modifie a minima : la mission impose la redirection ; aucun R.*, aucun layout.)
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = (application as YChatApp).repository
        val next = if (repo.hasProfile()) {
            Intent(this, ChatListActivity::class.java)
        } else {
            Intent(this, ProfileSetupActivity::class.java)
        }
        startActivity(next)
        finish()
    }
}
