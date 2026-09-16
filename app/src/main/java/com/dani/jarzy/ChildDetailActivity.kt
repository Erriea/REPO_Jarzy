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
import com.dani.jarzy.data.ChildAccount
import com.dani.jarzy.data.JarzyDatabase
import com.dani.jarzy.data.SavingsCategory
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

// The three views this screen can show, swapped by the top toggle row instead of one
// long scrolling form - kept as its own enum rather than three Booleans so selectTab()
// and styleTabButton() below read clearly and a fourth tab could be added later without
// renaming anything (the same reasoning ExpenseHistoryActivity's HistoryViewMode enum
// used).
private enum class DetailTab {
    STATS,
    CATEGORIES,
    HISTORY
}

/**
 * ChildDetailActivity is opened from ParentHomeActivity when the parent picks one of
 * their children from the dropdown. Everything on this screen is scoped to that ONE
 * child, and everything the parent set up when registering this child - username,
 * password, monthly allowance, min/max monthly spend, and their savings categories - can
 * be edited here. The parent can no longer log an expense for the child from this screen;
 * that's a child-only action now (see ChildHomeActivity), so this screen only offers a
 * quick summary and a link to the full "View Expense History" for that side of things.
 *
 * The screen is organised into three tabs (Stats / Categories / History) instead of one
 * long form, inspired by an app the user showed as a reference: Stats holds the child's
 * balance/budget/min-max figures plus the profile-editing fields; Categories shows every
 * savings category as a swipeable coloured "cube" (via vpCategories, a ViewPager2) with
 * Edit/Add New/Delete acting on whichever cube is currently centred; History shows a
 * quick per-category total with a button through to the full expense history screen.
 */
class ChildDetailActivity : AppCompatActivity() {

    private lateinit var database: JarzyDatabase
    private var childId: Long = -1L

    // The full, freshly-loaded row for this child - kept around so the "Save Changes"
    // button has the current childId/primary key to copy() from, without re-fetching it.
    private var currentChild: ChildAccount? = null

    // This child's savings categories, in the same order the cube carousel shows them -
    // kept around so Edit/Delete Category can turn "whichever cube is on screen right
    // now" back into the actual category it represents.
    private var categories: List<SavingsCategory> = emptyList()

    private var currentTab = DetailTab.STATS

    private lateinit var tvSavingsTotal: TextView
    private lateinit var tvAllowanceDisplay: TextView
    private lateinit var tvSpendRange: TextView
    private lateinit var tvSavingsBreakdown: TextView

    private lateinit var btnToggleEditProfile: Button
    private lateinit var llEditAccountFields: LinearLayout
    private lateinit var etEditUsername: EditText
    private lateinit var etEditPassword: EditText
    private lateinit var etEditAllowance: EditText
    private lateinit var etEditMinSpend: EditText
    private lateinit var etEditMaxSpend: EditText
    private lateinit var tvAccountEditError: TextView

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
        setContentView(R.layout.activity_child_detail)

        database = JarzyDatabase.getDatabase(this)

        // Both extras are read once here - the id for every database call on this
        // screen, the username just for display in the heading (Vogel, 2016).
        childId = intent.getLongExtra("CHILD_ID", -1L)
        val childUsername = intent.getStringExtra("CHILD_USERNAME") ?: ""

        val btnBack = findViewById<Button>(R.id.btnBack)
        val tvChildDetailHeading = findViewById<TextView>(R.id.tvChildDetailHeading)
        tvSavingsTotal = findViewById(R.id.tvSavingsTotal)
        tvAllowanceDisplay = findViewById(R.id.tvAllowanceDisplay)
        tvSpendRange = findViewById(R.id.tvSpendRange)
        tvSavingsBreakdown = findViewById(R.id.tvSavingsBreakdown)

