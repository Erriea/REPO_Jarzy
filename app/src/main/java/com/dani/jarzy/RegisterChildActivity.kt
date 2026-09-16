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
 * and creates a new child account linked to that specific parent. The parent also builds
 * up this child's starting savings categories here, one name at a time via the "+ Add
 * Category" button - each one is created holding 0.0, ready for the parent to fund later
 * from ChildDetailActivity. If the parent doesn't add any, a single "General" category is
 * created automatically instead, so a child is never left without at least one.
 */
class RegisterChildActivity : AppCompatActivity() {

    private lateinit var database: JarzyDatabase

    // Category names the parent has added so far on this screen, in the order they were
    // added - held in memory only until "Register Child" is tapped and they're all saved
    // to the database together.
    private val categoryNames = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_child)

        database = JarzyDatabase.getDatabase(this)

        val parentId = intent.getLongExtra("PARENT_ID", -1L)

        val btnBack = findViewById<Button>(R.id.btnBack)
        val etUsername = findViewById<EditText>(R.id.etChildUsername)
        val etPassword = findViewById<EditText>(R.id.etChildPassword)
        val etAllowance = findViewById<EditText>(R.id.etMonthlyAllowance)
        val etMinSpend = findViewById<EditText>(R.id.etMinSpend)
        val etMaxSpend = findViewById<EditText>(R.id.etMaxSpend)
        val etCategoryName = findViewById<EditText>(R.id.etCategoryName)
        val btnAddCategory = findViewById<Button>(R.id.btnAddCategory)
        val tvCategoriesAdded = findViewById<TextView>(R.id.tvCategoriesAdded)
        val btnRegisterChild = findViewById<Button>(R.id.btnRegisterChild)
        val tvError = findViewById<TextView>(R.id.tvRegChildError)

        btnBack.setOnClickListener { finish() }

        btnAddCategory.setOnClickListener {
            val name = etCategoryName.text.toString().trim()

            if (name.isEmpty()) {
                tvError.text = "Enter a category name before adding it."
                return@setOnClickListener
            }

            if (categoryNames.any { it.equals(name, ignoreCase = true) }) {
                tvError.text = "That category has already been added."
                return@setOnClickListener
            }

            categoryNames.add(name)
            etCategoryName.text.clear()
            tvError.text = ""

            // joinToString() turns the running list back into one readable line, the same
            // approach ChildHomeActivity uses for its savings breakdown (Duggu, 2023).
            tvCategoriesAdded.text = "Categories added: " + categoryNames.joinToString(", ")
        }

        btnRegisterChild.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString()
            val allowanceText = etAllowance.text.toString().trim()
            val minSpendText = etMinSpend.text.toString().trim()
            val maxSpendText = etMaxSpend.text.toString().trim()

            if (parentId == -1L) {
                tvError.text = "Something went wrong identifying the parent account. Please go back and try again."
                return@setOnClickListener
            }

            if (username.isEmpty() || password.isEmpty() || allowanceText.isEmpty() ||
                minSpendText.isEmpty() || maxSpendText.isEmpty()
            ) {
                tvError.text = "Please fill in all fields."
                return@setOnClickListener
            }

            if (username.length < 4) {
                tvError.text = "Username must be at least 4 characters long."
                return@setOnClickListener
            }

            if (password.length < 8) {
                tvError.text = "Password must be at least 8 characters long."
                return@setOnClickListener
            }

            // any() checks whether at least one character in the password is neither a
            // letter nor a digit - i.e. a special character - without needing a full
            // regular expression (Yuan, 2024).
            if (!password.any { !it.isLetterOrDigit() }) {
                tvError.text = "Password must contain at least one special character."
                return@setOnClickListener
            }

            val allowance = allowanceText.toDoubleOrNull()
            val minSpend = minSpendText.toDoubleOrNull()
            val maxSpend = maxSpendText.toDoubleOrNull()

            if (allowance == null || minSpend == null || maxSpend == null ||
                allowance < 0 || minSpend < 0 || maxSpend < 0
            ) {
                tvError.text = "Allowance and spend amounts must be valid numbers of 0 or more."
                return@setOnClickListener
            }

            if (maxSpend < minSpend) {
                tvError.text = "Maximum spend cannot be less than the minimum spend."
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val existing = database.childDao().getByUsername(username)
                if (existing != null) {
                    tvError.text = "That username is already taken."
                    return@launch
                }

                // insert() returns the new childId, which we need immediately below to
                // create that child's savings categories.
                val newChildId = database.childDao().insert(
                    ChildAccount(
                        parentId = parentId,
                        username = username,
                        password = password,
                        monthlyAllowance = allowance,
                        minMonthlySpend = minSpend,
                        maxMonthlySpend = maxSpend
                    )
                )

                // The parent didn't add any categories - fall back to one "General"
                // category, same as before, so this child always has somewhere for
                // money to land.
                val categoriesToCreate = if (categoryNames.isEmpty()) listOf("General") else categoryNames

                // The monthly allowance is credited once here, at setup, straight into
                // whichever category ends up first in the list - it doesn't repeat or
                // get added again automatically, that's the only thing this does for now.
                for ((index, name) in categoriesToCreate.withIndex()) {
                    val startingAmount = if (index == 0) allowance else 0.0
                    database.savingsCategoryDao().insert(
                        SavingsCategory(childId = newChildId, name = name, amountSaved = startingAmount)
                    )
                }

                Toast.makeText(this@RegisterChildActivity, "Child account created!", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
}

// References:
// Vogel, L., 2016. Android Intents - Tutorial (Version 0.3) [Webpage]. Available at:
//     https://www.vogella.com/tutorials/AndroidIntent/article.html [Accessed 9 September 2026].

// Tutorialspoint, 2026. Kotlin String - toDoubleOrNull() Function [Webpage]. Available at:
//     https://www.tutorialspoint.com/kotlin/kotlin_string_todoubleornull_function.htm
//     [Accessed 8 September 2026].

// Yuan, K., 2024. Password Validation in Kotlin [Webpage]. Available at: // https://www.baeldung.com/kotlin/password-validation [Accessed 16 September 2026].

// Duggu, 2023. joinToString in Kotlin [Webpage]. Available at:
//     https://medium.com/@dugguRK/jointostring-in-kotlin-d227b9394486 [Accessed 11 September 2026].
// Kotlin, n.d. withIndex() [Webpage]. Available at:
//     https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/with-index.html
//     [Accessed 13 September 2026].
