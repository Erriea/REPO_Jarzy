package com.dani.jarzy.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// A Quest is a parent-created chore for one specific child, with a Rand reward attached.
// Only the parent can create, edit or delete a Quest (ChildDetailActivity) - a child can
// only check one off as done (ChildHomeActivity). Checking a quest off does NOT pay the
// reward straight away: the parent has to confirm every quest due that day is finished
// first (see QuestDao.getActiveQuestsForChild() and the batch-confirm logic in
// ChildDetailActivity), which is what actually moves the reward money into the child's
// General savings category.
//
// type is stored as a plain String rather than a Kotlin enum because Room needs an extra
// TypeConverter class to store enums directly, and nothing else in this project uses one -
// QuestType (see below) is just a type-safe wrapper the rest of the app's Kotlin code uses
// instead of comparing raw strings everywhere (Android Developers, n.d.-a).
@Entity(
    tableName = "quests",
    indices = [Index(value = ["childId"])],
    foreignKeys = [
        ForeignKey(
            entity = ChildAccount::class,
            parentColumns = ["childId"],
            childColumns = ["childId"],
            onDelete = ForeignKey.CASCADE // delete child = delete their quests too
        )
    ]
)
data class Quest(
    @PrimaryKey(autoGenerate = true) val questId: Long = 0,
    val childId: Long,
    val name: String,
    val reward: Double,

    // One of QuestType.ONCE_OFF / DAILY / WEEKLY's .name value - see QuestType below.
    val type: String,

    // Only meaningful when type == WEEKLY. Stores a java.util.Calendar day-of-week
    // constant (Calendar.SUNDAY = 1 ... Calendar.SATURDAY = 7), so "every Thursday" is
    // just Calendar.THURSDAY stored here (Android Developers, n.d.-b). Null for
    // ONCE_OFF/DAILY quests, which don't care which day it is.
    val weeklyDayOfWeek: Int? = null,

    val startDate: Long, // midnight timestamp - quest isn't active before this day
    val endDate: Long? = null, // midnight timestamp, inclusive - null means it never ends

    // Whether the CHILD has ticked this quest off for the period it's currently in
    // (today, for a daily quest; this week's occurrence, for a weekly one). Reset back to
    // false automatically once that period ends, whether or not it was ever confirmed -
    // see resetQuestsIfNeeded() in QuestRepositoryHelpers.kt.
    val isChecked: Boolean = false,

    // Whether the PARENT has already confirmed and paid out this period's reward - stops
    // the same quest being paid twice inside one period.
    val isPaid: Boolean = false,

    // Midnight timestamp of the day this quest's isChecked/isPaid values currently belong
    // to. Compared against "today" (or, for a weekly quest, the most recent occurrence of
    // weeklyDayOfWeek) every time this child's quests are loaded, to decide whether
    // isChecked/isPaid need resetting for a new period.
    val lastResetDate: Long
)

// A type-safe wrapper around Quest.type's raw String, so the rest of the app never
// compares against a hardcoded string directly. fromDb()/toString() are a hand-written
// substitute for a Room TypeConverter (Android Developers, n.d.-a).
enum class QuestType {
    ONCE_OFF,
    DAILY,
    WEEKLY;

    companion object {
        fun fromDb(value: String): QuestType = valueOf(value)
    }
}

// References:
// Android Developers, n.d.-a. Save data using Room - reference a custom type [Webpage].
// Available at: <https://developer.android.com/training/data-storage/room/referencing-data>
// [Accessed 18 October 2026].
// Android Developers, n.d.-b. Calendar [Webpage]. Available at:
// <https://developer.android.com/reference/java/util/Calendar> [Accessed 18 October 2026].
