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
import com.dani.jarzy.data.ExpenseRepository
import com.dani.jarzy.data.SavingsCategory
import com.dani.jarzy.data.SavingsCategoryRepository
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * AddExpenseActivity lets a parent log a new expense for a specific child:
 * an amount, a date, a category picked from a dropdown, an optional receipt photo, and an optional description.
 * The category dropdown shows the childs's savings categories
 * the amount spent is deducted straight from whichever categoryit's recorded against
 * It can never exceed what that category currently holds
 * it can never be logged on a future date.
 */
class AddExpenseActivity : AppCompatActivity() {

    // Replaces the old JarzyDatabase (Room) reference. ExpenseRepository needs a
    // SavingsCategoryRepository passed into its constructor - see ExpenseRepository's
    // class comment for why.
    private val savingsCategoryRepository = SavingsCategoryRepository()
    private val expenseRepository = ExpenseRepository(savingsCategoryRepository)

    private var categories: List<SavingsCategory> = emptyList()
    private var selectedDateMillis: Long? = null
    private var selectedPhotoUri: Uri? = null // optional - stays null if no photo is picked
    private var childId: Long = -1L

    private lateinit var spinnerCategory: Spinner
    private lateinit var ivReceiptPreview: ImageView

    // Registered as a class property
    // the system requires this call to happen before the Activity is started
    // the callback lambda runs later once the user picks a photo
    // (Android Developers, n.d.-a).
    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            selectedPhotoUri = uri

            // The photo picker grants read access "until the app stops" by default
            // it takes persistable permission meaning the photo is still viewable if
            // the user leaves this screen and comes back to it later (Android Developers, n.d.-a).
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

            ivReceiptPreview.visibility = View.VISIBLE
            ivReceiptPreview.setImageURI(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_expense)

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
                    // The 6-argument set() above only touches YEAR/MONTH/DAY_OF_MONTH/
                    // HOUR_OF_DAY/MINUTE/SECOND - it leaves MILLISECOND exactly as
                    // Calendar.getInstance() found it (whatever millisecond-of-second it
                    // happened to be when this callback ran, 0-999). That leftover
                    // millisecond pushed "today" very slightly past exact midnight, which
                    // was just enough to fail the dateMillis > todayMidnight.timeInMillis
                    // check below (todayMidnight zeroes its own MILLISECOND explicitly) -
                    // falsely rejecting today's own date as "in the future" by a fraction
                    // of a second. Zeroing it here too keeps both sides of that comparison
                    // on the exact same footing (Gonzalez, 2025).
                    picked.set(Calendar.MILLISECOND, 0)
                    selectedDateMillis = picked.timeInMillis
                    btnPickDate.text = "Date: %04d-%02d-%02d".format(year, month + 1, dayOfMonth)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // Opens Android's own Photo Picker screen, restricted to images only
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

            //todays time fields are zeroed out the same way the picked date is.
            //this a pure day-vs-day comparison
            // does not depend on what time it happens to be right now (Gonzalez, 2025).
            val todayMidnight = Calendar.getInstance()
            todayMidnight.set(Calendar.HOUR_OF_DAY, 0)
            todayMidnight.set(Calendar.MINUTE, 0)
            todayMidnight.set(Calendar.SECOND, 0)
            todayMidnight.set(Calendar.MILLISECOND, 0)

