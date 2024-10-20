package com.example.inventorysystem.ui.reports

data class InventoryItem(
    val product: String,
    val existence: Int,
    val category: String
)

data class SalesItem(
    val month: String,
    val product: String,
    val total: Double
)

data class SalesReportRequest(
    val month1: String,
    val year1: String,
    val month2: String,
    val year2: String
)
