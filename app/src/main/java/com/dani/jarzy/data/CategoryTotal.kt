package com.dani.jarzy.data

// CategoryTotal is NOT a database table
// plain Kotlin data class used only to hold the result of ExpenseDao.getTotalsPerCategory()
// one row per category, summed up by SQL, with property names Room can match to the query's column aliases
// (categoryId, categoryName, totalSpent).
data class CategoryTotal(
    val categoryId: Long,
    val categoryName: String,
    val totalSpent: Double
)

// No references - this file only uses plain Kotlin, nothing here is adapted from an external source.