package com.example.inventorysystem

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat

class HomeActivity : AppCompatActivity(), SignInFragment.OnSignInButtonClickListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        //Código para poner una palabra en negritas
        val tvRegister = findViewById<TextView>(R.id.tvRegister)
        val rawText = getString(R.string.text_with_bold)
        val formattedText = HtmlCompat.fromHtml(rawText, HtmlCompat.FROM_HTML_MODE_LEGACY)
        tvRegister.text = formattedText

        // Cargar por defecto el LoginFragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SignInFragment())
                .commit()
        }

        // OnClickListener al tvRegister para cambiar al RegisterFragment
        tvRegister.setOnClickListener {
            showRegisterFragment()
        }
    }

    // Implementa el método de la interfaz
    override fun onSignInButtonClick() {
        // Maneja el clic del botón aquí
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
    }

    fun showRegisterFragment() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.fragment_left_enter,   // Animación de entrada
                R.anim.fragment_right_exit     // Animación de salida
            )
            .replace(R.id.fragment_container, RegisterFragment())
            .addToBackStack(null)  // Para poder regresar al fragmento anterior
            .commit()
    }

    fun showLoginFragment() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.fragment_right_enter,   // Animación de entrada
                R.anim.fragment_right_exit     // Animación de salida
            )
            .replace(R.id.fragment_container, SignInFragment())
            .addToBackStack(null)
            .commit()
    }
}