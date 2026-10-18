package com.dani.jarzy.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

// QuestDao declares every database operation this app needs for the quests table. Same
// @Dao pattern as every other DAO in this project (Android Developers, n.d.-a). Deciding
// which quests are "active today", resetting a finished period, and totalling up a
// confirmed payout are all done in plain Kotlin (see ChildDetailActivity/
// ChildHomeActivity) rather than in SQL here, the same way AddExpenseActivity already
// does its own date-range comparisons in Kotlin instead of pushing that logic into a query.
@Dao
interface QuestDao {

    @Insert
    suspend fun insert(quest: Quest): Long

    // Returns Int instead of Unit - the same KSP2 compiler bug every other @Update/@Delete
    // in this project works around (Google, n.d.).
    @Update
    suspend fun update(quest: Quest): Int

    @Delete
    suspend fun delete(quest: Quest): Int

    // Used by both ChildHomeActivity (the checklist) and ChildDetailActivity (the
    // progress tracker + Add/Edit/Delete Quest) to load every quest belonging to one child.
    @Query("SELECT * FROM quests WHERE childId = :childId")
    suspend fun getQuestsForChild(childId: Long): List<Quest>
}

// References:
// Android Developers, n.d.-a. Access data using Room DAOs [Webpage]. Available at:
// <https://developer.android.com/training/data-storage/room/accessing-data>
// [Accessed 18 October 2026].
// Google, n.d. The error 'unexpected jvm signature V' is a known bug in KSP2, GitHub
// Issue #2957 [Webpage]. Available at: <https://github.com/google/ksp/issues/2957>
// [Accessed 18 October 2026].
