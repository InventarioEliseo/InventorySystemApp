package com.example.inventorysystem.ui.inventory

import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.inventorysystem.R

class AdapterRecyclerViewInventory(private val onEditClick: (ItemInventory) -> Unit, private val onDeleteClick: (ItemInventory) -> Unit, private val onItemClick: (ItemInventory) -> Unit) : RecyclerView.Adapter<AdapterRecyclerViewInventory.ProductViewHolder>() {

    private var itemInventories: List<ItemInventory> = emptyList()

    class ProductViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.productImage)
        val nameTextView: TextView = view.findViewById(R.id.productName)
        val priceTextView: TextView = view.findViewById(R.id.productPrice)
        val brandTextView: TextView = view.findViewById(R.id.productMarca)
        val amountTextView: TextView = view.findViewById(R.id.productAmount)
        val editButton: Button = view.findViewById(R.id.btn_edit)
        val deleteButton: Button = itemView.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_product_recycler_inventory, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = itemInventories[position]
        holder.imageView.setImageBitmap(product.image)
        holder.nameTextView.text = product.name
        holder.priceTextView.text = "$${product.price}"
        holder.brandTextView.text = Html.fromHtml("<b>Marca:</b> ${product.marca}", Html.FROM_HTML_MODE_LEGACY)
        holder.amountTextView.text = Html.fromHtml("<b>Existencia:</b> ${product.amount}", Html.FROM_HTML_MODE_LEGACY)
        holder.editButton.setOnClickListener { onEditClick(product) }
        holder.deleteButton.setOnClickListener { onDeleteClick(product) }
        holder.itemView.setOnClickListener { onItemClick(product) }
    }

    override fun getItemCount() = itemInventories.size

    fun updateProducts(newItemInventories: List<ItemInventory>) {
        itemInventories = newItemInventories
        notifyDataSetChanged()
    }
}