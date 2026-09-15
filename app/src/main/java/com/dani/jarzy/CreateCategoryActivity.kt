package com.dani.jarzy

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dani.jarzy.data.Category
import com.dani.jarzy.data.JarzyDatabase
import kotlinx.coroutines.launch

/**
 * CreateCategoryActivity lets a parent (or a child, from their own home screen) add a new
 * spending category. Categories belong to the parent and are shared across every child of
 * that parent, so this screen is reachable from more than one place in the app.
 */

class CreateCategoryActivity : AppCompatActivity() {

    private lateinit var database: JarzyDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_category)

        database = JarzyDatabase.getDatabase(this)
        val parentId = intent.getLongExtra("PARENT_ID", -1L)

        val btnBack = findViewById<Button>(R.id.btnBack)
        val etCategoryName = findViewById<EditText>(R.id.etCategoryName)
        val etCategoryBudget = findViewById<EditText>(R.id.etCategoryBudget)
        val btnSaveCategory = findViewById<Button>(R.id.btnSaveCategory)
        val tvCategoryError = findViewById<TextView>(R.id.tvCategoryError)

        btnBack.setOnClickListener { finish() }

        btnSaveCategory.setOnClickListener {
            val name = etCategoryName.text.toString().trim()
            val budgetText = etCategoryBudget.text.toString().trim()

            // Whatever screen opened this one is supposed to always pass a real PARENT_ID
            // safety net in case that Intent extra is missing.
            if (parentId == -1L) {
                tvCategoryError.text = "Something went wrong identifying the parent account. Please go back and try again."
                return@setOnClickListener
            }

            // Name and budget are checked as two separate error messages
            // user knows exactly which field to fix
            // (Azhar, 2020).
            if (name.isEmpty()) {
                tvCategoryError.text = "Please give the category a name."
                return@setOnClickListener
            }

            if (budgetText.isEmpty()) {
                tvCategoryError.text = "Please enter a monthly budget amount."
                return@setOnClickListener
            }

            // toDoubleOrNull() safely converts the typed text to a Double, or gives us null
            // if what was typed isn't actually a number (Tutorialspoint, n.d.).
            val budget = budgetText.toDoubleOrNull()

            if (budget == null || budget < 0) {
                tvCategoryError.text = "Budget amount must be a valid number of 0 or more."
                return@setOnClickListener
            }

            lifecycleScope.launch {
                // Pull every category this parent already has, then use any() to check
                // whether one of them matches the new name. any() runs the given condition
                // against each item in the list and returns true the moment one matches,
                // which reads more clearly here than a manual loop with a flag variable
                // (Krishnan, 2024).
                // ignoreCase = true means "Snacks" and "snacks" are treated
                // as the same category name, so we don't end up with near-duplicates.
                val existingCategories = database.categoryDao().getCategoriesForParent(parentId)
                val alreadyExists = existingCategories.any { it.name.equals(name, ignoreCase = true) }

                if (alreadyExists) {
                    tvCategoryError.text = "You already have a category with that name."
                    return@launch
                }

                database.categoryDao().insert(
                    Category(
                        parentId = parentId,
                        name = name,
                        monthlyBudgetAmount = budget
                    )
                )
                // Confirms the save with a short pop-up before returning to whichever
                // screen opened this one (Android Developers, 2026).
                Toast.makeText(this@CreateCategoryActivity, "Category created!", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
}

// References:
// Azhar, 2020. How to check if android editText is empty? [Webpage]. Available at:
//     https://www.tutorialspoint.com/how-to-check-if-android-edittext-is-empty-in-kotlin
//     [Accessed 15 September 2026].
// Tutorialspoint, n.d. Kotlin String - toDoubleOrNull() Function [Webpage]. Available at:
//     https://www.tutorialspoint.com/kotlin/kotlin_string_todoubleornull_function.htm
//     [Accessed 15 September 2026].
// Krishnan, Y., 2024. any() vs. none() vs. all() in Kotlin [Webpage]. Available at:
//     https://www.baeldung.com/kotlin/any-none-all-differences [Accessed 15 September 2026].
// Android Developers, 2026. Toasts overview [Webpage]. Available at:
//     https://developer.android.com/guide/topics/ui/notifiers/toasts [Accessed 15 September 2026].