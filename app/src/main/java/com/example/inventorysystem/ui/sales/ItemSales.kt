package com.example.inventorysystem.ui.sales

import android.graphics.Bitmap

data class ItemSales(
    val id: String,
    val image: Bitmap?,
    val name: String,
    val price: String,
    val size: String,
    val marca: String,
    val amount: String, // Cantidad disponible
    var selectedQuantity: Int, // Cantidad seleccionada por el usuario
    val category: String,
    val quality: String,
    val description: String
)
