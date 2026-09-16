package com.dani.jarzy.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

// SavingsCategoryDao declares every database operation this app needs for the
// savings_categories table. Same @Dao pattern as every other DAO in this project
// (Android Developers, n.d.-a).
@Dao
interface SavingsCategoryDao {

    @Insert
    suspend fun insert(savingsCategory: SavingsCategory): Long

    // Returns Int instead of Unit, same reason as every other @Update/@Delete in this
    // project - it avoids a confirmed KSP2 compiler crash on suspend functions returning
    // Unit (Google, n.d.).
    @Update
    suspend fun update(savingsCategory: SavingsCategory): Int

    @Delete
    suspend fun delete(savingsCategory: SavingsCategory): Int

    // Used by the child's own home screen (and the transfer/expense screens) to show and
    // total up this one child's savings categories.
    @Query("SELECT * FROM savings_categories WHERE childId = :childId")
    suspend fun getCategoriesForChild(childId: Long): List<SavingsCategory>

    // Used when transferring money between two of a child's categories, or when deducting
    // an expense amount from the specific category it's drawn from - both need to fetch one
    // exact category row by its id first.
    @Query("SELECT * FROM savings_categories WHERE savingsCategoryId = :savingsCategoryId LIMIT 1")
    suspend fun getById(savingsCategoryId: Long): SavingsCategory?
}

// References:
// Android Developers, 2026.-a. Access data using Room DAOs [Webpage].
// Available at: <https://developer.android.com/training/data-storage/room/accessing-data>
// [Accessed 15 September 2026].

// Google, 2026. The error 'unexpected jvm signature V' is a known bug in KSP2,
// GitHub Issue #2957 [Webpage].
// Available at: <https://github.com/google/ksp/issues/2957> [Accessed 15 September 2026].