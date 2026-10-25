package com.dani.jarzy.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * ChildRepository is the Firestore replacement for the old ChildDao - same method
 * names, parameters and return types as before. See ParentRepository's class comment
 * for why fields are stored in a top-level collection keyed by id.toString(), and why
 * fields are read/written by name explicitly instead of Firestore's toObject().
 */
class ChildRepository {

    private val collection = FirebaseFirestore.getInstance().collection("children")

    private fun ChildAccount.toMap(): Map<String, Any?> = mapOf(
        "childId" to childId,
        "parentId" to parentId,
        "username" to username,
        "password" to password,
        "monthlyAllowance" to monthlyAllowance,
        "minMonthlySpend" to minMonthlySpend,
        "maxMonthlySpend" to maxMonthlySpend
    )

    private fun docToChild(doc: DocumentSnapshot): ChildAccount? {
        val id = doc.getLong("childId") ?: return null
        val parentId = doc.getLong("parentId") ?: return null
        val username = doc.getString("username") ?: return null
        val password = doc.getString("password") ?: return null
        // getDouble() can itself return null if the field is missing - ?: 0.0 falls
        // back to the same default these fields already have in the ChildAccount data
        // class, rather than letting a missing field quietly break the whole read.
        val monthlyAllowance = doc.getDouble("monthlyAllowance") ?: 0.0
        val minMonthlySpend = doc.getDouble("minMonthlySpend") ?: 0.0
        val maxMonthlySpend = doc.getDouble("maxMonthlySpend") ?: 0.0
        return ChildAccount(
            childId = id,
            parentId = parentId,
            username = username,
            password = password,
            monthlyAllowance = monthlyAllowance,
            minMonthlySpend = minMonthlySpend,
            maxMonthlySpend = maxMonthlySpend
        )
    }

    suspend fun insert(child: ChildAccount): Long {
        val id = if (child.childId != 0L) child.childId else FirestoreIds.next()
        val toSave = child.copy(childId = id)
        collection.document(id.toString()).set(toSave.toMap()).await()
        return id
    }

    // Overwrites every field of the matching document with child's current values -
    // the Firestore equivalent of Room's @Update, which always replaces a whole row
    // rather than patching individual columns (Google, n.d.).
    suspend fun update(child: ChildAccount): Int {
        collection.document(child.childId.toString()).set(child.toMap()).await()
        return 1
    }

    suspend fun delete(child: ChildAccount): Int {
        collection.document(child.childId.toString()).delete().await()
        return 1
    }

    suspend fun login(username: String, password: String): ChildAccount? {
        val results = collection
            .whereEqualTo("username", username)
            .whereEqualTo("password", password)
            .limit(1)
            .get()
            .await()
        return results.documents.firstOrNull()?.let { docToChild(it) }
    }

    suspend fun getByUsername(username: String): ChildAccount? {
        val results = collection
            .whereEqualTo("username", username)
            .limit(1)
            .get()
            .await()
        return results.documents.firstOrNull()?.let { docToChild(it) }
    }

    suspend fun getChildrenForParent(parentId: Long): List<ChildAccount> {
        val results = collection
            .whereEqualTo("parentId", parentId)
            .get()
            .await()
        return results.documents.mapNotNull { docToChild(it) }
    }

    suspend fun getById(childId: Long): ChildAccount? {
        val doc = collection.document(childId.toString()).get().await()
        return if (doc.exists()) docToChild(doc) else null
    }
}

// References:
// Google, n.d. Add data to Cloud Firestore [Webpage]. Available at:
//     <https://firebase.google.com/docs/firestore/manage-data/add-data>
//     [Accessed 25 October 2026].
