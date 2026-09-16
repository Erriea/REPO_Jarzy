package com.dani.jarzy.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

//Expense is a single spending record logged against one child and one of that child's own savings categories
// spending now draws down directly from the category it's recorded
//against, rather than a separate parent-owned category list (Android Developers, n.b)
// Two foreign keys are declared below, both explained by Android Developers (n.d.);
// Ranju (n.d.) also walks through setting up a Room entity with relationships like this.

@Entity(
    tableName = "expenses",
    indices = [
        Index(value = ["childId"]),
        Index(value = ["savingsCategoryId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = ChildAccount::class,
            parentColumns = ["childId"],
            childColumns = ["childId"],
            onDelete = ForeignKey.CASCADE //deleting child deletes their expenses
        ),
        ForeignKey(
            entity = SavingsCategory::class,
            parentColumns = ["savingsCategoryId"],
            childColumns = ["savingsCategoryId"],
            onDelete = ForeignKey.CASCADE //delete category = delete expenses drawn from it
        )
    ]
)
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val expenseId: Long = 0,
    val childId: Long,
    val savingsCategoryId: Long, // which of the child's own savings categories this was spent from
    val amount: Double,
    val date: Long, // stored as a Unix timestamp (milliseconds), not a String, so date-range queries work
    val description: String, // optional stored as "" if blank
    val photoUri: String? = null // nullable. A receipt photo is optional, added in a later step
)

// References:
// Android Developers, n.d. Define data using Room entities [Webpage].
// Available at: <https://developer.android.com/training/data-storage/room/defining-data>
// [Accessed 12 September 2026].
// Ranju, S., n.d. Step-by-Step: Setting Up and Implementing Room Database in Android
// [Webpage]. Available at:
// <https://medium.com/@sdranju/step-by-step-how-to-setting-up-and-implementing-room-database-aeb211c56702>
// [Accessed 10 September 2026].