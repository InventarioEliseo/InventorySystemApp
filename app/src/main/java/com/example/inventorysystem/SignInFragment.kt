package com.example.inventorysystem

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.inventorysystem.io.ApiService
import com.example.inventorysystem.io.LoginRequest
import com.example.inventorysystem.io.UserResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale

class SignInFragment : Fragment() {

    // Define la interfaz para comunicar eventos de clic
    interface OnSignInButtonClickListener {
        fun onSignInButtonClick()
    }

    private var listener: OnSignInButtonClickListener? = null

    private lateinit var apiService: ApiService
    private lateinit var txtEmail: EditText
    private lateinit var txtPassword: EditText

    override fun onAttach(context: Context) {
        super.onAttach(context)

        if (context is OnSignInButtonClickListener) {
            listener = context
        } else {
            throw ClassCastException("$context must implement OnSignInButtonClickListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_sign_in, container, false)

        // Inicializa los campos de texto y el botón
        txtEmail = view.findViewById(R.id.txti_email)
        txtPassword = view.findViewById(R.id.txti_password)

        // Configura el cliente de la API
        apiService = ApiService.create()

        // Encuentra el botón y asigna el listener
        val btnSignIn = view.findViewById<Button>(R.id.btnSignIn)
        btnSignIn.setOnClickListener {
            performLogin()
        }

        return view
    }

    private fun performLogin() {
        val email = txtEmail.text.toString().trim().lowercase(Locale.getDefault()) // Convierte a minúsculas
        val password = txtPassword.text.toString().trim()

        // Valida que los campos no estén vacíos
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(requireContext(), "Por favor, ingrese todos los campos", Toast.LENGTH_SHORT).show()
            return
        }

        // Crea el objeto LoginRequest
        val loginRequest = LoginRequest(email, password)

        // Realiza la solicitud de login
        apiService.postLogin(loginRequest).enqueue(object : Callback<UserResponse> {
            override fun onResponse(call: Call<UserResponse>, response: Response<UserResponse>) {
                if (response.isSuccessful && response.body() != null) {

                    val sharedPref = requireActivity().getPreferences(Context.MODE_PRIVATE)
                    with(sharedPref.edit()) {
                        putString("email", email)
                        apply()
                    }

                    // Login exitoso
                    Toast.makeText(requireContext(), "Inicio de sesión exitoso", Toast.LENGTH_SHORT).show()
                    navigateToMainActivity()
                } else {
                    // Error en las credenciales o en el servidor
                    Toast.makeText(requireContext(), "Error en el inicio de sesión", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<UserResponse>, t: Throwable) {
                // Error en la conexión
                Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onStart() {
        super.onStart()

        // Obtener el Intent asociado a la actividad
        val intent = requireActivity().intent

        // Obtener el valor de la cadena "aux" del Intent
        val aux = intent.getStringExtra("aux")

        // Verificar si se debe realizar una limpieza en SharedPreferences
        if (aux == "0") {
            // Obtener el SharedPreferences de la actividad
            val sharedPref = requireActivity().getPreferences(Context.MODE_PRIVATE)

            // Limpiar todas las entradas en SharedPreferences
            with(sharedPref.edit()) {
                clear()
                apply()
            }
        }

        // Obtener el SharedPreferences de la actividad
        val sharedPref = requireActivity().getPreferences(Context.MODE_PRIVATE)

        // Recuperar el valor asociado a la clave "email" en SharedPreferences
        val email = sharedPref.getString("email", null)

        // Verificar si hay información del usuario almacenada en SharedPreferences
        if (email != null) {
            // Hay información del usuario almacenada, crear un Intent para la actividad RoomMain
            val newIntent = Intent(requireActivity(), MainActivity::class.java)

            // Añadir el correo electrónico como extra al Intent
            newIntent.putExtra("email", email)

            // Iniciar la actividad RoomMain
            startActivity(newIntent)

            // Opcionalmente, cerrar la actividad actual si no se desea volver a ella
            requireActivity().finish()
        }
    }


    private fun navigateToMainActivity() {
        val intent = Intent(requireContext(), MainActivity::class.java)
        startActivity(intent)
        requireActivity().finish() // Opcional: Finaliza la actividad actual si no quieres que siga en la pila de actividades
    }
}