package com.example.inventorysystem

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.example.inventorysystem.io.ApiService
import com.example.inventorysystem.io.UserCreateRequest
import com.example.inventorysystem.io.UserResponse
import retrofit2.Call
import retrofit2.Response
import retrofit2.Callback
import java.util.Locale

class RegisterFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_register, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Encuentra los campos de texto
        val txtiNombre = view.findViewById<EditText>(R.id.txti_Nombre)
        val txtiEmail = view.findViewById<EditText>(R.id.txti_email)
        val txtiPassword = view.findViewById<EditText>(R.id.txti_password)

        // Encuentra el botón para registrar
        val btnRegister = view.findViewById<Button>(R.id.btnRegister)

        // Configura el listener para el botón de registro
        btnRegister.setOnClickListener {
            // Obtén los valores de los campos
            val name = txtiNombre.text.toString().trim()
            val email = txtiEmail.text.toString().trim().lowercase(Locale.getDefault())
            val password = txtiPassword.text.toString().trim()

            // Validar que los campos no estén vacíos
            if (name.isNotEmpty() && email.isNotEmpty() && password.isNotEmpty()) {
                // Crear la solicitud de registro
                val userCreateRequest = UserCreateRequest(
                    name = name,
                    last_name = ".", // Puedes obtenerlo si lo tienes en el layout o dejar un valor por defecto
                    email = email,
                    password = password,
                    sex = "M", // Puedes ajustar esto según el valor que tengas en tu UI
                    address = ".", // Puedes añadir un campo de dirección o usar un valor por defecto
                    phone = 9876543210 // Puedes obtenerlo si lo tienes en tu UI
                )

                // Llamar al método de registro
                registerUser(userCreateRequest)
            } else {
                Toast.makeText(requireContext(), "Por favor completa todos los campos", Toast.LENGTH_SHORT).show()
            }
        }

        // Encuentra el botón en el layout
        val btnBackToLogin = view.findViewById<Button>(R.id.btnBackSignIn)

        // Configura el listener para el botón
        btnBackToLogin.setOnClickListener {
            // Maneja la navegación al fragmento de inicio de sesión
            navigateToLoginFragment()
        }
    }

    // Función para hacer el registro usando Retrofit
    private fun registerUser(userCreateRequest: UserCreateRequest) {
        // Instanciar el servicio de la API
        val apiService = ApiService.create()

        // Realizar la llamada de registro
        apiService.postCreate(userCreateRequest).enqueue(object : Callback<UserResponse> {
            override fun onResponse(call: Call<UserResponse>, response: Response<UserResponse>) {
                if (response.isSuccessful) {
                    // El registro fue exitoso
                    Toast.makeText(requireContext(), "Registro exitoso", Toast.LENGTH_SHORT).show()
                    // Navegar a la pantalla de inicio de sesión
                    navigateToLoginFragment()
                } else {
                    // Hubo un error en la respuesta
                    Toast.makeText(requireContext(), "Error en el registro: ${response.message()}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<UserResponse>, t: Throwable) {
                // Fallo en la conexión o la llamada
                Toast.makeText(requireContext(), "Error de conexión: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun navigateToLoginFragment() {
        // Maneja la transacción de fragmentos con animaciones
        requireActivity().supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.fragment_right_enter,   // Animación de entrada
                R.anim.fragment_right_exit     // Animación de salida
            )
            .replace(R.id.fragment_container, SignInFragment()) // Asegúrate de usar el contenedor correcto
            .addToBackStack(null) // Opcional: Agrega la transacción a la pila de retroceso
            .commit()
    }
}