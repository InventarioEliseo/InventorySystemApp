package com.example.inventorysystem.io

import com.example.inventorysystem.ui.reports.SalesReportRequest
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @POST( value = "/api/v1/user/login/")
    fun postLogin(@Body request: LoginRequest): Call<UserResponse>

    @POST( value = "/api/v1/user/")
    fun postCreate(@Body request: UserCreateRequest): Call<UserResponse>

    @POST( value = "/api/v1/producto/")
    fun postCreateProduct(@Body request: ProductCreateRequest): Call<ProductResponse>

    @GET("/api/v1/producto/")
    suspend fun getAllProducts(): List<ProductResponse>

    @PUT("/api/v1/producto/{orderId}")
    fun putProductUpdate(
        @Path("orderId") orderId: String,
        @Body request: ProductUpdateRequest
    ): Call<ProductUpdateResponse>

    @DELETE("/api/v1/producto/{productId}/")
    fun deleteProduct(@Path("productId") productId: String): Call<Unit>

    @GET("/api/v1/producto")
    suspend fun getProductListByName(@Query("nombre") nombre: String): List<ProductResponse>

    @GET("/api/v1/producto")
    suspend fun getProductListById(@Query("_id") id: String): ProductResponse

    @POST("/api/v1/venta/")
    suspend fun sendSales(@Body saleRequest: SaleRequest): Response<SaleResponse>

    @POST("/api/v1/venta/reporte/")
    suspend fun getSalesReport(@Body request: SalesReportRequest): Response<List<SaleResponse>>

    @GET("/api/v1/venta/")
    suspend fun getAllSalesReport(): Response<List<SaleResponse>>


    @POST("/api/v1/producto/reporte/")
    suspend fun getFilteredInventoryData(@Body filter: CategoryFilter): List<ProductResponse>



    companion object Factory{
        private const val BASE_URL = "https://inventariobackeliseo-production.up.railway.app"
        fun create(): ApiService {
            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return retrofit.create(ApiService::class.java)
        }
    }

}