        btnToggleEditProfile = findViewById(R.id.btnToggleEditProfile)
        llEditAccountFields = findViewById(R.id.llEditAccountFields)
        etEditUsername = findViewById(R.id.etEditUsername)
        etEditPassword = findViewById(R.id.etEditPassword)
        etEditAllowance = findViewById(R.id.etEditAllowance)
        etEditMinSpend = findViewById(R.id.etEditMinSpend)
        etEditMaxSpend = findViewById(R.id.etEditMaxSpend)
        val btnSaveAccountChanges = findViewById<Button>(R.id.btnSaveAccountChanges)
        tvAccountEditError = findViewById(R.id.tvAccountEditError)

        val etAddBalanceAmount = findViewById<EditText>(R.id.etAddBalanceAmount)
        val btnAddToBalance = findViewById<Button>(R.id.btnAddToBalance)

        val btnSaveCategory = findViewById<Button>(R.id.btnSaveCategory)
        val btnEditCategory = findViewById<Button>(R.id.btnEditCategory)
        val btnDeleteCategory = findViewById<Button>(R.id.btnDeleteCategory)

        val btnViewExpenseHistory = findViewById<Button>(R.id.btnViewExpenseHistory)

        btnTabStats = findViewById(R.id.btnTabStats)
        btnTabCategories = findViewById(R.id.btnTabCategories)
        btnTabHistory = findViewById(R.id.btnTabHistory)
        llStatsTabContent = findViewById(R.id.llStatsTabContent)
        llCategoriesTabContent = findViewById(R.id.llCategoriesTabContent)
        llHistoryTabContent = findViewById(R.id.llHistoryTabContent)

        // Names the specific child so this page can't be confused with
        // ParentHomeActivity's own "Welcome, <parent>" heading.
        tvChildDetailHeading.text = "Managing: $childUsername"

        btnBack.setOnClickListener { finish() }

        btnTabStats.setOnClickListener { selectTab(DetailTab.STATS) }
        btnTabCategories.setOnClickListener { selectTab(DetailTab.CATEGORIES) }
        btnTabHistory.setOnClickListener { selectTab(DetailTab.HISTORY) }
        selectTab(DetailTab.STATS)

        // The swipeable category carousel - offscreenPageLimit keeps the neighbouring
        // cube already laid out (rather than blank) as it's swiped into view, and
        // MarginPageTransformer opens a small gap between cubes on top of the peek
        // effect the ViewPager2's own padding + clipToPadding="false" already create
        // (Tutorialspoint, n.d.-c; Android Developers, n.d.-c).
        vpCategories = findViewById(R.id.vpCategories)
        categoryCubeAdapter = CategoryCubeAdapter(categories)
        vpCategories.adapter = categoryCubeAdapter
        vpCategories.offscreenPageLimit = 1
        vpCategories.setPageTransformer(MarginPageTransformer((8 * resources.displayMetrics.density).toInt()))

        // Shows/hides the whole "Edit Child Account" block instead of it always being on
        // screen - the read-only summary above (Balance/Budget/Min/Max) covers most
        // visits to this page, so the edit fields only need to appear when asked for
        // (View.GONE removes a view from layout entirely, unlike View.INVISIBLE which
        // would still leave an empty gap) (Android Developers, n.d.-a).
        btnToggleEditProfile.setOnClickListener {
            if (llEditAccountFields.visibility == View.VISIBLE) {
                llEditAccountFields.visibility = View.GONE
                btnToggleEditProfile.text = "Edit Child Profile"
            } else {
                llEditAccountFields.visibility = View.VISIBLE
                btnToggleEditProfile.text = "Hide Edit Fields"
            }
        }

