package com.example.inventorysystem.ui.sales

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventorysystem.R
import com.example.inventorysystem.databinding.FragmentSalesBinding
import com.example.inventorysystem.io.ApiService
import com.example.inventorysystem.io.ProductResponse
import com.example.inventorysystem.io.ProductUpdateRequest
import com.example.inventorysystem.io.ProductUpdateResponse
import com.example.inventorysystem.io.SaleRequest
import com.example.inventorysystem.ui.inventory.ItemInventory
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.HttpException
import retrofit2.Response
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.UnknownHostException

class SalesFragment : Fragment() {

    private var _binding: FragmentSalesBinding? = null
    private lateinit var productAdapterAdd: AdapterRecyclerViewSalesAdd
    private lateinit var cartAdapter: AdapterRecyclerViewSalesCart
    private lateinit var apiService: ApiService
    private val binding get() = _binding!!
    private lateinit var drawerLayout: DrawerLayout

    private var searchJob: Job? = null
    private var cachedProducts: List<ProductResponse> = emptyList()
    private val cartItems: MutableList<ItemSales> = mutableListOf()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSalesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        apiService = ApiService.create()
        setupRecyclerViewAdd()
        setupCartRecyclerView()
        fetchProducts()
        setupSearchWithDebounce()

        drawerLayout = binding.drawerLayout

