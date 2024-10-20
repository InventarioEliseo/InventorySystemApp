package com.example.inventorysystem.io

data class UserCreateRequest(
    val name: String,
    val last_name: String,
    val email: String,
    val password: String,
    val sex: String,
    val address: String,
    val phone: Long
)

data class UserResponse(
    val _id : String,
    val is_active: Boolean,
    val name: String,
    val last_name: String,
    val phone: Long,
    val address: String,
    val email: String,
    val sex: String,
    val admin: String,
    val createdAt: String,
    val updatedAt: String,
    val __v: Int
)

data class LoginRequest(
    val email: String,
    val password: String
)