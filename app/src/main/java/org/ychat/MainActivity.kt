package org.ychat

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val title = TextView(this).apply {
            text = "YChat"
            textSize = 32f
            gravity = Gravity.CENTER
        }
        val backendStatus = TextView(this).apply {
            text = "Backend SimpleX : non connecté"
            textSize = 16f
            gravity = Gravity.CENTER
        }
        layout.addView(title)
        layout.addView(backendStatus)
        setContentView(layout)
    }
}
