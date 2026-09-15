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
    val amountSaved: Double = 0.0 // how much of the child's total savings is set aside for this goal
)

// References:
// Android Developers, n.d. Define data using Room entities [Webpage].
// Available at: <https://developer.android.com/training/data-storage/room/defining-data>
// [Accessed 14 September 2026].