package com.dani.jarzy.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface ChildDao {

    @Insert
    suspend fun insert(child: ChildAccount): Long

    @Update
    suspend fun update(child: ChildAccount): Int

    @Delete
    suspend fun delete(child: ChildAccount): Int

    @Query("SELECT * FROM child_accounts WHERE username = :username AND password = :password LIMIT 1")
    suspend fun login(username: String, password: String): ChildAccount?

    @Query("SELECT * FROM child_accounts WHERE username = :username LIMIT 1")
    suspend fun getByUsername(username: String): ChildAccount?

    @Query("SELECT * FROM child_accounts WHERE parentId = :parentId")
    suspend fun getChildrenForParent(parentId: Long): List<ChildAccount>
}