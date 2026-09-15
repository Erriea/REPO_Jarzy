package com.dani.jarzy

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dani.jarzy.data.ChildAccount
import com.dani.jarzy.data.JarzyDatabase
import com.dani.jarzy.data.SavingsCategory
import kotlinx.coroutines.launch

/**
 * RegisterChildActivity is opened from inside a parent's account (ParentHomeActivity)
 * and creates a new child account linked to that specific parent. It also seeds a
 * starting "General" savings category for the new child - every child needs at least
 * one category to exist, since that's where the parent's budget lands before the child
 * organises it further.
 */
class RegisterChildActivity : AppCompatActivity() {

    private lateinit var database: JarzyDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_child)

        database = JarzyDatabase.getDatabase(this)

        val parentId = intent.getLongExtra("PARENT_ID", -1L)

        val btnBack = findViewById<Button>(R.id.btnBack)
        val etUsername = findViewById<EditText>(R.id.etChildUsername)
        val etPassword = findViewById<EditText>(R.id.etChildPassword)
        val etMinGoal = findViewById<EditText>(R.id.etMinGoal)
        val etMaxGoal = findViewById<EditText>(R.id.etMaxGoal)
        val btnRegisterChild = findViewById<Button>(R.id.btnRegisterChild)
        val tvError = findViewById<TextView>(R.id.tvRegChildError)

        btnBack.setOnClickListener { finish() }

        btnRegisterChild.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString()
            val minGoalText = etMinGoal.text.toString().trim()
            val maxGoalText = etMaxGoal.text.toString().trim()

            if (parentId == -1L) {
                tvError.text = "Something went wrong identifying the parent account. Please go back and try again."
                return@setOnClickListener
            }

            if (username.isEmpty() || password.isEmpty() || minGoalText.isEmpty() || maxGoalText.isEmpty()) {
                tvError.text = "Please fill in all fields."
                return@setOnClickListener
            }

            val minGoal = minGoalText.toDoubleOrNull()
            val maxGoal = maxGoalText.toDoubleOrNull()

            if (minGoal == null || maxGoal == null || minGoal < 0 || maxGoal < 0) {
                tvError.text = "Goals must be valid numbers of 0 or more."
                return@setOnClickListener
            }

            if (maxGoal < minGoal) {
                tvError.text = "Maximum goal cannot be less than the minimum goal."
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val existing = database.childDao().getByUsername(username)
                if (existing != null) {
                    tvError.text = "That username is already taken."
                    return@launch
                }

                // insert() returns the new childId, which we need immediately below to
                // create that child's first savings category.
                val newChildId = database.childDao().insert(
                    ChildAccount(
                        parentId = parentId,
                        username = username,
                        password = password,
                        minMonthlyGoal = minGoal,
                        maxMonthlyGoal = maxGoal
                    )
                )

                // Every new child starts with one "General" savings category, empty for
                // now - this guarantees there's always somewhere for a parent's budget to
                // land, even before the child creates categories of their own.
                database.savingsCategoryDao().insert(
                    SavingsCategory(childId = newChildId, name = "General")
                )

                Toast.makeText(this@RegisterChildActivity, "Child account created!", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
}

// References:
// Vogel, L., 2016. Android Intents - Tutorial (Version 0.3) [Webpage]. Available at:
//     https://www.vogella.com/tutorials/AndroidIntent/article.html [Accessed 12 September 2026].
// Tutorialspoint, n.d. Kotlin String - toDoubleOrNull() Function [Webpage]. Available at:
//     https://www.tutorialspoint.com/kotlin/kotlin_string_todoubleornull_function.htm
//     [Accessed 12 September 2026].