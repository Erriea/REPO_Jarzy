package com.dani.jarzy

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dani.jarzy.data.Category
import com.dani.jarzy.data.Expense
import com.dani.jarzy.data.JarzyDatabase
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * AddExpenseActivity lets a parent log a new expense for a specific child: an amount, a
 * date, a category picked from a dropdown, and an optional description.
 * NOTE: at this prototype stage there is no check yet that stops an expense being logged
 * on a future date, and no check that caps an expense at the child's account balance -
 * both are planned changes from lecturer feedback, not implemented in this pass, which is
 * comments/references only.
 */

class AddExpenseActivity : AppCompatActivity() {

    private lateinit var database: JarzyDatabase


    // Holds the last category list loaded from the database
    // Spinner's selected position can be matched back to a real Category object when the user saves.
    private var categories: List<Category> = emptyList()

    // Nullable because no date has been chosen yet when the screen first opens
    // tells "nothing picked" apart from a real picked date further down.
    private var selectedDateMillis: Long? = null

    private var childId: Long = -1L
    private var parentId: Long = -1L

    private lateinit var spinnerCategory: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_expense)

        database = JarzyDatabase.getDatabase(this)
        childId = intent.getLongExtra("CHILD_ID", -1L)
        parentId = intent.getLongExtra("PARENT_ID", -1L)

        val btnBack = findViewById<Button>(R.id.btnBack)
        val etAmount = findViewById<EditText>(R.id.etAmount)
        val etDescription = findViewById<EditText>(R.id.etDescription)
        val btnPickDate = findViewById<Button>(R.id.btnPickDate)
        spinnerCategory = findViewById(R.id.spinnerCategory)
        val btnAddCategory = findViewById<Button>(R.id.btnAddCategory)
        val btnSaveExpense = findViewById<Button>(R.id.btnSaveExpense)
        val tvExpenseError = findViewById<TextView>(R.id.tvExpenseError)

        btnBack.setOnClickListener { finish() }

        // Shortcut so the user isn't stuck if wanted category doesn't exist yet
        // opens category creation and forwards this parent's id.
        btnAddCategory.setOnClickListener {
            val intent = Intent(this, CreateCategoryActivity::class.java)
            intent.putExtra("PARENT_ID", parentId)
            startActivity(intent)
        }

        btnPickDate.setOnClickListener {
            // DatePickerDialog needs a starting point to open on, so we grab today's date
            // from Calendar.getInstance() first (Chavan, 2023).
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                this,
                // This lambda is the callback DatePickerDialog converts into a single millisecond timestamp
                // using another Calendar instance
                { _, year, month, dayOfMonth ->
                    val picked = Calendar.getInstance()
                    picked.set(year, month, dayOfMonth, 0, 0, 0)
                    selectedDateMillis = picked.timeInMillis
                    btnPickDate.text = "Date: %04d-%02d-%02d".format(year, month + 1, dayOfMonth)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        btnSaveExpense.setOnClickListener {
            val amountText = etAmount.text.toString().trim()
            val description = etDescription.text.toString().trim() // optional -> saved as "" if left blank
            val categoryIndex = spinnerCategory.selectedItemPosition

            if (childId == -1L) {
                tvExpenseError.text = "Something went wrong identifying the child account. Please go back and try again."
                return@setOnClickListener
            }

            // Covers both "no categories exist yet" and "nothing is selected" with one check.
            if (categories.isEmpty() || categoryIndex < 0 || categoryIndex >= categories.size) {
                tvExpenseError.text = "No categories yet - tap '+ New Category' to add one."
                return@setOnClickListener
            }

            // toDoubleOrNull() again for safe text-to-number conversion (Tutorialspoint, n.d.).
            // An expense of 0 or less isn't a real expense, so that's rejected too.
            val amount = amountText.toDoubleOrNull()
            if (amount == null || amount <= 0) {
                tvExpenseError.text = "Amount must be a valid number greater than 0."
                return@setOnClickListener
            }

            val dateMillis = selectedDateMillis
            if (dateMillis == null) {
                tvExpenseError.text = "Please pick a date."
                return@setOnClickListener
            }

            val selectedCategory = categories[categoryIndex]

            lifecycleScope.launch {
                database.expenseDao().insert(
                    Expense(
                        childId = childId,
                        categoryId = selectedCategory.categoryId,
                        amount = amount,
                        date = dateMillis,
                        description = description
                    )
                )
                Toast.makeText(this@AddExpenseActivity, "Expense saved!", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }


    // reload categories every time this screen becomes visible
    // category added shows up immediately when returned (Android Developers, n.d.)
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            categories = database.categoryDao().getCategoriesForParent(parentId)
            val names = categories.map { it.name }
            spinnerCategory.adapter = ArrayAdapter(
                this@AddExpenseActivity,
                android.R.layout.simple_spinner_dropdown_item,
                names
            )
        }
    }
}

// References:
// Chavan, D., 2023. Date Picker Using Kotlin in Android Studio | DatePickerDialog -
//     Android Studio Tutorial | Kotlin [Webpage]. Available at:
//     https://devendrac706.medium.com/date-picker-using-kotlin-in-android-studio-datepickerdialog-android-studio-tutorial-kotlin-3bbc606585a
//     [Accessed 13 September 2026].
// Tutorialspoint, n.d. Kotlin String - toDoubleOrNull() Function [Webpage]. Available at:
//     https://www.tutorialspoint.com/kotlin/kotlin_string_todoubleornull_function.htm
//     [Accessed 13 September 2026].
// Android Developers, 2026. The activity lifecycle [Webpage]. Available at:
//     https://developer.android.com/guide/components/activities/activity-lifecycle
//     [Accessed 13 September 2026].
// GeeksforGeeks, 2019. Spinner in Kotlin [Webpage]. Available at:
//     https://www.geeksforgeeks.org/kotlin/spinner-in-kotlin/ [Accessed 13 September 2026].