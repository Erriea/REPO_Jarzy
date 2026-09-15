package com.dani.jarzy.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

// ParentDao (Data Access Object) declares every database operation this app needs for the parent_accounts table
// Room writes the actual SQL implementation at compile time based on these method signatures and annotations
// Android Developers (n.d.) explains this @Dao pattern.

@Dao //interfaces where needed database operations declared
interface ParentDao {

    // inserts a new parent row and returns the auto-generated parentId
    @Insert
    suspend fun insert(parent: ParentAccount): Long

    // used by the login screen, returns the matching parent / null if
    // username/password combination doesn't exist
    @Query("SELECT * FROM parent_accounts WHERE username = :username AND password = :password LIMIT 1")
    suspend fun login(username: String, password: String): ParentAccount?

    @Query("SELECT * FROM parent_accounts WHERE username = :username LIMIT 1")
    suspend fun getByUsername(username: String): ParentAccount?
}

// References:
// Android Developers, n.d. Access data using Room DAOs [Webpage].
// Available at: <https://developer.android.com/training/data-storage/room/accessing-data>
// [Accessed 12 September 2026].