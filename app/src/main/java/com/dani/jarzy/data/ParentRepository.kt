package com.dani.jarzy.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * ParentRepository is the Firestore replacement for the old ParentDao - every method
 * below has the exact same name, parameters and return type the DAO version had, so
 * every screen that used to call database.parentDao() only has to swap that one
 * reference; nothing else about how it's called changes.
 *
 * Every parent account is stored as one document in Firestore's top-level "parents"
 * collection, with the document's own id set to parentId.toString() - that way a
 * parent can always be looked up directly by id with collection.document(id), the
 * same O(1) lookup Room's @PrimaryKey gave us, instead of needing a query (Google,
 * n.d.-a).
 *
 * Fields are read and written by name explicitly (toMap() / docToParent() below)
 * rather than using Firestore's automatic toObject() mapping. This is a deliberate
 * choice kept consistent across every repository in this project - see the comment on
 * QuestRepository's docToQuest() for the specific bug with Kotlin "is..." boolean
 * properties that this avoids.
 *
 * await() turns Firestore's callback-based Task into a suspend function this class
 * can just return from normally, the same way Room's suspend DAO functions already
 * worked - it comes from the kotlinx-coroutines-play-services library added alongside
 * Firestore (Kotlin, n.d.).
 */
class ParentRepository {

    private val collection = FirebaseFirestore.getInstance().collection("parents")

    private fun ParentAccount.toMap(): Map<String, Any?> = mapOf(
        "parentId" to parentId,
        "username" to username,
        "password" to password
    )

    // Returns null (rather than throwing) if a document is missing an expected field -
    // defensive, same spirit as every null-check already used throughout this app's
    // Activities, just applied to data coming back from the network instead of a form.
    private fun docToParent(doc: DocumentSnapshot): ParentAccount? {
        val id = doc.getLong("parentId") ?: return null
        val username = doc.getString("username") ?: return null
        val password = doc.getString("password") ?: return null
        return ParentAccount(parentId = id, username = username, password = password)
    }

    // Mirrors ParentDao.insert(): saves a new parent and returns its id. A fresh id is
    // only generated when the caller hasn't already set one (parentId == 0, the
    // ParentAccount data class default) - update-style callers that already know their
    // id can reuse this same function safely.
    suspend fun insert(parent: ParentAccount): Long {
        val id = if (parent.parentId != 0L) parent.parentId else FirestoreIds.next()
        val toSave = parent.copy(parentId = id)
        collection.document(id.toString()).set(toSave.toMap()).await()
        return id
    }

    suspend fun login(username: String, password: String): ParentAccount? {
        val results = collection
            .whereEqualTo("username", username)
            .whereEqualTo("password", password)
            .limit(1)
            .get()
            .await()
        return results.documents.firstOrNull()?.let { docToParent(it) }
    }

    suspend fun getByUsername(username: String): ParentAccount? {
        val results = collection
            .whereEqualTo("username", username)
            .limit(1)
            .get()
            .await()
        return results.documents.firstOrNull()?.let { docToParent(it) }
    }
}

// References:
// Google, n.d.-a. Add data to Cloud Firestore [Webpage]. Available at:
//     <https://firebase.google.com/docs/firestore/manage-data/add-data>
//     [Accessed 25 October 2026].
// Google, n.d.-b. Perform simple and compound queries in Cloud Firestore [Webpage].
//     Available at: <https://firebase.google.com/docs/firestore/query-data/queries>
//     [Accessed 25 October 2026].
// Kotlin, n.d. kotlinx-coroutines-play-services [Webpage]. Available at:
//     <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-play-services/>
//     [Accessed 25 October 2026].
