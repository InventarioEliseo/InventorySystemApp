package com.example.inventorysystem.ui.inventory

import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventorysystem.R
import com.example.inventorysystem.databinding.FragmentInventoryBinding
import com.example.inventorysystem.io.ApiService
import com.example.inventorysystem.io.ProductCreateRequest
import com.example.inventorysystem.io.ProductResponse
import com.example.inventorysystem.io.ProductUpdateRequest
import com.example.inventorysystem.io.ProductUpdateResponse
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.HttpException
import retrofit2.Response
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.UnknownHostException

class InventoryFragment : Fragment() {

    private var _binding: FragmentInventoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var productAdapterEdit: AdapterRecyclerViewInventory
    private lateinit var apiService: ApiService
    private var bottomSheetView: View? = null

    private var searchJob: Job? = null
    private var cachedProducts: List<ProductResponse> = emptyList()

    private var imageSelected = false // Flag para verificar si se ha seleccionado una imagen

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentInventoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        apiService = ApiService.create()
        setupRecyclerViewEdit()
        fetchProducts()
        setupFloatingActionButton()
        setupSearchWithDebounce()
    }

    private fun setupRecyclerViewEdit() {
        productAdapterEdit = AdapterRecyclerViewInventory(
            onEditClick = { product ->
                showProductBottomSheetEdit(product)
            },
            onDeleteClick = { product ->
                showDeleteConfirmationDialog(product)
            },
            onItemClick = { product ->
                showProductDetailDialog(product)
            }
        )
        binding.productsRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.productsRecyclerView.adapter = productAdapterEdit
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
                    ItemInventory(
                        image = imageBitmap, // Usa una imagen por defecto o carga una imagen desde la URL
                        name = product.nombre,
                        price = product.precio.toString(),
                        size = product.tamaño,
                        marca = product.marca,
                        category = product.categoria,
                        amount = product.cantidad.toString(),
                        quality = product.calidad.toString(),
                        description = product.descripcion,
                        id = product._id
                    )
                }
                productAdapterEdit.updateProducts(itemProducts)
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

    private fun setupFloatingActionButton() {
        binding.add.setOnClickListener {
            showProductBottomSheetAdd()
        }
    }

    private fun showProductDetailDialog(product: ItemInventory) {
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

    private fun showProductBottomSheetAdd() {
        bottomSheetView = layoutInflater.inflate(R.layout.btn_sheet_inventory, null)
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        bottomSheetDialog.setContentView(bottomSheetView!!)

        val quantitySpinner = bottomSheetView?.findViewById<Spinner>(R.id.quantitySpinner)
        val quantities = (1..10).toList()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, quantities)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        quantitySpinner?.adapter = adapter

        val addButton = bottomSheetView?.findViewById<Button>(R.id.btn_inventory_add_edit)
        addButton?.text = "Agregar"

        val productImage = bottomSheetView?.findViewById<ImageView>(R.id.img_product)

        productImage?.setImageResource(R.drawable.ic_add_image)

        var imageSelected = false // Flag para verificar si se ha seleccionado una imagen

        productImage?.setOnClickListener {
            openGallery()
        }

        // Obtiene el EditText para la cantidad
        val amountEditText = bottomSheetView?.findViewById<TextInputEditText>(R.id.edt_amount)

        // Configura los botones de incrementar y decrementar
        val btnMinus = bottomSheetView?.findViewById<Button>(R.id.btn_cant_menos)
        val btnPlus = bottomSheetView?.findViewById<Button>(R.id.btn_cant_mas)

        // Decrementar cantidad
        btnMinus?.setOnClickListener {
            val currentAmount = amountEditText?.text.toString().toIntOrNull() ?: 0
            if (currentAmount > 0) {
                amountEditText?.setText((currentAmount - 1).toString())
            }
        }

        // Incrementar cantidad
        btnPlus?.setOnClickListener {
            val currentAmount = amountEditText?.text.toString().toIntOrNull() ?: 0
            amountEditText?.setText((currentAmount + 1).toString())
        }

        addButton?.setOnClickListener {
            val name = bottomSheetView?.findViewById<TextInputEditText>(R.id.edt_name)?.text.toString()
            val price = bottomSheetView?.findViewById<TextInputEditText>(R.id.edt_price)?.text.toString().toDoubleOrNull() ?: 0.0
            val size = bottomSheetView?.findViewById<TextInputEditText>(R.id.edt_size)?.text.toString()
            val marca = bottomSheetView?.findViewById<TextInputEditText>(R.id.edt_marca)?.text.toString()
            val category = bottomSheetView?.findViewById<TextInputEditText>(R.id.edt_category)?.text.toString()
            val amount = amountEditText?.text.toString().toIntOrNull() ?: 0
            val quality = quantitySpinner?.selectedItem as Int
            val description = bottomSheetView?.findViewById<TextInputEditText>(R.id.edt_description)?.text.toString()

            // Si no se ha seleccionado una imagen, usa una imagen predeterminada
            val pictureBase64 = if (imageSelected) {
                imageViewToBase64(productImage!!)
            } else {
                productImage?.setImageResource(R.drawable.logo) // Establece la imagen predeterminada
                imageViewToBase64(productImage!!) // Luego, pasa el ImageView a la función
            }

            val productRequest = ProductCreateRequest(
                nombre = name,
                precio = price,
                tamaño = size,
                marca = marca,
                categoria = category,
                cantidad = amount,
                calidad = quality,
                descripcion = description,
                picture = pictureBase64
            )

            postNewProduct(productRequest)

            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    private fun showProductBottomSheetEdit(product: ItemInventory) {
        bottomSheetView = layoutInflater.inflate(R.layout.btn_sheet_inventory, null)
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        bottomSheetDialog.setContentView(bottomSheetView!!)

        val edtAmount = bottomSheetView?.findViewById<TextInputEditText>(R.id.edt_amount)
        val btnCantMenos = bottomSheetView?.findViewById<Button>(R.id.btn_cant_menos)
        val btnCantMas = bottomSheetView?.findViewById<Button>(R.id.btn_cant_mas)

        // Listener para el botón menos
        btnCantMenos?.setOnClickListener {
            val currentAmount = edtAmount?.text.toString().toIntOrNull() ?: 0
            if (currentAmount > 0) {
                edtAmount?.setText((currentAmount - 1).toString())
            }
        }

        // Listener para el botón más
        btnCantMas?.setOnClickListener {
            val currentAmount = edtAmount?.text.toString().toIntOrNull() ?: 0
            edtAmount?.setText((currentAmount + 1).toString())
        }

        val quantitySpinner = bottomSheetView?.findViewById<Spinner>(R.id.quantitySpinner)
        val quantities = (1..10).toList()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, quantities)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        quantitySpinner?.adapter = adapter

        val productQuantity = product.quality.toIntOrNull() ?: 1
        quantitySpinner?.setSelection(quantities.indexOf(productQuantity))

        val productImage = bottomSheetView?.findViewById<ImageView>(R.id.img_product)
        productImage?.setOnClickListener {
            openGallery()
        }

        productImage?.setImageBitmap(product.image)
        bottomSheetView?.findViewById<TextInputEditText>(R.id.edt_name)?.setText(product.name)
        bottomSheetView?.findViewById<TextInputEditText>(R.id.edt_price)?.setText(product.price)
        bottomSheetView?.findViewById<TextInputEditText>(R.id.edt_size)?.setText(product.size)
        bottomSheetView?.findViewById<TextInputEditText>(R.id.edt_marca)?.setText(product.marca)
        bottomSheetView?.findViewById<TextInputEditText>(R.id.edt_category)?.setText(product.category)
        bottomSheetView?.findViewById<TextInputEditText>(R.id.edt_amount)?.setText(product.amount)
        bottomSheetView?.findViewById<TextInputEditText>(R.id.edt_description)?.setText(product.description)

        val editButton = bottomSheetView?.findViewById<Button>(R.id.btn_inventory_add_edit)
        editButton?.text = "Editar"

        editButton?.setOnClickListener {
            val selectedQuantity = quantitySpinner?.selectedItem as? Int ?: 0
            val name = bottomSheetView?.findViewById<TextInputEditText>(R.id.edt_name)?.text.toString()
            val price = bottomSheetView?.findViewById<TextInputEditText>(R.id.edt_price)?.text.toString().toDoubleOrNull() ?: 0.0
            val size = bottomSheetView?.findViewById<TextInputEditText>(R.id.edt_size)?.text.toString()
            val marca = bottomSheetView?.findViewById<TextInputEditText>(R.id.edt_marca)?.text.toString()
            val category = bottomSheetView?.findViewById<TextInputEditText>(R.id.edt_category)?.text.toString()
            val amount = bottomSheetView?.findViewById<TextInputEditText>(R.id.edt_amount)?.text.toString().toInt()
            val description = bottomSheetView?.findViewById<TextInputEditText>(R.id.edt_description)?.text.toString()
            val pictureBase64 = imageViewToBase64(productImage!!)

            val productUpdateRequest = ProductUpdateRequest(
                nombre = name,
                precio = price,
                tamaño = size,
                marca = marca,
                categoria = category,
                cantidad = amount,
                calidad = selectedQuantity,
                descripcion = description,
                picture = pictureBase64
            )

            postProductUpdate(product.id, productUpdateRequest)

            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    private fun showDeleteConfirmationDialog(product: ItemInventory) {
        AlertDialog.Builder(requireContext())
            .setTitle("Confirmar eliminación")
            .setMessage("¿Estás seguro de que quieres eliminar este producto?")
            .setPositiveButton("Sí") { _, _ ->
                deleteProduct(product)
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun deleteProduct(product: ItemInventory) {
        lifecycleScope.launch {
            try {
                apiService.deleteProduct(product.id).enqueue(object : Callback<Unit> {
                    override fun onResponse(call: Call<Unit>, response: Response<Unit>) {
                        if (response.isSuccessful) {
                            Toast.makeText(requireContext(), "Producto eliminado correctamente", Toast.LENGTH_SHORT).show()
                            fetchProducts() // Refresh the product list
                        } else {
                            Toast.makeText(requireContext(), "Error al eliminar el producto: ${response.code()}", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(call: Call<Unit>, t: Throwable) {
                        Toast.makeText(requireContext(), "Error al eliminar el producto: ${t.message}", Toast.LENGTH_SHORT).show()
                    }
                })
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error al eliminar el producto: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun postNewProduct(productCreateRequest: ProductCreateRequest) {
        lifecycleScope.launch {
            apiService.postCreateProduct(productCreateRequest).enqueue(object : Callback<ProductResponse> {
                override fun onResponse(call: Call<ProductResponse>, response: Response<ProductResponse>) {
                    if (response.isSuccessful) {
                        Toast.makeText(requireContext(), "Producto agregado correctamente", Toast.LENGTH_SHORT).show()
                        // Actualizar la lista de productos
                        fetchProducts()
                    } else {
                        // Mostrar el código de error y el cuerpo de la respuesta en caso de error
                        val errorBody = response.errorBody()?.string()
                        Toast.makeText(requireContext(), "Error al agregar el producto: ${response.code()}\n$errorBody", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<ProductResponse>, t: Throwable) {
                    Toast.makeText(requireContext(), "Error al enviar la solicitud: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    fun imageViewToBase64(imageView: ImageView): String {
        // Asegúrate de que el drawable no sea null
        val drawable = imageView.drawable ?: return ""

        // Verifica que el drawable sea un BitmapDrawable
        if (drawable is BitmapDrawable) {
            val bitmap = (imageView.drawable as BitmapDrawable).bitmap

            // Redimensionar la imagen a 266x282
            val resizedBitmap = resizeBitmap(bitmap, 150, 150)

            // Convertir el bitmap a byte array
            val byteArrayOutputStream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()

            // Codificar el byte array a base64
            return Base64.encodeToString(byteArray, Base64.DEFAULT)
        } else {
            // Manejo de caso en que el drawable no es un BitmapDrawable
            Log.e("ImageViewToBase64", "El drawable no es un BitmapDrawable")
            return ""
        }
    }


    // Lanzador para la actividad de selección de imágenes
    private val getImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val productImage = bottomSheetView?.findViewById<ImageView>(R.id.img_product)
            productImage?.setImageURI(it)
            imageSelected = true // Marca que se ha seleccionado una imagen
        }
    }

    private fun openGallery() {
        getImage.launch("image/*")
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

    private fun resizeBitmap(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
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

                ItemInventory(
                    image = imageBitmap,
                    name = product.nombre,
                    price = product.precio.toString(),
                    size = product.tamaño,
                    marca = product.marca,
                    category = product.categoria,
                    amount = product.cantidad.toString(),
                    quality = product.calidad.toString(),
                    description = product.descripcion,
                    id = product._id
                )
            }

            productAdapterEdit.updateProducts(itemProducts)
        }
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