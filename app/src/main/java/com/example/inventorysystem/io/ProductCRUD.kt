package com.example.inventorysystem.io

data class ProductCreateRequest(
    val nombre: String,
    val precio: Double,
    val tamaño: String,
    val marca: String,
    val categoria: String,
    val cantidad: Int,
    val calidad: Int,
    val descripcion: String,
    val picture: String
)

data class ProductUpdateRequest(
    val nombre: String,
    val precio: Double,
    val tamaño: String,
    val marca: String,
    val categoria: String,
    val cantidad: Int,
    val calidad: Int,
    val descripcion: String,
    val picture: String
)

data class ProductResponse(
    val is_active: Boolean,
    val nombre: String,
    val precio: Double,
    val tamaño: String,
    val marca: String,
    val categoria: String,
    val cantidad: Int,
    val calidad: Int,
    val descripcion: String,
    val picture: String,
    val _id: String,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val __v: Int
)

data class ProductUpdateResponse(
    val acknowledged: Boolean,
    val modifiedCount: Int,
    val upsertedId: String?,
    val upsertedCount: Int,
    val matchedCount: Int
)

data class CategoryFilter(val categoria: String)

data class SaleRequest(
    val nombre: String,
    val cantidad: Int,
    val precio: Double
)

data class SaleResponse(
    val is_active: Boolean,
    val nombre: String,
    val cantidad: Int,
    val precio: Double,
    val _id: String,
    val createdAt: String,
    val updatedAt: String,
    val __v: Int
)

