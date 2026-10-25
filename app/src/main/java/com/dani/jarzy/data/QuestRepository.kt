package com.dani.jarzy.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * QuestRepository is the Firestore replacement for the old QuestDao - same method
 * names, parameters and return types as before. See ParentRepository's class comment
 * for why fields are stored in a top-level collection keyed by id.toString().
 *
 * Fields are read and written by name explicitly (toMap() / docToQuest() below)
 * rather than relying on Firestore's automatic toObject() mapping, and this is the
 * one repository in the project where that choice actually matters rather than just
 * being a style preference: Firestore's automatic mapping treats a Kotlin property
 * named isChecked as if its "real" name were checked (it strips a leading "is" off
 * boolean property names to match Java bean getter conventions), so toObject() would
 * silently save/load this quest's isChecked and isPaid fields under the wrong field
 * names and break without any error at all (Google, n.d.). Reading/writing every
 * field by its exact String name here sidesteps that entirely.
 */
class QuestRepository {

    private val collection = FirebaseFirestore.getInstance().collection("quests")

    private fun Quest.toMap(): Map<String, Any?> = mapOf(
        "questId" to questId,
        "childId" to childId,
        "name" to name,
        "reward" to reward,
        "type" to type,
        "weeklyDayOfWeek" to weeklyDayOfWeek,
        "startDate" to startDate,
        "endDate" to endDate,
        "isChecked" to isChecked,
        "isPaid" to isPaid,
        "lastResetDate" to lastResetDate
    )

    private fun docToQuest(doc: DocumentSnapshot): Quest? {
        val id = doc.getLong("questId") ?: return null
        val childId = doc.getLong("childId") ?: return null
        val name = doc.getString("name") ?: return null
        val reward = doc.getDouble("reward") ?: return null
        val type = doc.getString("type") ?: return null
        val weeklyDayOfWeek = doc.getLong("weeklyDayOfWeek")?.toInt() // null stays null
        val startDate = doc.getLong("startDate") ?: return null
        val endDate = doc.getLong("endDate") // null stays null
        val isChecked = doc.getBoolean("isChecked") ?: false
        val isPaid = doc.getBoolean("isPaid") ?: false
        val lastResetDate = doc.getLong("lastResetDate") ?: return null
        return Quest(
            questId = id,
            childId = childId,
            name = name,
            reward = reward,
            type = type,
            weeklyDayOfWeek = weeklyDayOfWeek,
            startDate = startDate,
            endDate = endDate,
            isChecked = isChecked,
            isPaid = isPaid,
            lastResetDate = lastResetDate
        )
    }

    suspend fun insert(quest: Quest): Long {
        val id = if (quest.questId != 0L) quest.questId else FirestoreIds.next()
        val toSave = quest.copy(questId = id)
        collection.document(id.toString()).set(toSave.toMap()).await()
        return id
    }

    suspend fun update(quest: Quest): Int {
        collection.document(quest.questId.toString()).set(quest.toMap()).await()
        return 1
    }

    suspend fun delete(quest: Quest): Int {
        collection.document(quest.questId.toString()).delete().await()
        return 1
    }

    suspend fun getQuestsForChild(childId: Long): List<Quest> {
        val results = collection
            .whereEqualTo("childId", childId)
            .get()
            .await()
        return results.documents.mapNotNull { docToQuest(it) }
    }
}

// References:
// Google, n.d. Add data to Cloud Firestore - Custom objects [Webpage]. Available at:
//     <https://firebase.google.com/docs/firestore/manage-data/add-data#custom_objects>
//     [Accessed 25 October 2026].
