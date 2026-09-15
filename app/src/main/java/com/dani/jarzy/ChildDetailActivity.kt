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
import com.dani.jarzy.data.SavingsCategory
import kotlinx.coroutines.launch

/**
 * ChildDetailActivity is opened from ParentHomeActivity when the parent picks one of
 * their children from the dropdown. Everything on this screen is scoped to that ONE
 * child: their total savings and category breakdown (view-only for the parent - only
 * the child can move money between categories, from their own ChildHomeActivity/
 * CreateSavingsCategoryActivity), giving them a budget, and logging an expense for them.
 * The heading at the top always names which child is being viewed, with an italic
 * "(Parent View)" label underneath, so this screen is never mistaken for the parent's
 * own home screen or the child's own home screen.
 */
class ChildDetailActivity : AppCompatActivity() {

    private lateinit var database: JarzyDatabase
    private var childId: Long = -1L

    private lateinit var tvSavingsTotal: TextView
    private lateinit var tvSavingsBreakdown: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_child_detail)

        database = JarzyDatabase.getDatabase(this)

        // Both extras are read once here - the id for every database call on this
        // screen, the username just for display in the heading (Vogel, 2016).
        childId = intent.getLongExtra("CHILD_ID", -1L)
        val childUsername = intent.getStringExtra("CHILD_USERNAME") ?: ""

        val btnBack = findViewById<Button>(R.id.btnBack)
        val tvChildDetailHeading = findViewById<TextView>(R.id.tvChildDetailHeading)
        tvSavingsTotal = findViewById(R.id.tvSavingsTotal)
        tvSavingsBreakdown = findViewById(R.id.tvSavingsBreakdown)
        val etBudgetAmount = findViewById<EditText>(R.id.etBudgetAmount)
        val btnAddBudget = findViewById<Button>(R.id.btnAddBudget)
        val btnAddExpense = findViewById<Button>(R.id.btnAddExpense)
        val btnViewExpenseHistory = findViewById<Button>(R.id.btnViewExpenseHistory)

        // Names the specific child so this page can't be confused with
        // ParentHomeActivity's own "Welcome, <parent>" heading.
        tvChildDetailHeading.text = "Managing: $childUsername"

        btnBack.setOnClickListener { finish() }

        btnAddBudget.setOnClickListener {
            if (childId == -1L) {
                Toast.makeText(this, "Something went wrong identifying the child account.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // toDoubleOrNull() again for safe text-to-number conversion (Tutorialspoint, n.d.-a).
            val amount = etBudgetAmount.text.toString().trim().toDoubleOrNull()
            if (amount == null || amount <= 0) {
                Toast.makeText(this, "Enter a valid budget amount greater than 0.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val existingCategories = database.savingsCategoryDao().getCategoriesForChild(childId)

                // A budget always lands in the child's "General" category first - the
                // child decides how to split it up further from there.
                val generalCategory = existingCategories.firstOrNull { it.name.equals("General", ignoreCase = true) }

                if (generalCategory != null) {
                    database.savingsCategoryDao().update(
                        generalCategory.copy(amountSaved = generalCategory.amountSaved + amount)
                    )
                } else {
                    // Defensive fallback - every child should already have a "General"
                    // category from registration, but create one if it's somehow missing.
                    database.savingsCategoryDao().insert(
                        SavingsCategory(childId = childId, name = "General", amountSaved = amount)
                    )
                }

                etBudgetAmount.text.clear()
                Toast.makeText(this@ChildDetailActivity, "Budget added!", Toast.LENGTH_SHORT).show()
                refreshSavings()
            }
        }

        btnAddExpense.setOnClickListener {
            val btnViewExpenseHistory = findViewById<Button>(R.id.btnViewExpenseHistory)
            val intent = Intent(this, AddExpenseActivity::class.java)
            intent.putExtra("CHILD_ID", childId)
            startActivity(intent)

            btnViewExpenseHistory.setOnClickListener {
                val intent = Intent(this, ExpenseHistoryActivity::class.java)
                intent.putExtra("CHILD_ID", childId)
                startActivity(intent)
            }
        }
    }

    // Runs on first open AND whenever this screen is returned to (e.g. after logging an
    // expense), so the figures shown are never stale (Android Developers, 2026).
    override fun onResume() {
        super.onResume()
        refreshSavings()
    }

    // Pulled out into its own function because both onResume() and the Add Budget
    // button need to reload and redisplay the same savings figures.
    private fun refreshSavings() {
        lifecycleScope.launch {
            val goals = database.savingsCategoryDao().getCategoriesForChild(childId)

            // Total savings is the sum of this child's categories, not a stored field -
            // the same one-source-of-truth approach as ChildHomeActivity (Tutorialspoint, n.d.-b).
            val total = goals.sumOf { it.amountSaved }
            tvSavingsTotal.text = "Total Savings: %.2f".format(total)

            tvSavingsBreakdown.text = if (goals.isEmpty()) {
                "No savings categories yet."
            } else {
                goals.joinToString("\n") { "${it.name}: %.2f".format(it.amountSaved) }
            }
        }
    }
}

// References:
// Vogel, L., 2016. Android Intents - Tutorial (Version 0.3) [Webpage]. Available at:
//     https://www.vogella.com/tutorials/AndroidIntent/article.html [Accessed 15 September 2026].
// Tutorialspoint, n.d.-a. Kotlin String - toDoubleOrNull() Function [Webpage]. Available at:
//     https://www.tutorialspoint.com/kotlin/kotlin_string_todoubleornull_function.htm
//     [Accessed 15 September 2026].
// Tutorialspoint, n.d.-b. Kotlin Array - sumOf() Function [Webpage]. Available at:
//     https://www.tutorialspoint.com/kotlin/kotlin_array_sumof_function.htm
//     [Accessed 15 September 2026].
// Android Developers, 2026. The activity lifecycle [Webpage]. Available at:
//     https://developer.android.com/guide/components/activities/activity-lifecycle
//     [Accessed 15 September 2026].
// Duggu, 2023. joinToString in Kotlin [Webpage]. Available at:
//     https://medium.com/@dugguRK/jointostring-in-kotlin-d227b9394486 [Accessed 15 September 2026].