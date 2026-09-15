package com.dani.jarzy

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dani.jarzy.data.JarzyDatabase
import com.dani.jarzy.data.ParentAccount
import kotlinx.coroutines.launch
import com.dani.jarzy.data.Category

/**
 * RegisterParentActivity handles creating a brand-new parent account.
 * A parent is the "root" account in Jarzy - every child account and every
 * spending category is always linked back to a parentId, so this screen has
 * to exist before anything else in the app can be used.
 */

class RegisterParentActivity : AppCompatActivity() {

    // Shared Room database instance
    // same pattern used across every Activity in the app.
    private lateinit var database: JarzyDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_parent)

        database = JarzyDatabase.getDatabase(this)

        // Bind every view we need from the layout by its id.
        val btnBack = findViewById<Button>(R.id.btnBack)
        val etUsername = findViewById<EditText>(R.id.etRegUsername)
        val etPassword = findViewById<EditText>(R.id.etRegPassword)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val tvError = findViewById<TextView>(R.id.tvRegError)

        // Back button just closes this screen and returns MainActivity.
        btnBack.setOnClickListener { finish() }

        btnRegister.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString()

            // Reject the attempt early if either field was left blank, rather than letting
            // an incomplete account get created (Azhar, 2020).
            if (username.isEmpty() || password.isEmpty()) {
                tvError.text = "Please fill in both fields."
                return@setOnClickListener
            }

            // Database work has to happen off the main thread inside a coroutine scoped
            // to this Activity's lifecycle.
            lifecycleScope.launch {
                // Look for an existing parent with this username first so we don't end up
                // with two accounts sharing the same login.
                val existing = database.parentDao().getByUsername(username)
                if (existing != null) {
                    tvError.text = "That username is already taken."
                    return@launch
                }

                // Create the new parent row. insert() returns the generated primary key
                // (new parentId) needed to attach categories to this specific parent.
                val newParentId = database.parentDao().insert(ParentAccount(username = username, password = password))

                // every new parent starts with 3 shared categories their children can spend against
                database.categoryDao().insert(Category(parentId = newParentId, name = "Snacks"))
                database.categoryDao().insert(Category(parentId = newParentId, name = "Toys"))
                database.categoryDao().insert(Category(parentId = newParentId, name = "Outings"))

                Toast.makeText(this@RegisterParentActivity, "Account created! You can now log in.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
}

// References:
// Android Developers, 2026. Toasts overview [Webpage]. Available at:
//     https://developer.android.com/guide/topics/ui/notifiers/toasts [Accessed 12 September 2026].
// Azhar, 2020. How to check if android editText is empty? [Webpage]. Available at:
//     https://www.tutorialspoint.com/how-to-check-if-android-edittext-is-empty-in-kotlin
//     [Accessed 12 September 2026].