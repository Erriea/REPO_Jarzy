package com.dani.jarzy.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// class is a database table
@Entity(
    tableName = "child_accounts",
    indices = [
        Index(value = ["username"], unique = true),
        Index(value = ["parentId"])
    ],
    // enforce link at database level so cant point at no exisiting parent
    foreignKeys = [
        ForeignKey(
            entity = ParentAccount::class,
            parentColumns = ["parentId"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ChildAccount(
    @PrimaryKey(autoGenerate = true)
    val childId: Long = 0,
    val parentId: Long, //links parent to child
    val username: String,
    val password: String,
    val minMonthlyGoal: Double = 0.0,
    val maxMonthlyGoal: Double = 0.0
)
