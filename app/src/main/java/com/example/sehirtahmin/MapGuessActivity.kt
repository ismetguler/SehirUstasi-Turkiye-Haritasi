package com.ismetguler.sehirtahmin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldValue
import java.util.UUID
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

class MapGuessActivity : AppCompatActivity(), MapView.OnCityClickListener, MapView.OnDataLoadedListener {

    private lateinit var mapView: MapView
    private lateinit var textViewCityNameQuestion: TextView
    private lateinit var textViewScoreMap: TextView
    private lateinit var heartIcons: List<ImageView>

    // Oyun Sonu UI ve Butonları
    private lateinit var layoutGameOverMap: View
    private lateinit var textViewMapResult: TextView
    private lateinit var textViewMapRecord: TextView
    private lateinit var buttonWatchAdMap: Button
    private lateinit var buttonPlayAgainMap: Button
    private lateinit var buttonExitToMenuMap: Button

    private var currentQuestionCity: CityData? = null
    private var allCities: List<CityData> = emptyList()

    private var lives: Int = 5
    private val MAX_LIVES = 5

    private val TOTAL_QUESTIONS_TO_ASK = 81
    private var totalQuestionsAsked: Int = 0

    private var correctGuessesInCurrentRound: Int = 0
    private var maxCorrectGuessesRecord: Int = 0
    private lateinit var userId: String
    private lateinit var userName: String
    private var isGoogleSignIn: Boolean = false

    private val HIGHLIGHT_DURATION_MS = 1500L

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private var rewardedAd: RewardedAd? = null
    private var adContinueCount: Int = 0
    private val AD_UNIT_ID = "ca-app-pub-5525493554747766/3276861372"

    // --- GEÇİŞ REKLAMI (INTERSTITIAL AD) İÇİN YENİ DEĞİŞKEN VE METOTLAR ---
    private var mInterstitialAd: InterstitialAd? = null

    companion object {
        private const val TAG = "MapGuessActivity"
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_guess)

        MobileAds.initialize(this) {}
        loadRewardedAd()
        loadInterstitialAd() // Geçiş reklamını da yükle

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        val currentUser = auth.currentUser
        if (currentUser != null) {
            userId = currentUser.uid
            userName = currentUser.displayName ?: "Google Kullanıcısı"
            isGoogleSignIn = true
            Log.d(TAG, "Kullanıcı durumu: Google ile giriş yapıldı, UID: $userId, Ad: $userName")
        } else {
            userId = getGuestUserId()
            userName = "Misafir"
            isGoogleSignIn = false
            Log.d(TAG, "Kullanıcı durumu: Misafir, ID: $userId, Ad: $userName")
        }
        loadMapGameRecord()

        mapView = findViewById(R.id.mapViewGame)
        textViewCityNameQuestion = findViewById(R.id.textViewCityNameQuestion)
        textViewScoreMap = findViewById(R.id.textViewScoreMap)
        heartIcons = listOf(
            findViewById(R.id.imageViewHeart1Map),
            findViewById(R.id.imageViewHeart2Map),
            findViewById(R.id.imageViewHeart3Map),
            findViewById(R.id.imageViewHeart4Map),
            findViewById(R.id.imageViewHeart5Map)
        )

        layoutGameOverMap = findViewById(R.id.layoutGameOverMap)
        textViewMapResult = findViewById(R.id.textViewMapResult)
        textViewMapRecord = findViewById(R.id.textViewMapRecord)
        buttonWatchAdMap = findViewById(R.id.buttonWatchAdMap)
        buttonPlayAgainMap = findViewById(R.id.buttonPlayAgainMap)
        buttonExitToMenuMap = findViewById(R.id.buttonExitToMenuMap)

        buttonWatchAdMap.setOnClickListener { showRewardedAd() }
        buttonPlayAgainMap.setOnClickListener { restartGame() }

