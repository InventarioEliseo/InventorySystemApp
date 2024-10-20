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

class ReportSalesFragment : Fragment() {
    private lateinit var salesRecyclerView: RecyclerView
    private lateinit var totalSalesRecyclerView: RecyclerView
    private lateinit var adapter: SalesAdapter
    private lateinit var totalSalesAdapter: TotalSalesAdapter
    private lateinit var monthSpinner: Spinner

    private var allSalesData = getSampleSalesData()
    private var filteredSalesData = allSalesData
    private var totalSalesData = emptyList<SalesItem>()

    private val REQUEST_CODE_NOTIFICATION_PERMISSION = 100

    private lateinit var apiService: ApiService // Cambia ApiService por el nombre de tu interfaz

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializa Retrofit y el apiService
        val retrofit = Retrofit.Builder()
            .baseUrl("https://inventariobackeliseo-production.up.railway.app") // Asegúrate de usar tu URL base correcta
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(ApiService::class.java) // Crea la instancia de tu servicio
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.report_sales, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        salesRecyclerView = view.findViewById(R.id.salesRecyclerView)
        totalSalesRecyclerView = view.findViewById(R.id.totalSalesRecyclerView)
        monthSpinner = view.findViewById(R.id.monthSpinner)

        // Configuración de RecyclerView y Spinner
        setupSalesRecyclerView()
        setupTotalSalesRecyclerView()
        setupSpinner()

