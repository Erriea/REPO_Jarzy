package com.dani.jarzy

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import com.dani.jarzy.data.JarzyDatabase
import com.dani.jarzy.data.SavingsCategory
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

// The three views this screen can show, swapped by the top tab row instead of one long
// scrolling form - the exact same enum/selectTab()/styleTabButton() pattern
// ChildDetailActivity uses for the parent's per-child view (its DetailTab), just renamed
// so the two screens' near-identical private enums don't read as the same type.
private enum class ChildTab {
    STATS,
    CATEGORIES,
    HISTORY
}

/**
 * ChildHomeActivity is the screen a child lands on after logging in. It's organised into
 * the same three tabs as the parent's ChildDetailActivity - Stats / Categories / History -
 * recoloured jarzy_purple instead of jarzy_blue so a child can tell their own screens
 * apart from the parent's at a glance. Stats shows the child's balance as one big cube
 * plus a way to log an expense; Categories shows every savings category as a swipeable
 * coloured cube (via vpCategories, a ViewPager2) with Add New/Edit/Delete acting on
 * whichever cube is currently centred, plus the money-transfer screen since that also
 * only ever moves money between categories; History shows a quick per-category total
 * with a button through to the full expense history screen.
 *
 * Three safety differences apply only here, because it's the CHILD'S own money:
 *   - Add New Category always creates a brand-new category starting at 0 - there's no
 *     amount box in the popup, so a child can never type themselves in a starting balance.
 *   - Edit Category only renames a category - it can't touch its amount at all.
 *   - There's no "Add to Balance" field like the parent's Stats tab has - only the parent
 *     can give a child extra money.
 * The only ways money can actually enter or move between a child's categories are the
 * parent adding to their balance, logging an expense, or the "Move Money Between
 * Categories" transfer screen (which only ever shifts money that's already there from one
 * category to another, never creates it). Letting a child type a new amount directly into
 * a category here would bypass all of that, so that ability simply isn't offered on this
 * screen.
 */
class ChildHomeActivity : AppCompatActivity() {

    private lateinit var database: JarzyDatabase
    private var childId: Long = -1L

    // This child's own savings categories, in the same order the cube carousel shows
    // them - kept around so Edit/Delete Category can turn "whichever cube is on screen
    // right now" back into the actual category it represents.
    private var categories: List<SavingsCategory> = emptyList()

    private var currentTab = ChildTab.STATS

    private lateinit var tvSavingsTotal: TextView
    private lateinit var tvSavingsBreakdown: TextView

    private lateinit var btnTabStats: MaterialButton
    private lateinit var btnTabCategories: MaterialButton
    private lateinit var btnTabHistory: MaterialButton
    private lateinit var llStatsTabContent: LinearLayout
    private lateinit var llCategoriesTabContent: LinearLayout
    private lateinit var llHistoryTabContent: LinearLayout

