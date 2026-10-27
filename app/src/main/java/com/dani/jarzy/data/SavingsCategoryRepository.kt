package com.dani.jarzy.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * SavingsCategoryRepository is the Firestore replacement for the old
 * SavingsCategoryDao - same method names, parameters and return types as before. See
 * ParentRepository's class comment for why fields are stored in a top-level
 * collection keyed by id.toString(), and why fields are read/written by name
 * explicitly instead of Firestore's toObject().
 */
class SavingsCategoryRepository {

    private val collection = FirebaseFirestore.getInstance().collection("savingsCategories")

    private fun SavingsCategory.toMap(): Map<String, Any?> = mapOf(
        "savingsCategoryId" to savingsCategoryId,
        "childId" to childId,
        "name" to name,
        "amountSaved" to amountSaved,
        "budgetSize" to budgetSize
    )

    private fun docToCategory(doc: DocumentSnapshot): SavingsCategory? {
        val id = doc.getLong("savingsCategoryId") ?: return null
        val childId = doc.getLong("childId") ?: return null
        val name = doc.getString("name") ?: return null
        val amountSaved = doc.getDouble("amountSaved") ?: 0.0
        val budgetSize = doc.getDouble("budgetSize") ?: 0.0
        return SavingsCategory(
            savingsCategoryId = id,
            childId = childId,
            name = name,
            amountSaved = amountSaved,
            budgetSize = budgetSize
        )
    }

    suspend fun insert(savingsCategory: SavingsCategory): Long {
        val id = if (savingsCategory.savingsCategoryId != 0L) savingsCategory.savingsCategoryId else FirestoreIds.next()
        val toSave = savingsCategory.copy(savingsCategoryId = id)
        collection.document(id.toString()).set(toSave.toMap()).await()
        return id
    }

    suspend fun update(savingsCategory: SavingsCategory): Int {
        collection.document(savingsCategory.savingsCategoryId.toString()).set(savingsCategory.toMap()).await()
        return 1
    }

    suspend fun delete(savingsCategory: SavingsCategory): Int {
        collection.document(savingsCategory.savingsCategoryId.toString()).delete().await()
        return 1
    }

    suspend fun getCategoriesForChild(childId: Long): List<SavingsCategory> {
        val results = collection
            .whereEqualTo("childId", childId)
            .get()
            .await()
        return results.documents.mapNotNull { docToCategory(it) }
    }

    suspend fun getById(savingsCategoryId: Long): SavingsCategory? {
        val doc = collection.document(savingsCategoryId.toString()).get().await()
        return if (doc.exists()) docToCategory(doc) else null
    }
}

// References:
// Google, n.d.-a. Add data to Cloud Firestore [Webpage]. Available at:
//     <https://firebase.google.com/docs/firestore/manage-data/add-data>
//     [Accessed 27 October 2026].
// Google, n.d.-b. Perform simple and compound queries in Cloud Firestore [Webpage].
//     Available at: <https://firebase.google.com/docs/firestore/query-data/queries>
//     [Accessed 27 October 2026].
// Kotlin, n.d. kotlinx-coroutines-play-services [Webpage]. Available at:
//     <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-play-services/>
//     [Accessed 27 October 2026].
