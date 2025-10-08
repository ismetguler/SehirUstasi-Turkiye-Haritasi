package com.ismetguler.sehirtahmin

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.enableEdgeToEdge // New import for edge-to-edge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat // Already there, but good to keep
import androidx.core.view.WindowInsetsCompat // Already there, but good to keep
import androidx.core.view.WindowInsetsControllerCompat // Already there, but good to keep
import com.ismetguler.sehirtahmin.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // Call enableEdgeToEdge() BEFORE super.onCreate() and setContentView()
        // This sets up the window for edge-to-edge display and handles status/navigation bar colors
        enableEdgeToEdge() // This line handles the deprecated statusBarColor and navigationBarColor

        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // After setting content view, apply specific insets behavior.
        // enableEdgeToEdge() makes bars transparent and extends content,
        // but if you want to HIDE them, you still use WindowInsetsControllerCompat.
        WindowInsetsControllerCompat(window, binding.root).let { controller ->
            // Hide the status bars and navigation bars
            controller.hide(WindowInsetsCompat.Type.systemBars())
            // Set the behavior for how system bars are shown (e.g., by swipe)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        // The following lines are no longer needed as enableEdgeToEdge() handles transparency,
        // and the controller above handles hiding.
        // window.statusBarColor = android.graphics.Color.TRANSPARENT
        // window.navigationBarColor = android.graphics.Color.TRANSPARENT


        // Splash ekranından MainMenuActivity'ye geçiş
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainMenuActivity::class.java)
            startActivity(intent)
            finish()
        }, 3000) // 3 saniye sonra geçiş yap
    }
}