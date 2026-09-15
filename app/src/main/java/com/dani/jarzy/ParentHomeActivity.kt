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
 * jump into logging an expense for that child.
 */

class ParentHomeActivity : AppCompatActivity() {

    private lateinit var database: JarzyDatabase

    // Kept as class-level fields because both onCreate() and onResume() need to read/write them
    // children holds the last list loaded from the
    // database so the Spinner's selected position can be matched back to an actual
    // ChildAccount object, and parentId identifies whose children we should be loading.
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
        val btnAddExpense = findViewById<Button>(R.id.btnAddExpense)

        tvWelcome.text = "Welcome, $parentUsername"

        // Opens the child registration screen with this parent's id
        // new child gets linked to the right parent.
        btnRegisterChild.setOnClickListener {
            val intent = Intent(this, RegisterChildActivity::class.java)
            intent.putExtra("PARENT_ID", parentId)
            startActivity(intent)
        }

        btnAddExpense.setOnClickListener {
            // selectedItemPosition tells which row of the dropdown is highlighted.
            val selectedIndex = spinnerChildren.selectedItemPosition
            if (selectedIndex < 0 || selectedIndex >= children.size) {
                return@setOnClickListener
            }
            val selectedChild = children[selectedIndex]
            val intent = Intent(this, AddExpenseActivity::class.java)
            intent.putExtra("CHILD_ID", selectedChild.childId)
            intent.putExtra("PARENT_ID", parentId)
            startActivity(intent)
        }

        // Logging out sends the user back to MainActivity
        // sets two special flags set on the Intent:
        // NEW_TASK starts a fresh task, and CLEAR_TASK wipes out every screen currently sitting underneath it.
        // Together they stop the user from pressing the phone's Back button after logging out and
        // ending up back on this "logged in" screen (Kumar, 2025).
        btnLogout.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    // onResume runs every time this screen becomes visible again, including when returned
    // after registering new child
    // the dropdown always shows current data
    // (Android Developers, 2026)
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            children = database.childDao().getChildrenForParent(parentId)
            val usernames = children.map { it.username }
            // ArrayAdapter takes our plain list of usernames and turns it into the rows the
            // Spinner (Android's drop-down widget) actually displays on screen
            // (GeeksforGeeks, 2019).
            spinnerChildren.adapter = ArrayAdapter(
                this@ParentHomeActivity,
                android.R.layout.simple_spinner_dropdown_item,
                usernames
            )
        }
    }
}

// References:
// Android Developers, 2026. The activity lifecycle [Webpage]. Available at:
// https://developer.android.com/guide/components/activities/activity-lifecycle
// [Accessed 15 September 2026].
// GeeksforGeeks, 2019. Spinner in Kotlin [Webpage]. Available at:
// https://www.geeksforgeeks.org/kotlin/spinner-in-kotlin/ [Accessed 15 September 2026].
// Kumar, M., 2025. Mastering Android Launch Modes and Intent Flags: A Complete Developer
// Guide [Webpage]. Available at:
// https://medium.com/@manishkumar_75473/mastering-android-launch-modes-and-intent-flags-a-complete-developer-guide-f44d298e29c9
// [Accessed 15 September 2026].