        // Set up the FloatingActionButton click listener
        binding.shoppingCart.setOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                drawerLayout.closeDrawer(GravityCompat.END)
            } else {
                drawerLayout.openDrawer(GravityCompat.END)
            }
        }

        binding.finishSaleButton.setOnClickListener {
            if (cartItems.isNotEmpty()) {
                finishSale() // No se pasa parámetro
            } else {
                Toast.makeText(requireContext(), "El carrito está vacío", Toast.LENGTH_SHORT).show()
            }
        }


    }

    private fun postProductUpdate(productId: String, productUpdateRequest: ProductUpdateRequest) {
        lifecycleScope.launch {
            apiService.putProductUpdate(productId, productUpdateRequest).enqueue(object : Callback<ProductUpdateResponse> {
                override fun onResponse(call: Call<ProductUpdateResponse>, response: Response<ProductUpdateResponse>) {
                    if (response.isSuccessful) {
                        Toast.makeText(requireContext(), "Producto actualizado correctamente", Toast.LENGTH_SHORT).show()
                        // Actualizar la lista de productos
                        fetchProducts()
                    } else {
                        // Mostrar el código de error y el cuerpo de la respuesta en caso de error
                        val errorBody = response.errorBody()?.string()
                        Toast.makeText(requireContext(), "Error al actualizar el producto: ${response.code()}\n$errorBody", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<ProductUpdateResponse>, t: Throwable) {
                    Toast.makeText(requireContext(), "Error al enviar la solicitud: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun setupCartRecyclerView() {
        cartAdapter = AdapterRecyclerViewSalesCart(cartItems, { product ->
            // Acciones al eliminar un producto
            Toast.makeText(requireContext(), "${product.name} eliminado del carrito", Toast.LENGTH_SHORT).show()
            saveCartItems()  // Guarda el carrito
            recalculateCartAndUpdate()  // Nueva función para recalcular y actualizar
        }, this) // Pasa 'this' como el fragmento

        binding.cartItemsRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.cartItemsRecyclerView.adapter = cartAdapter

        recalculateCartAndUpdate()
    }


    private fun recalculateCartAndUpdate() {
        cartAdapter.recalculateTotal()  // Recalcula el total en el adaptador
        updateCartSummary()  // Actualiza el resumen en la UI
    }

    fun updateCartSummary() {
        // Sumar todas las cantidades seleccionadas en cartItems
        val totalQuantity = cartItems.sumOf { it.selectedQuantity }

        // Recalcular el total con las cantidades seleccionadas
        val totalAmount = cartItems.sumOf { it.price.removePrefix("$").toDouble() * it.selectedQuantity }

        // Asegurar que las actualizaciones a la UI se ejecuten en el hilo principal
        requireActivity().runOnUiThread {
            // Actualizar los textos del total y la cantidad
            if(cartItems.size == 0){
                binding.totalQuantity.text = "Cantidad: $totalQuantity"
                binding.totalAmount.text = "Total: $0.00"
            } else {
                binding.totalQuantity.text = "Cantidad: $totalQuantity"
                binding.totalAmount.text = "Total: $${String.format("%.2f", totalAmount)}"
            }
        }
    }

    private fun setupRecyclerViewAdd() {
        productAdapterAdd = AdapterRecyclerViewSalesAdd(
            onAddClick = { product ->
                addProductToCart(product)  // Agrega el producto al carrito
            },
            onItemClick = { product ->
                showProductDetailDialog(product)
            }
        )
        binding.productsRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.productsRecyclerView.adapter = productAdapterAdd
    }

    private fun showProductDetailDialog(product: ItemSales) {
        val dialog = Dialog(requireContext())
        // Establecer las animaciones
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.product_details)

        // Establecer el fondo del diálogo como transparente
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Configurar el ancho del diálogo
        val layoutParams = WindowManager.LayoutParams()
        layoutParams.copyFrom(dialog.window?.attributes)
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        dialog.window?.attributes = layoutParams

        // Configurar los datos del producto en el diálogo
        dialog.findViewById<ImageView>(R.id.detailProductImage).setImageBitmap(product.image)
        dialog.findViewById<TextView>(R.id.detailProductName).text = product.name
        dialog.findViewById<TextView>(R.id.detailProductPrice).text = "$${product.price}"
        dialog.findViewById<TextView>(R.id.detailProductSize).text = Html.fromHtml("<b>Tamaño:</b> ${product.size}", Html.FROM_HTML_MODE_LEGACY)
        dialog.findViewById<TextView>(R.id.detailProductBrand).text = Html.fromHtml("<b>Marca:</b> ${product.marca}", Html.FROM_HTML_MODE_LEGACY)
        dialog.findViewById<TextView>(R.id.detailProductCategory).text = Html.fromHtml("<b>Categoría:</b> ${product.category}", Html.FROM_HTML_MODE_LEGACY)
        dialog.findViewById<TextView>(R.id.detailProductAmount).text = Html.fromHtml("<b>Existencia:</b> ${product.amount}", Html.FROM_HTML_MODE_LEGACY)
        dialog.findViewById<TextView>(R.id.detailProductQuality).text = Html.fromHtml("<b>Calidad:</b> ${product.quality}", Html.FROM_HTML_MODE_LEGACY)
        dialog.findViewById<TextView>(R.id.detailProductDescription).text = Html.fromHtml("<b>Descripción:</b> ${product.description}", Html.FROM_HTML_MODE_LEGACY)

        // Configurar el botón de cerrar
        dialog.findViewById<Button>(R.id.detailCloseButton).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun addProductToCart(product: ItemSales) {
        if (cartItems.none { it.id == product.id }) {
            val newItem = ItemSales(
                id = product.id,
                image = product.image,
                name = product.name,
                price = product.price,
                size = product.size,
                marca = product.marca,
                amount = product.amount,
                selectedQuantity = 1, // Puedes inicializar con 1 o el valor que consideres
                category = product.category,
                quality = product.quality,
                description = product.description
            )

            // Agrega el nuevo producto al carrito
            cartAdapter.addItem(newItem)
            updateCartSummary()
            saveCartItems()
            drawerLayout.openDrawer(GravityCompat.END)
        } else {
            Toast.makeText(requireContext(), "${product.name} ya está en el carrito", Toast.LENGTH_SHORT).show()
        }
    }


    private fun saveCartItems() {
        val sharedPreferences = requireActivity().getSharedPreferences("CartPreferences", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val cartIds = cartItems.map { it.id } // Asegúrate de que estás usando el ID correcto
        editor.putStringSet("cart_items", cartIds.toSet())
        editor.apply()
    }

    private fun loadCartItems() {
        val sharedPreferences = requireActivity().getSharedPreferences("CartPreferences", Context.MODE_PRIVATE)
        val cartIds = sharedPreferences.getStringSet("cart_items", emptySet())

        cartIds?.forEach { id ->
            val item = getItemById(id) // Asegúrate de que este método funcione correctamente
            if (item != null) {
                cartItems.add(item)
            }
        }
        cartAdapter.notifyDataSetChanged()  // Notifica al adaptador sobre el cambio de datos
        updateCartSummary()  // Actualiza el resumen del carrito
    }

    private fun getItemById(id: String): ItemSales? {
        return cachedProducts.find { it._id == id }?.let { product ->
            ItemSales(
                id = product._id,
                image = base64ToBitmap(product.picture) ?: drawableToBitmap(requireContext().getDrawable(R.drawable.logo)!!),
                name = product.nombre,
                price = "$${product.precio}",
                size = product.tamaño,
                marca = product.marca,
                amount = product.cantidad.toString(),
                category = product.categoria,
                quality = product.calidad.toString(),
                description = product.descripcion,
                selectedQuantity = 1
            )
        }
    }

    private fun fetchProducts() {
        lifecycleScope.launch {
            try {
                cachedProducts = apiService.getAllProducts()
                val itemProducts = cachedProducts.map { product ->
                    var imageBitmap = base64ToBitmap(product.picture)

                    if (imageBitmap == null) {
                        val defaultDrawable = requireContext().getDrawable(R.drawable.logo) // Cambia 'logo' por tu recurso drawable
                        imageBitmap = drawableToBitmap(defaultDrawable!!)
                    }

                    // Mapea tu ProductResponse a ItemProduct
                    ItemSales(
                        image = imageBitmap, // Usa una imagen por defecto o carga una imagen desde la URL
                        name = product.nombre,
                        price = "$${product.precio}", // Formatea el precio
                        size = product.tamaño,
                        marca = product.marca,
                        category = product.categoria,
                        amount = product.cantidad.toString(),
                        quality = product.calidad.toString(),
                        description = product.descripcion,
                        id = product._id,
                        selectedQuantity = 0
                    )
                }
                productAdapterAdd.updateProducts(itemProducts)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error de red", Toast.LENGTH_SHORT).show()
            } catch (e: HttpException) {
                // Manejo detallado del error HTTP como antes
                handleHttpException(e)
            } catch (e: UnknownHostException) {
                // Error de conectividad, como problemas de red o DNS
                Toast.makeText(requireContext(), "No se pudo conectar al servidor. Verifica tu conexión a internet.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                // Cualquier otro error inesperado
                Toast.makeText(requireContext(), "Ocurrió un error inesperado", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun base64ToBitmap(base64String: String): Bitmap? {
        return try {
            val decodedString = Base64.decode(base64String, Base64.DEFAULT)
            val decodedByte = ByteArrayInputStream(decodedString)
            BitmapFactory.decodeStream(decodedByte)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        return if (drawable is BitmapDrawable) {
            drawable.bitmap
        } else {
            val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        }
    }

    private fun setupSearchWithDebounce() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()

                searchJob = lifecycleScope.launch {
                    delay(300) // Debounce de 300ms
                    searchProducts(s.toString())
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun searchProducts(query: String) {
        lifecycleScope.launch {
            if (cachedProducts.isEmpty()) {
                try {
                    cachedProducts = apiService.getAllProducts()
                } catch (e: Exception) {
                    Log.e("searchProducts", "Error al obtener productos", e)
                    Toast.makeText(requireContext(), "Error al cargar productos", Toast.LENGTH_SHORT).show()
                    return@launch
                }
            }

            val filteredProducts = if (query.isBlank()) {
                cachedProducts
            } else {
                cachedProducts.filter { product ->
                    product.nombre.contains(query, ignoreCase = true) ||
                            product.marca.contains(query, ignoreCase = true)
                }
            }

            val itemProducts = filteredProducts.map { product ->
                var imageBitmap = base64ToBitmap(product.picture)

                if (imageBitmap == null) {
                    val defaultDrawable = requireContext().getDrawable(R.drawable.logo)
                    imageBitmap = drawableToBitmap(defaultDrawable!!)
                }

                ItemSales(
                    image = imageBitmap,
                    name = product.nombre,
                    price = product.precio.toString(),
                    size = product.tamaño,
                    marca = product.marca,
                    category = product.categoria,
                    amount = product.cantidad.toString(),
                    quality = product.calidad.toString(),
                    description = product.descripcion,
                    id = product._id,
                    selectedQuantity = 0
                )
            }

            productAdapterAdd.updateProducts(itemProducts)
        }
    }

    private fun finishSale() {
        lifecycleScope.launch {
            try {
                for (item in cartItems) {
                    val productToUpdate = cachedProducts.find { it._id == item.id }

                    if (productToUpdate != null) {
                        val newQuantity = productToUpdate.cantidad - item.selectedQuantity // Usa selectedQuantity

                        if (newQuantity > 0) {
                            val updateRequest = ProductUpdateRequest(
                                nombre = productToUpdate.nombre,
                                precio = productToUpdate.precio,
                                tamaño = productToUpdate.tamaño,
                                marca = productToUpdate.marca,
                                categoria = productToUpdate.categoria,
                                cantidad = newQuantity,
                                calidad = productToUpdate.calidad,
                                descripcion = productToUpdate.descripcion,
                                picture = productToUpdate.picture
                            )

                            postProductUpdate(productToUpdate._id, updateRequest)
                        } else {
                            // Eliminar producto si la cantidad llega a cero
                            apiService.deleteProduct(productToUpdate._id).enqueue(object : Callback<Unit> {
                                override fun onResponse(call: Call<Unit>, response: Response<Unit>) {
                                    if (!response.isSuccessful) {
                                        Toast.makeText(requireContext(), "Error al autorizar la venta", Toast.LENGTH_SHORT).show()
                                    }
                                }

                                override fun onFailure(call: Call<Unit>, t: Throwable) {
                                    Toast.makeText(requireContext(), "Error de red: ${t.message}", Toast.LENGTH_SHORT).show()
                                }
                            })
                        }
                    }
                }
                clearCart()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error al completar la venta: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun clearCart() {
        cartItems.clear()
        cartAdapter.notifyDataSetChanged()  // Notifica al adaptador sobre el cambio de datos
        updateCartSummary()  // Actualiza el resumen del carrito
        saveCartItems()  // Guarda el carrito vacío
    }

    private fun handleHttpException(e: HttpException) {
        val errorMessage = when (e.code()) {
            400 -> "Solicitud incorrecta (Error 400)"
            401 -> "No autorizado (Error 401)"
            404 -> "Recurso no encontrado (Error 404)"
            500 -> "Error interno del servidor (Error 500)"
            else -> "Error en la respuesta del servidor: ${e.code()}"
        }
        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}