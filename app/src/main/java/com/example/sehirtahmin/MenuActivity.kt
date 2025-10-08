package com.ismetguler.sehirtahmin

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.ismetguler.sehirtahmin.databinding.ActivityMenuBinding

// --- REKLAM İÇİN YENİ IMPORT'LAR ---
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
// --- REKLAM IMPORT'LARI SONU ---

class MainMenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMenuBinding
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var auth: FirebaseAuth

    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>

    // --- REKLAM İÇİN YENİ DEĞİŞKEN ---
    private var mInterstitialAd: InterstitialAd? = null

    companion object {
        private const val TAG = "MainMenuActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowInsetsControllerCompat(window, binding.root).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        auth = FirebaseAuth.getInstance()

        // --- REKLAM: Uygulama açılışında reklamı yüklemeye başla ---
        loadInterstitialAd()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data: Intent? = result.data
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                Log.d(TAG, "Google sign in başarılı, Firebase ile doğrulanıyor.")
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: com.google.android.gms.common.api.ApiException) {
                Log.w(TAG, "Google sign in başarısız", e)
                Toast.makeText(this, "Google Girişi İptal Edildi veya Hata Oluştu.", Toast.LENGTH_SHORT).show()
                updateUIForSignedOutUser()
            }
        }

        binding.buttonStartGame.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        binding.buttonStartMapMode.setOnClickListener {
            val intent = Intent(this, MapGuessActivity::class.java)
            startActivity(intent)
        }

        binding.buttonLeaderboard.setOnClickListener {
            val currentUserSession = SessionManager.getCurrentUser()
            if (currentUserSession?.isGoogleSignIn == true) {
                val intent = Intent(this, LeaderboardActivity::class.java)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Liderlik tablosunu görüntülemek için Google ile giriş yapmalısınız.", Toast.LENGTH_LONG).show()
            }
        }

        binding.buttonAboutGame.setOnClickListener {
            val intent = Intent(this, AboutGameActivity::class.java)
            startActivity(intent)
            Log.d(TAG, "Hakkında butonu tıklandı, AboutGameActivity başlatılıyor.")
        }

        binding.buttonSignInGoogle.setOnClickListener {
            signInWithGoogle()
        }

        binding.buttonSignOut.setOnClickListener {
            signOut()
        }

        binding.buttonPlayAsGuest.setOnClickListener {
            signInAnonymously()
        }
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private fun signOut() {
        SessionManager.endSession()
        Log.d(TAG, "SessionManager.endSession() çağrıldı.")

        googleSignInClient.signOut().addOnCompleteListener(this) {
            Log.d(TAG, "Google hesabından çıkış yapıldı.")
            Toast.makeText(this, "Google hesabından çıkış yapıldı.", Toast.LENGTH_SHORT).show()
            updateUIForSignedOutUser()
            // --- REKLAM: Çıkış yapıldıktan sonra reklamı göster ---
            showInterstitialAd()
        }.addOnFailureListener { e ->
            Log.e(TAG, "Google çıkış hatası: ${e.message}", e)
            Toast.makeText(this, "Çıkış yapılırken hata oluştu.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun signInAnonymously() {
        auth.signInAnonymously()
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    Log.d(TAG, "Misafir olarak giriş başarılı. UID: ${user?.uid}")
                    Toast.makeText(this, "Misafir olarak giriş yapıldı.", Toast.LENGTH_SHORT).show()
                    SessionManager.startGuestSession()
                    updateUIForSignedInUser(SessionManager.getCurrentUser())
                    // --- REKLAM: Misafir girişi başarılı olunca reklamı göster ---
                    showInterstitialAd()
                } else {
                    Log.w(TAG, "Misafir olarak giriş başarısız", task.exception)
                    Toast.makeText(this, "Misafir olarak giriş yapılamadı.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    Log.d(TAG, "Firebase Google Auth başarılı. UID: ${user?.uid}, DisplayName: ${user?.displayName}")
                    Toast.makeText(this, "Google ile giriş yapıldı: ${user?.displayName}", Toast.LENGTH_SHORT).show()
                    SessionManager.startGoogleSession(user!!)
                    updateUIForSignedInUser(SessionManager.getCurrentUser())
                    // --- REKLAM: Google girişi başarılı olunca reklamı göster ---
                    showInterstitialAd()
                } else {
                    Log.w(TAG, "Firebase Google Auth başarısız", task.exception)
                    Toast.makeText(this, "Firebase Doğrulama Başarısız: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    updateUIForSignedOutUser()
                }
            }
    }

    private fun updateUIForSignedInUser(userSession: UserSession?) {
        if (userSession != null) {
            binding.textViewStatus.text = "Giriş Yapıldı: ${userSession.playerName}"
            binding.buttonSignInGoogle.visibility = View.GONE
            binding.buttonPlayAsGuest.visibility = View.GONE
            binding.buttonSignOut.visibility = View.VISIBLE
            binding.buttonStartGame.visibility = View.VISIBLE
            binding.buttonStartMapMode.visibility = View.VISIBLE
            binding.buttonLeaderboard.visibility = View.VISIBLE
            binding.buttonAboutGame.visibility = View.VISIBLE
        }
    }

    private fun updateUIForSignedOutUser() {
        binding.textViewStatus.text = "Giriş Yapın veya Misafir Olarak Oynayın"
        binding.buttonSignInGoogle.visibility = View.VISIBLE
        binding.buttonPlayAsGuest.visibility = View.VISIBLE
        binding.buttonSignOut.visibility = View.GONE
        binding.buttonStartGame.visibility = View.GONE
        binding.buttonStartMapMode.visibility = View.GONE
        binding.buttonLeaderboard.visibility = View.GONE
        binding.buttonAboutGame.visibility = View.GONE
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            if (currentUser.isAnonymous) {
                SessionManager.startGuestSession()
                Log.d(TAG, "Mevcut Firebase anonim kullanıcı algılandı, misafir oturumu başlatıldı.")
            } else {
                val googleAccount = GoogleSignIn.getLastSignedInAccount(this)
                if (googleAccount != null && googleAccount.idToken != null) {
                    SessionManager.startGoogleSession(currentUser)
                    Log.d(TAG, "Mevcut Firebase Google kullanıcısı algılandı, Google oturumu başlatıldı.")
                } else {
                    auth.signOut()
                    SessionManager.endSession()
                    Log.d(TAG, "Firebase kullanıcısı var ancak geçerli bir Google oturumu bulunamadı. Firebase oturumu sonlandırıldı.")
                    updateUIForSignedOutUser()
                    return
                }
            }
            updateUIForSignedInUser(SessionManager.getCurrentUser())
        } else {
            updateUIForSignedOutUser()
            Log.d(TAG, "Hiçbir kullanıcı oturumu algılanmadı.")
        }
    }

    // --- REKLAM İÇİN YENİ METOTLAR ---
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
                mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Log.d(TAG, "Ad dismissed. Loading new ad...")
                        loadInterstitialAd()
                    }
                }
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
    // --- REKLAM METOTLARI SONU ---
}