    private lateinit var vpCategories: ViewPager2
    private lateinit var categoryCubeAdapter: CategoryCubeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_child_home)

        database = JarzyDatabase.getDatabase(this)

        val childUsername = intent.getStringExtra("CHILD_USERNAME") ?: ""
        childId = intent.getLongExtra("CHILD_ID", -1L)

        val tvChildWelcome = findViewById<TextView>(R.id.tvChildWelcome)
        tvSavingsTotal = findViewById(R.id.tvSavingsTotal)
        tvSavingsBreakdown = findViewById(R.id.tvSavingsBreakdown)

        btnTabStats = findViewById(R.id.btnTabStats)
        btnTabCategories = findViewById(R.id.btnTabCategories)
        btnTabHistory = findViewById(R.id.btnTabHistory)
        llStatsTabContent = findViewById(R.id.llStatsTabContent)
        llCategoriesTabContent = findViewById(R.id.llCategoriesTabContent)
        llHistoryTabContent = findViewById(R.id.llHistoryTabContent)

        val btnSaveCategory = findViewById<Button>(R.id.btnSaveCategory)
        val btnEditCategory = findViewById<Button>(R.id.btnEditCategory)
        val btnDeleteCategory = findViewById<Button>(R.id.btnDeleteCategory)

        val btnAddSavingsGoal = findViewById<Button>(R.id.btnAddSavingsGoal)
        val btnAddExpense = findViewById<Button>(R.id.btnAddExpense)
        val btnViewExpenseHistory = findViewById<Button>(R.id.btnViewExpenseHistory)
        val btnLogout = findViewById<Button>(R.id.btnLogout)

        tvChildWelcome.text = "Welcome, $childUsername"

        btnTabStats.setOnClickListener { selectTab(ChildTab.STATS) }
        btnTabCategories.setOnClickListener { selectTab(ChildTab.CATEGORIES) }
        btnTabHistory.setOnClickListener { selectTab(ChildTab.HISTORY) }
        selectTab(ChildTab.STATS)

        // The swipeable category carousel - offscreenPageLimit keeps the neighbouring
        // cube already laid out (rather than blank) as it's swiped into view, and
        // MarginPageTransformer opens a small gap between cubes on top of the peek
        // effect the ViewPager2's own padding + clipToPadding="false" already create,
        // the same setup ChildDetailActivity uses on the parent's side
        // (Tutorialspoint, n.d.-b; Android Developers, n.d.-b).
        vpCategories = findViewById(R.id.vpCategories)
        categoryCubeAdapter = CategoryCubeAdapter(categories)
        vpCategories.adapter = categoryCubeAdapter
        vpCategories.offscreenPageLimit = 1
        vpCategories.setPageTransformer(MarginPageTransformer((8 * resources.displayMetrics.density).toInt()))

        // "Add New Category" always creates a brand-new one, starting at 0 - never with a
        // typed-in amount, so this button can't be used to give the child money that
        // isn't actually theirs yet.
        btnSaveCategory.setOnClickListener {
            showCategoryNameDialog(title = "Add New Category", existingName = "") { name ->
                if (categories.any { it.name.equals(name, ignoreCase = true) }) {
                    Toast.makeText(this, "A category with that name already exists.", Toast.LENGTH_SHORT).show()
                    return@showCategoryNameDialog
                }

                lifecycleScope.launch {
                    database.savingsCategoryDao().insert(SavingsCategory(childId = childId, name = name, amountSaved = 0.0))
                    Toast.makeText(this@ChildHomeActivity, "Category added!", Toast.LENGTH_SHORT).show()
                    refreshCategories()
                }
            }
        }

        // "Edit Category" only renames whichever cube is currently centred in the
        // carousel - its amount is left completely untouched, unlike the parent's
        // version of this same popup.
        btnEditCategory.setOnClickListener {
            val selected = categoryCubeAdapter.categoryAt(vpCategories.currentItem)
            if (selected == null) {
                Toast.makeText(this, "Swipe to a category to edit first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            showCategoryNameDialog(title = "Edit Category", existingName = selected.name) { name ->
                lifecycleScope.launch {
                    database.savingsCategoryDao().update(selected.copy(name = name))
                    Toast.makeText(this@ChildHomeActivity, "Category updated!", Toast.LENGTH_SHORT).show()
                    refreshCategories()
                }
            }
        }

        btnDeleteCategory.setOnClickListener {
            val selected = categoryCubeAdapter.categoryAt(vpCategories.currentItem)
            if (selected == null) {
                Toast.makeText(this, "Swipe to a category to delete first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // A child always needs at least one category to exist, so the very last one
            // can't be deleted here - the same invariant registration relies on.
            if (categories.size <= 1) {
                Toast.makeText(
                    this,
                    "You need at least one savings category - add another before deleting this one.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            // "General" is where a deleted category's funds land (see the confirmation
            // handler below) - protected here for the same reason ChildDetailActivity
            // protects it on the parent's side: if General itself were deletable there'd
            // be nowhere for that redirect to go.
            if (selected.name.equals("General", ignoreCase = true)) {
                Toast.makeText(
                    this,
                    "The General category can't be deleted - it's where money goes when other categories are removed.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            // A plain confirmation popup - Delete is destructive and can't be undone, so
            // it gets a "are you sure?" step the other two buttons don't need
            // (Android Developers, n.d.-a).
            AlertDialog.Builder(this)
                .setTitle("Delete Category")
                .setMessage("Delete \"${selected.name}\"? Any money in it will move to General first.")
                .setPositiveButton("Delete") { _, _ ->
                    lifecycleScope.launch {
                        // Redirect whatever this category was holding into General
                        // instead of just deleting it along with the category - the
                        // child shouldn't lose money just because a category got removed.
                        if (selected.amountSaved > 0) {
                            val generalCategory = categories.firstOrNull { it.name.equals("General", ignoreCase = true) }
                            if (generalCategory != null) {
                                database.savingsCategoryDao().update(
                                    generalCategory.copy(amountSaved = generalCategory.amountSaved + selected.amountSaved)
                                )
                            } else {
                                // Defensive fallback - every child should already have a
                                // General category, but create one if it's somehow missing
                                // rather than silently losing this money.
                                database.savingsCategoryDao().insert(
                                    SavingsCategory(childId = childId, name = "General", amountSaved = selected.amountSaved)
                                )
                            }
                        }

                        database.savingsCategoryDao().delete(selected)
                        Toast.makeText(this@ChildHomeActivity, "Category deleted.", Toast.LENGTH_SHORT).show()
                        refreshCategories()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Opens the money-transfer screen - the only place a child's category amounts can
        // move around without going through an expense, and even there it's always FROM
        // one category TO another, never created from nothing.
        btnAddSavingsGoal.setOnClickListener {
            val intent = Intent(this, CreateSavingsCategoryActivity::class.java)
            intent.putExtra("CHILD_ID", childId)
            startActivity(intent)
        }

        // Opens the same AddExpenseActivity screen the parent uses - logging an expense
        // works identically no matter which account type opens it.
        btnAddExpense.setOnClickListener {
            val intent = Intent(this, AddExpenseActivity::class.java)
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

    // Runs on first open AND whenever this screen is returned to (e.g. after logging an
    // expense, or coming back from the transfer screen), so the balance and category list
    // are never stale.
    override fun onResume() {
        super.onResume()
        refreshCategories()
    }

    // Switches which of the three content blocks is visible and restyles the three tab
    // buttons to match - pulled into its own function since it runs both from each
    // button's click listener and once up front in onCreate() to set the initial state,
    // the same selectTab() ChildDetailActivity uses on the parent's side.
    private fun selectTab(tab: ChildTab) {
        currentTab = tab

        llStatsTabContent.visibility = if (tab == ChildTab.STATS) View.VISIBLE else View.GONE
        llCategoriesTabContent.visibility = if (tab == ChildTab.CATEGORIES) View.VISIBLE else View.GONE
        llHistoryTabContent.visibility = if (tab == ChildTab.HISTORY) View.VISIBLE else View.GONE

        styleTabButton(btnTabStats, tab == ChildTab.STATS)
        styleTabButton(btnTabCategories, tab == ChildTab.CATEGORIES)
        styleTabButton(btnTabHistory, tab == ChildTab.HISTORY)
    }

    // The selected tab is filled white with purple text (matching every other primary
    // button on this screen); the other two stay outlined - transparent fill, white text -
    // so there's always exactly one obviously "on" button in the row.
    private fun styleTabButton(button: MaterialButton, selected: Boolean) {
        if (selected) {
            button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white))
            button.setTextColor(ContextCompat.getColor(this, R.color.jarzy_purple))
        } else {
            button.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            button.setTextColor(ContextCompat.getColor(this, R.color.white))
        }
    }

    // Shared by "Add New Category" and "Edit Category" and onResume() - all of them need
    // this child's categories reloaded and the balance, breakdown and cube carousel
    // redrawn from that fresh data. The same refreshAll() pattern ChildDetailActivity uses
    // on the parent's side.
    private fun refreshCategories() {
        lifecycleScope.launch {
            categories = database.savingsCategoryDao().getCategoriesForChild(childId)

            // Balance is calculated by adding up every category's amountSaved, not
            // stored as its own number - one source of truth for how much the child has
            // (Tutorialspoint, n.d.-a). Just the number here - the "Balance" label itself
            // is static text in the layout, sitting above this inside the same white cube.
            val total = categories.sumOf { it.amountSaved }
            tvSavingsTotal.text = "%.2f".format(total)

            tvSavingsBreakdown.text = if (categories.isEmpty()) {
                "No savings categories yet."
            } else {
                categories.joinToString("\n") { "${it.name}: %.2f".format(it.amountSaved) }
            }

            // Keeps whichever cube was on screen in view rather than snapping back to
            // the first one every time a category is added, edited or deleted.
            val previousPosition = vpCategories.currentItem
            categoryCubeAdapter.updateCategories(categories)
            if (previousPosition < categories.size) {
                vpCategories.setCurrentItem(previousPosition, false)
            }
        }
    }

    // Shared by "Add New Category" and "Edit Category" - both need the same single-field
    // popup (just a name box), unlike the parent's version of this dialog which also
    // collects an amount - a child's category amount is never set through this dialog.
    private fun showCategoryNameDialog(
        title: String,
        existingName: String,
        onConfirm: (name: String) -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_category_name_input, null)
        val etDialogName = dialogView.findViewById<EditText>(R.id.etDialogCategoryName)
        etDialogName.setText(existingName)

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = etDialogName.text.toString().trim()

                if (name.isEmpty()) {
                    Toast.makeText(this, "Category name can't be empty.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                onConfirm(name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

// References:
// Tutorialspoint, n.d.-a. Kotlin Array - sumOf() Function [Webpage]. Available at:
//     https://www.tutorialspoint.com/kotlin/kotlin_array_sumof_function.htm
//     [Accessed 15 September 2026].
// Duggu, 2023. joinToString in Kotlin [Webpage]. Available at:
//     https://medium.com/@dugguRK/jointostring-in-kotlin-d227b9394486 [Accessed 15 September 2026].
// Android Developers, n.d.-a. AlertDialog.Builder [Webpage]. Available at:
//     https://developer.android.com/reference/android/app/AlertDialog.Builder
//     [Accessed 16 September 2026].
// Tutorialspoint, n.d.-b. ViewPager2 in Android with Example [Webpage]. Available at:
//     https://www.tutorialspoint.com/viewpager2-in-android-with-example
//     [Accessed 16 September 2026].
// Android Developers, n.d.-b. MarginPageTransformer [Webpage]. Available at:
//     https://developer.android.com/reference/androidx/viewpager2/widget/MarginPageTransformer
//     [Accessed 16 September 2026].
