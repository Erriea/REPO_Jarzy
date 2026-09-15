package com.dani.jarzy

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * ChildHomeActivity is the screen a child lands on after logging in. It is deliberately
 * simple at this bare-bones prototype stage: a welcome message, a shortcut to create a
 * new spending category, and a way to log out.
 */

class ChildHomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_child_home)

        // Read the values MainActivity attached to the Intent when it sent the child here.
        val childUsername = intent.getStringExtra("CHILD_USERNAME") ?: ""
        // PARENT_ID is needed
        // categories belong to the parent and are shared across all of that parent's children
        // rather than belonging to one individual child account.
        val parentId = intent.getLongExtra("PARENT_ID", -1L)

        val tvChildWelcome = findViewById<TextView>(R.id.tvChildWelcome)
        val btnCreateCategory = findViewById<Button>(R.id.btnCreateCategory)
        val btnLogout = findViewById<Button>(R.id.btnLogout)

        tvChildWelcome.text = "Welcome, $childUsername"

        btnCreateCategory.setOnClickListener {
            val intent = Intent(this, CreateCategoryActivity::class.java)
            intent.putExtra("PARENT_ID", parentId)
            startActivity(intent)
        }

        // clearing the task means pressing Back from the login screen won't return
        // to this logged-in screen (Android Developers, n.d.)
        btnLogout.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}

// References:
// Kumar, M., 2025. Mastering Android Launch Modes and Intent Flags: A Complete Developer
//     Guide [Webpage]. Available at:
//     https://medium.com/@manishkumar_75473/mastering-android-launch-modes-and-intent-flags-a-complete-developer-guide-f44d298e29c9
//     [Accessed 12 September 2026].