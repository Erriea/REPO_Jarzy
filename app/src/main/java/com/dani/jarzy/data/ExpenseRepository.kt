package com.dani.jarzy.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * ExpenseRepository is the Firestore replacement for the old ExpenseDao - same method
 * names, parameters and return types as before, with one difference explained below.
 * See ParentRepository's class comment for why fields are stored in a top-level
 * collection keyed by id.toString(), and why fields are read/written by name
 * explicitly instead of Firestore's toObject().
 *
 * getExpensesInRange() only filters by childId directly in the Firestore query, then
 * filters the date range and sorts in plain Kotlin afterwards, rather than asking
 * Firestore to do both in one query. Firestore CAN combine an equality filter
 * (childId) with a range filter (date BETWEEN) in one query, but that combination
 * needs a composite index to be created first in the Firebase console - the app would
 * throw an error the very first time this ran, with a link in the error message to
 * create that index. Filtering the (small, per-child) date range in Kotlin instead
 * avoids that trap entirely for an app this size, at basically no performance cost
 * (Google, n.d.).
 *
 * getTotalsPerSavingsCategory() used to be one SQL query that JOINed the expenses and
 * savings_categories tables directly. Firestore has no JOIN operation at all, so this
 * is rebuilt as two separate reads (this child's categories, then this child's
 * expenses in range) followed by grouping/summing in Kotlin - the same LEFT JOIN
 * behaviour (every category appears even with R0.00 spent) falls out naturally here
 * because categories.map {} always produces one row per category, and sumOf {} on an
 * empty list of matching expenses already returns 0.0.
 *
 * savingsCategoryRepository is passed in through the constructor rather than created
 * here (constructor/dependency injection) so this class doesn't need to know how a
 * SavingsCategoryRepository is built - the same single-responsibility reasoning
 * behind keeping each repository focused on one Firestore collection (Android
 * Developers, n.d.).
 */
class ExpenseRepository(private val savingsCategoryRepository: SavingsCategoryRepository) {

    private val collection = FirebaseFirestore.getInstance().collection("expenses")

    private fun Expense.toMap(): Map<String, Any?> = mapOf(
        "expenseId" to expenseId,
        "childId" to childId,
        "savingsCategoryId" to savingsCategoryId,
        "amount" to amount,
        "date" to date,
        "description" to description,
        "photoUri" to photoUri
    )

    private fun docToExpense(doc: DocumentSnapshot): Expense? {
        val id = doc.getLong("expenseId") ?: return null
        val childId = doc.getLong("childId") ?: return null
        val savingsCategoryId = doc.getLong("savingsCategoryId") ?: return null
        val amount = doc.getDouble("amount") ?: return null
        val date = doc.getLong("date") ?: return null
        val description = doc.getString("description") ?: ""
        val photoUri = doc.getString("photoUri") // stays null if the field is absent
        return Expense(
            expenseId = id,
            childId = childId,
            savingsCategoryId = savingsCategoryId,
            amount = amount,
            date = date,
            description = description,
            photoUri = photoUri
        )
    }

    suspend fun insert(expense: Expense): Long {
        val id = if (expense.expenseId != 0L) expense.expenseId else FirestoreIds.next()
        val toSave = expense.copy(expenseId = id)
        collection.document(id.toString()).set(toSave.toMap()).await()
        return id
    }

    suspend fun update(expense: Expense): Int {
        collection.document(expense.expenseId.toString()).set(expense.toMap()).await()
        return 1
    }

    suspend fun delete(expense: Expense): Int {
        collection.document(expense.expenseId.toString()).delete().await()
        return 1
    }

    suspend fun getExpensesInRange(childId: Long, startDate: Long, endDate: Long): List<Expense> {
        val results = collection
            .whereEqualTo("childId", childId)
            .get()
            .await()
        return results.documents
            .mapNotNull { docToExpense(it) }
            .filter { it.date in startDate..endDate }
            .sortedByDescending { it.date }
    }

    suspend fun getTotalsPerSavingsCategory(childId: Long, startDate: Long, endDate: Long): List<CategoryTotal> {
        val categories = savingsCategoryRepository.getCategoriesForChild(childId)
        val expensesInRange = getExpensesInRange(childId, startDate, endDate)
        return categories.map { category ->
            val totalSpent = expensesInRange
                .filter { it.savingsCategoryId == category.savingsCategoryId }
                .sumOf { it.amount }
            CategoryTotal(
                categoryId = category.savingsCategoryId,
                categoryName = category.name,
                totalSpent = totalSpent
            )
        }
    }
}

// References:
// Google, n.d. Index types for Cloud Firestore queries [Webpage]. Available at:
//     <https://firebase.google.com/docs/firestore/query-data/index-overview>
//     [Accessed 25 October 2026].
// Android Developers, n.d. Dependency injection on Android [Webpage]. Available at:
//     <https://developer.android.com/training/dependency-injection>
//     [Accessed 25 October 2026].
