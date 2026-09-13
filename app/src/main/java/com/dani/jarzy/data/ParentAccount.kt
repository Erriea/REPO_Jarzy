package com.dani.jarzy.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

//class is a database table
@Entity(
    tableName = "parent_accounts", //name table
    indices = [Index(value = ["username"], unique = true)] //get unique username
)

//all info for the account
data class ParentAccount(
    @PrimaryKey(autoGenerate = true)
    val parentId: Long = 0,
    val username: String,
    val password: String
)