        btnSaveAccountChanges.setOnClickListener {
            val child = currentChild
            if (child == null) {
                tvAccountEditError.text = "Child data hasn't loaded yet - try again in a moment."
                return@setOnClickListener
            }

            val username = etEditUsername.text.toString().trim()
            val password = etEditPassword.text.toString()
            val allowanceText = etEditAllowance.text.toString().trim()
            val minSpendText = etEditMinSpend.text.toString().trim()
            val maxSpendText = etEditMaxSpend.text.toString().trim()

            if (username.isEmpty() || password.isEmpty() || allowanceText.isEmpty() ||
                minSpendText.isEmpty() || maxSpendText.isEmpty()
            ) {
                tvAccountEditError.text = "Please fill in all fields."
                return@setOnClickListener
            }

            if (username.length < 4) {
                tvAccountEditError.text = "Username must be at least 4 characters long."
                return@setOnClickListener
            }

            if (password.length < 8) {
                tvAccountEditError.text = "Password must be at least 8 characters long."
                return@setOnClickListener
            }

            // any() checks whether at least one character in the password is neither a
            // letter nor a digit - i.e. a special character - same rule RegisterChildActivity
            // enforces at signup (Yuan, 2024).
            if (!password.any { !it.isLetterOrDigit() }) {
                tvAccountEditError.text = "Password must contain at least one special character."
                return@setOnClickListener
            }

            val allowance = allowanceText.toDoubleOrNull()
            val minSpend = minSpendText.toDoubleOrNull()
            val maxSpend = maxSpendText.toDoubleOrNull()

            if (allowance == null || minSpend == null || maxSpend == null ||
                allowance < 0 || minSpend < 0 || maxSpend < 0
            ) {
                tvAccountEditError.text = "Allowance and spend amounts must be valid numbers of 0 or more."
                return@setOnClickListener
            }

            if (maxSpend < minSpend) {
                tvAccountEditError.text = "Maximum spend cannot be less than the minimum spend."
                return@setOnClickListener
            }

            lifecycleScope.launch {
                // Username has to stay unique across every child - but this child is
                // allowed to keep ITS OWN existing username, so only block the save if a
                // DIFFERENT child already has it.
                val existing = database.childDao().getByUsername(username)
                if (existing != null && existing.childId != child.childId) {
                    tvAccountEditError.text = "That username is already taken by another account."
                    return@launch
                }

                database.childDao().update(
                    child.copy(
                        username = username,
                        password = password,
                        monthlyAllowance = allowance,
                        minMonthlySpend = minSpend,
                        maxMonthlySpend = maxSpend
                    )
                )

                tvAccountEditError.text = ""
                Toast.makeText(this@ChildDetailActivity, "Child account updated!", Toast.LENGTH_SHORT).show()

                // Collapse back to the read-only summary now that the save succeeded,
                // instead of leaving the edit fields open.
                llEditAccountFields.visibility = View.GONE
                btnToggleEditProfile.text = "Edit Child Profile"

                refreshAll()
            }
        }