        // --- BUTON: Menüye dön butonunu reklam gösterecek şekilde düzenle ---
        buttonExitToMenuMap.setOnClickListener {
            // Menüye dönerken geçiş reklamı göster
            if (mInterstitialAd != null) {
                mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Log.d(TAG, "Reklam kapatıldı. Ana menüye dönülüyor.")
                        // Reklam kapatıldıktan sonra ana menüye dön
                        val intent = Intent(this@MapGuessActivity, MainMenuActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                        finish()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                        Log.e(TAG, "Reklam gösterilemedi: ${adError.message}")
                        // Reklam gösterilemezse bile ana menüye dön
                        val intent = Intent(this@MapGuessActivity, MainMenuActivity::class.java)
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
        // --- BUTON SONU ---

        mapView.onCityClickListener = this
        mapView.onDataLoadedListener = this

        updateGameUI()
    }

    override fun onGeoJsonDataLoaded() {
        Log.d(TAG, "GeoJSON verisi yüklendi. Şehir sayısı: ${mapView.getAllLoadedCities().size}")
        allCities = mapView.getAllLoadedCities().shuffled()
        if (allCities.isNotEmpty()) {
            startGame()
        } else {
            Toast.makeText(this, "Şehir verisi yüklenemedi. Lütfen uygulamayı yeniden başlatın.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Harita verisi boş veya yüklenemedi. allCities listesi boş.")
        }
    }

    private fun startGame() {
        Log.d(TAG, "Oyun Başladı.")
        correctGuessesInCurrentRound = 0
        lives = MAX_LIVES
        totalQuestionsAsked = 0
        adContinueCount = 0

        mapView.clearAllHighlights()
        updateGameUI()
        mapView.onCityClickListener = this
        generateNewQuestion()
        hideGameOverScreen()
    }

    private fun generateNewQuestion() {
        if (totalQuestionsAsked >= TOTAL_QUESTIONS_TO_ASK) {
            endGame()
            return
        }
        mapView.updateHighlights(null, null, null)

        currentQuestionCity = allCities[totalQuestionsAsked]
        totalQuestionsAsked++

        textViewCityNameQuestion.text = getString(R.string.city_guess_prompt, currentQuestionCity?.ilAdi)
        Log.d(TAG, "Yeni soru: ${currentQuestionCity?.ilAdi}. Sorulan Soru: $totalQuestionsAsked/$TOTAL_QUESTIONS_TO_ASK. Can: $lives")

        updateGameUI()
    }

    override fun onCityClicked(clickedCity: CityData?) {
        if (currentQuestionCity == null) {
            Log.w(TAG, "currentQuestionCity boş. Tıklama işlemi yoksayıldı.")
            return
        }

        mapView.onCityClickListener = null

        val isCorrect = clickedCity?.ilAdi == currentQuestionCity?.ilAdi
        Log.d(TAG, "Tıklanan: ${clickedCity?.ilAdi}. Doğru: ${currentQuestionCity?.ilAdi}. Sonuç: $isCorrect")

        if (isCorrect) {
            correctGuessesInCurrentRound++
            Toast.makeText(this, "Doğru!", Toast.LENGTH_SHORT).show()
            mapView.updateHighlights(currentQuestionCity, null, true)
        } else {
            lives--
            Toast.makeText(this, "Yanlış! Doğru cevap: ${currentQuestionCity?.ilAdi}", Toast.LENGTH_SHORT).show()
            mapView.updateHighlights(currentQuestionCity, null, false)
        }
        updateGameUI()

        Handler(Looper.getMainLooper()).postDelayed({
            if (lives <= 0) {
                endGame()
            } else if (totalQuestionsAsked < TOTAL_QUESTIONS_TO_ASK) {
                mapView.onCityClickListener = this
                generateNewQuestion()
            }
        }, HIGHLIGHT_DURATION_MS)
    }

    private fun updateGameUI() {
        textViewScoreMap.text = getString(R.string.score_and_question_text, correctGuessesInCurrentRound, totalQuestionsAsked)

        for (i in heartIcons.indices) {
            if (i < lives) {
                heartIcons[i].setColorFilter(ContextCompat.getColor(this, R.color.red_heart))
            } else {
                heartIcons[i].setColorFilter(ContextCompat.getColor(this, R.color.gray_heart_empty))
            }
        }
    }

    private fun endGame() {
        Log.d(TAG, "Oyun Bitti! Bu turda bilinen şehir: $correctGuessesInCurrentRound. Kalan Can: $lives. Toplam Soru: $totalQuestionsAsked")

        mapView.onCityClickListener = null

        if (correctGuessesInCurrentRound > maxCorrectGuessesRecord) {
            maxCorrectGuessesRecord = correctGuessesInCurrentRound
            saveMapGameRecord(maxCorrectGuessesRecord)
            updateUserScoreInFirebase(maxCorrectGuessesRecord, "mapScores")
        }

        // REKLAM ÇAĞRISI BURADAN KALDIRILDI.

        showGameOverScreen()
    }

    private fun restartGame() {
        correctGuessesInCurrentRound = 0
        lives = MAX_LIVES
        totalQuestionsAsked = 0
        adContinueCount = 0

        updateGameUI()
        startGame()
        loadRewardedAd()
    }

    private fun loadMapGameRecord() {
        val sharedPref = getSharedPreferences("MapGameRecords", MODE_PRIVATE)
        maxCorrectGuessesRecord = sharedPref.getInt("map_record_$userId", 0)
        Log.d(TAG, "Yüklenen harita rekoru ($userId): $maxCorrectGuessesRecord")
    }

    private fun saveMapGameRecord(record: Int) {
        val sharedPref = getSharedPreferences("MapGameRecords", MODE_PRIVATE)
        with (sharedPref.edit()) {
            putInt("map_record_$userId", record)
            apply()
        }
        Log.d(TAG, "Kaydedilen harita rekoru ($userId): $record")
    }

    private fun updateUserScoreInFirebase(newScore: Int, collectionName: String) {
        Log.d(TAG, "updateUserScoreInFirebase çağrıldı. Yeni Skor: $newScore, Kullanıcı ID: $userId")
        firestore.collection(collectionName)
            .whereEqualTo("userId", userId)
            .orderBy("correctAnswersCount", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val existingScoreDoc = querySnapshot.documents[0]
                    val existingPlayerScore = existingScoreDoc.toObject(PlayerScore::class.java)

                    if (existingPlayerScore != null && newScore > existingPlayerScore.correctAnswersCount) {
                        existingScoreDoc.reference.update(
                            "correctAnswersCount", newScore,
                            "playerName", userName,
                            "timestamp", FieldValue.serverTimestamp()
                        )
                            .addOnSuccessListener {
                                Log.d(TAG, "$collectionName koleksiyonunda skor başarıyla güncellendi (yeni rekor): ${existingScoreDoc.id}")
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "$collectionName koleksiyonunda skor güncellenirken hata oluştu: ${e.message}", e)
                            }
                    } else {
                        Log.d(TAG, "Yeni skor mevcut rekordan daha düşük veya eşit.")
                    }
                } else {
                    val scoreEntry = PlayerScore(
                        userId = userId,
                        playerName = userName,
                        correctAnswersCount = newScore,
                        googleSignIn = isGoogleSignIn
                    )
                    firestore.collection(collectionName)
                        .add(scoreEntry)
                        .addOnSuccessListener { documentReference ->
                            Log.d(TAG, "$collectionName koleksiyonuna yeni skor başarıyla eklendi: ${documentReference.id}")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "$collectionName koleksiyonuna yeni skor eklenirken hata oluştu: ${e.message}", e)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Mevcut skor kontrol sorgusu hatası: ${e.message}", e)
            }
    }


    private fun getGuestUserId(): String {
        val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        var guestId = sharedPref.getString("guest_id", null)
        if (guestId == null) {
            guestId = UUID.randomUUID().toString()
            sharedPref.edit().putString("guest_id", guestId).apply()
        }
        return guestId
    }

    private fun showGameOverScreen() {
        textViewMapResult.text = getString(R.string.final_result_text, correctGuessesInCurrentRound)
        textViewMapRecord.text = getString(R.string.high_score_end_text, maxCorrectGuessesRecord)

        val livesToGive = when (adContinueCount) {
            0 -> 3
            1 -> 2
            2 -> 1
            else -> 0
        }

        if (livesToGive > 0 && rewardedAd != null) {
            buttonWatchAdMap.visibility = View.VISIBLE
            buttonWatchAdMap.text = getString(R.string.watch_ad_to_continue, livesToGive)
        } else {
            buttonWatchAdMap.visibility = View.GONE
        }

        layoutGameOverMap.alpha = 0f
        layoutGameOverMap.visibility = View.VISIBLE
        layoutGameOverMap.animate().alpha(1f).setDuration(300).start()
    }

    private fun hideGameOverScreen() {
        layoutGameOverMap.animate().alpha(0f).setDuration(200).withEndAction {
            layoutGameOverMap.visibility = View.GONE
        }.start()
    }

    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(this, AD_UNIT_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.e(TAG, "Ödüllü reklam yüklenemedi: ${adError.message}")
                rewardedAd = null
            }

            override fun onAdLoaded(ad: RewardedAd) {
                Log.d(TAG, "Ödüllü reklam yüklendi.")
                rewardedAd = ad
            }
        })
    }

    private fun showRewardedAd() {
        rewardedAd?.let { ad ->
            ad.show(this, OnUserEarnedRewardListener { rewardItem ->
                Log.d(TAG, "Ödüllü reklam başarıyla izlendi. Ödül veriliyor.")

                val livesToGive = when (adContinueCount) {
                    0 -> 3
                    1 -> 2
                    2 -> 1
                    else -> 0
                }

                if (livesToGive > 0) {
                    lives += livesToGive
                    adContinueCount++
                    updateGameUI()
                    hideGameOverScreen()
                    mapView.onCityClickListener = this
                    generateNewQuestion()
                    Toast.makeText(this, "$livesToGive can kazandın! Oyuna devam et.", Toast.LENGTH_SHORT).show()
                }

                loadRewardedAd()
            })
        } ?: run {
            Log.e(TAG, "Ödüllü reklam henüz hazır değil.")
            Toast.makeText(this, "Reklam henüz yüklenmedi, lütfen tekrar deneyin.", Toast.LENGTH_SHORT).show()
        }
    }
}