package com.dani.jarzy.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

//Room entity representing one child's login, allowance and spending limits.
// Android Developers (n.d.) explains how @Entity classes map to tables, and the same
// idea is walked through step by step in CodingSTUFF's (2024) Room tutorial.

@Entity(
    tableName = "child_accounts",
    indices = [
        Index(value = ["username"], unique = true), // no two children share username
        Index(value = ["parentId"]) //find all children for this parent
    ],
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
    val username: String, //unique
    val password: String, //stored as plain text for now
    val monthlyAllowance: Double = 0.0, // how much the parent gives this child each month
    val minMonthlySpend: Double = 0.0, // lower bound of the child's monthly spending
    val maxMonthlySpend: Double = 0.0 //upper bound of the child's monthly spending
)

// References:
// Android Developers, 2026.-a. Access data using Room DAOs [Webpage].
// Available at: <https://developer.android.com/training/data-storage/room/accessing-data>
// [Accessed 10 September 2026].

// Android Developers, 2024.-b. Read and update data with Room [Webpage].
// Available at: <https://developer.android.com/codelabs/basic-android-kotlin-compose-update-data-room#0>
// [Accessed 10 September 2026].

// Google, 2026. The error 'unexpected jvm signature V' is a known bug in KSP2,
// GitHub Issue #2957 [Webpage].
// Available at: <https://github.com/google/ksp/issues/2957> [Accessed 11 September 2026].

