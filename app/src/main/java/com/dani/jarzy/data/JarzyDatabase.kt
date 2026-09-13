package com.dani.jarzy.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ParentAccount::class, ChildAccount::class, Category::class, Expense::class],
    version = 1,
    exportSchema = false
)
abstract class JarzyDatabase : RoomDatabase() {

    abstract fun parentDao(): ParentDao
    abstract fun childDao(): ChildDao
    abstract fun categoryDao(): CategoryDao
    abstract fun expenseDao(): ExpenseDao

    companion object {
        @Volatile
        private var INSTANCE: JarzyDatabase? = null

        fun getDatabase(context: Context): JarzyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JarzyDatabase::class.java,
                    "jarzy_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}