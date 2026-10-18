package com.dani.jarzy

import com.dani.jarzy.data.Quest
import com.dani.jarzy.data.QuestType
import java.util.Calendar

// QuestScheduler holds every bit of "is this quest due today, and has its tick expired"
// logic in one place, instead of copy-pasting it into both ChildHomeActivity (the child's
// checklist) and ChildDetailActivity (the parent's progress tracker) - one source of
// truth for how quests behave, the same reasoning behind every other shared
// helper/adapter class in this project (e.g. CategoryCubeAdapter).
object QuestScheduler {

    // Zeroes out the time-of-day fields on a COPY of the given Calendar, so two Calendars
    // on the same day always compare equal regardless of what time it currently is - the
    // same pattern AddExpenseActivity already uses for its future-date check
    // (Android Developers, n.d.-a).
    fun midnightOf(calendar: Calendar): Long {
        val midnight = calendar.clone() as Calendar
        midnight.set(Calendar.HOUR_OF_DAY, 0)
        midnight.set(Calendar.MINUTE, 0)
        midnight.set(Calendar.SECOND, 0)
        midnight.set(Calendar.MILLISECOND, 0)
        return midnight.timeInMillis
    }

    private fun isWithinDateRange(quest: Quest, todayMidnight: Long): Boolean {
        val afterStart = todayMidnight >= quest.startDate
        val beforeEnd = quest.endDate == null || todayMidnight <= quest.endDate
        return afterStart && beforeEnd
    }

    // The midnight timestamp of "the period this quest is currently in": today itself for
    // a ONCE_OFF or DAILY quest, or the most recent day on/before today whose day-of-week
    // matches weeklyDayOfWeek for a WEEKLY one - e.g. a Thursday quest checked on a
    // Saturday still belongs to that same Thursday's period until the following Thursday
    // rolls around. Walking backwards one day at a time is simpler to follow than working
    // out the gap with modulo arithmetic, and this only ever loops at most 6 times
    // (Android Developers, n.d.-b).
    fun currentPeriodStart(quest: Quest, today: Calendar): Long {
        val cursor = today.clone() as Calendar
        cursor.set(Calendar.HOUR_OF_DAY, 0)
        cursor.set(Calendar.MINUTE, 0)
        cursor.set(Calendar.SECOND, 0)
        cursor.set(Calendar.MILLISECOND, 0)

        if (QuestType.fromDb(quest.type) == QuestType.WEEKLY) {
            val targetDay = quest.weeklyDayOfWeek ?: cursor.get(Calendar.DAY_OF_WEEK)
            while (cursor.get(Calendar.DAY_OF_WEEK) != targetDay) {
                cursor.add(Calendar.DAY_OF_MONTH, -1)
            }
        }

        return cursor.timeInMillis
    }

    // Whether this quest should show up in the child's checklist (and count towards the
    // 5-quests-a-day cap) today specifically:
    //   ONCE_OFF  - showing right up until it's been paid, then it drops off for good
    //   DAILY     - showing every day it's within its start/end date range
    //   WEEKLY    - showing only on the one day of the week it's assigned to
    fun isActiveToday(quest: Quest, today: Calendar): Boolean {
        val todayMidnight = midnightOf(today)
        if (!isWithinDateRange(quest, todayMidnight)) return false

        return when (QuestType.fromDb(quest.type)) {
            QuestType.ONCE_OFF -> !quest.isPaid
            QuestType.DAILY -> true
            QuestType.WEEKLY -> today.get(Calendar.DAY_OF_WEEK) == quest.weeklyDayOfWeek
        }
    }

    // Clears a quest's tick (and paid flag) back to unchecked once its current period has
    // passed - a daily quest at the start of a new day, or a weekly quest once its day has
    // come and gone - whether or not it was ever confirmed, exactly as agreed: an
    // unconfirmed chore is simply lost for that period rather than carrying over. Call
    // this on every quest before it's shown or acted on. ONCE_OFF quests are untouched -
    // they only ever change when the child checks them or the parent pays them.
    fun resetIfNeeded(quest: Quest, today: Calendar): Quest {
        if (QuestType.fromDb(quest.type) == QuestType.ONCE_OFF) return quest

        val periodStart = currentPeriodStart(quest, today)
        return if (quest.lastResetDate != periodStart) {
            quest.copy(isChecked = false, isPaid = false, lastResetDate = periodStart)
        } else {
            quest
        }
    }
}

// References:
// Android Developers, n.d.-a. Calendar [Webpage]. Available at:
// <https://developer.android.com/reference/java/util/Calendar> [Accessed 18 October 2026].
// Android Developers, n.d.-b. Calendar.add(int, int) [Webpage]. Available at:
// <https://developer.android.com/reference/java/util/Calendar#add(int,%20int)>
// [Accessed 18 October 2026].
