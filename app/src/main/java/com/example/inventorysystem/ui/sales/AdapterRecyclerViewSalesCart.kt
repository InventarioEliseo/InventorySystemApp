package com.example.inventorysystem.ui.sales

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.inventorysystem.R

class AdapterRecyclerViewSalesCart(
    private val cartItems: MutableList<ItemSales>,
    private val onDeleteClick: (ItemSales) -> Unit,
    private val fragment: SalesFragment // Pasa el fragmento
) : RecyclerView.Adapter<AdapterRecyclerViewSalesCart.ProductViewHolder>() {

    // Propiedad para el total
    var totalAmount: Double = 0.0
        private set

    inner class ProductViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.product_image)
        val nameTextView: TextView = view.findViewById(R.id.product_name)
        val priceTextView: TextView = view.findViewById(R.id.product_price)
        val deleteButton: Button = view.findViewById(R.id.delete_button)
        val quantitySpinner: Spinner = view.findViewById(R.id.amount_spinner)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_product_sales, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = cartItems[position]
        holder.imageView.setImageBitmap(product.image)
        holder.nameTextView.text = product.name
        holder.priceTextView.text = product.price

        // Configurar el Spinner para mostrar la cantidad en existencia
        val availableQuantities = (1..product.amount.toInt()).toList()
        val adapter = ArrayAdapter(holder.itemView.context, android.R.layout.simple_spinner_item, availableQuantities)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        holder.quantitySpinner.adapter = adapter

        // Verificar que hay cantidades disponibles antes de seleccionar
        holder.quantitySpinner.setSelection(availableQuantities.indexOf(product.selectedQuantity).takeIf { it >= 0 } ?: 0)

        // Listener para actualizar la cantidad seleccionada y actualizar el total
        holder.quantitySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                cartItems[holder.adapterPosition].selectedQuantity = availableQuantities[position]
                fragment.updateCartSummary() // Llama al método de actualización de resumen en el fragmento
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // No se hace nada
            }
        }

        holder.deleteButton.setOnClickListener {
            removeItem(position)
            onDeleteClick(product)
            recalculateTotal()
        }
    }


    override fun getItemCount() = cartItems.size

    // Agregar un producto y actualizar el total
    fun addItem(product: ItemSales) {
        cartItems.add(product)
        recalculateTotal()  // Recalcular el total después de agregar
        notifyDataSetChanged()  // Actualiza la vista del carrito
    }

    // Eliminar un producto y actualizar el total
    private fun removeItem(position: Int) {
        cartItems.removeAt(position)
        recalculateTotal()  // Recalcular el total después de eliminar
    }

    // Método para recalcular el total al eliminar o agregar productos
    fun recalculateTotal() {
        totalAmount = cartItems.sumOf { it.price.removePrefix("$").toDouble() }

        // Llamar a notifyDataSetChanged para asegurarse de que los cambios se reflejen en la UI
        notifyDataSetChanged()
    }
}


