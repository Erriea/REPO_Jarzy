package com.dani.jarzy.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

// ExpenseDao declares every database operation this app needs for the expenses table.
// Android Developers (n.d.) explains the general @Dao pattern used throughout this file.

@Dao
interface ExpenseDao {

    @Insert
    suspend fun insert(expense: Expense): Long

    // returns Int instead of Unit
    // the same KSP2 compiler bug explained in ChildDao applies here too (Google, n.d.)
    // Android Developers (n.d.-b) shows the normal @Update pattern this is adapted from.
    @Update
    suspend fun update(expense: Expense): Int

    @Delete
    suspend fun delete(expense: Expense): Int

    // used by the expense-history screens to show all of one child's spending within a chosen date range
    @Query("SELECT * FROM expenses WHERE childId = :childId AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    suspend fun getExpensesInRange(childId: Long, startDate: Long, endDate: Long): List<Expense>

    // Totals are grouped by the parent-shared list savings categories
    //spending draws down directly from those categories (SQLite Tutorial, n.d.).
    @Query("""
        SELECT sc.savingsCategoryId AS categoryId, sc.name AS categoryName, IFNULL(SUM(e.amount), 0.0) AS totalSpent
        FROM savings_categories sc
        LEFT JOIN expenses e ON e.savingsCategoryId = sc.savingsCategoryId AND e.date BETWEEN :startDate AND :endDate
        WHERE sc.childId = :childId
        GROUP BY sc.savingsCategoryId
    """)
    suspend fun getTotalsPerSavingsCategory(childId: Long, startDate: Long, endDate: Long): List<CategoryTotal>
}

// References:
// Android Developers, 2026.-a. Access data using Room DAOs [Webpage].
// Available at: <https://developer.android.com/training/data-storage/room/accessing-data>
// [Accessed 15 September 2026].

// Android Developers, 2024.-b. Read and update data with Room [Webpage].
// Available at: <https://developer.android.com/codelabs/basic-android-kotlin-compose-update-data-room#0>
// [Accessed 15 September 2026].

// Google, 2026. The error 'unexpected jvm signature V' is a known bug in KSP2,
// GitHub Issue #2957 [Webpage].
// Available at: <https://github.com/google/ksp/issues/2957> [Accessed 15 September 2026].

// SQLite Tutorial, n.d. SQLite Left Join [Webpage].
// Available at: <https://www.sqlitetutorial.net/sqlite-left-join/> [Accessed 15 September 2026].