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

class RegisterParentActivity : AppCompatActivity() {

    private lateinit var database: JarzyDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_parent)

        database = JarzyDatabase.getDatabase(this)

        val etUsername = findViewById<EditText>(R.id.etRegUsername)
        val etPassword = findViewById<EditText>(R.id.etRegPassword)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val tvError = findViewById<TextView>(R.id.tvRegError)

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

                database.parentDao().insert(ParentAccount(username = username, password = password))
                Toast.makeText(this@RegisterParentActivity, "Account created! You can now log in.", Toast.LENGTH_LONG).show()
                finish() // closes this screen and returns to whatever opened it (the Login screen)
            }
        }
    }
}