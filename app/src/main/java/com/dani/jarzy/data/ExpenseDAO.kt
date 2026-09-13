package com.dani.jarzy.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface ExpenseDao {

    @Insert
    suspend fun insert(expense: Expense): Long

    @Update
    suspend fun update(expense: Expense): Int

    @Delete
    suspend fun delete(expense: Expense): Int

    @Query("SELECT * FROM expenses WHERE childId = :childId AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    suspend fun getExpensesInRange(childId: Long, startDate: Long, endDate: Long): List<Expense>

    @Query("""
        SELECT c.categoryId AS categoryId, c.name AS categoryName, IFNULL(SUM(e.amount), 0.0) AS totalSpent
        FROM categories c
        LEFT JOIN expenses e ON e.categoryId = c.categoryId AND e.date BETWEEN :startDate AND :endDate
        WHERE c.childId = :childId
        GROUP BY c.categoryId
    """)
    suspend fun getTotalsPerCategory(childId: Long, startDate: Long, endDate: Long): List<CategoryTotal>
}