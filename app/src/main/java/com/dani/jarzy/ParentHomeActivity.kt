package com.dani.jarzy

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dani.jarzy.data.ChildAccount
import com.dani.jarzy.data.JarzyDatabase
import kotlinx.coroutines.launch

/**
 * ParentHomeActivity is the screen a parent lands on after logging in. From here they
 * can register a new child, pick one of their existing children from a dropdown, and
 * open that child's own detail page - which is where budget, expenses and savings for
 * that specific child are actually managed (see ChildDetailActivity).
 */
class ParentHomeActivity : AppCompatActivity() {

    private lateinit var database: JarzyDatabase

    private var children: List<ChildAccount> = emptyList()
    private var parentId: Long = -1L

    private lateinit var spinnerChildren: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parent_home)

        database = JarzyDatabase.getDatabase(this)
        parentId = intent.getLongExtra("PARENT_ID", -1L)
        val parentUsername = intent.getStringExtra("PARENT_USERNAME") ?: ""

        val tvWelcome = findViewById<TextView>(R.id.tvWelcome)
        val btnRegisterChild = findViewById<Button>(R.id.btnRegisterChild)
        val btnLogout = findViewById<Button>(R.id.btnLogout)
        spinnerChildren = findViewById(R.id.spinnerChildren)
        val btnViewChild = findViewById<Button>(R.id.btnViewChild)

        tvWelcome.text = "Welcome, $parentUsername"

        btnRegisterChild.setOnClickListener {
            val intent = Intent(this, RegisterChildActivity::class.java)
            intent.putExtra("PARENT_ID", parentId)
            startActivity(intent)
        }

        // Opens the selected child's own detail page rather than acting on them
        // directly from this screen - budget, expenses and savings all live there now
        // (Vogel, 2016).
        btnViewChild.setOnClickListener {
            val selectedIndex = spinnerChildren.selectedItemPosition
            if (selectedIndex < 0 || selectedIndex >= children.size) {
                return@setOnClickListener
            }
            val selectedChild = children[selectedIndex]
            val intent = Intent(this, ChildDetailActivity::class.java)
            intent.putExtra("CHILD_ID", selectedChild.childId)
            intent.putExtra("CHILD_USERNAME", selectedChild.username)
            startActivity(intent)
        }

        btnLogout.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            children = database.childDao().getChildrenForParent(parentId)
            val usernames = children.map { it.username }

            // item_spinner_text is a custom row layout (just a bigger TextView) used for
            // both the closed spinner and its dropdown list, so the child names read at
            // the same larger size as the rest of this screen's text - the system
            // default row layout used before this was noticeably smaller.
            val adapter = ArrayAdapter(
                this@ParentHomeActivity,
                R.layout.item_spinner_text,
                usernames
            )
            adapter.setDropDownViewResource(R.layout.item_spinner_text)
            spinnerChildren.adapter = adapter
        }
    }
}

// References:
// Android Developers, 2026. The activity lifecycle [Webpage]. Available at:
//     https://developer.android.com/guide/components/activities/activity-lifecycle
//     [Accessed 8 September 2026].

// GeeksforGeeks, 2025. Spinner in Kotlin [Webpage]. Available at:
//     https://www.geeksforgeeks.org/kotlin/spinner-in-kotlin/ [Accessed 14 September 2026].

// Kumar, M., 2025. Mastering Android Launch Modes and Intent Flags: A Complete Developer
//     Guide [Webpage]. Available at:
//     https://medium.com/@manishkumar_75473/mastering-android-launch-modes-and-intent-flags-a-complete-developer-guide-f44d298e29c9
//     [Accessed 8 September 2026].

// Vogel, L., 2016. Android Intents - Tutorial (Version 0.3) [Webpage]. Available at:
//     https://www.vogella.com/tutorials/AndroidIntent/article.html [Accessed 9 September 2026].