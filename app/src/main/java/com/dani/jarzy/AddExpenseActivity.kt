package com.dani.jarzy

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dani.jarzy.data.Expense
import com.dani.jarzy.data.JarzyDatabase
import com.dani.jarzy.data.SavingsCategory
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * AddExpenseActivity lets a parent log a new expense for a specific child: an amount, a
 * date, a category picked from a dropdown, an optional receipt photo, and an optional
 * description. The category dropdown shows the CHILD's own savings categories (not a
 * parent-shared list), and the amount spent is deducted straight from whichever category
 * it's recorded against - it can never exceed what that category currently holds, and it
 * can never be logged on a future date.
 */
class AddExpenseActivity : AppCompatActivity() {

    private lateinit var database: JarzyDatabase

    private var categories: List<SavingsCategory> = emptyList()
    private var selectedDateMillis: Long? = null
    private var selectedPhotoUri: Uri? = null // optional - stays null if no photo is picked
    private var childId: Long = -1L

    private lateinit var spinnerCategory: Spinner
    private lateinit var ivReceiptPreview: ImageView

    // Registered as a class property (rather than inside onCreate or a listener) because
    // the system requires this call to happen before the Activity is STARTED - the
    // callback lambda itself only actually runs later, once the user picks a photo
    // (Android Developers, n.d.).
    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            selectedPhotoUri = uri

            // The photo picker only grants read access "until the app stops" by default -
            // taking a persistable permission here means the photo is still viewable if
            // the user leaves this screen and comes back to it later (Android Developers, n.d.).
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

            ivReceiptPreview.visibility = View.VISIBLE
            ivReceiptPreview.setImageURI(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_expense)

        database = JarzyDatabase.getDatabase(this)
        childId = intent.getLongExtra("CHILD_ID", -1L)

        val btnBack = findViewById<Button>(R.id.btnBack)
        val etAmount = findViewById<EditText>(R.id.etAmount)
        val etDescription = findViewById<EditText>(R.id.etDescription)
        val btnPickDate = findViewById<Button>(R.id.btnPickDate)
        spinnerCategory = findViewById(R.id.spinnerCategory)
        val btnAddPhoto = findViewById<Button>(R.id.btnAddPhoto)
        ivReceiptPreview = findViewById(R.id.ivReceiptPreview)
        val btnSaveExpense = findViewById<Button>(R.id.btnSaveExpense)
        val tvExpenseError = findViewById<TextView>(R.id.tvExpenseError)

        btnBack.setOnClickListener { finish() }

        btnPickDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                this,
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

        // Opens Android's own Photo Picker screen, restricted to images only - this needs
        // no storage permission at all, unlike older ways of picking a gallery image
        // (Android Developers, n.d.).
        btnAddPhoto.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        btnSaveExpense.setOnClickListener {
            val amountText = etAmount.text.toString().trim()
            val description = etDescription.text.toString().trim()
            val categoryIndex = spinnerCategory.selectedItemPosition

            if (childId == -1L) {
                tvExpenseError.text = "Something went wrong identifying the child account. Please go back and try again."
                return@setOnClickListener
            }

            if (categories.isEmpty() || categoryIndex < 0 || categoryIndex >= categories.size) {
                tvExpenseError.text = "This child has no savings categories yet."
                return@setOnClickListener
            }

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

            // Zeroing out today's time fields the same way the picked date already is
            // (see btnPickDate above) makes this a pure day-vs-day comparison, not one
            // that depends on what time it happens to be right now (Gonzalez, 2025).
            val todayMidnight = Calendar.getInstance()
            todayMidnight.set(Calendar.HOUR_OF_DAY, 0)
            todayMidnight.set(Calendar.MINUTE, 0)
            todayMidnight.set(Calendar.SECOND, 0)
            todayMidnight.set(Calendar.MILLISECOND, 0)

            if (dateMillis > todayMidnight.timeInMillis) {
                Toast.makeText(this, "You can't log an expense on a future date.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedCategory = categories[categoryIndex]

            // The cap: an expense can never draw more out of a category than it currently
            // holds.
            if (amount > selectedCategory.amountSaved) {
                tvExpenseError.text = "That category only has %.2f available.".format(selectedCategory.amountSaved)
                return@setOnClickListener
            }

            lifecycleScope.launch {
                database.expenseDao().insert(
                    Expense(
                        childId = childId,
                        savingsCategoryId = selectedCategory.savingsCategoryId,
                        amount = amount,
                        date = dateMillis,
                        description = description,
                        photoUri = selectedPhotoUri?.toString() // stays null if no photo was picked
                    )
                )

                // Recording the expense deducts it from the category it was spent from -
                // the same pattern CreateSavingsCategoryActivity uses for its source category.
                database.savingsCategoryDao().update(
                    selectedCategory.copy(amountSaved = selectedCategory.amountSaved - amount)
                )

                Toast.makeText(this@AddExpenseActivity, "Expense saved!", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    // Reloads categories (and their current amounts) every time this screen becomes
    // visible, so the cap check always reflects up-to-date figures (Android Developers, 2026).
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            categories = database.savingsCategoryDao().getCategoriesForChild(childId)
            val labels = categories.map { "${it.name} (%.2f)".format(it.amountSaved) }
            spinnerCategory.adapter = ArrayAdapter(
                this@AddExpenseActivity,
                android.R.layout.simple_spinner_dropdown_item,
                labels
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
// Gonzalez, M.L., 2025. Get Date Without Time in Java [Webpage]. Available at:
//     https://www.baeldung.com/java-date-without-time [Accessed 15 September 2026].
// Android Developers, n.d. Select photos and videos with the photo picker [Webpage].
//     Available at: <https://developer.android.com/training/data-storage/shared/photopicker>
//     [Accessed 15 September 2026].