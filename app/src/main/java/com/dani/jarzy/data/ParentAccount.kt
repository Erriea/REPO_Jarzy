package com.dani.jarzy.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

//Room "entity"
//a Kotlin data class that Room turns directly into a database table, with one property becoming one column.
//Android Developers (n.d.) and GeeksforGeeks (n.d.) both explain how this @Entity pattern works.
@Entity(
    tableName = "parent_accounts", //table name created in SQLite
    //Indices
    // a unique index on username stops two parents from ever registering the same one
    // Room enforces this at the database level itself so check can't be bypassed elsewhere
    indices = [Index(value = ["username"], unique = true)]
)

//all info for the account
data class ParentAccount(
    @PrimaryKey(autoGenerate = true) //unique ID assigned on insert
    val parentId: Long = 0,
    val username: String, // must br unique
    val password: String // plain text for now
)

// References:
// Android Developers, n.d. Define data using Room entities [Webpage].
// Available at: <https://developer.android.com/training/data-storage/room/defining-data>
// [Accessed 10 September 2026].
// GeeksforGeeks, n.d. Room Database with Kotlin Coroutines in Android [Webpage].
// Available at: <https://www.geeksforgeeks.org/android/room-database-with-kotlin-coroutines-in-android/>
// [Accessed 10 September 2026].