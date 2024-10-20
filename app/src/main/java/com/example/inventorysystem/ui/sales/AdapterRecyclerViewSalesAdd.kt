package com.example.inventorysystem.ui.sales

import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.inventorysystem.R
import com.example.inventorysystem.ui.inventory.ItemInventory

class AdapterRecyclerViewSalesAdd(private val onAddClick: (ItemSales) -> Unit, private val onItemClick: (ItemSales) -> Unit) : RecyclerView.Adapter<AdapterRecyclerViewSalesAdd.ProductViewHolder>() {

    private var itemSales: List<ItemSales> = emptyList()

    inner class ProductViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.productImage)
        val nameTextView: TextView = view.findViewById(R.id.productName)
        val priceTextView: TextView = view.findViewById(R.id.productPrice)
        val brandTextView: TextView = view.findViewById(R.id.productMarca)
        val amountTextView: TextView = view.findViewById(R.id.productAmount)
        val addButton: Button = view.findViewById(R.id.btn_add)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_product_recycler_sales, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = itemSales[position]
        holder.imageView.setImageBitmap(product.image)
        holder.nameTextView.text = product.name
        holder.priceTextView.text = product.price
        holder.brandTextView.text = Html.fromHtml("<b>Marca:</b> ${product.marca}", Html.FROM_HTML_MODE_LEGACY)
        holder.amountTextView.text = Html.fromHtml("<b>Existencia:</b> ${product.amount}", Html.FROM_HTML_MODE_LEGACY)
        holder.addButton.setOnClickListener { onAddClick(product) }
        holder.itemView.setOnClickListener { onItemClick(product) }
    }

    override fun getItemCount() = itemSales.size

    fun updateProducts(newItemSales: List<ItemSales>) {
        itemSales = newItemSales
        notifyDataSetChanged()
    }
}