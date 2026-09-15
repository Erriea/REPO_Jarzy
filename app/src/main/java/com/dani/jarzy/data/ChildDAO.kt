package com.dani.jarzy.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

// ChildDao declares every database operation this app needs for the child_accounts table.
// Android Developers (n.d.-a) explains the general @Dao pattern used here.

@Dao
interface ChildDao {

    // inserts a new child row and returns the auto-generated childId
    @Insert
    suspend fun insert(child: ChildAccount): Long

    // Update and Delete return Int (the number of rows affected) instead of the more
    // natural Unit.
    // Android Developers (n.d.-b) shows the normal @Update pattern
    // KSP2 (the compiler plugin Room uses to generate code)
    // has a confirmed bug that crashes on suspend functions returning Unit specifically.
    // Google (n.d.) documents this bug, and returning Int instead avoids it entirely.
    @Update
    suspend fun update(child: ChildAccount): Int

    @Delete
    suspend fun delete(child: ChildAccount): Int

    // used by the login screen
    // returns the matching child or null if the username/password combination doesn't exist
    @Query("SELECT * FROM child_accounts WHERE username = :username AND password = :password LIMIT 1")
    suspend fun login(username: String, password: String): ChildAccount?

    // used during registration to check whether a username is already taken
    @Query("SELECT * FROM child_accounts WHERE username = :username LIMIT 1")
    suspend fun getByUsername(username: String): ChildAccount?

    // used on the parent's home screen to populate the "which child?" dropdown
    @Query("SELECT * FROM child_accounts WHERE parentId = :parentId")
    suspend fun getChildrenForParent(parentId: Long): List<ChildAccount>

    // gets full ChildAccount row, including the current savingsBalance
    @Query("SELECT * FROM child_accounts WHERE childId = :childId LIMIT 1")
    suspend fun getById(childId: Long): ChildAccount?
}

// References:
// Android Developers, n.d.-a. Access data using Room DAOs [Webpage].
// Available at: <https://developer.android.com/training/data-storage/room/accessing-data>
// [Accessed 10 September 2026].
// Android Developers, n.d.-b. Read and update data with Room [Webpage].
// Available at: <https://developer.android.com/codelabs/basic-android-kotlin-compose-update-data-room#0>
// [Accessed 10 September 2026].
// Google, n.d. The error 'unexpected jvm signature V' is a known bug in KSP2,
// GitHub Issue #2957 [Webpage].
// Available at: <https://github.com/google/ksp/issues/2957> [Accessed 11 September 2026].