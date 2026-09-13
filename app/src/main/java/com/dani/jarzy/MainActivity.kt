package com.dani.jarzy

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dani.jarzy.data.JarzyDatabase
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var database: JarzyDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        database = JarzyDatabase.getDatabase(this)

        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvError = findViewById<TextView>(R.id.tvError)
        val btnGoToRegister = findViewById<Button>(R.id.btnGoToRegister)

        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString()

            if (username.isEmpty() || password.isEmpty()) {
                tvError.text = "Please enter both a username and password."
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val parent = database.parentDao().login(username, password)
                if (parent != null) {
                    tvError.text = ""
                    Toast.makeText(this@MainActivity, "Logged in as parent: ${parent.username}", Toast.LENGTH_SHORT).show()
                    // TODO: navigate to ParentHomeActivity once it exists
                    return@launch
                }

                val child = database.childDao().login(username, password)
                if (child != null) {
                    tvError.text = ""
                    Toast.makeText(this@MainActivity, "Logged in as child: ${child.username}", Toast.LENGTH_SHORT).show()
                    // TODO: navigate to ChildHomeActivity once it exists
                    return@launch
                }

                tvError.text = "Incorrect username or password."
            }
        }

        btnGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterParentActivity::class.java))
        }
    }
}