        // Llama a la función para obtener el reporte de ventas
        fetchSalesReport()
    }

    private fun setupSalesRecyclerView() {
        salesRecyclerView.layoutManager = LinearLayoutManager(context)
        adapter = SalesAdapter(filteredSalesData)
        salesRecyclerView.adapter = adapter
    }

    private fun setupTotalSalesRecyclerView() {
        totalSalesRecyclerView.layoutManager = LinearLayoutManager(context)
        totalSalesAdapter = TotalSalesAdapter(totalSalesData)
        totalSalesRecyclerView.adapter = totalSalesAdapter
    }

    private fun setupSpinner() {
        val months = listOf("Todos") + allSalesData.map { it.month }.distinct()

        monthSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, months)

        monthSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                filterData()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
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

    private fun filterData() {
        val selectedMonth = monthSpinner.selectedItem.toString()

        filteredSalesData = allSalesData.filter { salesItem ->
            selectedMonth == "Todos" || salesItem.month == selectedMonth
        }

        totalSalesData = calculateTotalSales(filteredSalesData, selectedMonth)
        adapter.updateData(filteredSalesData)
        totalSalesAdapter.updateData(totalSalesData)
    }

    private fun calculateTotalSales(filteredData: List<SalesItem>, selectedMonth: String): List<SalesItem> {
        return when {
            selectedMonth == "Todos" -> listOf(SalesItem("Todos", "Todos", filteredData.sumOf { it.total }))
            else -> {
                // Sumar total de todos los productos para el mes seleccionado
                val totalForSelectedMonth = filteredData.sumOf { it.total }
                // Generar una lista con el total general para el mes
                listOf(SalesItem(selectedMonth, "Total", totalForSelectedMonth)) +
                        filteredData.groupBy { it.product }
                            .map { (product, group) -> SalesItem(selectedMonth, product, group.sumOf { it.total }) }
            }
        }
    }


    private fun getSampleSalesData(): List<SalesItem> {
        return listOf(
        )
    }

    private fun fetchSalesReport() {
        // Obtiene el mes seleccionado del Spinner
        val selectedMonth = monthSpinner.selectedItem.toString()

        lifecycleScope.launch {
            try {
                val response = if (selectedMonth == "Todos") {
                    // Hacer la petición para obtener todas las ventas
                    apiService.getAllSalesReport()
                } else {
                    // Hacer la petición para un rango específico de meses
                    val request = SalesReportRequest("08", "2024", "11", "2024") // Cambia esto según tu lógica
                    apiService.getSalesReport(request)
                }

                // Maneja la respuesta de manera similar
                if (response.isSuccessful) {
                    val salesData = response.body()
                    if (salesData != null) {
                        // Mapea la respuesta a SalesItem usando los nombres de los meses
                        val mappedSalesData = salesData.map { saleResponse ->
                            SalesItem(
                                month = getMonthName(saleResponse.createdAt.split("-")[1]), // Usar el nombre del mes
                                product = saleResponse.nombre,
                                total = saleResponse.precio // O cualquier lógica para calcular total
                            )
                        }

                        // Obtén meses únicos
                        val uniqueMonths = mappedSalesData.map { it.month }.distinct()

                        // Agrega "Todos" al inicio de la lista si no está presente
                        val monthsToShow = if ("Todos" !in uniqueMonths) {
                            listOf("Todos") + uniqueMonths
                        } else {
                            uniqueMonths
                        }

                        allSalesData = mappedSalesData // Actualiza allSalesData
                        filteredSalesData = allSalesData // Asegúrate de que los datos filtrados se actualicen
                        adapter.updateData(filteredSalesData) // Actualiza el adaptador

                        // Mostrar los meses en el Spinner
                        showMonthsInSpinner(monthsToShow)

                        totalSalesData = calculateTotalSales(filteredSalesData, selectedMonth) // Actualiza el total
                        totalSalesAdapter.updateData(totalSalesData) // Actualiza el adaptador total
                    } else {
                        // Si no se encontraron datos, asegurarte de que "Todos" esté en el Spinner
                        showMonthsInSpinner(listOf("Todos"))
                        Toast.makeText(requireContext(), "No se encontraron datos", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Error: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace() // Imprime el error en Logcat
                Toast.makeText(requireContext(), "Error al obtener el reporte de ventas: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun showMonthsInSpinner(months: List<String>) {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, months)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        monthSpinner.adapter = adapter

        // Establecer un listener para cuando el usuario selecciona un mes
        monthSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                filterData() // Filtra los datos cuando se selecciona un mes
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // No necesitas hacer nada aquí
            }
        }
    }

    private fun getMonthName(monthNumber: String): String {
        return when (monthNumber) {
            "01" -> "Enero"
            "02" -> "Febrero"
            "03" -> "Marzo"
            "04" -> "Abril"
            "05" -> "Mayo"
            "06" -> "Junio"
            "07" -> "Julio"
            "08" -> "Agosto"
            "09" -> "Septiembre"
            "10" -> "Octubre"
            "11" -> "Noviembre"
            "12" -> "Diciembre"
            else -> "Desconocido" // Manejar el caso de un número no válido
        }
    }

    private fun generatePdf() {
        val document = Document()
        try {
            val dateFormat = SimpleDateFormat("ddMMyyyy_HHmmss", Locale.getDefault())
            val currentDateTime = dateFormat.format(Date())
            val pdfFileName = "sales_report_$currentDateTime.pdf"

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
                    document.add(Paragraph("Reporte de Ventas\n"))
                    document.add(Paragraph(" "))

                    // Agrupar datos por mes
                    val groupedByMonth = filteredSalesData.groupBy { it.month }
                    groupedByMonth.forEach { (month, salesItems) ->
                        // Crear la tabla para cada mes
                        val table = PdfPTable(3) // Tres columnas
                        table.widthPercentage = 100f // Ancho de la tabla al 100% del documento
                        table.setWidths(floatArrayOf(3f, 1f, 1f)) // Ancho relativo de las columnas

                        // Agregar encabezados
                        table.addCell("Producto")
                        table.addCell("Cantidad")
                        table.addCell("Precio Total")

                        // Agrupar ventas por producto para contar la cantidad y calcular el total
                        val groupedByProduct = salesItems.groupBy { it.product }
                        val monthlyTotal = groupedByProduct.map { (product, items) ->
                            val quantity = items.size // Cantidad de veces que se repite el producto
                            val totalPrice = items.sumOf { it.total } // Sumar los precios de todas las ventas de ese producto
                            // Agregar la fila a la tabla
                            table.addCell(product) // Producto
                            table.addCell(quantity.toString()) // Cantidad
                            table.addCell(totalPrice.toString()) // Precio Total
                            totalPrice
                        }.sum() // Sumar el total de precios de todos los productos

                        // Agregar la tabla al documento
                        document.add(Paragraph("Mes: $month"))
                        document.add(table)
                        document.add(Paragraph("Total por mes: $monthlyTotal"))
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

class SalesAdapter(private var items: List<SalesItem>) : RecyclerView.Adapter<SalesAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val productTextView: TextView = view.findViewById(R.id.productTextView)
        val totalTextView: TextView = view.findViewById(R.id.totalTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_report_sales, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.productTextView.text = item.product
        holder.totalTextView.text = String.format("$%.2f", item.total)
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<SalesItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}

class TotalSalesAdapter(private var items: List<SalesItem>) : RecyclerView.Adapter<TotalSalesAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val monthTextView: TextView = view.findViewById(R.id.monthTextView)
        val productTextView: TextView = view.findViewById(R.id.productTextView)
        val totalTextView: TextView = view.findViewById(R.id.totalTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_report_sales_total, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.monthTextView.text = item.month
        holder.productTextView.text = item.product
        holder.totalTextView.text = String.format("$%.2f", item.total)
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<SalesItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
