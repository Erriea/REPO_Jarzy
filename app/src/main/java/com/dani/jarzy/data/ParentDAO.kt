package com.dani.jarzy.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao //interfaces where needed database operations declared
interface ParentDao {

    @Insert
    suspend fun insert(parent: ParentAccount): Long

    @Query("SELECT * FROM parent_accounts WHERE username = :username AND password = :password LIMIT 1")
    suspend fun login(username: String, password: String): ParentAccount?

    @Query("SELECT * FROM parent_accounts WHERE username = :username LIMIT 1")
    suspend fun getByUsername(username: String): ParentAccount?
}