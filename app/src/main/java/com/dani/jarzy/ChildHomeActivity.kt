package com.dani.jarzy

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dani.jarzy.data.JarzyDatabase
import kotlinx.coroutines.launch

/**
 * ChildHomeActivity is the screen a child lands on after logging in. It shows the child's
 * total savings (the sum of all their own savings categories) and a breakdown per
 * category, and lets them open the transfer screen to move money between categories.
 * Only the child can create, edit or move money between their own categories - the
 * parent can only view this same breakdown from their side.
 */
class ChildHomeActivity : AppCompatActivity() {

    private lateinit var database: JarzyDatabase
    private var childId: Long = -1L

    private lateinit var tvSavingsTotal: TextView
    private lateinit var tvSavingsBreakdown: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_child_home)

        database = JarzyDatabase.getDatabase(this)

        val childUsername = intent.getStringExtra("CHILD_USERNAME") ?: ""
        childId = intent.getLongExtra("CHILD_ID", -1L)

        val tvChildWelcome = findViewById<TextView>(R.id.tvChildWelcome)
        tvSavingsTotal = findViewById(R.id.tvSavingsTotal)
        tvSavingsBreakdown = findViewById(R.id.tvSavingsBreakdown)
        val btnAddSavingsGoal = findViewById<Button>(R.id.btnAddSavingsGoal)
        val btnViewExpenseHistory = findViewById<Button>(R.id.btnViewExpenseHistory)
        val btnLogout = findViewById<Button>(R.id.btnLogout)

        tvChildWelcome.text = "Welcome, $childUsername"

        btnAddSavingsGoal.setOnClickListener {
            val btnViewExpenseHistory = findViewById<Button>(R.id.btnViewExpenseHistory)
            val intent = Intent(this, CreateSavingsCategoryActivity::class.java)
            intent.putExtra("CHILD_ID", childId)
            startActivity(intent)
        }

        btnViewExpenseHistory.setOnClickListener {
            val intent = Intent(this, ExpenseHistoryActivity::class.java)
            intent.putExtra("CHILD_ID", childId)
            startActivity(intent)
        }

        btnLogout.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val goals = database.savingsCategoryDao().getCategoriesForChild(childId)

            // Total savings is calculated by adding up every category's amountSaved, not
            // stored as its own number - one source of truth for how much the child has
            // (Tutorialspoint, n.d.-a).
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
// Tutorialspoint, n.d.-a. Kotlin Array - sumOf() Function [Webpage]. Available at:
//     https://www.tutorialspoint.com/kotlin/kotlin_array_sumof_function.htm
//     [Accessed 15 September 2026].
// Duggu, 2023. joinToString in Kotlin [Webpage]. Available at:
//     https://medium.com/@dugguRK/jointostring-in-kotlin-d227b9394486 [Accessed 15 September 2026].