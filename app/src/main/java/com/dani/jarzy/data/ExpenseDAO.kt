package com.dani.jarzy.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

// ExpenseDao declares every database operation this app needs for the expenses table.
// Android Developers (n.d.-a) explains the general @Dao pattern used throughout this file.

@Dao
interface ExpenseDao {

    // inserts a new expense row and returns the auto-generated expenseId
    @Insert
    suspend fun insert(expense: Expense): Long

    // returns Int (rows affected) instead of Unit
    // the same KSP2 compiler bug explained in ChildDao applies here too (Google, n.d.)
    // Android Developers (n.d.-b) shows the normal @Update pattern this is adapted from.
    @Update
    suspend fun update(expense: Expense): Int

    @Delete
    suspend fun delete(expense: Expense): Int

    // used by the (future) expense-list screen to show all of one child's spending
    // within a chosen date range, most recent first
    @Query("SELECT * FROM expenses WHERE childId = :childId AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    suspend fun getExpensesInRange(childId: Long, startDate: Long, endDate: Long): List<Expense>

    // categories come from the parent (shared across children)
    // amount spent is filtered down to just one child's expenses in date range
    @Query("""
        SELECT c.categoryId AS categoryId, c.name AS categoryName, IFNULL(SUM(e.amount), 0.0) AS totalSpent
        FROM categories c
        LEFT JOIN expenses e ON e.categoryId = c.categoryId AND e.childId = :childId AND e.date BETWEEN :startDate AND :endDate
        WHERE c.parentId = :parentId
        GROUP BY c.categoryId
    """)
    suspend fun getTotalsPerCategory(parentId: Long, childId: Long, startDate: Long, endDate: Long): List<CategoryTotal>
}

// References:
// Android Developers, n.d.-a. Access data using Room DAOs [Webpage].
// Available at: <https://developer.android.com/training/data-storage/room/accessing-data>
// [Accessed 12 September 2026].
// Android Developers, n.d.-b. Read and update data with Room [Webpage].
// Available at: <https://developer.android.com/codelabs/basic-android-kotlin-compose-update-data-room#0>
// [Accessed 12 September 2026].
// Google, n.d. The error 'unexpected jvm signature V' is a known bug in KSP2,
// GitHub Issue #2957 [Webpage].
// Available at: <https://github.com/google/ksp/issues/2957> [Accessed 12 September 2026].
// SQLite Tutorial, n.d. SQLite Left Join [Webpage].
// Available at: <https://www.sqlitetutorial.net/sqlite-left-join/> [Accessed 12 September 2026].