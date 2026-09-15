package com.dani.jarzy

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dani.jarzy.data.JarzyDatabase
import com.dani.jarzy.data.SavingsCategory
import kotlinx.coroutines.launch

/**
 * CreateSavingsCategoryActivity lets a CHILD move money between their own savings
 * categories - picking an existing category to move FROM, and typing a destination
 * category name to move TO (an existing name adds to that category, a new name creates
 * one). The amount moved can never exceed what the source category currently holds -
 * money only ever moves between categories here, it's never created from nothing.
 */
class CreateSavingsCategoryActivity : AppCompatActivity() {

    private lateinit var database: JarzyDatabase
    private var childId: Long = -1L
    private var categories: List<SavingsCategory> = emptyList()

    private lateinit var spinnerFromCategory: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_savings_category)

        database = JarzyDatabase.getDatabase(this)
        childId = intent.getLongExtra("CHILD_ID", -1L)

        val btnBack = findViewById<Button>(R.id.btnBack)
        spinnerFromCategory = findViewById(R.id.spinnerFromCategory)
        val etDestinationName = findViewById<EditText>(R.id.etGoalName)
        val etAmount = findViewById<EditText>(R.id.etGoalAmount)
        val btnSaveGoal = findViewById<Button>(R.id.btnSaveGoal)
        val tvGoalError = findViewById<TextView>(R.id.tvGoalError)

        btnBack.setOnClickListener { finish() }

        btnSaveGoal.setOnClickListener {
            val fromIndex = spinnerFromCategory.selectedItemPosition
            val destinationName = etDestinationName.text.toString().trim()
            val amountText = etAmount.text.toString().trim()

            if (childId == -1L) {
                tvGoalError.text = "Something went wrong identifying the child account. Please go back and try again."
                return@setOnClickListener
            }

            if (categories.isEmpty() || fromIndex < 0 || fromIndex >= categories.size) {
                tvGoalError.text = "You don't have any categories to move money from yet."
                return@setOnClickListener
            }

            if (destinationName.isEmpty()) {
                tvGoalError.text = "Please enter a category name to move money to."
                return@setOnClickListener
            }

            val sourceCategory = categories[fromIndex]

            if (sourceCategory.name.equals(destinationName, ignoreCase = true)) {
                tvGoalError.text = "Choose a different destination category."
                return@setOnClickListener
            }

            val amount = amountText.toDoubleOrNull()
            if (amount == null || amount <= 0) {
                tvGoalError.text = "Amount must be a valid number greater than 0."
                return@setOnClickListener
            }

            // The cap: can't move more out of a category than it currently holds.
            if (amount > sourceCategory.amountSaved) {
                tvGoalError.text = "That category only has %.2f in it.".format(sourceCategory.amountSaved)
                return@setOnClickListener
            }

            lifecycleScope.launch {
                // Take the amount out of the source category first.
                database.savingsCategoryDao().update(
                    sourceCategory.copy(amountSaved = sourceCategory.amountSaved - amount)
                )

                // If a category with the destination name already exists, add to it;
                // otherwise create a brand-new one holding just this amount.
                val existingDestination = categories.firstOrNull { it.name.equals(destinationName, ignoreCase = true) }
                if (existingDestination != null) {
                    database.savingsCategoryDao().update(
                        existingDestination.copy(amountSaved = existingDestination.amountSaved + amount)
                    )
                } else {
                    database.savingsCategoryDao().insert(
                        SavingsCategory(childId = childId, name = destinationName, amountSaved = amount)
                    )
                }

                Toast.makeText(this@CreateSavingsCategoryActivity, "Money moved!", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    // Reloads this child's categories every time the screen becomes visible, so the "from"
    // dropdown always reflects up-to-date amounts (Android Developers, 2026).
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            categories = database.savingsCategoryDao().getCategoriesForChild(childId)
            val labels = categories.map { "${it.name} (%.2f)".format(it.amountSaved) }
            spinnerFromCategory.adapter = ArrayAdapter(
                this@CreateSavingsCategoryActivity,
                android.R.layout.simple_spinner_dropdown_item,
                labels
            )
        }
    }
}

// References:
// Tutorialspoint, n.d. Kotlin String - toDoubleOrNull() Function [Webpage]. Available at:
//     https://www.tutorialspoint.com/kotlin/kotlin_string_todoubleornull_function.htm
//     [Accessed 15 September 2026].
// Android Developers, 2026. The activity lifecycle [Webpage]. Available at:
//     https://developer.android.com/guide/components/activities/activity-lifecycle
//     [Accessed 15 September 2026].
// GeeksforGeeks, 2019. Spinner in Kotlin [Webpage]. Available at:
//     https://www.geeksforgeeks.org/kotlin/spinner-in-kotlin/ [Accessed 15 September 2026].