            if (dateMillis > todayMidnight.timeInMillis) {
                Toast.makeText(this, "You can't log an expense on a future date.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val pickedCategory = categories[categoryIndex]
            tvExpenseError.text = ""

            // Disabled the instant a save is attempted, instead of only after the cap
            // check below finishes - this is what actually stops a second, impatient
            // tap (while the first save is still in flight) from launching a SECOND
            // coroutine that reads this same category's balance before the first one
            // has finished writing its deduction. Without this, two taps fired close
            // together would both check against - and both compute their new balance
            // from - the exact same starting amount, since neither tap would yet have
            // seen the other's update: a textbook stale-read race condition, and
            // exactly how this screen was letting someone spend more than a category
            // actually held (Reyes, 2022).
            btnSaveExpense.isEnabled = false

            lifecycleScope.launch {
                // Re-read this exact category fresh from Firestore right here, instead
                // of trusting "categories" - the list this screen loaded once back in
                // onResume() and never since. If the user has already logged another
                // expense from this same category earlier in this same visit to this
                // screen (without leaving and returning, the only thing that would
                // normally trigger a reload), that cached copy's amountSaved is stale -
                // checking the spending cap below against a stale, too-high balance is
                // exactly how it could be passed even though the category had already
                // run out of money.
                val freshCategory = savingsCategoryRepository.getById(pickedCategory.savingsCategoryId)
                if (freshCategory == null) {
                    tvExpenseError.text = "That category no longer exists - go back and try again."
                    btnSaveExpense.isEnabled = true
                    return@launch
                }

                // The cap: an expense can never draw more out of a category than it
                // currently holds - checked here, against freshCategory, rather than
                // back in the synchronous click handler against a possibly-stale copy.
                if (amount > freshCategory.amountSaved) {
                    tvExpenseError.text = "That category only has %.2f available.".format(freshCategory.amountSaved)
                    btnSaveExpense.isEnabled = true
                    return@launch
                }

                expenseRepository.insert(
                    Expense(
                        childId = childId,
                        savingsCategoryId = freshCategory.savingsCategoryId,
                        amount = amount,
                        date = dateMillis,
                        description = description,
                        photoUri = selectedPhotoUri?.toString() // stays null if no photo was picked
                    )
                )

                // Recording the expense deducts it from the category it was spent from
                // the same pattern CreateSavingsCategoryActivity uses for its source category.
                savingsCategoryRepository.update(
                    freshCategory.copy(amountSaved = freshCategory.amountSaved - amount)
                )

                // Overspending warning - separate from (and never blocks) the hard cap
                // above, which stops an expense exceeding what's actually IN a category.
                // budgetSize is a target the parent optionally sets for how much should be
                // spent from this category in a month; going over it is still allowed
                // (the money might genuinely be there), it just gets flagged so the parent
                // notices. A budgetSize of 0 means no budget was set, so this check is
                // skipped entirely for that category.
                if (freshCategory.budgetSize > 0) {
                    val monthStart = Calendar.getInstance()
                    monthStart.timeInMillis = dateMillis
                    monthStart.set(Calendar.DAY_OF_MONTH, 1)
                    monthStart.set(Calendar.HOUR_OF_DAY, 0)
                    monthStart.set(Calendar.MINUTE, 0)
                    monthStart.set(Calendar.SECOND, 0)
                    monthStart.set(Calendar.MILLISECOND, 0)

                    val monthEnd = Calendar.getInstance()
                    monthEnd.timeInMillis = dateMillis
                    monthEnd.set(Calendar.DAY_OF_MONTH, monthEnd.getActualMaximum(Calendar.DAY_OF_MONTH))
                    monthEnd.set(Calendar.HOUR_OF_DAY, 23)
                    monthEnd.set(Calendar.MINUTE, 59)
                    monthEnd.set(Calendar.SECOND, 59)
                    monthEnd.set(Calendar.MILLISECOND, 999)

                    // getActualMaximum(Calendar.DAY_OF_MONTH) finds the last real day of
                    // whichever month the expense falls in (28-31, correctly handling
                    // February) rather than hardcoding 31 (Android Developers, n.d.-b).
                    // getTotalsPerSavingsCategory already includes the expense just
                    // inserted above, since it re-reads from Firestore rather than using
                    // any value cached earlier in this function.
                    val monthTotals = expenseRepository
                        .getTotalsPerSavingsCategory(childId, monthStart.timeInMillis, monthEnd.timeInMillis)
                    val spentThisMonth = monthTotals
                        .firstOrNull { it.categoryId == freshCategory.savingsCategoryId }
                        ?.totalSpent ?: 0.0

                    if (spentThisMonth > freshCategory.budgetSize) {
                        Toast.makeText(
                            this@AddExpenseActivity,
                            "Heads up: ${freshCategory.name} is now %.2f over its %.2f monthly budget."
                                .format(spentThisMonth - freshCategory.budgetSize, freshCategory.budgetSize),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                Toast.makeText(this@AddExpenseActivity, "Expense saved!", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    // Reloads categories (and their current amounts) every time this screen becomes visible
    //the cap check always reflects up-to-date figures (Android Developers, 2026).
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            categories = savingsCategoryRepository.getCategoriesForChild(childId)
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
//     https://www.baeldung.com/java-date-without-time [Accessed 13 September 2026].
// Android Developers, n.d.-a. Select photos and videos with the photo picker [Webpage].
//     Available at: <https://developer.android.com/training/data-storage/shared/photopicker>
//     [Accessed 14 September 2026].
// Android Developers, n.d.-b. Calendar.getActualMaximum(int) [Webpage]. Available at:
//     https://developer.android.com/reference/java/util/Calendar#getActualMaximum(int)
//     [Accessed 18 October 2026].
// Reyes, G., 2022. Solving Android multiple clicks problem with coroutines - Kotlin
//     [Webpage]. Available at:
//     https://medium.com/@gibrannhit/solving-android-multiple-clicks-problem-with-coroutines-kotlin-267d1c2063da
//     [Accessed 27 October 2026].
