package com.dani.jarzy

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dani.jarzy.data.JarzyDatabase
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ExpenseHistoryActivity shows one child's full expense history, grouped by which
 * savings category each expense was recorded against - per the requirement that
 * expenses be organised by savings category rather than just by date. Both the parent
 * (opened from ChildDetailActivity) and the child (opened from ChildHomeActivity) open
 * this same screen to view the same history - only the parent can ADD an expense (from
 * AddExpenseActivity); this screen itself is view-only for everyone.
 */
class ExpenseHistoryActivity : AppCompatActivity() {

    private lateinit var database: JarzyDatabase
    private var childId: Long = -1L

    private lateinit var tvExpenseHistoryTotal: TextView
    private lateinit var tvExpenseHistoryBreakdown: TextView

    // Formats a raw millisecond timestamp (how Expense.date is stored) into a short
    // human-readable date, the same general approach Pajgade (2025) walks through for
    // working with date objects in Kotlin.
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expense_history)

        database = JarzyDatabase.getDatabase(this)
        childId = intent.getLongExtra("CHILD_ID", -1L)

        val btnBack = findViewById<Button>(R.id.btnBack)
        tvExpenseHistoryTotal = findViewById(R.id.tvExpenseHistoryTotal)
        tvExpenseHistoryBreakdown = findViewById(R.id.tvExpenseHistoryBreakdown)

        btnBack.setOnClickListener { finish() }
    }

    // Reloads every time this screen is shown, so a newly-added expense appears
    // immediately when returned to (Android Developers, 2026).
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val categories = database.savingsCategoryDao().getCategoriesForChild(childId)

            if (categories.isEmpty()) {
                tvExpenseHistoryTotal.text = "Total Spent: 0.00"
                tvExpenseHistoryBreakdown.text = "No savings categories yet."
                return@launch
            }

            // 0L..Long.MAX_VALUE covers this child's entire history - there's no date
            // range picker on this screen, it's meant to show everything at once.
            val expenses = database.expenseDao().getExpensesInRange(childId, 0L, Long.MAX_VALUE)

            // sumOf() again for the running total spent across every category
            // (Tutorialspoint, n.d.-a).
            val total = expenses.sumOf { it.amount }
            tvExpenseHistoryTotal.text = "Total Spent: %.2f".format(total)

            // groupBy() splits the flat expense list into one list per
            // savingsCategoryId - exactly the "sorted by savings category" layout this
            // screen needs (Tutorialspoint, n.d.-b).
            val expensesByCategory = expenses.groupBy { it.savingsCategoryId }

            val breakdown = StringBuilder()
            for (category in categories) {
                breakdown.append("== ${category.name} ==\n")

                val categoryExpenses = expensesByCategory[category.savingsCategoryId]
                if (categoryExpenses.isNullOrEmpty()) {
                    breakdown.append("  No expenses recorded.\n")
                } else {
                    for (expense in categoryExpenses) {
                        val dateText = dateFormat.format(Date(expense.date))
                        val descriptionText = if (expense.description.isBlank()) "" else " - ${expense.description}"
                        breakdown.append("  $dateText: %.2f%s\n".format(expense.amount, descriptionText))
                    }
                }
                breakdown.append("\n")
            }

            tvExpenseHistoryBreakdown.text = breakdown.toString().trim()
        }
    }
}

// References:
// Android Developers, 2026. The activity lifecycle [Webpage]. Available at:
//     https://developer.android.com/guide/components/activities/activity-lifecycle
//     [Accessed 15 September 2026].
// Tutorialspoint, n.d.-a. Kotlin Array - sumOf() Function [Webpage]. Available at:
//     https://www.tutorialspoint.com/kotlin/kotlin_array_sumof_function.htm
//     [Accessed 15 September 2026].
// Tutorialspoint, n.d.-b. Kotlin Array - groupBy() Function [Webpage]. Available at:
//     https://www.tutorialspoint.com/kotlin/kotlin_array_groupby_function.htm
//     [Accessed 15 September 2026].
// Pajgade, A., 2025. Working with date objects in Kotlin [Webpage]. Available at:
//     https://medium.com/@atharvapajgade/working-with-date-objects-in-kotlin-e6af6cb9688c
//     [Accessed 15 September 2026].