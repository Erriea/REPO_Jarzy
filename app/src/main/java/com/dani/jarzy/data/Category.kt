package com.dani.jarzy.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// A spending category shared by ALL of a parent's children with own monthly budget.
// It links to the parent rather than a single child
// Makes the sharing possible.
// Android Developers (n.d.) covers this same @Entity/foreign-key structure.
@Entity(
    tableName = "categories",
    indices = [Index(value = ["parentId"])], // speeds up finding all categories for a parent
    foreignKeys = [
        ForeignKey(
            entity = ParentAccount::class,
            parentColumns = ["parentId"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE // if the parent account is deleted -> delete their categories too
        )
    ]
)
data class Category(
    @PrimaryKey(autoGenerate = true)
    val categoryId: Long = 0,
    val parentId: Long, // links this category to the parent account shared by all their children
    val name: String,
    val monthlyBudgetAmount: Double = 0.0
)

// References:
// Android Developers, n.d. Define data using Room entities [Webpage].
// Available at: <https://developer.android.com/training/data-storage/room/defining-data>
// [Accessed 10 September 2026].