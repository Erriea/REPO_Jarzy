package com.dani.jarzy.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// JarzyDatabase is the single Room database for the whole app.
// lists every table it contains and a version number Room uses to detect schema changes.
// Android Developers (n.d.) and Ranju (n.d.) both walk through setting up this same @Database class.

@Database(
    entities = [ParentAccount::class, ChildAccount::class, Category::class, Expense::class],
    version = 1,
    exportSchema = false
)
abstract class JarzyDatabase : RoomDatabase() {

    // one abstract function per DAO
    // Room implements these automatically giving rest of the app a way to reach each table's operations
    abstract fun parentDao(): ParentDao
    abstract fun childDao(): ChildDao
    abstract fun categoryDao(): CategoryDao
    abstract fun expenseDao(): ExpenseDao

    companion object {
        // @Volatile makes writes to instance visible to every thread immediately
        // two threads can't each end up building their own separate copy of the database
        @Volatile
        private var INSTANCE: JarzyDatabase? = null

        // Singleton pattern - only one instance of the database is ever open (Android Developers, n.d.)
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

// References:
// Android Developers, n.d. Save data in a local database using Room [Webpage].
// Available at: <https://developer.android.com/training/data-storage/room>
// [Accessed 12 September 2026].
// Ranju, S., n.d. Step-by-Step: Setting Up and Implementing Room Database in Android
// [Webpage]. Available at:
// <https://medium.com/@sdranju/step-by-step-how-to-setting-up-and-implementing-room-database-aeb211c56702>
// [Accessed 12 September 2026].
// CodingSTUFF, 2024. The Complete Beginner Guide for Room in Android 2024 | Local
// Database Tutorial for Android - Part 1 [Video].
// Available at: <https://www.youtube.com/watch?v=r_UfOz3yaLg> [Accessed 12 September 2026].