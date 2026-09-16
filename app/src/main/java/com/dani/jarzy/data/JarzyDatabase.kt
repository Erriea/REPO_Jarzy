package com.dani.jarzy.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// JarzyDatabase is the single Room database for the whole app.
// lists every table it contains and a version number Room uses to detect schema changes.
// Android Developers (n.d.) and Ranju (n.d.) both walk through setting up this same @Database class.

@Database(
    entities = [ParentAccount::class, ChildAccount::class, Expense::class, SavingsCategory::class],
    version = 2, // bumped from 1 - ChildAccount's fields changed (monthlyAllowance added,
                 // minMonthlyGoal/maxMonthlyGoal renamed to minMonthlySpend/maxMonthlySpend)
    exportSchema = false
)
abstract class JarzyDatabase : RoomDatabase() {

    abstract fun parentDao(): ParentDao
    abstract fun childDao(): ChildDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun savingsCategoryDao(): SavingsCategoryDao

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
                    // There's no real user data to preserve in this prototype, and no
                    // migration path has been written for the version 1 -> 2 change above -
                    // without this, Room would crash on launch instead of just rebuilding
                    // the database fresh (Android Developers, n.d.).
                    .fallbackToDestructiveMigration()
                    .build()
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
// Android Developers, n.d. RoomDatabase.Builder [Webpage]. Available at:
//     <https://developer.android.com/reference/kotlin/androidx/room/RoomDatabase.Builder>
//     [Accessed 16 September 2026].
