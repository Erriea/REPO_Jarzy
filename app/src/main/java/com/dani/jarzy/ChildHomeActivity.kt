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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import com.dani.jarzy.data.ChildRepository
import com.dani.jarzy.data.ExpenseRepository
import com.dani.jarzy.data.Quest
import com.dani.jarzy.data.QuestRepository
import com.dani.jarzy.data.SavingsCategory
import com.dani.jarzy.data.SavingsCategoryRepository
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.util.Calendar

// The four views this screen can show, swapped by the top tab row instead of one long
// scrolling form - the exact same enum/selectTab()/styleTabButton() pattern
// ChildDetailActivity uses for the parent's per-child view (its DetailTab), just renamed
// so the two screens' near-identical private enums don't read as the same type.
private enum class ChildTab {
    STATS,
    CATEGORIES,
    QUESTS,
    HISTORY
}

/**
 * ChildHomeActivity is the screen a child lands on after logging in. It's organised into
 * the same four tabs as the parent's ChildDetailActivity - Stats / Categories / Quests /
 * History - recoloured jarzy_purple instead of jarzy_blue so a child can tell their own
 * screens apart from the parent's at a glance. Stats shows the child's balance as one big
 * cube plus a way to log an expense; Categories shows every savings category as a
 * swipeable coloured cube (via vpCategories, a ViewPager2) with Add New/Edit/Delete
 * acting on whichever cube is currently centred, plus the money-transfer screen since
 * that also only ever moves money between categories; Quests is the child's side of the
 * chore system - a checklist of whatever the parent has set up for them, where ticking a
 * box marks it done but doesn't pay it (the parent still has to confirm); History shows a
 * quick per-category total with a button through to the full expense history screen.
 *
 * Three safety differences apply only here, because it's the CHILD'S own money:
 *   - Add New Category always creates a brand-new category starting at 0 - there's no
 *     amount box in the popup, so a child can never type themselves in a starting balance.
 *   - Edit Category only renames a category - it can't touch its amount at all.
 *   - There's no "Add to Balance" field like the parent's Stats tab has - only the parent
 *     can give a child extra money.
 * The only ways money can actually enter or move between a child's categories are the
 * parent adding to their balance, logging an expense, the "Move Money Between
 * Categories" transfer screen, or the parent confirming a completed quest - none of
 * which this screen lets a child short-circuit themselves.
 *
 * The Stats tab also shows an MPAndroidChart half-circle gauge (chartMonthlyGauge) -
 * this child's total spend so far THIS calendar month, against their minMonthlySpend/
 * maxMonthlySpend set at registration, with a small purple marker showing exactly where
 * that total currently sits. See setUpMonthlyGauge()/refreshMonthlyGauge()/
 * updateMonthlyGauge() below.
 */
class ChildHomeActivity : AppCompatActivity() {

    // Replaces the old JarzyDatabase (Room) reference - see SavingsCategoryRepository/
    // QuestRepository's own class comments for how they mirror the old DAOs' methods
    // against Firestore instead.
    private val savingsCategoryRepository = SavingsCategoryRepository()
    private val questRepository = QuestRepository()

    // Both added only to power the "This Month's Spending" gauge on the Stats tab -
    // childRepository reads minMonthlySpend/maxMonthlySpend, expenseRepository (which
    // needs savingsCategoryRepository passed in - see its own class comment) sums this
    // child's expenses for the current calendar month.
    private val childRepository = ChildRepository()
    private val expenseRepository = ExpenseRepository(savingsCategoryRepository)
    private var childId: Long = -1L

    // This child's own savings categories, in the same order the cube carousel shows
    // them - kept around so Edit/Delete Category can turn "whichever cube is on screen
    // right now" back into the actual category it represents.
    private var categories: List<SavingsCategory> = emptyList()

    // This child's quests, already passed through QuestScheduler.resetIfNeeded() every
    // time they're loaded - see refreshQuests().
    private var quests: List<Quest> = emptyList()

    private var currentTab = ChildTab.STATS

