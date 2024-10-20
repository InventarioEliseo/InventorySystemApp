package com.example.inventorysystem.ui.inventory

import android.graphics.Bitmap

data class ItemInventory(
    val id: String,
    val image: Bitmap,
    val name: String,
    val price: String,
    val size: String,
    val marca: String,
    val category: String,
    val amount: String,
    val quality: String,
    val description: String
)
