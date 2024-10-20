package com.example.inventorysystem.ui.reports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.inventorysystem.databinding.FragmentReportsBinding
import com.google.android.material.tabs.TabLayoutMediator

class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = ReportsPagerAdapter(this)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Inventario"
                1 -> "Ventas"
                else -> throw IndexOutOfBoundsException()
            }
        }.attach()

        // Configurar el botón para generar el reporte
        binding.generateReportButton.setOnClickListener {
            generateReport()
        }
    }

    private fun generateReport() {
        // Obtener el fragmento actual basado en el ViewPager
        val currentItem = binding.viewPager.currentItem
        val fragment = childFragmentManager.findFragmentByTag("f$currentItem")

        when (currentItem) {
            0 -> (fragment as? ReportInventoryFragment)?.checkAndRequestNotificationPermission()
            1 -> (fragment as? ReportSalesFragment)?.checkAndRequestNotificationPermission()
            else -> Toast.makeText(requireContext(), "Fragmento desconocido.", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}

class ReportsPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ReportInventoryFragment()
            1 -> ReportSalesFragment()
            else -> throw IndexOutOfBoundsException()
        }
    }
}