    private lateinit var tvSavingsTotal: TextView
    private lateinit var tvSavingsBreakdown: TextView
    private lateinit var chartMonthlyGauge: PieChart
    private lateinit var tvMonthlyGaugeLegend: TextView

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
    private lateinit var rvQuests: RecyclerView
    private lateinit var questAdapter: QuestAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_child_home)

        val childUsername = intent.getStringExtra("CHILD_USERNAME") ?: ""
        childId = intent.getLongExtra("CHILD_ID", -1L)

        val tvChildWelcome = findViewById<TextView>(R.id.tvChildWelcome)
        tvSavingsTotal = findViewById(R.id.tvSavingsTotal)
        tvSavingsBreakdown = findViewById(R.id.tvSavingsBreakdown)
        chartMonthlyGauge = findViewById(R.id.chartMonthlyGauge)
        tvMonthlyGaugeLegend = findViewById(R.id.tvMonthlyGaugeLegend)
        setUpMonthlyGauge()

        btnTabStats = findViewById(R.id.btnTabStats)
        btnTabCategories = findViewById(R.id.btnTabCategories)
        btnTabQuests = findViewById(R.id.btnTabQuests)
        btnTabHistory = findViewById(R.id.btnTabHistory)
        llStatsTabContent = findViewById(R.id.llStatsTabContent)
        llCategoriesTabContent = findViewById(R.id.llCategoriesTabContent)
        llQuestsTabContent = findViewById(R.id.llQuestsTabContent)
        llHistoryTabContent = findViewById(R.id.llHistoryTabContent)

        tvQuestProgress = findViewById(R.id.tvQuestProgress)
        rvQuests = findViewById(R.id.rvQuests)

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
        btnTabQuests.setOnClickListener { selectTab(ChildTab.QUESTS) }
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

        // QuestAdapter in CHILD mode - the checkbox is tappable (marks a quest done
        // without paying it), and there's no Edit/Delete since only the parent can
        // create, change or remove a quest (see ChildDetailActivity for the PARENT-mode
        // version of this same adapter class).
        questAdapter = QuestAdapter(
            quests = quests,
            mode = QuestAdapter.Mode.CHILD,
            onCheckToggled = { quest, isChecked -> onQuestCheckToggled(quest, isChecked) }
        )
        // A plain vertical list - RecyclerView needs a LayoutManager told how to arrange
        // its rows, unlike ViewPager2 above which supplies its own (Android Developers, n.d.-c).
        rvQuests.layoutManager = LinearLayoutManager(this)
        rvQuests.adapter = questAdapter

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
                    savingsCategoryRepository.insert(SavingsCategory(childId = childId, name = name, amountSaved = 0.0))
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
                    savingsCategoryRepository.update(selected.copy(name = name))
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
                                savingsCategoryRepository.update(
                                    generalCategory.copy(amountSaved = generalCategory.amountSaved + selected.amountSaved)
                                )
                            } else {
                                // Defensive fallback - every child should already have a
                                // General category, but create one if it's somehow missing
                                // rather than silently losing this money.
                                savingsCategoryRepository.insert(
                                    SavingsCategory(childId = childId, name = "General", amountSaved = selected.amountSaved)
                                )
                            }
                        }

                        savingsCategoryRepository.delete(selected)
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
    // expense, or coming back from the transfer screen), so the balance, category list
    // and quest checklist are never stale.
    override fun onResume() {
        super.onResume()
        refreshCategories()
        refreshMonthlyGauge()
        lifecycleScope.launch { refreshQuests() }
    }

    // Switches which of the four content blocks is visible and restyles the four tab
    // buttons to match - pulled into its own function since it runs both from each
    // button's click listener and once up front in onCreate() to set the initial state,
    // the same selectTab() ChildDetailActivity uses on the parent's side.
    private fun selectTab(tab: ChildTab) {
        currentTab = tab

        llStatsTabContent.visibility = if (tab == ChildTab.STATS) View.VISIBLE else View.GONE
        llCategoriesTabContent.visibility = if (tab == ChildTab.CATEGORIES) View.VISIBLE else View.GONE
        llQuestsTabContent.visibility = if (tab == ChildTab.QUESTS) View.VISIBLE else View.GONE
        llHistoryTabContent.visibility = if (tab == ChildTab.HISTORY) View.VISIBLE else View.GONE

        styleTabButton(btnTabStats, tab == ChildTab.STATS)
        styleTabButton(btnTabCategories, tab == ChildTab.CATEGORIES)
        styleTabButton(btnTabQuests, tab == ChildTab.QUESTS)
        styleTabButton(btnTabHistory, tab == ChildTab.HISTORY)
    }

    // The selected tab is filled white with purple text (matching every other primary
    // button on this screen); the other three stay outlined - transparent fill, white
    // text - so there's always exactly one obviously "on" button in the row.
    private fun styleTabButton(button: MaterialButton, selected: Boolean) {
        if (selected) {
            button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white))
            button.setTextColor(ContextCompat.getColor(this, R.color.jarzy_purple))
        } else {
            button.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            button.setTextColor(ContextCompat.getColor(this, R.color.white))
        }
    }

    // A child ticking (or unticking) a quest just flips isChecked - it deliberately never
    // touches isPaid or the child's balance itself. Only the parent's "Confirm & Pay" on
    // ChildDetailActivity can actually move money, the same permission boundary every
    // other money-related action on this screen already respects.
    private fun onQuestCheckToggled(quest: Quest, isChecked: Boolean) {
        lifecycleScope.launch {
            questRepository.update(quest.copy(isChecked = isChecked))
            refreshQuests()
        }
    }

    // Loads this child's quests, runs every one of them through
    // QuestScheduler.resetIfNeeded() (writing any reset straight back to the database so
    // it sticks), then redraws the checklist and the "X of Y done today" progress line -
    // the same two-step reload-then-redisplay shape refreshCategories() below uses, just
    // for quests instead of categories.
    private suspend fun refreshQuests() {
        val today = Calendar.getInstance()
        val loaded = questRepository.getQuestsForChild(childId)

        val resetQuests = loaded.map { quest ->
            val reset = QuestScheduler.resetIfNeeded(quest, today)
            if (reset !== quest) {
                questRepository.update(reset)
            }
            reset
        }

        quests = resetQuests
        questAdapter.updateQuests(quests)

        val activeToday = quests.filter { QuestScheduler.isActiveToday(it, today) }
        val doneToday = activeToday.count { it.isChecked }

        tvQuestProgress.text = if (activeToday.isEmpty()) {
            "No quests today - check back later!"
        } else {
            "$doneToday of ${activeToday.size} done today"
        }
    }

    // Shared by "Add New Category" and "Edit Category" and onResume() - all of them need
    // this child's categories reloaded and the balance, breakdown and cube carousel
    // redrawn from that fresh data. The same refreshAll() pattern ChildDetailActivity uses
    // on the parent's side.
    private fun refreshCategories() {
        lifecycleScope.launch {
            categories = savingsCategoryRepository.getCategoriesForChild(childId)

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

    // One-time gauge setup, called once from onCreate() - exactly the same split
    // ExpenseHistoryActivity uses for its own chart (setUpChart() for one-time styling,
    // updateCategoryChart() for the data that changes every reload). This is the styling
    // half; updateMonthlyGauge() below is the data half.
    private fun setUpMonthlyGauge() {
        chartMonthlyGauge.description.isEnabled = false
        chartMonthlyGauge.legend.isEnabled = false // tvMonthlyGaugeLegend (plain text) explains the colours instead
        chartMonthlyGauge.setDrawEntryLabels(false) // no text crowded onto the thin slices themselves
        chartMonthlyGauge.setTouchEnabled(false) // a read-only indicator, not something to drag/rotate

        // setMaxAngle(180f) is MPAndroidChart's own documented way to draw a half pie
        // instead of a full circle (PhilJay, n.d.). rotationAngle=180f then positions
        // that half starting at the left (9 o'clock) and sweeping up and over to the
        // right (3 o'clock) - the usual "dome on top, flat on the bottom" speedometer
        // shape, rather than some other quarter or half of the circle.
        chartMonthlyGauge.maxAngle = 180f
        chartMonthlyGauge.rotationAngle = 180f

        chartMonthlyGauge.isDrawHoleEnabled = true
        chartMonthlyGauge.holeRadius = 65f
        chartMonthlyGauge.setHoleColor(ContextCompat.getColor(this, R.color.white))
        chartMonthlyGauge.setCenterTextColor(ContextCompat.getColor(this, R.color.jarzy_purple))
        chartMonthlyGauge.setCenterTextSize(15f)
    }

    // Reloads how much this child has spent so far THIS CALENDAR MONTH (every category
    // combined - unlike ExpenseHistoryActivity's chart, there's no date range picker
    // here, it's always "this month so far") and this child's minMonthlySpend/
    // maxMonthlySpend, then hands all three to updateMonthlyGauge() to actually redraw
    // the gauge. Pulled into its own function, called from onResume() alongside
    // refreshCategories()/refreshQuests(), rather than folded into either of those -
    // this is its own independent piece of data, not really "categories" or "quests".
    private fun refreshMonthlyGauge() {
        lifecycleScope.launch {
            val child = childRepository.getById(childId)

            val monthStart = Calendar.getInstance()
            monthStart.set(Calendar.DAY_OF_MONTH, 1)
            monthStart.set(Calendar.HOUR_OF_DAY, 0)
            monthStart.set(Calendar.MINUTE, 0)
            monthStart.set(Calendar.SECOND, 0)
            monthStart.set(Calendar.MILLISECOND, 0)

            val monthEnd = Calendar.getInstance()
            monthEnd.set(Calendar.HOUR_OF_DAY, 23)
            monthEnd.set(Calendar.MINUTE, 59)
            monthEnd.set(Calendar.SECOND, 59)
            monthEnd.set(Calendar.MILLISECOND, 999)

            val expensesThisMonth = expenseRepository.getExpensesInRange(
                childId, monthStart.timeInMillis, monthEnd.timeInMillis
            )
            val currentSpend = expensesThisMonth.sumOf { it.amount }

            updateMonthlyGauge(currentSpend, child?.minMonthlySpend ?: 0.0, child?.maxMonthlySpend ?: 0.0)
        }
    }

    // Builds the gauge's coloured background bands (amber = under the monthly minimum,
    // green = on track, red/pink = over the monthly maximum) plus a thin purple "you are
    // here" marker sliver at the current spend's exact position - MPAndroidChart's
    // PieChart has no built-in needle, so this marker is just a short, differently-
    // coloured segment inserted at the right spot instead, the same slice-based approach
    // the rest of the chart already uses.
    //
    // A maxMonthlySpend of 0.0 means "no max was set" (same convention
    // updateCategoryChart() in ExpenseHistoryActivity already uses for its own min/max
    // lines) - everything above the minimum just counts as "on track" rather than ever
    // showing red, since there's no real ceiling to compare against.
    private fun updateMonthlyGauge(currentSpend: Double, minSpend: Double, maxSpend: Double) {
        // Always covers the current spend AND the max target, with 20% headroom so
        // neither sits flush against the very end of the arc. The "10.0" fallback only
        // ever applies if spend, min and max are ALL still zero (a brand new child with
        // no data yet) - without it, ceiling would be 0 and the whole gauge would
        // collapse to a zero-width arc instead of drawing a sensible empty one.
        val ceiling = (maxOf(currentSpend, minSpend, maxSpend) * 1.2).let { if (it > 0.0) it else 10.0 }

        val clampedMin = minSpend.coerceIn(0.0, ceiling)
        val clampedMax = maxSpend.coerceIn(0.0, ceiling)

        // A thin marker sliver, 2% of the whole scale wide, centred on currentSpend.
        val markerHalfWidth = ceiling * 0.01
        val markerStart = (currentSpend - markerHalfWidth).coerceIn(0.0, ceiling)
        val markerEnd = (currentSpend + markerHalfWidth).coerceIn(0.0, ceiling)

        // Every point where the arc needs to change colour - the three zone edges plus
        // the marker's own two edges, sorted and de-duplicated. distinct() means two
        // boundaries landing on the same value (a very normal case - e.g. a brand new
        // child where min, max and spend are all 0) just collapse into one point rather
        // than creating a zero-width segment.
        val boundaries = listOf(0.0, clampedMin, clampedMax, ceiling, markerStart, markerEnd)
            .sorted()
            .distinct()

        val entries = mutableListOf<PieEntry>()
        val colors = mutableListOf<Int>()

        for (i in 0 until boundaries.size - 1) {
            val segmentStart = boundaries[i]
            val segmentEnd = boundaries[i + 1]
            val width = segmentEnd - segmentStart
            if (width <= 0.0) continue

            val midpoint = (segmentStart + segmentEnd) / 2.0
            val color = when {
                midpoint in markerStart..markerEnd ->
                    ContextCompat.getColor(this, R.color.jarzy_purple) // the "you are here" marker
                midpoint < clampedMin ->
                    ContextCompat.getColor(this, R.color.cube_amber) // under the monthly minimum
                midpoint < clampedMax || maxSpend <= 0.0 ->
                    ContextCompat.getColor(this, R.color.cube_green) // on track
                else ->
                    ContextCompat.getColor(this, R.color.cube_pink) // over the monthly maximum
            }

            entries.add(PieEntry(width.toFloat()))
            colors.add(color)
        }

        val dataSet = PieDataSet(entries, "")
        dataSet.colors = colors
        dataSet.setDrawValues(false) // colour bands only - no numbers crowded onto the slices
        dataSet.sliceSpace = 0f

        chartMonthlyGauge.data = PieData(dataSet)
        chartMonthlyGauge.centerText = "Spent\n%.2f".format(currentSpend)
        chartMonthlyGauge.invalidate()

        tvMonthlyGaugeLegend.text = "Amber: under min (%.2f)   Green: on track   Red: over max (%.2f)"
            .format(minSpend, maxSpend)
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
// Android Developers, n.d.-c. RecyclerView.LinearLayoutManager [Webpage]. Available at:
//     https://developer.android.com/reference/androidx/recyclerview/widget/LinearLayoutManager
//     [Accessed 18 October 2026].
// PhilJay, n.d. Specific Chart Settings & Styling [Webpage]. Available at:
//     https://github.com/PhilJay/MPAndroidChart/wiki/Specific-Chart-Settings-&-Styling
//     [Accessed 25 October 2026].
