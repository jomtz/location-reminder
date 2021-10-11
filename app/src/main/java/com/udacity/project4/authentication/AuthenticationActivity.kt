package com.udacity.project4.authentication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.viewModels
import androidx.lifecycle.observe
import androidx.lifecycle.Observer
import com.firebase.ui.auth.AuthUI
import com.udacity.project4.R
import com.udacity.project4.databinding.ActivityAuthenticationBinding
import com.udacity.project4.locationreminders.RemindersActivity

/**
 * This class should be the starting point of the app, It asks the users to sign in / register, and redirects the
 * signed in users to the RemindersActivity.
 */
class AuthenticationActivity : AppCompatActivity() {

    companion object {
        const val SIGN_IN_REQUEST_CODE = 1
    }

    private lateinit var binding: ActivityAuthenticationBinding
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAuthenticationBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        // Implement the create account and sign in using FirebaseUI, use sign in using email and sign in using Google
        viewModel.authenticationState.observe(this, Observer{ authenticationState ->
            // If the user was authenticated, send him to RemindersActivity
            if (authenticationState == LoginViewModel.AuthenticationState.AUTHENTICATED) {

                binding.loginButton.setOnClickListener { remindersActivityIntent() }

            } else if (authenticationState == LoginViewModel.AuthenticationState.UNAUTHENTICATED) {

                binding.loginButton.setOnClickListener { launchSignInFlow() }
            }
        })

    }

    private fun launchSignInFlow() {
        val providers = arrayListOf(
            AuthUI.IdpConfig.EmailBuilder().build(), AuthUI.IdpConfig.GoogleBuilder().build()
        )
        startActivityForResult(
            AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAvailableProviders(providers)
                .build(),
            SIGN_IN_REQUEST_CODE
        )
    }

    private fun remindersActivityIntent(){
        val intent = Intent(this, RemindersActivity::class.java)
        startActivity(intent)
    }
}
