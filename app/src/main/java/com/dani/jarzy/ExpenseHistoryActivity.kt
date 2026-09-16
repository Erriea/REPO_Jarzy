package com.dani.jarzy

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import com.dani.jarzy.data.Expense
import com.dani.jarzy.data.JarzyDatabase
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// The two things this screen can show for the selected date range - either every
// individual expense entry (grouped by category), or just each category's total.
// Kept as its own enum rather than a Boolean so the "when" below reads clearly and a
// third view mode could be added later without renaming anything.
private enum class HistoryViewMode {
    ALL_ENTRIES,
    CATEGORY_TOTALS
}

/**
 * ExpenseHistoryActivity shows one child's expense history for a chosen date range,
 * either as a full list of entries grouped by savings category, or as just each
 * category's total for that range - toggled with btnToggleView. Both the parent
 * (opened from ChildDetailActivity) and the child (opened from ChildHomeActivity) open
 * this same screen to view the same history - only the child can ADD an expense (from
 * AddExpenseActivity, via ChildHomeActivity); this screen itself is view-only for
 * everyone. In "All Entries" mode, any expense that has a receipt photo shows a small
 * thumbnail next to it - tapping the thumbnail opens the same photo full-size.
 *
 * The date range defaults to "start of this month" -> "today" when the screen first
 * opens, and can be narrowed with the Start Date / End Date buttons, which reuse the
 * same DatePickerDialog pattern as AddExpenseActivity's date picker (Chavan, 2023).
 */
class ExpenseHistoryActivity : AppCompatActivity() {

    private lateinit var database: JarzyDatabase
    private var childId: Long = -1L

    private lateinit var tvExpenseHistoryTotal: TextView
    private lateinit var llExpenseEntries: LinearLayout
    private lateinit var tvExpenseHistoryBreakdown: TextView
    private lateinit var btnStartDate: Button
    private lateinit var btnEndDate: Button
    private lateinit var btnToggleView: Button

    // The currently selected range, as raw millisecond timestamps - the same unit
    // Expense.date is stored in, so these can be passed straight into the DAO queries.
    private var startDateMillis: Long = 0L
    private var endDateMillis: Long = 0L

    // Which of the two views is currently showing. Starts on ALL_ENTRIES.
    private var viewMode: HistoryViewMode = HistoryViewMode.ALL_ENTRIES

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
        llExpenseEntries = findViewById(R.id.llExpenseEntries)
        tvExpenseHistoryBreakdown = findViewById(R.id.tvExpenseHistoryBreakdown)
        btnStartDate = findViewById(R.id.btnStartDate)
        btnEndDate = findViewById(R.id.btnEndDate)
        btnToggleView = findViewById(R.id.btnToggleView)

        btnBack.setOnClickListener { finish() }

        // Default range: the 1st of the current month, at the very start of the day,
        // through to right now, at the very end of the day.
        val defaultStart = Calendar.getInstance()
        defaultStart.set(Calendar.DAY_OF_MONTH, 1)
        defaultStart.set(Calendar.HOUR_OF_DAY, 0)
        defaultStart.set(Calendar.MINUTE, 0)
        defaultStart.set(Calendar.SECOND, 0)
        defaultStart.set(Calendar.MILLISECOND, 0)
        startDateMillis = defaultStart.timeInMillis

        val defaultEnd = Calendar.getInstance()
        defaultEnd.set(Calendar.HOUR_OF_DAY, 23)
        defaultEnd.set(Calendar.MINUTE, 59)
        defaultEnd.set(Calendar.SECOND, 59)
        defaultEnd.set(Calendar.MILLISECOND, 999)
        endDateMillis = defaultEnd.timeInMillis

        btnStartDate.text = "Start: ${dateFormat.format(Date(startDateMillis))}"
        btnEndDate.text = "End: ${dateFormat.format(Date(endDateMillis))}"
        btnToggleView.text = "View: All Entries"

        btnStartDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = startDateMillis
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    // Pushed to the very start of the picked day, same as AddExpenseActivity's
                    // date picker - this is the lower bound of the range, so it needs to
                    // catch every expense recorded on that day, not just from midday onward.
                    val picked = Calendar.getInstance()
                    picked.set(year, month, dayOfMonth, 0, 0, 0)
                    picked.set(Calendar.MILLISECOND, 0)
                    val newStart = picked.timeInMillis

