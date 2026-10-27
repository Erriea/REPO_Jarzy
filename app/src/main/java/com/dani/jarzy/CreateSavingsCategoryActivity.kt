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
import com.dani.jarzy.data.SavingsCategory
import com.dani.jarzy.data.SavingsCategoryRepository
import kotlinx.coroutines.launch

/**
 * CreateSavingsCategoryActivity lets a CHILD move money between their own savings
 * categories - picking an existing category to move FROM, and typing a destination
 * category name to move TO (an existing name adds to that category, a new name creates
 * one). The amount moved can never exceed what the source category currently holds -
 * money only ever moves between categories here, it's never created from nothing.
 */
class CreateSavingsCategoryActivity : AppCompatActivity() {

    // Replaces the old JarzyDatabase (Room) reference - see SavingsCategoryRepository's
    // class comment for how it mirrors SavingsCategoryDao's methods against Firestore.
    private val savingsCategoryRepository = SavingsCategoryRepository()
    private var childId: Long = -1L
    private var categories: List<SavingsCategory> = emptyList()

    private lateinit var spinnerFromCategory: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_savings_category)

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

            val pickedSourceCategory = categories[fromIndex]

            if (pickedSourceCategory.name.equals(destinationName, ignoreCase = true)) {
                tvGoalError.text = "Choose a different destination category."
                return@setOnClickListener
            }

            val amount = amountText.toDoubleOrNull()
            if (amount == null || amount <= 0) {
                tvGoalError.text = "Amount must be a valid number greater than 0."
                return@setOnClickListener
            }

            tvGoalError.text = ""

            // Disabled the instant a transfer is attempted - the same reasoning as
            // AddExpenseActivity's btnSaveExpense.isEnabled = false: without this, a
            // second impatient tap could launch a second coroutine that reads this same
            // source category's balance before the first transfer has finished writing
            // its deduction, letting both taps pass the cap check below against (and
            // move out) the same money twice (Reyes, 2022).
            btnSaveGoal.isEnabled = false

            lifecycleScope.launch {
                // Re-read the source category fresh from Firestore right here, instead
                // of trusting "categories" - the list this screen loaded once back in
                // onResume() and never since. See AddExpenseActivity's equivalent fresh
                // read for the full reasoning: a stale cached balance is exactly how the
                // cap below could be passed even after this category had already been
                // moved out of earlier in this same visit to this screen.
                val sourceCategory = savingsCategoryRepository.getById(pickedSourceCategory.savingsCategoryId)
                if (sourceCategory == null) {
                    tvGoalError.text = "That category no longer exists - go back and try again."
                    btnSaveGoal.isEnabled = true
                    return@launch
                }

                // The cap: can't move more out of a category than it currently holds -
                // checked here, against the fresh read above, not the cached copy.
                if (amount > sourceCategory.amountSaved) {
                    tvGoalError.text = "That category only has %.2f in it.".format(sourceCategory.amountSaved)
                    btnSaveGoal.isEnabled = true
                    return@launch
                }

                // Take the amount out of the source category first.
                savingsCategoryRepository.update(
                    sourceCategory.copy(amountSaved = sourceCategory.amountSaved - amount)
                )

                // If a category with the destination name already exists, add to it;
                // otherwise create a brand-new one holding just this amount. Re-read
                // fresh for the same reason as the source category above, rather than
                // searching the cached "categories" list, in case the destination is
                // also the target of an expense/transfer earlier in this same visit.
                val existingDestination = savingsCategoryRepository
                    .getCategoriesForChild(childId)
                    .firstOrNull { it.name.equals(destinationName, ignoreCase = true) && it.savingsCategoryId != sourceCategory.savingsCategoryId }
                if (existingDestination != null) {
                    savingsCategoryRepository.update(
                        existingDestination.copy(amountSaved = existingDestination.amountSaved + amount)
                    )
                } else {
                    savingsCategoryRepository.insert(
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
            categories = savingsCategoryRepository.getCategoriesForChild(childId)
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
// Tutorialspoint, 2026. Kotlin String - toDoubleOrNull() Function [Webpage]. Available at:
//     https://www.tutorialspoint.com/kotlin/kotlin_string_todoubleornull_function.htm
//     [Accessed 8 September 2026].

// Android Developers, 2026. The activity lifecycle [Webpage]. Available at:
//     https://developer.android.com/guide/components/activities/activity-lifecycle
//     [Accessed 8 September 2026].

// GeeksforGeeks, 2025. Spinner in Kotlin [Webpage]. Available at:
//     https://www.geeksforgeeks.org/kotlin/spinner-in-kotlin/ [Accessed 14  September 2026].

// Reyes, G., 2022. Solving Android multiple clicks problem with coroutines - Kotlin
//     [Webpage]. Available at:
//     https://medium.com/@gibrannhit/solving-android-multiple-clicks-problem-with-coroutines-kotlin-267d1c2063da
//     [Accessed 27 October 2026].
