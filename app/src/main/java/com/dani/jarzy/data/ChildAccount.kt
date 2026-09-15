package com.dani.jarzy.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

//Room entity representing one child's login and spending goals.
// Android Developers (n.d.) explains how @Entity classes map to tables, and the same
// idea is walked through step by step in CodingSTUFF's (2024) Room tutorial.
@Entity(
    tableName = "child_accounts",
    indices = [
        Index(value = ["username"], unique = true), // no two children share username
        Index(value = ["parentId"]) //find all children for this parent
    ],
    // A foreign key enforces link at database level so cant point at non-exisiting parent
    foreignKeys = [
        ForeignKey(
            entity = ParentAccount::class,
            parentColumns = ["parentId"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE // deleting parent also delete child
        )
    ]
)
data class ChildAccount(
    @PrimaryKey(autoGenerate = true)
    val childId: Long = 0,
    val parentId: Long, //links parent to child
    val username: String, //must be unique
    val password: String, //stored as plain text for now
    val minMonthlyGoal: Double = 0.0, // lower bounds of child's monthly spending
    val maxMonthlyGoal: Double = 0.0 //upper bounds of child's monthly spending
)

// References:
// Android Developers, n.d. Define data using Room entities [Webpage].
// Available at: <https://developer.android.com/training/data-storage/room/defining-data>
// [Accessed 10 September 2026].
// CodingSTUFF, 2024. The Complete Beginner Guide for Room in Android 2024 | Local
// Database Tutorial for Android - Part 1 [Video].
// Available at: <https://www.youtube.com/watch?v=r_UfOz3yaLg> [Accessed 10 September 2026].