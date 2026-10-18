package com.dani.jarzy.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// A SavingsCategory is a goal the CHILD sets up to break down their own savings balance
// this is separate table from the shared expense Category
// SavingsCategory belongs to exactly one child via childId,
// only that child can create, edit or add money to it
// the parent can view it but not change it.
// Android Developers (n.d.) explains how @Entity classes like this map to database tables.

@Entity(
    tableName = "savings_categories",
    indices = [Index(value = ["childId"])], // quickly find all savings categories for this child
    foreignKeys = [
        ForeignKey(
            entity = ChildAccount::class,
            parentColumns = ["childId"],
            childColumns = ["childId"],
            onDelete = ForeignKey.CASCADE // delete child = delete their savings categories
        )
    ]
)
data class SavingsCategory(
    @PrimaryKey(autoGenerate = true) val savingsCategoryId: Long = 0,
    val childId: Long, // which child this savings goal belongs to
    val name: String,
    val amountSaved: Double = 0.0, // how much of the child's total savings is set aside for this goal

    // Optional monthly spending budget for this category, set by the PARENT only (same
    // permission split the app already uses for amountSaved - a child can rename a
    // category but never touch the money side of it). 0.0 means "no budget set" - that
    // category is just treated as a plain savings pot with no overspending check at all,
    // which keeps every category created before this field existed working exactly as
    // they did (Room just fills new columns with their default on the version bump -
    // Android Developers, n.d.). When it IS set above 0, AddExpenseActivity compares this
    // child's total spending against it for the current calendar month and warns (but
    // does not block) once an expense pushes them over it.
    val budgetSize: Double = 0.0
)

// References:
// Android Developers, 2026. Define data using Room entities [Webpage].
// Available at: <https://developer.android.com/training/data-storage/room/defining-data>
// [Accessed 14 September 2026].