                    if (newStart > endDateMillis) {
                        Toast.makeText(this, "Start date can't be after the end date.", Toast.LENGTH_SHORT).show()
                    } else {
                        startDateMillis = newStart
                        btnStartDate.text = "Start: ${dateFormat.format(Date(startDateMillis))}"
                        loadExpenseHistory()
                    }
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).apply {
                // Can't pick a start date in the future - matches the same "no future
                // dates" rule AddExpenseActivity already enforces when logging an expense.
                datePicker.maxDate = System.currentTimeMillis()
            }.show()
        }

        btnEndDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = endDateMillis
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    // Pushed to the very end of the picked day (23:59:59.999) rather than
                    // the start of it - this is the upper bound of the range, so it needs
                    // to include every expense recorded on that day, however late in the
                    // day it was logged.
                    val picked = Calendar.getInstance()
                    picked.set(year, month, dayOfMonth, 23, 59, 59)
                    picked.set(Calendar.MILLISECOND, 999)
                    val newEnd = picked.timeInMillis

                    if (newEnd < startDateMillis) {
                        Toast.makeText(this, "End date can't be before the start date.", Toast.LENGTH_SHORT).show()
                    } else {
                        endDateMillis = newEnd
                        btnEndDate.text = "End: ${dateFormat.format(Date(endDateMillis))}"
                        loadExpenseHistory()
                    }
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).apply {
                datePicker.maxDate = System.currentTimeMillis()
            }.show()
        }

        // Flips between the two view modes and immediately reloads to reflect the switch.
        btnToggleView.setOnClickListener {
            viewMode = if (viewMode == HistoryViewMode.ALL_ENTRIES) {
                HistoryViewMode.CATEGORY_TOTALS
            } else {
                HistoryViewMode.ALL_ENTRIES
            }

            btnToggleView.text = if (viewMode == HistoryViewMode.ALL_ENTRIES) {
                "View: All Entries"
            } else {
                "View: Category Totals"
            }

            loadExpenseHistory()
        }
    }

    // Reloads every time this screen is shown, so a newly-added expense appears
    // immediately when returned to (Android Developers, 2026).
    override fun onResume() {
        super.onResume()
        loadExpenseHistory()
    }

    // Pulled out of onCreate/onResume so the date-picker and toggle-button callbacks
    // above can all trigger a reload the moment something changes, instead of only
    // refreshing the next time this screen happens to resume.
    private fun loadExpenseHistory() {
        lifecycleScope.launch {
            val categories = database.savingsCategoryDao().getCategoriesForChild(childId)

            if (categories.isEmpty()) {
                tvExpenseHistoryTotal.text = "Total Spent: 0.00"
                llExpenseEntries.visibility = View.GONE
                tvExpenseHistoryBreakdown.visibility = View.VISIBLE
                tvExpenseHistoryBreakdown.text = "No savings categories yet."
                return@launch
            }

            when (viewMode) {
                HistoryViewMode.ALL_ENTRIES -> {
                    llExpenseEntries.visibility = View.VISIBLE
                    tvExpenseHistoryBreakdown.visibility = View.GONE

                    val expenses = database.expenseDao().getExpensesInRange(childId, startDateMillis, endDateMillis)

                    // sumOf() for the running total spent across every category, within
                    // the chosen range (Tutorialspoint, n.d.-a).
                    val total = expenses.sumOf { it.amount }
                    tvExpenseHistoryTotal.text = "Total Spent: %.2f".format(total)

                    // groupBy() splits the flat expense list into one list per
                    // savingsCategoryId - exactly the "sorted by savings category" layout
                    // this view needs (Tutorialspoint, n.d.-b).
                    val expensesByCategory = expenses.groupBy { it.savingsCategoryId }

                    // Rebuilt from scratch every reload - removeAllViews() clears whatever
                    // rows were added last time before the new ones go in, otherwise every
                    // reload would just keep stacking more rows on top of the old ones
                    // (Android Developers, n.d.-b).
                    llExpenseEntries.removeAllViews()

                    for (category in categories) {
                        // Built in code rather than in item_expense_entry.xml, since there's
                        // exactly one of these per category rather than one per expense -
                        // coloured/sized to match the rest of the app's white-on-blue look,
                        // loaded the same backward-compatible way the app's XML fonts resolve
                        // under the hood (Android Developers, n.d.-d).
                        val headerView = TextView(this@ExpenseHistoryActivity)
                        headerView.text = "== ${category.name} =="
                        headerView.setPadding(0, 16, 0, 4)
                        headerView.setTextColor(ContextCompat.getColor(this@ExpenseHistoryActivity, R.color.white))
                        headerView.typeface = ResourcesCompat.getFont(this@ExpenseHistoryActivity, R.font.comic_relief_bold)
                        headerView.textSize = 16f
                        llExpenseEntries.addView(headerView)

                        val categoryExpenses = expensesByCategory[category.savingsCategoryId]
                        if (categoryExpenses.isNullOrEmpty()) {
                            val emptyView = TextView(this@ExpenseHistoryActivity)
                            emptyView.text = "  No expenses recorded."
                            emptyView.setTextColor(ContextCompat.getColor(this@ExpenseHistoryActivity, R.color.white))
                            emptyView.typeface = ResourcesCompat.getFont(this@ExpenseHistoryActivity, R.font.comic_relief_regular)
                            emptyView.textSize = 15f
                            llExpenseEntries.addView(emptyView)
                        } else {
                            for (expense in categoryExpenses) {
                                addExpenseRow(expense)
                            }
                        }
                    }
                }

                HistoryViewMode.CATEGORY_TOTALS -> {
                    llExpenseEntries.visibility = View.GONE
                    tvExpenseHistoryBreakdown.visibility = View.VISIBLE

                    // getTotalsPerSavingsCategory() does the summing itself in SQL, so
                    // this view never needs to load the individual expenses at all - just
                    // one total per category for the chosen range.
                    val categoryTotals = database.expenseDao().getTotalsPerSavingsCategory(childId, startDateMillis, endDateMillis)

                    // associateBy() turns that list into a Map keyed by categoryId, so
                    // each category's total can be looked up directly instead of
                    // searched for (Kotlin, n.d.).
                    val totalsByCategory = categoryTotals.associateBy { it.categoryId }

                    val total = categoryTotals.sumOf { it.totalSpent }
                    tvExpenseHistoryTotal.text = "Total Spent: %.2f".format(total)

                    val breakdown = StringBuilder()
                    for (category in categories) {
                        val categoryTotal = totalsByCategory[category.savingsCategoryId]?.totalSpent ?: 0.0
                        breakdown.append("${category.name}: %.2f\n".format(categoryTotal))
                    }
                    tvExpenseHistoryBreakdown.text = breakdown.toString().trim()
                }
            }
        }
    }

    // Inflates one item_expense_entry row for a single expense and adds it to
    // llExpenseEntries - pulled out of loadExpenseHistory() since every expense in every
    // category goes through the exact same steps.
    private fun addExpenseRow(expense: Expense) {
        val row = layoutInflater.inflate(R.layout.item_expense_entry, llExpenseEntries, false)
        val tvEntryText = row.findViewById<TextView>(R.id.tvEntryText)
        val ivThumbnail = row.findViewById<ImageView>(R.id.ivEntryThumbnail)

        val dateText = dateFormat.format(Date(expense.date))
        val descriptionText = if (expense.description.isBlank()) "" else " - ${expense.description}"
        tvEntryText.text = "  $dateText: %.2f%s".format(expense.amount, descriptionText)

        val photoUriText = expense.photoUri
        if (photoUriText != null) {
            try {
                val uri = Uri.parse(photoUriText)
                ivThumbnail.setImageURI(uri)
                ivThumbnail.visibility = View.VISIBLE
                ivThumbnail.setOnClickListener { showPhotoDialog(uri) }
            } catch (e: Exception) {
                // The photo's permission may have been revoked, or the file may no
                // longer exist - either way, just skip the thumbnail rather than
                // crashing this whole screen over one bad photo.
                ivThumbnail.visibility = View.GONE
            }
        } else {
            ivThumbnail.visibility = View.GONE
        }

        llExpenseEntries.addView(row)
    }

    // Shows the same receipt photo full-size in a popup, reusing the same
    // AlertDialog.Builder + custom view pattern ChildDetailActivity uses for its category
    // popups (Android Developers, n.d.-c).
    private fun showPhotoDialog(uri: Uri) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_photo_view, null)
        val ivFullPhoto = dialogView.findViewById<ImageView>(R.id.ivFullPhoto)
        ivFullPhoto.setImageURI(uri)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
    }
}

