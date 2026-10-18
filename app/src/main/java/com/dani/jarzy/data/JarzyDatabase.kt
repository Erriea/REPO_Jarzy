package com.dani.jarzy.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// JarzyDatabase is the single Room database for the whole app.
// lists every table it contains and a version number Room uses to detect schema changes.
// Android Developers (n.d.) and Ranju (n.d.) both walk through setting up this same @Database class.

@Database(
    entities = [ParentAccount::class, ChildAccount::class, Expense::class, SavingsCategory::class, Quest::class],
    version = 3, // bumped from 2 - SavingsCategory gained budgetSize (per-category monthly
                 // budget, used for the overspending warning) and the new Quest entity/
                 // table was added for the chore system. fallbackToDestructiveMigration()
                 // below just wipes and recreates the local database on a version bump
                 // rather than needing a hand-written Migration object - fine for this
                 // stage of development where there's no real user data to preserve.
    exportSchema = false
)
abstract class JarzyDatabase : RoomDatabase() {

    abstract fun parentDao(): ParentDao
    abstract fun childDao(): ChildDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun savingsCategoryDao(): SavingsCategoryDao
    abstract fun questDao(): QuestDao

    companion object {
        @Volatile
        private var INSTANCE: JarzyDatabase? = null

        fun getDatabase(context: Context): JarzyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JarzyDatabase::class.java,
                    "jarzy_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// References:
// Android Developers, 2026. Save data in a local database using Room [Webpage].
// Available at: <https://developer.android.com/training/data-storage/room>
// [Accessed 12 September 2026].

// Ranju, S., 2024. Step-by-Step: Setting Up and Implementing Room Database in Android
// [Webpage]. Available at:
// <https://medium.com/@sdranju/step-by-step-how-to-setting-up-and-implementing-room-database-aeb211c56702>
// [Accessed 12 September 2026].

// CodingSTUFF, 2024. The Complete Beginner Guide for Room in Android 2024 | Local
// Database Tutorial for Android - Part 1 [Video].
// Available at: <https://www.youtube.com/watch?v=r_UfOz3yaLg> [Accessed 12 September 2026].
