package com.dani.jarzy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.dani.jarzy.data.Quest
import com.dani.jarzy.data.QuestType
import java.util.Calendar

/**
 * Backs the quest checklist shown on both ChildHomeActivity and ChildDetailActivity -
 * one scrollable list of rows instead of a swipeable carousel like CategoryCubeAdapter,
 * since quests make more sense read top-to-bottom than flicked through one at a time.
 * The same adapter class drives both screens, just constructed with a different [mode]:
 *
 *   Mode.CHILD  - the checkbox is tappable (ticks a quest off, doesn't pay it) and the
 *                 Delete button/edit-on-tap are hidden, since a child can mark a quest
 *                 done but never create, change or remove one.
 *   Mode.PARENT - the checkbox is shown read-only (reflects whether the child has ticked
 *                 it), and tapping a row opens it for editing while the Delete button
 *                 removes it outright.
 *
 * This mirrors how CategoryCubeAdapter is already shared between the parent and child
 * screens rather than writing two near-identical adapters (Android Developers, n.d.-a).
 */
class QuestAdapter(
    private var quests: List<Quest>,
    private val mode: Mode,
    private val onCheckToggled: (Quest, Boolean) -> Unit = { _, _ -> },
    private val onEditRequested: (Quest) -> Unit = {},
    private val onDeleteRequested: (Quest) -> Unit = {}
) : RecyclerView.Adapter<QuestAdapter.QuestViewHolder>() {

    enum class Mode { CHILD, PARENT }

    class QuestViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox: CheckBox = view.findViewById(R.id.cbQuestDone)
        val name: TextView = view.findViewById(R.id.tvQuestName)
        val meta: TextView = view.findViewById(R.id.tvQuestMeta)
        val deleteButton: Button = view.findViewById(R.id.btnQuestDelete)
    }

    // Swaps in a freshly-loaded/reset quest list and redraws every visible row - the same
    // "reload then redisplay" pattern every other adapter/refresh function in this app uses.
    fun updateQuests(newQuests: List<Quest>) {
        quests = newQuests
        notifyDataSetChanged()
    }

    fun questAt(position: Int): Quest? = quests.getOrNull(position)

    override fun getItemCount(): Int = quests.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuestViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_quest, parent, false)
        return QuestViewHolder(view)
    }

    override fun onBindViewHolder(holder: QuestViewHolder, position: Int) {
        val quest = quests[position]

        holder.name.text = quest.name
        holder.meta.text = metaLabel(quest)

        // The listener has to be cleared before setting isChecked here, otherwise setting
        // the checkbox's state to match the database would itself fire the listener and
        // be mistaken for the user tapping it (Android Developers, n.d.-b).
        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.isChecked = quest.isChecked
        holder.checkbox.isEnabled = mode == Mode.CHILD && !quest.isPaid

        if (mode == Mode.CHILD) {
            holder.checkbox.setOnCheckedChangeListener { _, isChecked -> onCheckToggled(quest, isChecked) }
        }

        if (mode == Mode.PARENT) {
            holder.deleteButton.visibility = View.VISIBLE
            holder.deleteButton.setOnClickListener { onDeleteRequested(quest) }
            holder.itemView.setOnClickListener { onEditRequested(quest) }
        } else {
            holder.deleteButton.visibility = View.GONE
            holder.itemView.setOnClickListener(null)
        }
    }

    // "R15.00 · Daily" / "R20.00 · Every Thursday · Done, waiting on parent" / etc - every
    // detail about a quest other than its name and checkbox, in one line.
    private fun metaLabel(quest: Quest): String {
        val schedule = when (QuestType.fromDb(quest.type)) {
            QuestType.ONCE_OFF -> "Once-off"
            QuestType.DAILY -> "Daily"
            QuestType.WEEKLY -> "Every ${dayName(quest.weeklyDayOfWeek)}"
        }

        val status = when {
            quest.isPaid -> " · Paid"
            quest.isChecked -> " · Done, waiting on parent"
            else -> ""
        }

        return "R%.2f · %s%s".format(quest.reward, schedule, status)
    }

    private fun dayName(day: Int?): String = when (day) {
        Calendar.SUNDAY -> "Sunday"
        Calendar.MONDAY -> "Monday"
        Calendar.TUESDAY -> "Tuesday"
        Calendar.WEDNESDAY -> "Wednesday"
        Calendar.THURSDAY -> "Thursday"
        Calendar.FRIDAY -> "Friday"
        Calendar.SATURDAY -> "Saturday"
        else -> "?"
    }
}

// References:
// Android Developers, n.d.-a. Access data using Room DAOs [Webpage]. Available at:
// <https://developer.android.com/training/data-storage/room/accessing-data>
// [Accessed 18 October 2026].
// Android Developers, n.d.-b. CompoundButton.setOnCheckedChangeListener [Webpage].
// Available at:
// <https://developer.android.com/reference/android/widget/CompoundButton#setOnCheckedChangeListener(android.widget.CompoundButton.OnCheckedChangeListener)>
// [Accessed 18 October 2026].
