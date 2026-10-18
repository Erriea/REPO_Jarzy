package com.dani.jarzy

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import com.dani.jarzy.data.ChildAccount
import com.dani.jarzy.data.JarzyDatabase
import com.dani.jarzy.data.Quest
import com.dani.jarzy.data.QuestType
import com.dani.jarzy.data.SavingsCategory
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.util.Calendar

// The four views this screen can show, swapped by the top toggle row instead of one
// long scrolling form - kept as its own enum rather than four Booleans so selectTab()
// and styleTabButton() below read clearly. Quests was added as a fourth value alongside
// the original three rather than a separate screen, so it reuses the exact same tab
// mechanism (the same reasoning ExpenseHistoryActivity's HistoryViewMode enum used).
private enum class DetailTab {
    STATS,
    CATEGORIES,
    QUESTS,
    HISTORY
}

/**
 * ChildDetailActivity is opened from ParentHomeActivity when the parent picks one of
 * their children from the dropdown.
 * Everything on this screen is scoped to that one child
 * everything the parent set up when registering this child
 * username, password, monthly allowance, min/max monthly spend, and their savings categories
 * can be edited here.
 *
 * The screen is organised into four tabs (Stats / Categories / Quests / History) instead
 * of one long form
 * Stats holds the child's balance/budget/min-max figures plus the profile-editing fields
 * Categories shows every savings category as a swipeable coloured "cube" with
 * Edit/Add New/Delete acting on whichever cube is currently centred
 * Quests is the parent's side of the chore system - creating/editing/deleting quests for
 * this child, seeing how many of today's quests they've checked off, and confirming
 * payment once every quest due today is done
 * History shows a quick per-category total with a button through to the full expense history screen.
 */
class ChildDetailActivity : AppCompatActivity() {

    private lateinit var database: JarzyDatabase
    private var childId: Long = -1L

    // The full, freshly-loaded row for this child - kept around so the "Save Changes"
    // button has the current childId/primary key to copy() from, without re-fetching it.
    private var currentChild: ChildAccount? = null

    // This child's savings categories, in the same order the cube carousel shows them
    // kept around so Edit/Delete Category can turn "whichever cube is on screen right
    // now" back into the actual category it represents.
    private var categories: List<SavingsCategory> = emptyList()

    // This child's quests, already passed through QuestScheduler.resetIfNeeded() every
    // time they're loaded, so isChecked/isPaid always reflect the CURRENT period rather
    // than a stale one from before a daily/weekly reset.
    private var quests: List<Quest> = emptyList()

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
    private lateinit var btnTabQuests: MaterialButton
    private lateinit var btnTabHistory: MaterialButton
    private lateinit var llStatsTabContent: LinearLayout
    private lateinit var llCategoriesTabContent: LinearLayout
    private lateinit var llQuestsTabContent: LinearLayout
    private lateinit var llHistoryTabContent: LinearLayout

    private lateinit var vpCategories: ViewPager2
    private lateinit var categoryCubeAdapter: CategoryCubeAdapter