        // Lets the parent give the child some extra money whenever they want, on top of
        // whatever the allowance already set up at registration - any amount they choose,
        // not tied to the stored monthlyAllowance figure at all.
        btnAddToBalance.setOnClickListener {
            val amount = etAddBalanceAmount.text.toString().trim().toDoubleOrNull()
            if (amount == null || amount <= 0) {
                Toast.makeText(this, "Enter a valid amount greater than 0.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val existingCategories = database.savingsCategoryDao().getCategoriesForChild(childId)

                // Extra money always lands in the child's "General" category first - the
                // child decides how to split it up further from there.
                val generalCategory = existingCategories.firstOrNull { it.name.equals("General", ignoreCase = true) }

                if (generalCategory != null) {
                    database.savingsCategoryDao().update(
                        generalCategory.copy(amountSaved = generalCategory.amountSaved + amount)
                    )
                } else {
                    database.savingsCategoryDao().insert(
                        SavingsCategory(childId = childId, name = "General", amountSaved = amount)
                    )
                }

                etAddBalanceAmount.text.clear()
                Toast.makeText(this@ChildDetailActivity, "Added to balance!", Toast.LENGTH_SHORT).show()
                refreshAll()
            }
        }

        // "Add New Category" always means creating a brand new one, from a blank popup -
        // this button doesn't touch whatever cube is currently on screen at all.
        btnSaveCategory.setOnClickListener {
            showCategoryDialog(title = "Add New Category", existingName = "", existingAmount = "0.0") { name, amount ->
                if (categories.any { it.name.equals(name, ignoreCase = true) }) {
                    Toast.makeText(this, "A category with that name already exists.", Toast.LENGTH_SHORT).show()
                    return@showCategoryDialog
                }

                lifecycleScope.launch {
                    database.savingsCategoryDao().insert(SavingsCategory(childId = childId, name = name, amountSaved = amount))
                    Toast.makeText(this@ChildDetailActivity, "Category added!", Toast.LENGTH_SHORT).show()
                    refreshAll()
                }
            }
        }

        // "Edit Category" opens the same popup, pre-filled with whichever cube is
        // currently centred in the carousel, and updates that exact category on confirm.
        btnEditCategory.setOnClickListener {
            val selected = categoryCubeAdapter.categoryAt(vpCategories.currentItem)
            if (selected == null) {
                Toast.makeText(this, "Swipe to a category to edit first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            showCategoryDialog(
                title = "Edit Category",
                existingName = selected.name,
                existingAmount = selected.amountSaved.toString()
            ) { name, amount ->
                lifecycleScope.launch {
                    database.savingsCategoryDao().update(selected.copy(name = name, amountSaved = amount))
                    Toast.makeText(this@ChildDetailActivity, "Category updated!", Toast.LENGTH_SHORT).show()
                    refreshAll()
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
                    "A child needs at least one savings category - add another before deleting this one.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            // "General" is where a deleted category's funds land (see the confirmation
            // handler below) - if General itself were deletable there'd be nowhere for
            // that redirect to go, so it's protected here instead of handling that as a
            // special case every time something else is deleted.
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
            // (Android Developers, n.d.-b).
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
                        Toast.makeText(this@ChildDetailActivity, "Category deleted.", Toast.LENGTH_SHORT).show()
                        refreshAll()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnViewExpenseHistory.setOnClickListener {
            val intent = Intent(this, ExpenseHistoryActivity::class.java)
            intent.putExtra("CHILD_ID", childId)
            startActivity(intent)
        }
    }

    // Runs on first open AND whenever this screen is returned to, so every figure and
    // field shown is never stale (Android Developers, 2026).
    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    // Switches which of the three content blocks is visible and restyles the three tab
    // buttons to match - pulled into its own function since it runs both from each
    // button's click listener and once up front in onCreate() to set the initial state.
    private fun selectTab(tab: DetailTab) {
        currentTab = tab

        llStatsTabContent.visibility = if (tab == DetailTab.STATS) View.VISIBLE else View.GONE
        llCategoriesTabContent.visibility = if (tab == DetailTab.CATEGORIES) View.VISIBLE else View.GONE
        llHistoryTabContent.visibility = if (tab == DetailTab.HISTORY) View.VISIBLE else View.GONE

        styleTabButton(btnTabStats, tab == DetailTab.STATS)
        styleTabButton(btnTabCategories, tab == DetailTab.CATEGORIES)
        styleTabButton(btnTabHistory, tab == DetailTab.HISTORY)
    }

    // The selected tab is filled white with blue text (matching every other primary
    // button in this app); the other two stay outlined - transparent fill, white text -
    // so there's always exactly one obviously "on" button in the row.
    private fun styleTabButton(button: MaterialButton, selected: Boolean) {
        if (selected) {
            button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white))
            button.setTextColor(ContextCompat.getColor(this, R.color.jarzy_blue))
        } else {
            button.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            button.setTextColor(ContextCompat.getColor(this, R.color.white))
        }
    }

    // Shared by "Add New Category" and "Edit Category" - both need the exact same popup
    // (a name box and an amount box), just with different starting text and a different
    // action once confirmed, so the actual name/amount validation and the onConfirm
    // callback are the only things that differ between the two buttons
    // (Android Developers, n.d.-b).
    private fun showCategoryDialog(
        title: String,
        existingName: String,
        existingAmount: String,
        onConfirm: (name: String, amount: Double) -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_category_input, null)
        val etDialogName = dialogView.findViewById<EditText>(R.id.etDialogCategoryName)
        val etDialogAmount = dialogView.findViewById<EditText>(R.id.etDialogCategoryAmount)
        etDialogName.setText(existingName)
        etDialogAmount.setText(existingAmount)

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = etDialogName.text.toString().trim()
                val amount = etDialogAmount.text.toString().trim().toDoubleOrNull()

                if (name.isEmpty()) {
                    Toast.makeText(this, "Category name can't be empty.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (amount == null || amount < 0) {
                    Toast.makeText(this, "Amount must be a valid number of 0 or more.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                onConfirm(name, amount)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Pulled out into its own function because onResume() and every Save/Add/Delete
    // button above all need to reload and redisplay the same child + category data.
    private fun refreshAll() {
        lifecycleScope.launch {
            val child = database.childDao().getById(childId)
            currentChild = child

            if (child != null) {
                etEditUsername.setText(child.username)
                etEditPassword.setText(child.password)
                etEditAllowance.setText(child.monthlyAllowance.toString())
                etEditMinSpend.setText(child.minMonthlySpend.toString())
                etEditMaxSpend.setText(child.maxMonthlySpend.toString())
                tvAllowanceDisplay.text = "Budget: %.2f".format(child.monthlyAllowance)
                tvSpendRange.text = "Min Spend: %.2f   Max Spend: %.2f".format(child.minMonthlySpend, child.maxMonthlySpend)
            }

            categories = database.savingsCategoryDao().getCategoriesForChild(childId)

            // Balance is the sum of this child's categories, not a stored field - the
            // same one-source-of-truth approach as ChildHomeActivity (Tutorialspoint, n.d.-b).
            // Just the number here - the "Balance" label itself is static text in the
            // layout, sitting above this inside the same white cube.
            val total = categories.sumOf { it.amountSaved }
            tvSavingsTotal.text = "%.2f".format(total)

            tvSavingsBreakdown.text = if (categories.isEmpty()) {
                "No savings categories yet."
            } else {
                categories.joinToString("\n") { "${it.name}: %.2f".format(it.amountSaved) }
            }

            // Keeps whichever cube was on screen in view rather than snapping back to
            // the first one every time something elsewhere on this screen is saved.
            val previousPosition = vpCategories.currentItem
            categoryCubeAdapter.updateCategories(categories)
            if (previousPosition < categories.size) {
                vpCategories.setCurrentItem(previousPosition, false)
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
// Yuan, K., 2024. Password Validation in Kotlin [Webpage]. Available at:
//     https://www.baeldung.com/kotlin/password-validation [Accessed 16 September 2026].
// Android Developers, n.d.-a. View [Webpage]. Available at:
//     https://developer.android.com/reference/android/view/View#GONE [Accessed 16 September 2026].
// Android Developers, n.d.-b. AlertDialog.Builder [Webpage]. Available at:
//     https://developer.android.com/reference/android/app/AlertDialog.Builder
//     [Accessed 16 September 2026].
// Tutorialspoint, n.d.-c. ViewPager2 in Android with Example [Webpage]. Available at:
//     https://www.tutorialspoint.com/viewpager2-in-android-with-example
//     [Accessed 16 September 2026].
// Android Developers, n.d.-c. MarginPageTransformer [Webpage]. Available at:
//     https://developer.android.com/reference/androidx/viewpager2/widget/MarginPageTransformer
//     [Accessed 16 September 2026].
