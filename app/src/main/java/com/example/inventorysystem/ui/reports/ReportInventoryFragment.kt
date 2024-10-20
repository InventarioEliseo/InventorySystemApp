package com.example.inventorysystem.ui.reports

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.inventorysystem.R
import com.example.inventorysystem.io.ApiService
import com.example.inventorysystem.io.CategoryFilter
import com.google.android.material.snackbar.Snackbar
import com.itextpdf.text.Document
import com.itextpdf.text.DocumentException
import com.itextpdf.text.Paragraph
import com.itextpdf.text.pdf.PdfPTable
import com.itextpdf.text.pdf.PdfWriter
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportInventoryFragment : Fragment() {

    private lateinit var inventoryRecyclerView: RecyclerView
    private lateinit var totalInventoryRecyclerView: RecyclerView
    private lateinit var adapter: InventoryAdapter
    private lateinit var totalInventoryAdapter: TotalInventoryAdapter
    private lateinit var productSpinner: Spinner
    private lateinit var generateReportButton: Button

    private var allInventoryData: List<InventoryItem> = emptyList()
    private var filteredInventoryData = allInventoryData
    private var totalInventoryData = emptyList<InventoryItem>()

    private val REQUEST_CODE_NOTIFICATION_PERMISSION = 100

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.report_inventory, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        inventoryRecyclerView = view.findViewById(R.id.inventoryRecyclerView)
        totalInventoryRecyclerView = view.findViewById(R.id.totalInventoryRecyclerView)
        productSpinner = view.findViewById(R.id.productSpinner)

        setupInventoryRecyclerView()
        setupTotalInventoryRecyclerView()
        setupSpinners()
        fetchInventoryData("Todos")
    }

    private fun setupInventoryRecyclerView() {
        inventoryRecyclerView.layoutManager = LinearLayoutManager(context)
        adapter = InventoryAdapter(filteredInventoryData)
        inventoryRecyclerView.adapter = adapter
    }

    private fun setupTotalInventoryRecyclerView() {
        totalInventoryRecyclerView.layoutManager = LinearLayoutManager(context)
        totalInventoryAdapter = TotalInventoryAdapter(totalInventoryData)
        totalInventoryRecyclerView.adapter = totalInventoryAdapter
    }

    private fun setupSpinners() {
        productSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedCategory = productSpinner.selectedItem as String
                filterData(selectedCategory) // Filtra datos al seleccionar la categoría
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    // Cambia la función filterData para que acepte la categoría como parámetro
    private fun filterData(selectedCategory: String) {
        filteredInventoryData = if (selectedCategory == "Todos") {
            allInventoryData
        } else {
            allInventoryData.filter { it.category == selectedCategory }
        }

        // Actualiza los datos en el adaptador
        adapter.updateData(filteredInventoryData)

        // Calcula y actualiza el total del inventario
        val totalInventory = calculateTotalInventory(filteredInventoryData, selectedCategory)
        totalInventoryAdapter.updateData(totalInventory)
    }

    fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> {
                    // Permiso ya concedido
                    Toast.makeText(context, "Generando reporte...", Toast.LENGTH_SHORT).show()
                    generatePdf()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Mostrar explicación y volver a solicitar el permiso
                    showPermissionSnackbar()
                }
                else -> {
                    // El usuario marcó "No volver a preguntar", mostrar un Snackbar para ir a la configuración
                    showSettingsSnackbar()
                }
            }
        } else {
            // En versiones anteriores a Android 13, no se requiere permiso explícito para notificaciones
            Toast.makeText(context, "Generando reporte...", Toast.LENGTH_SHORT).show()
            generatePdf()
        }
    }

    private fun showPermissionSnackbar() {
        Snackbar.make(requireView(), "Necesitas conceder el permiso de notificaciones para generar el reporte.", Snackbar.LENGTH_LONG)
            .setAction("Solicitar") {
                // Vuelve a solicitar el permiso
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_CODE_NOTIFICATION_PERMISSION
                )
            }
            .show()
    }

    private fun showSettingsSnackbar() {
        Snackbar.make(requireView(), "Debes habilitar el permiso de notificaciones en la configuración.", Snackbar.LENGTH_LONG)
            .setAction("Configuración") {
                // Abrir la configuración de la aplicación
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                }
                startActivity(intent)
            }
            .show()
    }

    private fun calculateTotalInventory(filteredData: List<InventoryItem>, selectedProduct: String): List<InventoryItem> {
        return when {
            selectedProduct == "Todos" -> listOf(
                InventoryItem("Todos", filteredData.sumOf { it.existence }, "")
            )
            else -> filteredData.groupBy { it.category }
                .map { (category, group) ->
                    InventoryItem(category, group.sumOf { it.existence }, "")
                }
        }
    }

    private fun fetchInventoryData(category: String) {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://inventariobackeliseo-production.up.railway.app")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(ApiService::class.java)

        lifecycleScope.launch {
            try {
                val inventoryData = service.getAllProducts() // Obtener todos los productos

                allInventoryData = inventoryData.map { item ->
                    InventoryItem(product = item.nombre, existence = item.cantidad, category = item.categoria)
                }

                // Extraer categorías únicas y configurar el spinner una sola vez
                val categories = allInventoryData.map { it.category }.distinct()
                setupProductSpinner(categories) // Configura el Spinner solo una vez

                // Filtra los datos según la categoría seleccionada
                filteredInventoryData = if (category == "Todos") {
                    allInventoryData
                } else {
                    allInventoryData.filter { it.category == category }
                }

                // Actualiza el adaptador
                adapter.updateData(filteredInventoryData)

                // Calcula y muestra el total del inventario
                val totalInventory = calculateTotalInventory(filteredInventoryData, category)
                totalInventoryAdapter.updateData(totalInventory)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setupProductSpinner(categories: List<String>) {
        val allCategories = listOf("Todos") + categories
        productSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, allCategories)
    }

    private fun generatePdf() {
        val document = Document()
        try {
            val dateFormat = SimpleDateFormat("ddMMyyyy_HHmmss", Locale.getDefault())
            val currentDateTime = dateFormat.format(Date())
            val pdfFileName = "inventory_report_$currentDateTime.pdf"

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, pdfFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = requireContext().contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    PdfWriter.getInstance(document, outputStream)
                    document.open()

                    // Título del documento
                    document.add(Paragraph("Reporte de Inventario\n"))
                    document.add(Paragraph(" "))

                    // Agrupar datos por categoría
                    val groupedData = filteredInventoryData.groupBy { it.category }

                    groupedData.forEach { (category, items) ->
                        // Crear la tabla para cada categoría
                        val table = PdfPTable(2) // Dos columnas
                        table.widthPercentage = 100f // Ancho de la tabla al 100% del documento
                        table.setWidths(floatArrayOf(3f, 1f)) // Ancho relativo de las columnas

                        // Agregar encabezados
                        table.addCell("Producto")
                        table.addCell("En existencia")

                        // Agregar una fila de categoría
                        table.addCell("Categoría: $category")
                        table.addCell("") // Espacio en blanco para alineación

                        // Agregar los productos a la tabla
                        items.forEach { item ->
                            table.addCell(item.product) // Producto
                            table.addCell(item.existence.toString()) // Existencia
                        }

                        // Agregar la tabla al documento
                        document.add(table)
                        document.add(Paragraph(" ")) // Espacio entre tablas
                    }

                    document.close()
                    Toast.makeText(context, "Reporte generado en: $uri", Toast.LENGTH_LONG).show()
                    sendNotification(pdfFileName, uri)
                }
            }
        } catch (e: DocumentException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun sendNotification(fileName: String, fileUri: Uri) {
        val notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "report_channel"
            val channelName = "Report Notifications"
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val notificationBuilder = NotificationCompat.Builder(requireContext(), "report_channel")
            .setSmallIcon(R.drawable.logo) // Asegúrate de tener un icono de notificación en tus recursos
            .setContentTitle("Reporte Generado")
            .setContentText("El reporte $fileName ha sido generado.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val pendingIntent = PendingIntent.getActivity(requireContext(), 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        notificationBuilder.setContentIntent(pendingIntent)

        notificationManager.notify(1, notificationBuilder.build())
    }
}

class InventoryAdapter(private var items: List<InventoryItem>) : RecyclerView.Adapter<InventoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val productTextView: TextView = view.findViewById(R.id.productTextView)
        val existenceTextView: TextView = view.findViewById(R.id.existenceTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_report_inventory, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.productTextView.text = item.product
        holder.existenceTextView.text = item.existence.toString()
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<InventoryItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}

class TotalInventoryAdapter(private var items: List<InventoryItem>) : RecyclerView.Adapter<TotalInventoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val productTextView: TextView = view.findViewById(R.id.productTextView)
        val existenceTextView: TextView = view.findViewById(R.id.existenceTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_report_inventory, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.productTextView.text = item.product
        holder.existenceTextView.text = item.existence.toString()
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<InventoryItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