    private lateinit var tvQuestProgress: TextView
    private lateinit var btnConfirmQuests: Button
    private lateinit var rvQuests: RecyclerView
    private lateinit var questAdapter: QuestAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_child_detail)

        database = JarzyDatabase.getDatabase(this)

        // Both extras are read once here
        // the id for every database call on this
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
        btnTabQuests = findViewById(R.id.btnTabQuests)
        btnTabHistory = findViewById(R.id.btnTabHistory)
        llStatsTabContent = findViewById(R.id.llStatsTabContent)
        llCategoriesTabContent = findViewById(R.id.llCategoriesTabContent)
        llQuestsTabContent = findViewById(R.id.llQuestsTabContent)
        llHistoryTabContent = findViewById(R.id.llHistoryTabContent)

        tvQuestProgress = findViewById(R.id.tvQuestProgress)
        btnConfirmQuests = findViewById(R.id.btnConfirmQuests)
        rvQuests = findViewById(R.id.rvQuests)
        val btnAddQuest = findViewById<Button>(R.id.btnAddQuest)

        tvChildDetailHeading.text = "Managing: $childUsername"

        btnBack.setOnClickListener { finish() }

        btnTabStats.setOnClickListener { selectTab(DetailTab.STATS) }
        btnTabCategories.setOnClickListener { selectTab(DetailTab.CATEGORIES) }
        btnTabQuests.setOnClickListener { selectTab(DetailTab.QUESTS) }
        btnTabHistory.setOnClickListener { selectTab(DetailTab.HISTORY) }
        selectTab(DetailTab.STATS)

        // The swipeable category carousel
        // (Tutorialspoint, n.d.-c; Android Developers, n.d.-c).
        vpCategories = findViewById(R.id.vpCategories)
        categoryCubeAdapter = CategoryCubeAdapter(categories)
        vpCategories.adapter = categoryCubeAdapter
        vpCategories.offscreenPageLimit = 1
        vpCategories.setPageTransformer(MarginPageTransformer((8 * resources.displayMetrics.density).toInt()))

        // QuestAdapter in PARENT mode - tapping a row opens it for editing, the Delete
        // button removes it outright, and the checkbox itself is shown but disabled since
        // only the CHILD ever taps it (see ChildHomeActivity for the CHILD-mode version
        // of this same adapter class).
        questAdapter = QuestAdapter(
            quests = quests,
            mode = QuestAdapter.Mode.PARENT,
            onEditRequested = { quest -> showQuestDialog(title = "Edit Quest", existingQuest = quest) },
            onDeleteRequested = { quest -> confirmDeleteQuest(quest) }
        )
        // A plain vertical list - RecyclerView needs a LayoutManager told how to arrange
        // its rows, unlike ViewPager2 above which supplies its own (Android Developers, n.d.-e).
        rvQuests.layoutManager = LinearLayoutManager(this)
        rvQuests.adapter = questAdapter

        btnAddQuest.setOnClickListener {
            showQuestDialog(title = "Add New Quest", existingQuest = null)
        }

        btnConfirmQuests.setOnClickListener {
            confirmAndPayQuests()
        }

        // Shows/hides the whole "Edit Child Account" block instead of it always being on
        // screen
        // the read-only summary above (Balance/Budget/Min/Max) covers most
        // visits to this page, so the edit fields only need to appear when asked for
        //(Android Developers, n.d.-a - View.GONE/VISIBLE).
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
                // Username has to stay unique across every child
                // but this child is allowed to keep its own existing username
                // only blocks the save if a different child already has it.
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
        // whatever the allowance already set up at registration
        btnAddToBalance.setOnClickListener {
            val amount = etAddBalanceAmount.text.toString().trim().toDoubleOrNull()
            if (amount == null || amount <= 0) {
                Toast.makeText(this, "Enter a valid amount greater than 0.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                addToGeneralCategory(amount)
                etAddBalanceAmount.text.clear()
                Toast.makeText(this@ChildDetailActivity, "Added to balance!", Toast.LENGTH_SHORT).show()
                refreshAll()
            }
        }

        // "Add New Category" always means creating a brand new one from a blank popup
        btnSaveCategory.setOnClickListener {
            showCategoryDialog(title = "Add New Category", existingName = "", existingAmount = "0.0", existingBudget = "") { name, amount, budget ->
                if (categories.any { it.name.equals(name, ignoreCase = true) }) {
                    Toast.makeText(this, "A category with that name already exists.", Toast.LENGTH_SHORT).show()
                    return@showCategoryDialog
                }

                lifecycleScope.launch {
                    database.savingsCategoryDao().insert(
                        SavingsCategory(childId = childId, name = name, amountSaved = amount, budgetSize = budget)
                    )
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
                existingAmount = selected.amountSaved.toString(),
                existingBudget = if (selected.budgetSize > 0) selected.budgetSize.toString() else ""
            ) { name, amount, budget ->
                lifecycleScope.launch {
                    database.savingsCategoryDao().update(selected.copy(name = name, amountSaved = amount, budgetSize = budget))
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

            // A child always needs at least one category to exist
            // last one can't be deleted here
            if (categories.size <= 1) {
                Toast.makeText(
                    this,
                    "A child needs at least one savings category - add another before deleting this one.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            // "General" is where a deleted category's funds land
            // if General itself were deletable there'd be nowhere for
            // that redirect to go,
            if (selected.name.equals("General", ignoreCase = true)) {
                Toast.makeText(
                    this,
                    "The General category can't be deleted - it's where money goes when other categories are removed.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            // A plain confirmation popup
            // Delete is destructive and can't be undone, so
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
                                // Defensive fallback
                                // every child should already have a General category
                                //  create one if it's somehow missing
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

    // Runs on first open & whenever this screen is returned to so every figure and
    // field shown is never stale (Android Developers, 2026).
    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    // Switches which of the four content blocks is visible and restyles the four tab
    // buttons to match
    // pulled into its own function since it runs both from each
    // button's click listener and once up front in onCreate() to set the initial state.
    private fun selectTab(tab: DetailTab) {
        currentTab = tab

        llStatsTabContent.visibility = if (tab == DetailTab.STATS) View.VISIBLE else View.GONE
        llCategoriesTabContent.visibility = if (tab == DetailTab.CATEGORIES) View.VISIBLE else View.GONE
        llQuestsTabContent.visibility = if (tab == DetailTab.QUESTS) View.VISIBLE else View.GONE
        llHistoryTabContent.visibility = if (tab == DetailTab.HISTORY) View.VISIBLE else View.GONE

        styleTabButton(btnTabStats, tab == DetailTab.STATS)
        styleTabButton(btnTabCategories, tab == DetailTab.CATEGORIES)
        styleTabButton(btnTabQuests, tab == DetailTab.QUESTS)
        styleTabButton(btnTabHistory, tab == DetailTab.HISTORY)
    }

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
    //  just with different starting text and a different
    // action once confirmed, so the actual name/amount/budget validation and the
    // onConfirm callback are the only things that differ between the two buttons
    // (Android Developers, n.d.-b).
    private fun showCategoryDialog(
        title: String,
        existingName: String,
        existingAmount: String,
        existingBudget: String,
        onConfirm: (name: String, amount: Double, budget: Double) -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_category_input, null)
        val etDialogName = dialogView.findViewById<EditText>(R.id.etDialogCategoryName)
        val etDialogAmount = dialogView.findViewById<EditText>(R.id.etDialogCategoryAmount)
        val etDialogBudget = dialogView.findViewById<EditText>(R.id.etDialogCategoryBudget)
        etDialogName.setText(existingName)
        etDialogAmount.setText(existingAmount)
        etDialogBudget.setText(existingBudget)

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = etDialogName.text.toString().trim()
                val amount = etDialogAmount.text.toString().trim().toDoubleOrNull()
                // Budget is optional - a blank box is treated as "no budget set" (0.0),
                // not an error, unlike the amount field above.
                val budgetText = etDialogBudget.text.toString().trim()
                val budget = if (budgetText.isEmpty()) 0.0 else budgetText.toDoubleOrNull()

                if (name.isEmpty()) {
                    Toast.makeText(this, "Category name can't be empty.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (amount == null || amount < 0) {
                    Toast.makeText(this, "Amount must be a valid number of 0 or more.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (budget == null || budget < 0) {
                    Toast.makeText(this, "Budget must be left blank or a valid number of 0 or more.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                onConfirm(name, amount, budget)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Adds money straight into this child's General category, creating it first if it's
    // somehow missing - shared by "Add to Balance" above and quest payouts below, since
    // both put money in exactly the same place: any money not tied to a specific
    // budgeting category the child set up themselves lands in General by default.
    private suspend fun addToGeneralCategory(amount: Double) {
        val existingCategories = database.savingsCategoryDao().getCategoriesForChild(childId)
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
    }

    // Shared "Add New Quest"/"Edit Quest" popup - same reasoning as showCategoryDialog
    // above, one dialog covering both buttons rather than two near-identical ones.
    private fun showQuestDialog(title: String, existingQuest: Quest?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_quest_input, null)
        val etName = dialogView.findViewById<EditText>(R.id.etDialogQuestName)
        val etReward = dialogView.findViewById<EditText>(R.id.etDialogQuestReward)
        val rgType = dialogView.findViewById<RadioGroup>(R.id.rgDialogQuestType)
        val spinnerWeekday = dialogView.findViewById<Spinner>(R.id.spinnerQuestWeekday)
        val btnPickStartDate = dialogView.findViewById<Button>(R.id.btnDialogPickStartDate)
        val cbNoEndDate = dialogView.findViewById<CheckBox>(R.id.cbDialogNoEndDate)
        val btnPickEndDate = dialogView.findViewById<Button>(R.id.btnDialogPickEndDate)

        // Calendar.SUNDAY..Calendar.SATURDAY are 1..7 in that order already, so the
        // Spinner's position lines up directly with the Calendar constant - position 0
        // is Sunday (Calendar.SUNDAY == 1), position 6 is Saturday (Calendar.SATURDAY == 7)
        // (Android Developers, n.d.-d).
        val weekdayNames = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        spinnerWeekday.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, weekdayNames)

        // These two live for the lifetime of this one dialog only - captured by the date
        // picker click listeners below and read again when Save is tapped.
        var startDateMillis = QuestScheduler.midnightOf(Calendar.getInstance())
        var endDateMillis: Long? = null

        rgType.setOnCheckedChangeListener { _, checkedId ->
            spinnerWeekday.visibility = if (checkedId == R.id.rbQuestWeekly) View.VISIBLE else View.GONE
        }

        cbNoEndDate.setOnCheckedChangeListener { _, isChecked ->
            btnPickEndDate.isEnabled = !isChecked
            if (isChecked) endDateMillis = null
        }

        btnPickStartDate.text = "Start: " + formatDate(startDateMillis)
        btnPickStartDate.setOnClickListener {
            val cal = Calendar.getInstance()
            cal.timeInMillis = startDateMillis
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    val picked = Calendar.getInstance()
                    picked.set(year, month, day, 0, 0, 0)
                    startDateMillis = QuestScheduler.midnightOf(picked)
                    btnPickStartDate.text = "Start: " + formatDate(startDateMillis)
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        btnPickEndDate.setOnClickListener {
            val cal = Calendar.getInstance()
            endDateMillis?.let { cal.timeInMillis = it }
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    val picked = Calendar.getInstance()
                    picked.set(year, month, day, 0, 0, 0)
                    endDateMillis = QuestScheduler.midnightOf(picked)
                    btnPickEndDate.text = "End: " + formatDate(endDateMillis!!)
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // Pre-fill every field when editing an existing quest, instead of leaving the
        // dialog at its blank/default state.
        if (existingQuest != null) {
            etName.setText(existingQuest.name)
            etReward.setText(existingQuest.reward.toString())
            when (QuestType.fromDb(existingQuest.type)) {
                QuestType.ONCE_OFF -> rgType.check(R.id.rbQuestOnceOff)
                QuestType.DAILY -> rgType.check(R.id.rbQuestDaily)
                QuestType.WEEKLY -> rgType.check(R.id.rbQuestWeekly)
            }
            existingQuest.weeklyDayOfWeek?.let { day -> spinnerWeekday.setSelection(day - 1) }
            spinnerWeekday.visibility = if (QuestType.fromDb(existingQuest.type) == QuestType.WEEKLY) View.VISIBLE else View.GONE
            startDateMillis = existingQuest.startDate
            btnPickStartDate.text = "Start: " + formatDate(startDateMillis)
            endDateMillis = existingQuest.endDate
            cbNoEndDate.isChecked = existingQuest.endDate == null
            btnPickEndDate.isEnabled = existingQuest.endDate != null
            if (existingQuest.endDate != null) {
                btnPickEndDate.text = "End: " + formatDate(existingQuest.endDate)
            }
        } else {
            rgType.check(R.id.rbQuestOnceOff)
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = etName.text.toString().trim()
                val reward = etReward.text.toString().trim().toDoubleOrNull()

                if (name.isEmpty()) {
                    Toast.makeText(this, "Quest name can't be empty.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (reward == null || reward <= 0) {
                    Toast.makeText(this, "Reward must be a valid number greater than 0.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val type = when (rgType.checkedRadioButtonId) {
                    R.id.rbQuestDaily -> QuestType.DAILY
                    R.id.rbQuestWeekly -> QuestType.WEEKLY
                    else -> QuestType.ONCE_OFF
                }

                val weeklyDay = if (type == QuestType.WEEKLY) spinnerWeekday.selectedItemPosition + 1 else null

                lifecycleScope.launch {
                    if (existingQuest == null) {
                        // The 5-a-day cap only counts quests that would actually be
                        // active TODAY - a weekly quest for a day that isn't today
                        // doesn't take up one of today's 5 slots.
                        val today = Calendar.getInstance()
                        val proposedQuest = Quest(
                            childId = childId, name = name, reward = reward, type = type.name,
                            weeklyDayOfWeek = weeklyDay, startDate = startDateMillis, endDate = endDateMillis,
                            lastResetDate = QuestScheduler.midnightOf(today)
                        )
                        val activeTodayCount = quests.count { QuestScheduler.isActiveToday(it, today) }
                        val newOneIsActiveToday = QuestScheduler.isActiveToday(proposedQuest, today)

                        if (newOneIsActiveToday && activeTodayCount >= 5) {
                            Toast.makeText(this@ChildDetailActivity, "This child already has 5 quests on the go today.", Toast.LENGTH_LONG).show()
                            return@launch
                        }

                        database.questDao().insert(proposedQuest)
                        Toast.makeText(this@ChildDetailActivity, "Quest added!", Toast.LENGTH_SHORT).show()
                    } else {
                        // Changing a quest's schedule mid-period could leave isChecked/
                        // isPaid referring to a period that no longer makes sense (e.g.
                        // switching Daily to Weekly), so editing always resets them -
                        // simpler and safer than trying to carry old progress across a
                        // schedule change.
                        database.questDao().update(
                            existingQuest.copy(
                                name = name, reward = reward, type = type.name, weeklyDayOfWeek = weeklyDay,
                                startDate = startDateMillis, endDate = endDateMillis,
                                isChecked = false, isPaid = false,
                                lastResetDate = QuestScheduler.midnightOf(Calendar.getInstance())
                            )
                        )
                        Toast.makeText(this@ChildDetailActivity, "Quest updated!", Toast.LENGTH_SHORT).show()
                    }

                    refreshQuests()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteQuest(quest: Quest) {
        AlertDialog.Builder(this)
            .setTitle("Delete Quest")
            .setMessage("Delete \"${quest.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    database.questDao().delete(quest)
                    Toast.makeText(this@ChildDetailActivity, "Quest deleted.", Toast.LENGTH_SHORT).show()
                    refreshQuests()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Pays out every quest due today that the child has checked off but hasn't been paid
    // for yet, all in one go, straight into General - only reachable once every quest due
    // today is checked (btnConfirmQuests.isEnabled, set in refreshQuests()).
    private fun confirmAndPayQuests() {
        lifecycleScope.launch {
            val today = Calendar.getInstance()
            val toPay = quests.filter { QuestScheduler.isActiveToday(it, today) && it.isChecked && !it.isPaid }

            if (toPay.isEmpty()) {
                Toast.makeText(this@ChildDetailActivity, "Nothing to confirm yet.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val totalReward = toPay.sumOf { it.reward }

            toPay.forEach { quest ->
                database.questDao().update(quest.copy(isPaid = true))
            }

            addToGeneralCategory(totalReward)

            Toast.makeText(
                this@ChildDetailActivity,
                "Paid out %.2f for %d completed quest(s)!".format(totalReward, toPay.size),
                Toast.LENGTH_LONG
            ).show()

            refreshAll()
        }
    }

    private fun formatDate(millis: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        return "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }

    // Loads this child's quests, runs every one of them through
    // QuestScheduler.resetIfNeeded() (writing any reset straight back to the database so
    // it sticks), then redraws the checklist and the "X of Y done today" progress line.
    // Pulled out from refreshAll() into its own function so showQuestDialog() and the
    // confirm/delete handlers above can refresh just the Quests tab without re-running
    // every other query on this screen.
    private suspend fun refreshQuests() {
        val today = Calendar.getInstance()
        val loaded = database.questDao().getQuestsForChild(childId)

        val resetQuests = loaded.map { quest ->
            val reset = QuestScheduler.resetIfNeeded(quest, today)
            if (reset !== quest) {
                database.questDao().update(reset)
            }
            reset
        }

        quests = resetQuests
        questAdapter.updateQuests(quests)

        val activeToday = quests.filter { QuestScheduler.isActiveToday(it, today) }
        val doneToday = activeToday.count { it.isChecked }

        tvQuestProgress.text = if (activeToday.isEmpty()) {
            "No quests due today."
        } else {
            "$doneToday of ${activeToday.size} done today"
        }

        btnConfirmQuests.isEnabled = activeToday.isNotEmpty() && doneToday == activeToday.size &&
            activeToday.any { !it.isPaid }
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

            refreshQuests()
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
// Android Developers, n.d.-d. Spinner.setSelection(int) [Webpage]. Available at:
//     https://developer.android.com/reference/android/widget/AdapterView#setSelection(int)
//     [Accessed 18 October 2026].
// Android Developers, n.d.-e. RecyclerView.LinearLayoutManager [Webpage]. Available at:
//     https://developer.android.com/reference/androidx/recyclerview/widget/LinearLayoutManager
//     [Accessed 18 October 2026].
