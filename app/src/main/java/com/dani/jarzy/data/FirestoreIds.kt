package com.dani.jarzy.data

/**
 * FirestoreIds generates the Long ids every entity in this app still uses, now that
 * Firestore has replaced Room as the real data store.
 *
 * Room used to hand these out for us: every @PrimaryKey(autoGenerate = true) column
 * just worked, and insert() returned a brand-new unique Long automatically. Firestore
 * has no equivalent built in - its own auto-generated ids are random Strings, not
 * Longs. Switching every id field in the app over to String (ChildAccount.childId,
 * every Intent.putExtra/getLongExtra call, every adapter, every FK-style field like
 * Expense.childId) would have meant touching almost every file in the project just to
 * change a type, for no real benefit to how the app behaves. Keeping Long ids and
 * generating them ourselves here instead keeps that entire surface area unchanged -
 * every repository below still returns a Long from insert(), exactly like the DAOs
 * they're replacing did.
 *
 * next() returns the current device time in milliseconds (System.currentTimeMillis()),
 * which is already unique enough for one person using this app on one device at a
 * time (Android Developers, n.d.). The check below only guards against two inserts
 * happening inside the exact same millisecond - e.g. the loop in RegisterChildActivity
 * that can create several savings categories one after another - by bumping the id by
 * 1 instead of accidentally reusing it. This is not a "real" distributed id scheme and
 * would not be safe if many devices were generating ids against the same project at
 * once, but that's outside what a single-family budgeting app needs to do.
 */
object FirestoreIds {
    private var lastIssuedId = 0L

    @Synchronized
    fun next(): Long {
        val now = System.currentTimeMillis()
        val id = if (now > lastIssuedId) now else lastIssuedId + 1
        lastIssuedId = id
        return id
    }
}

// References:
// Android Developers, n.d. System.currentTimeMillis() [Webpage]. Available at:
//     <https://developer.android.com/reference/java/lang/System#currentTimeMillis()>
//     [Accessed 25 October 2026].
