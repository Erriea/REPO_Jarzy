package com.dani.jarzy.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

// CategoryDao declares every database operation this app needs for the shared categories table.
// Android Developers (n.d.-a) explains the general @Dao pattern.
@Dao
interface CategoryDao {

    // inserts a new category row and returns the auto-generated categoryId
    @Insert
    suspend fun insert(category: Category): Long

    // returns Int (rows affected) instead of Unit
    // the same KSP2 compiler bug explained in ChildDao applies here too
    // (Google, n.d.); Android Developers(n.d.-b) shows the normal @Update pattern this is adapted from.
    @Update
    suspend fun update(category: Category): Int

    @Delete
    suspend fun delete(category: Category): Int

    // used to populate the category dropdown on the Add Expense screen
    // checks for duplicate names when creating a new category
    @Query("SELECT * FROM categories WHERE parentId = :parentId")
    suspend fun getCategoriesForParent(parentId: Long): List<Category>
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