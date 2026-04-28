package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request

class LoginActivity : AppCompatActivity() {

    private lateinit var usernameInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var loginButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if already logged in
        if (isLoggedIn()) {
            navigateToMainActivity()
            return
        }

        setContentView(R.layout.activity_login)

        usernameInput = findViewById(R.id.usernameInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginButton = findViewById(R.id.loginButton)

        // Auto fill saved credentials
        loadSavedCredentials()

        // Handle login button click
        loginButton.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter username and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validate credentials with API
            validateCredentials(username, password)
        }
    }

    private fun isLoggedIn(): Boolean {
        val sharedPrefs = getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
        return sharedPrefs.contains("username") && sharedPrefs.contains("password")
    }

    private fun loadSavedCredentials() {
        val sharedPrefs = getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
        val savedUsername = sharedPrefs.getString("username", "")
        val savedPassword = sharedPrefs.getString("password", "")

        usernameInput.setText(savedUsername)
        passwordInput.setText(savedPassword)
    }

    private fun saveCredentials(username: String, password: String) {
        val sharedPrefs = getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().apply {
            putString("username", username)
            putString("password", password)
            apply()
        }
    }

    private fun validateCredentials(username: String, password: String) {
        // Disable button during validation
        loginButton.isEnabled = false
        loginButton.text = "Validating..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Make a test API call to verify credentials
                val client = OkHttpClient()
                val credential = Credentials.basic(username, password)

                val request = Request.Builder()
                    .url("https://testing.pt1view.com/services/api/alarms?limit=1")
                    .header("Authorization", credential)
                    .build()

                val response = client.newCall(request).execute()

                withContext(Dispatchers.Main) {
                    when (response.code) {
                        200, 204 -> {
                            // Success - credentials are valid
                            saveCredentials(username, password)
                            navigateToMainActivity()
                        }
                        401 -> {
                            // Unauthorized - invalid credentials
                            Toast.makeText(
                                this@LoginActivity,
                                "Invalid OneView credentials. Please check your username and password.",
                                Toast.LENGTH_LONG
                            ).show()
                            loginButton.isEnabled = true
                            loginButton.text = "Login"
                        }
                        else -> {
                            // Other error (network, server, etc.)
                            Toast.makeText(
                                this@LoginActivity,
                                "Unable to verify credentials. Error: ${response.code}",
                                Toast.LENGTH_LONG
                            ).show()
                            loginButton.isEnabled = true
                            loginButton.text = "Login"
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@LoginActivity,
                        "Network error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    loginButton.isEnabled = true
                    loginButton.text = "Login"
                }
            }
        }
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish() // Close login activity so back button doesn't return here
    }
}