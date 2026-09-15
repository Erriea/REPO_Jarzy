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
import kotlinx.coroutines.launch

/**
 * RegisterChildActivity is opened from inside a parent's account (ParentHomeActivity)
 * and creates a new child account linked to that specific parent. It also collects the
 * child's minimum/maximum monthly spending goals, which are used elsewhere in the app to
 * give the parent a sense of whether the child is spending within a healthy range.
 */

class RegisterChildActivity : AppCompatActivity() {

    private lateinit var database: JarzyDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_child)

        database = JarzyDatabase.getDatabase(this)

        // Read the parentId that was attached to the Intent used to launch this screen.
        // -1L is used as a "not found" default so we can detect a missing/invalid id below
        // instead of silently registering a child with a broken parent link (Vogel, 2016).
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


            //checks if parent is missing from intent and treat as error
            if (parentId == -1L) {
                tvError.text = "Something went wrong identifying the parent account. Please go back and try again."
                return@setOnClickListener
            }

            // all four fields are required before we even look at the database.
            if (username.isEmpty() || password.isEmpty() || minGoalText.isEmpty() || maxGoalText.isEmpty()) {
                tvError.text = "Please fill in all fields."
                return@setOnClickListener
            }

            // toDoubleOrNull() returns null instead of crashing if the text isn't a valid number
            val minGoal = minGoalText.toDoubleOrNull()
            val maxGoal = maxGoalText.toDoubleOrNull()

            if (minGoal == null || maxGoal == null || minGoal < 0 || maxGoal < 0) {
                tvError.text = "Goals must be valid numbers of 0 or more."
                return@setOnClickListener
            }

            // A max goal smaller than the min goal doesn't make sense
            // reject it here rather than storing an inconsistent pair of values.
            if (maxGoal < minGoal) {
                tvError.text = "Maximum goal cannot be less than the minimum goal."
                return@setOnClickListener
            }

            lifecycleScope.launch {
                // Same duplicate-username protection used on the parent registration screen.
                val existing = database.childDao().getByUsername(username)
                if (existing != null) {
                    tvError.text = "That username is already taken."
                    return@launch
                }

                database.childDao().insert(
                    ChildAccount(
                        parentId = parentId,
                        username = username,
                        password = password,
                        minMonthlyGoal = minGoal,
                        maxMonthlyGoal = maxGoal
                    )
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