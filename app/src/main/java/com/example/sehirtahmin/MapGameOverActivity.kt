package com.ismetguler.sehirtahmin

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.ismetguler.sehirtahmin.databinding.ActivityMapGameOverBinding

class MapGameOverActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapGameOverBinding

    private var mInterstitialAd: InterstitialAd? = null

    companion object {
        private const val TAG = "MapGameOverActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMapGameOverBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Reklamı yükle
        loadInterstitialAd()

        // Reklamı oyun bitiş ekranı açılırken göster
        showInterstitialAd()

        // Tam ekran modu için
        WindowInsetsControllerCompat(window, binding.root).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val correctGuessesThisRound = intent.getIntExtra("correct_guesses_this_round", 0)
        val maxRecordAchieved = intent.getIntExtra("max_record_achieved", 0)

        binding.textViewGuessesThisRound.text = "Bu turda bildiğin şehir: $correctGuessesThisRound"
        binding.textViewMaxRecord.text = "Rekorun: $maxRecordAchieved"

        binding.buttonPlayAgain.setOnClickListener {
            val intent = Intent(this, MapGuessActivity::class.java)
            startActivity(intent)
            finish()
        }

        binding.buttonExitToMenu.setOnClickListener {
            // Ana menüye dönerken geçiş reklamı göster
            if (mInterstitialAd != null) {
                mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Log.d(TAG, "Reklam kapatıldı. Ana menüye dönülüyor.")
                        // Reklam kapatıldıktan sonra ana menüye dön
                        val intent = Intent(this@MapGameOverActivity, MainMenuActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                        finish()
                    }
                    override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                        Log.e(TAG, "Reklam gösterilemedi: ${adError.message}")
                        // Reklam gösterilemezse bile ana menüye dön
                        val intent = Intent(this@MapGameOverActivity, MainMenuActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                        finish()
                    }
                }
                mInterstitialAd?.show(this)
            } else {
                // Reklam hazır değilse direkt ana menüye dön
                Log.d(TAG, "Geçiş reklamı hazır değil. Doğrudan ana menüye dönülüyor.")
                val intent = Intent(this, MainMenuActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            }
        }
    }

    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this, "ca-app-pub-5525493554747766/4123039186", adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d(TAG, adError.toString())
                mInterstitialAd = null
            }
            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                Log.d(TAG, "Interstitial ad loaded.")
                mInterstitialAd = interstitialAd
            }
        })
    }

    private fun showInterstitialAd() {
        if (mInterstitialAd != null) {
            Log.d(TAG, "Showing interstitial ad.")
            mInterstitialAd?.show(this)
        } else {
            Log.d(TAG, "Interstitial ad is not ready yet.")
        }
    }
}