// References:
// Android Developers, 2026. The activity lifecycle [Webpage]. Available at:
//     https://developer.android.com/guide/components/activities/activity-lifecycle
//     [Accessed 8 September 2026].

// Tutorialspoint, 2026.-a. Kotlin Array - sumOf() Function [Webpage]. Available at:
//     https://www.tutorialspoint.com/kotlin/kotlin_array_sumof_function.htm
//     [Accessed 10 September 2026].

// Tutorialspoint, 2026.-b. Kotlin Array - groupBy() Function [Webpage]. Available at:
//     https://www.tutorialspoint.com/kotlin/kotlin_array_groupby_function.htm
//     [Accessed 10 September 2026].

// Pajgade, A., 2025. Working with date objects in Kotlin [Webpage]. Available at:
//     https://medium.com/@atharvapajgade/working-with-date-objects-in-kotlin-e6af6cb9688c
//     [Accessed 12 September 2026].

// Chavan, D., 2023. Date Picker Using Kotlin in Android Studio | DatePickerDialog -
// Android Studio Tutorial | Kotlin [Webpage].
//Available at: https://devendrac706.medium.com/date-picker-using-kotlin-in-android-studio-datepickerdialog-android-studio-tutorial-kotlin-3bbc606585a
// [Accessed 11 September 2026].

// Android Developers, 2026. DatePicker [Webpage].
// Available at: https://developer.android.com/reference/android/widget/DatePicker
// [Accessed 14 September 2026].

// Kotlin, n.d. associateBy [Webpage].
// Available at: https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/associate-by.html
// [Accessed 13 September 2026].

// Android Developers, 2026.-b. ViewGroup - removeAllViews() [Webpage]. Available at:
//     https://developer.android.com/reference/android/view/ViewGroup#removeAllViews()
//     [Accessed 13 September 2026].

// Android Developers, 2026.-c. AlertDialog.Builder [Webpage]. Available at:
//     https://developer.android.com/reference/android/app/AlertDialog.Builder
//     [Accessed 15 September 2026].

// Android Developers, 2026.-d. ResourcesCompat [Webpage]. Available at:
//     https://developer.android.com/reference/androidx/core/content/res/ResourcesCompat
//     [Accessed 14 September 2026].
