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

/**
 * RegisterParentActivity handles creating a brand-new parent account.
 * A parent is the "root" account in Jarzy - every child account is always linked back to
 * a parentId, so this screen has to exist before anything else in the app can be used.
 */
class RegisterParentActivity : AppCompatActivity() {

    private lateinit var database: JarzyDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_parent)

        database = JarzyDatabase.getDatabase(this)

        val btnBack = findViewById<Button>(R.id.btnBack)
        val etUsername = findViewById<EditText>(R.id.etRegUsername)
        val etPassword = findViewById<EditText>(R.id.etRegPassword)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val tvError = findViewById<TextView>(R.id.tvRegError)

        btnBack.setOnClickListener { finish() }

        btnRegister.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString()

            if (username.isEmpty() || password.isEmpty()) {
                tvError.text = "Please fill in both fields."
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val existing = database.parentDao().getByUsername(username)
                if (existing != null) {
                    tvError.text = "That username is already taken."
                    return@launch
                }

                // Categories no longer need seeding here - they now belong to each CHILD
                // (as savings categories), so a "General" one is created when a child
                // registers instead of here when the parent does.
                database.parentDao().insert(ParentAccount(username = username, password = password))

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