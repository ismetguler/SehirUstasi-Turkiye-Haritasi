package com.ismetguler.sehirtahmin

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.ismetguler.sehirtahmin.databinding.ActivityMainBinding
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.UUID
import java.util.Collections
import com.google.firebase.auth.GoogleAuthProvider
import java.util.Date

// --- REKLAM İÇİN YENİ IMPORT'LAR ---
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
// --- REKLAM IMPORT'LARI SONU ---

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // --- Oyun Durum Yönetimi ---
    private var questionNumber: Int = 0
    private var currentQuestionIndex: Int = 0
    private var fiftyFiftyUseCount: Int = 0
    private var hintUseCount: Int = 0
    private var currentScore: Int = 0
    private var correctAnswersCount: Int = 0
    private var lives: Int = 3
    private var highScoreCorrectAnswers: Int = 0

    // *** REKLAM: AdMob için değişkenler ***
    private var adContinueCount: Int = 0
    private var rewardedAd: RewardedAd? = null
    private var adUnitId: String = "ca-app-pub-5525493554747766/6940774218"
    private var mInterstitialAd: InterstitialAd? = null

    private lateinit var questions: List<Question>

    // Firebase ve SharedPreferences değişkenleri
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var sharedPreferences: SharedPreferences

    private var currentUserId: String = ""
    private var currentPlayerName: String = ""
    private var isGoogleSignedInUser: Boolean = false
    private var currentUserSession: UserSession? = null

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "GamePrefs"
        private const val KEY_LOCAL_HIGH_SCORE = "local_high_score"
        private const val FIRESTORE_SCORES_COLLECTION = "scores"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowInsetsControllerCompat(window, binding.root).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // --- REKLAM: AdMob SDK'sını başlat ve reklamları yükle ---
        MobileAds.initialize(this) {}
        loadRewardedAd()
        loadInterstitialAd()

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        currentUserSession = SessionManager.getCurrentUser()

        currentUserSession?.let { session ->
            currentUserId = session.userId
            currentPlayerName = session.playerName ?: "Bilinmeyen Kullanıcı"
            isGoogleSignedInUser = session.isGoogleSignIn
            Log.d(TAG, "Mevcut Oturum Bilgileri (SessionManager): UID=${currentUserId}, Ad=${currentPlayerName}, Google Girişi: $isGoogleSignedInUser")
        } ?: run {
            SessionManager.startGuestSession()
            currentUserSession = SessionManager.getCurrentUser()
            currentUserSession?.let { session ->
                currentUserId = session.userId
                currentPlayerName = session.playerName ?: "Bilinmeyen Misafir"
                isGoogleSignedInUser = session.isGoogleSignIn
                Log.w(TAG, "MainActivity başlatılırken SessionManager'da kullanıcı yoktu, fallback misafir oturumu başlatıldı. UID: $currentUserId, Ad: $currentPlayerName, Google Girişi: $isGoogleSignedInUser")
            } ?: run {
                currentUserId = "unknown_guest_${UUID.randomUUID()}"
                currentPlayerName = "Bilinmeyen Misafir"
                isGoogleSignedInUser = false
                Log.e(TAG, "HATA: Misafir oturumu bile başlatılamadı! Geçici varsayılan değerler kullanıldı.")
            }
        }

        binding.buttonOption1.setOnClickListener { checkAnswer(binding.buttonOption1) }
        binding.buttonOption2.setOnClickListener { checkAnswer(binding.buttonOption2) }
        binding.buttonOption3.setOnClickListener { checkAnswer(binding.buttonOption3) }
        binding.buttonOption4.setOnClickListener { checkAnswer(binding.buttonOption4) }

        binding.buttonFiftyFifty.setOnClickListener { showFiftyFiftyConfirm() }
        binding.buttonHint.setOnClickListener { showHintConfirm() }

        binding.buttonFiftyFiftyConfirmYes.setOnClickListener { useFiftyFifty() }
        binding.buttonFiftyFiftyConfirmNo.setOnClickListener { hideFiftyFiftyConfirm() }

        binding.buttonHintConfirmYes.setOnClickListener { useHint() }
        binding.buttonHintConfirmNo.setOnClickListener { hideHintConfirm() }

        binding.buttonGoToMainMenu.setOnClickListener { goToMainMenu() }
        binding.buttonRestartGameOnWrongAnswer.setOnClickListener { restartGame() }
        binding.buttonWatchAd.setOnClickListener { showRewardedAd() }

        binding.buttonRestartGame.setOnClickListener { restartGame() }
        binding.buttonGoToMainMenuFinal.setOnClickListener { goToMainMenu() }
        binding.buttonShowLeaderboard.setOnClickListener {
            val intent = Intent(this, LeaderboardActivity::class.java)
            startActivity(intent)
        }

        fetchHighScore(currentUserId, isGoogleSignedInUser) { fetchedScore ->
            highScoreCorrectAnswers = fetchedScore
            updateHighScoreDisplayInGame()
            Log.d(TAG, "onCreate: Yüksek skor yüklendi: $highScoreCorrectAnswers (Google:${isGoogleSignedInUser})")
        }

        questions = generateAllQuestions().shuffled()
        startGame()
    }

    override fun onResume() {
        super.onResume()
        updateFiftyFiftyButtonText()
        updateHintButtonText()
        fetchHighScore(currentUserId, isGoogleSignedInUser) { fetchedHighScore ->
            if (fetchedHighScore != highScoreCorrectAnswers) {
                highScoreCorrectAnswers = fetchedHighScore
                updateHighScoreDisplayInGame()
                Log.d(TAG, "onResume: Yüksek skor yeniden yüklendi ve güncellendi: $highScoreCorrectAnswers")
            } else {
                Log.d(TAG, "onResume: Yüksek skor zaten güncel: $highScoreCorrectAnswers")
            }
        }
    }

    private fun startGame() {
        Log.d(TAG, "Oyun başlıyor. Yeni oyun başlatıldı.")
        correctAnswersCount = 0
        questionNumber = 0
        currentQuestionIndex = 0
        fiftyFiftyUseCount = 0
        hintUseCount = 0
        lives = 3
        currentScore = 0
        adContinueCount = 0
        loadRewardedAd()

        fetchHighScore(currentUserId, isGoogleSignedInUser) { fetchedHighScore ->
            highScoreCorrectAnswers = fetchedHighScore
            updateHighScoreDisplayInGame()
            Log.d(TAG, "Oyun başlangıcı: Kullanıcının çekilen yüksek skoru: $highScoreCorrectAnswers (Google:${isGoogleSignedInUser})")
        }

        updateScore(currentScore)
        updateQuestionNumber(0)
        updateHeartUI()
        updateFiftyFiftyButtonText()
        updateHintButtonText()

        hideEndGameScreens()
        hideJokerConfirmationsAndHintDisplay()
        showGameElements()

        questions = generateAllQuestions().shuffled()
        Log.d(TAG, "Toplam soru sayısı: ${questions.size}")

        if (questions.isEmpty()) {
            Log.e(TAG, "HATA: generateAllQuestions() boş bir liste döndürdü. Lütfen soruları kontrol edin!")
            Toast.makeText(this, "Oyun başlatılamadı: Soru bulunamadı!", Toast.LENGTH_LONG).show()
            endGame(allQuestionsAnswered = false)
            return
        }
        loadQuestion()
    }

    private fun loadQuestion() {
        Log.d(TAG, "loadQuestion çağrıldı. Yüklenecek soru indeksi: $currentQuestionIndex, Toplam soru: ${questions.size}")
        resetButtonColors()
        enableAllButtons()
        hideJokerConfirmationsAndHintDisplay()

        binding.buttonOption1.visibility = View.VISIBLE
        binding.buttonOption2.visibility = View.VISIBLE
        binding.buttonOption3.visibility = View.VISIBLE
        binding.buttonOption4.visibility = View.VISIBLE

        updateFiftyFiftyButtonText()
        updateHintButtonText()

        showGameElements()

        if (currentQuestionIndex < questions.size) {
            val currentQuestion = questions[currentQuestionIndex]
            Log.d(TAG, "Yüklenen soru: Resim ID: ${currentQuestion.imageResId}, Doğru Cevap: ${currentQuestion.correctCityName}, İpucu: ${currentQuestion.hint}")

            updateQuestionNumber(currentQuestionIndex + 1)

            binding.imageViewCity.setImageResource(currentQuestion.imageResId)

            binding.buttonOption1.text = currentQuestion.options[0]
            binding.buttonOption2.text = currentQuestion.options[1]
            binding.buttonOption3.text = currentQuestion.options[2]
            binding.buttonOption4.text = currentQuestion.options[3]

        } else {
            Log.d(TAG, "Tüm sorular bitti. Oyun sonuna gidiliyor.")
            endGame(allQuestionsAnswered = true)
        }
    }

    private fun checkAnswer(clickedButton: Button) {
        disableAllButtons()
        hideJokerConfirmationsAndHintDisplay()

        val currentQuestion = questions[currentQuestionIndex]
        Log.d(TAG, "Cevap kontrol ediliyor. Tıklanan: ${clickedButton.text}, Doğru: ${currentQuestion.correctCityName}")

        if (clickedButton.text.toString() == currentQuestion.correctCityName) {
            val pointsEarned = when {
                correctAnswersCount < 10 -> 10
                correctAnswersCount < 20 -> 15
                else -> 20
            }
            currentScore += pointsEarned
            correctAnswersCount++
            updateScore(currentScore)
            clickedButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.green_correct)

            Log.d(TAG, "Doğru cevap! Kazanılan puan: $pointsEarned, Yeni skor: $currentScore, Doğru cevap sayısı: $correctAnswersCount")

            if (correctAnswersCount > highScoreCorrectAnswers) {
                highScoreCorrectAnswers = correctAnswersCount
                updateHighScoreDisplayInGame()
            }

            Handler(Looper.getMainLooper()).postDelayed({
                currentQuestionIndex++
                if (currentQuestionIndex < questions.size) {
                    loadQuestion()
                } else {
                    endGame(allQuestionsAnswered = true)
                }
            }, 1000)
        } else {
            clickedButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.red_wrong)
            Log.d(TAG, "Yanlış cevap! Mevcut skor: $currentScore, Doğru cevap sayısı: $correctAnswersCount, Kalan can: $lives")

            val heartImageViews = listOf(binding.imageViewHeart1, binding.imageViewHeart2, binding.imageViewHeart3)
            val previouslyFullHeartIndex = lives - 1

            lives--

            if (previouslyFullHeartIndex >= 0 && previouslyFullHeartIndex < heartImageViews.size) {
                val lostHeartView = heartImageViews[previouslyFullHeartIndex]
                lostHeartView.animate()
                    .alpha(0.3f)
                    .scaleX(0.8f)
                    .scaleY(0.8f)
                    .setDuration(300)
                    .withEndAction {
                        updateHeartUI()
                        lostHeartView.alpha = 1.0f
                        lostHeartView.scaleX = 1.0f
                        lostHeartView.scaleY = 1.0f
                    }
                    .start()
            } else {
                updateHeartUI()
            }

            val message = String.format(getString(R.string.wrong_answer_with_correct_and_high_score), currentQuestion.correctCityName, highScoreCorrectAnswers)
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()

            highlightCorrectAnswer(currentQuestion.correctCityName)

            Handler(Looper.getMainLooper()).postDelayed({
                if (lives <= 0) {
                    Log.d(TAG, "Can kalmadı. 'Yanlış Cevap' sonrası oyun bitiş ekranına gidiliyor.")
                    submitScore(correctAnswersCount, currentUserId, currentPlayerName, isGoogleSignedInUser)
                    showGameOverScreen()
                } else {
                    Log.d(TAG, "Canlar kaldı ($lives). Sonraki soruya geçiliyor.")
                    currentQuestionIndex++
                    if (currentQuestionIndex < questions.size) {
                        loadQuestion()
                    } else {
                        endGame(allQuestionsAnswered = true)
                    }
                }
            }, 2000)
        }
    }

    private fun highlightCorrectAnswer(correctAnswer: String) {
        Log.d(TAG, "Doğru cevap vurgulanıyor: $correctAnswer")
        val buttons = listOf(binding.buttonOption1, binding.buttonOption2, binding.buttonOption3, binding.buttonOption4)
        for (button in buttons) {
            if (button.text.toString() == correctAnswer) {
                button.backgroundTintList = ContextCompat.getColorStateList(this, R.color.green_correct)
            }
        }
    }

    private fun updateScore(score: Int) {
        currentScore = score
        binding.textViewScore.text = String.format(getString(R.string.score_text), currentScore)
        Log.d(TAG, "Skor UI güncellendi: $currentScore")
    }

    private fun updateQuestionNumber(number: Int) {
        questionNumber = number
        binding.textViewQuestionNumber.text = String.format(getString(R.string.question_number_text), questionNumber)
        Log.d(TAG, "Soru numarası güncellendi: $questionNumber")
    }

    private fun updateHighScoreDisplayInGame() {
        binding.textViewHighScoreInGame.text = String.format(getString(R.string.high_score_in_game_text), highScoreCorrectAnswers)
        Log.d(TAG, "Oyun içi yüksek skor gösterimi güncellendi: $highScoreCorrectAnswers")
    }

    private fun updateHeartUI() {
        val heartImageViews = listOf(binding.imageViewHeart1, binding.imageViewHeart2, binding.imageViewHeart3)
        for (i in heartImageViews.indices) {
            if (i < lives) {
                heartImageViews[i].setImageResource(R.drawable.ic_heart_full)
            } else {
                heartImageViews[i].setImageResource(R.drawable.ic_heart_empty)
            }
        }
        Log.d(TAG, "Kalp UI güncellendi. Kalan can: $lives")
    }

    private fun disableAllButtons() {
        binding.buttonOption1.isEnabled = false
        binding.buttonOption2.isEnabled = false
        binding.buttonOption3.isEnabled = false
        binding.buttonOption4.isEnabled = false
        binding.buttonFiftyFifty.isEnabled = false
        binding.buttonHint.isEnabled = false
        Log.d(TAG, "Tüm cevap butonları ve jokerler devre dışı bırakıldı.")
    }

    private fun enableAllButtons() {
        binding.buttonOption1.isEnabled = true
        binding.buttonOption2.isEnabled = true
        binding.buttonOption3.isEnabled = true
        binding.buttonOption4.isEnabled = true
        updateFiftyFiftyButtonText()
        updateHintButtonText()
        Log.d(TAG, "Tüm cevap butonları etkinleştirildi, joker durumları güncellendi.")
    }

    private fun resetButtonColors() {
        val defaultColorStateList = ContextCompat.getColorStateList(this, android.R.color.white)
        binding.buttonOption1.backgroundTintList = defaultColorStateList
        binding.buttonOption2.backgroundTintList = defaultColorStateList
        binding.buttonOption3.backgroundTintList = defaultColorStateList
        binding.buttonOption4.backgroundTintList = defaultColorStateList
        binding.buttonOption1.setTextColor(ContextCompat.getColor(this, android.R.color.black))
        binding.buttonOption2.setTextColor(ContextCompat.getColor(this, android.R.color.black))
        binding.buttonOption3.setTextColor(ContextCompat.getColor(this, android.R.color.black))
        binding.buttonOption4.setTextColor(ContextCompat.getColor(this, android.R.color.black))
        Log.d(TAG, "Buton renkleri sıfırlandı.")
    }

    private fun showGameElements() {
        binding.imageViewCity.visibility = View.VISIBLE
        binding.buttonOption1.visibility = View.VISIBLE
        binding.buttonOption2.visibility = View.VISIBLE
        binding.buttonOption3.visibility = View.VISIBLE
        binding.buttonOption4.visibility = View.VISIBLE
        binding.buttonFiftyFifty.visibility = View.VISIBLE
        binding.buttonHint.visibility = View.VISIBLE
        binding.textViewHighScoreInGame.visibility = View.VISIBLE
        binding.textViewQuestionNumber.visibility = View.VISIBLE
        binding.textViewScore.visibility = View.VISIBLE
        binding.layoutHearts.visibility = View.VISIBLE
        binding.textViewHintDisplay.visibility = View.GONE
        Log.d(TAG, "Tüm oyun elementleri görünür yapıldı.")
    }

    private fun hideGameElements() {
        binding.imageViewCity.visibility = View.GONE
        binding.buttonOption1.visibility = View.GONE
        binding.buttonOption2.visibility = View.GONE
        binding.buttonOption3.visibility = View.GONE
        binding.buttonOption4.visibility = View.GONE
        binding.buttonFiftyFifty.visibility = View.GONE
        binding.buttonHint.visibility = View.GONE
        binding.textViewHighScoreInGame.visibility = View.GONE
        binding.textViewQuestionNumber.visibility = View.GONE
        binding.textViewScore.visibility = View.GONE
        binding.layoutHearts.visibility = View.GONE
        binding.textViewHintDisplay.visibility = View.GONE
        Log.d(TAG, "Tüm oyun elementleri gizlendi.")
    }

    private fun showGameOverScreen() {
        Log.d(TAG, "'Oyun Bitti!' ekranı yanlış cevaptan sonra gösterildi.")
        hideGameElements()
        hideEndGameScreens()
        hideJokerConfirmationsAndHintDisplay()

        val continueButton = binding.buttonWatchAd

        when (adContinueCount) {
            0 -> {
                continueButton.visibility = View.VISIBLE
                continueButton.isEnabled = rewardedAd != null
                continueButton.text = "Reklam İzle, 3 Can Kazan"
            }
            1 -> {
                continueButton.visibility = View.VISIBLE
                continueButton.isEnabled = rewardedAd != null
                continueButton.text = "Reklam İzle, 2 Can Kazan"
            }
            2 -> {
                continueButton.visibility = View.VISIBLE
                continueButton.isEnabled = rewardedAd != null
                continueButton.text = "Reklam İzle, 1 Can Kazan"
            }
            else -> {
                continueButton.visibility = View.GONE
            }
        }

        binding.layoutGameOverWrongAnswer.alpha = 0f
        binding.layoutGameOverWrongAnswer.visibility = View.VISIBLE
        binding.layoutGameOverWrongAnswer.animate().alpha(1f).setDuration(300).start()
    }

    private fun hideJokerConfirmationsAndHintDisplay() {
        binding.layoutFiftyFiftyConfirm.animate().alpha(0f).setDuration(200).withEndAction { binding.layoutFiftyFiftyConfirm.visibility = View.GONE }.start()
        binding.layoutHintConfirm.animate().alpha(0f).setDuration(200).withEndAction { binding.layoutHintConfirm.visibility = View.GONE }.start()
        binding.textViewHintDisplay.animate().alpha(0f).setDuration(200).withEndAction { binding.textViewHintDisplay.visibility = View.GONE }.start()
    }

    private fun hideEndGameScreens() {
        Log.d(TAG, "Tüm oyun bitiş/devam ekranları gizleniyor.")
        binding.layoutGameOverWrongAnswer.animate().alpha(0f).setDuration(200).withEndAction { binding.layoutGameOverWrongAnswer.visibility = View.GONE }.start()
        binding.layoutEndGameFinal.animate().alpha(0f).setDuration(200).withEndAction { binding.layoutEndGameFinal.visibility = View.GONE }.start()
        hideJokerConfirmationsAndHintDisplay()
    }

    private fun endGame(allQuestionsAnswered: Boolean) {
        Log.d(TAG, "Oyun bitti. Tüm sorular cevaplandı mı? $allQuestionsAnswered. Son Doğru Cevap Sayısı: $correctAnswersCount")

        // --- REKLAM: Oyun bittiğinde reklamı göster ---
        showInterstitialAd()

        hideGameElements()
        hideEndGameScreens()
        hideJokerConfirmationsAndHintDisplay()

        Log.d(TAG, "endGame: submitScore çağrılmadan önce currentUserId: $currentUserId")
        Log.d(TAG, "endGame: submitScore çağrılmadan önce currentPlayerName: $currentPlayerName")
        Log.d(TAG, "endGame: submitScore çağrılmadan önce isGoogleSignedInUser: $isGoogleSignedInUser")
        Log.d(TAG, "endGame: submitScore çağrılmadan önce highScoreCorrectAnswers (Firestore'dan çekilen/önceki en yüksek): $highScoreCorrectAnswers")
        Log.d(TAG, "endGame: submitScore çağrılacak finalCorrectAnswers (bu oyunun skoru): $correctAnswersCount")

        submitScore(correctAnswersCount, currentUserId, currentPlayerName, isGoogleSignedInUser)
        Log.d(TAG, "Skor kaydediliyor (Firebase veya lokal). submitScore çağrıldı.")

        binding.textViewFinalResult.text = String.format(getString(R.string.final_result_text), correctAnswersCount)
        binding.textViewHighScoreEnd.text = String.format(getString(R.string.high_score_end_text), highScoreCorrectAnswers)

        binding.layoutEndGameFinal.alpha = 0f
        binding.layoutEndGameFinal.visibility = View.VISIBLE
        binding.layoutEndGameFinal.animate().alpha(1f).setDuration(300).start()
    }

    private fun restartGame() {
        Log.d(TAG, "Oyunu tamamen yeniden başlatılıyor.")
        hideEndGameScreens()
        startGame()
    }

    private fun goToMainMenu() {
        Log.d(TAG, "Ana menüye dönülüyor. Geçiş reklamı gösterilecek.")
        if (mInterstitialAd != null) {
            mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Reklam kapatıldı. Ana menüye dönülüyor.")
                    // Reklam kapatıldıktan sonra ana menüye dön
                    val intent = Intent(this@MainActivity, MainMenuActivity::class.java)
                    startActivity(intent)
                    finish()
                }

                override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                    Log.e(TAG, "Reklam gösterilemedi: ${adError.message}")
                    // Reklam gösterilemezse bile ana menüye dön
                    val intent = Intent(this@MainActivity, MainMenuActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
            mInterstitialAd?.show(this)
        } else {
            // Reklam hazır değilse direkt ana menüye dön
            Log.d(TAG, "Geçiş reklamı hazır değil. Doğrudan ana menüye dönülüyor.")
            val intent = Intent(this, MainMenuActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun showFiftyFiftyConfirm() {
        binding.textViewFiftyFiftyConfirm.text = String.format(getString(R.string.fifty_fifty_confirm_text), getFiftyFiftyCost())
        Log.d(TAG, "50/50 onay ekranı gösterildi. Maliyet: ${getFiftyFiftyCost()}")
        binding.layoutFiftyFiftyConfirm.alpha = 0f
        binding.layoutFiftyFiftyConfirm.visibility = View.VISIBLE
        binding.layoutFiftyFiftyConfirm.animate().alpha(1f).setDuration(300).start()
    }

    private fun hideFiftyFiftyConfirm() {
        Log.d(TAG, "50/50 onay ekranı gizlendi.")
        binding.layoutFiftyFiftyConfirm.animate().alpha(0f).setDuration(200).withEndAction { binding.layoutFiftyFiftyConfirm.visibility = View.GONE }.start()
    }

    private fun useFiftyFifty() {
        val fiftyFiftyCost = getFiftyFiftyCost()
        Log.d(TAG, "50/50 kullanma denemesi. Maliyet: $fiftyFiftyCost, Mevcut skor: $currentScore, Kullanım sayısı: $fiftyFiftyUseCount")

        hideFiftyFiftyConfirm()

        if (currentScore >= fiftyFiftyCost) {
            currentScore -= fiftyFiftyCost
            updateScore(currentScore)
            fiftyFiftyUseCount++
            updateFiftyFiftyButtonText()

            Log.d(TAG, "50/50 başarıyla kullanıldı. Yeni skor: $currentScore, Kullanım sayısı: $fiftyFiftyUseCount")

            val currentQuestion = questions[currentQuestionIndex]
            val correctAnswer = currentQuestion.correctCityName
            val incorrectOptions = currentQuestion.options.filter { it != correctAnswer }.toMutableList()

            incorrectOptions.shuffle()
            val optionsToRemove = incorrectOptions.take(2)

            val buttons = listOf(binding.buttonOption1, binding.buttonOption2, binding.buttonOption3, binding.buttonOption4)
            for (button in buttons) {
                if (button.text.toString() in optionsToRemove) {
                    button.visibility = View.INVISIBLE
                    button.isEnabled = false
                    Log.d(TAG, "50/50 ile gizlenen buton: ${button.text}")
                }
            }
            Toast.makeText(this, "50/50 joker kullanıldı!", Toast.LENGTH_SHORT).show()
        } else {
            val message = "50/50 Joker'i kullanmak için en az $fiftyFiftyCost puana ihtiyacın var! Mevcut: $currentScore"
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            Log.d(TAG, "50/50 için yeterli skor yok.")
        }
    }

    private fun getFiftyFiftyCost(): Int {
        return 50 + (fiftyFiftyUseCount * 25)
    }

    private fun updateFiftyFiftyButtonText() {
        val cost = getFiftyFiftyCost()
        binding.buttonFiftyFifty.text = String.format(getString(R.string.fifty_fifty_button_text_dynamic), cost)

        if (currentScore < cost) {
            binding.buttonFiftyFifty.isEnabled = false
            binding.buttonFiftyFifty.alpha = 0.5f
            binding.buttonFiftyFifty.backgroundTintList = ContextCompat.getColorStateList(this, R.color.gray_disabled)
        } else {
            binding.buttonFiftyFifty.isEnabled = true
            binding.buttonFiftyFifty.alpha = 1.0f
            binding.buttonFiftyFifty.backgroundTintList = ContextCompat.getColorStateList(this, R.color.teal_700)
        }
    }

    private fun showHintConfirm() {
        binding.textViewHintConfirm.text = String.format(getString(R.string.hint_confirm_text), getHintCost())
        Log.d(TAG, "İpucu onay ekranı gösterildi. Maliyet: ${getHintCost()}")
        binding.layoutHintConfirm.alpha = 0f
        binding.layoutHintConfirm.visibility = View.VISIBLE
        binding.layoutHintConfirm.animate().alpha(1f).setDuration(300).start()
    }

    private fun hideHintConfirm() {
        Log.d(TAG, "İpucu onay ekranı gizlendi.")
        binding.layoutHintConfirm.animate().alpha(0f).setDuration(200).withEndAction { binding.layoutHintConfirm.visibility = View.GONE }.start()
    }

    private fun useHint() {
        val hintCost = getHintCost()
        Log.d(TAG, "İpucu kullanma denemesi. Maliyet: $hintCost, Mevcut skor: $currentScore, Kullanım sayısı: $hintUseCount")

        hideHintConfirm()

        if (currentScore >= hintCost) {
            currentScore -= hintCost
            updateScore(currentScore)
            hintUseCount++
            updateHintButtonText()

            Log.d(TAG, "İpucu başarıyla kullanıldı. Yeni skor: $currentScore, Kullanım sayısı: $hintUseCount")

            val currentQuestion = questions[currentQuestionIndex]
            binding.textViewHintDisplay.text = currentQuestion.hint
            binding.textViewHintDisplay.alpha = 0f
            binding.textViewHintDisplay.visibility = View.VISIBLE
            binding.textViewHintDisplay.animate().alpha(1f).setDuration(300).start()
            Toast.makeText(this, "İpucu joker kullanıldı!", Toast.LENGTH_SHORT).show()
        } else {
            val message = "İpucu Joker'i kullanmak için en az $hintCost puana ihtiyacın var! Mevcut: $currentScore"
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            Log.d(TAG, "İpucu için yeterli skor yok.")
        }
    }

    private fun getHintCost(): Int {
        return 25 + (hintUseCount * 10)
    }

    private fun updateHintButtonText() {
        val cost = getHintCost()
        binding.buttonHint.text = String.format(getString(R.string.hint_button_text_dynamic), cost)

        if (currentScore < cost) {
            binding.buttonHint.isEnabled = false
            binding.buttonHint.alpha = 0.5f
            binding.buttonHint.backgroundTintList = ContextCompat.getColorStateList(this, R.color.gray_disabled)
        } else {
            binding.buttonHint.isEnabled = true
            binding.buttonHint.alpha = 1.0f
            binding.buttonHint.backgroundTintList = ContextCompat.getColorStateList(this, R.color.teal_700)
        }
    }

    // *** REKLAM: AdMob'dan ödüllü reklam yükleme fonksiyonu ***
    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(this, adUnitId, adRequest, object : RewardedAdLoadCallback() {
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

    // *** REKLAM: AdMob'dan ödüllü reklam gösterme fonksiyonu ***
    private fun showRewardedAd() {
        rewardedAd?.let { ad ->
            ad.show(this) { rewardItem ->
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
                    updateHeartUI()
                    hideEndGameScreens()
                    showGameElements()

                    currentQuestionIndex++
                    if (currentQuestionIndex < questions.size) {
                        loadQuestion()
                    } else {
                        endGame(allQuestionsAnswered = true)
                    }

                    Toast.makeText(this, "$livesToGive can kazandın! Oyuna devam et.", Toast.LENGTH_SHORT).show()
                }

                loadRewardedAd()
            }
        } ?: run {
            Log.e(TAG, "Ödüllü reklam henüz hazır değil.")
            Toast.makeText(this, "Reklam henüz yüklenmedi, lütfen tekrar deneyin.", Toast.LENGTH_SHORT).show()
        }
    }

    // --- REKLAM: Geçiş reklamı metotları ---
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

    // --- Skor Kaydetme ve Çekme Metotları (Firebase & Lokal) ---
    private fun fetchHighScore(userId: String, isGoogleSignedIn: Boolean, onComplete: (Int) -> Unit) {
        if (userId.isEmpty() || userId.startsWith("unknown_")) {
            Log.w(TAG, "fetchHighScore: Geçersiz veya bilinmeyen kullanıcı ID'si. Yüksek skor 0 olarak kabul edildi. (UID: $userId)")
            onComplete(0)
            return
        }

        Log.d(TAG, "fetchHighScore: Firestore'dan yüksek skor çekiliyor: UID=$userId, Google Girişi: $isGoogleSignedIn")
        firestore.collection(FIRESTORE_SCORES_COLLECTION)
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val highScore = document.getLong("correctAnswersCount")?.toInt() ?: 0
                    Log.d(TAG, "fetchHighScore: Firestore'dan yüksek skor başarıyla çekildi: $highScore (UID: $userId)")
                    onComplete(highScore)
                } else {
                    Log.d(TAG, "fetchHighScore: Firestore'da bu kullanıcı ($userId) için skor belgesi bulunamadı. Yüksek skor 0 olarak kabul edildi.")
                    onComplete(0)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "fetchHighScore: Firestore'dan yüksek skor çekilirken hata oluştu: ${e.message}", e)
                Toast.makeText(this@MainActivity, "Yüksek skor çekilirken hata oluştu: ${e.message}", Toast.LENGTH_SHORT).show()
                onComplete(0)
            }
    }

    private fun submitScore(finalCorrectAnswers: Int, userId: String, playerName: String, isGoogleSignedIn: Boolean) {
        Log.d(TAG, "submitScore çağrıldı. Final Skor: $finalCorrectAnswers, UID: $userId, Ad: $playerName, Google Girişi: $isGoogleSignedIn")

        val localHighScore = sharedPreferences.getInt(KEY_LOCAL_HIGH_SCORE, 0)
        if (!isGoogleSignedIn && finalCorrectAnswers > localHighScore) {
            sharedPreferences.edit().putInt(KEY_LOCAL_HIGH_SCORE, finalCorrectAnswers).apply()
            Log.d(TAG, "submitScore: Lokal yüksek skor güncellendi (Misafir): $finalCorrectAnswers")
            highScoreCorrectAnswers = finalCorrectAnswers
            updateHighScoreDisplayInGame()
            Toast.makeText(this, "Yeni lokal yüksek skorunuz kaydedildi!", Toast.LENGTH_SHORT).show()
        } else if (!isGoogleSignedIn && finalCorrectAnswers <= localHighScore) {
            Log.d(TAG, "submitScore: Mevcut skor ($finalCorrectAnswers), misafir kullanıcısı için önceki lokal yüksek skordan ($localHighScore) daha yüksek değil. Lokal kaydedilmedi.")
            Toast.makeText(this, "Skorunuz yüksek skordan daha az.", Toast.LENGTH_SHORT).show()
        }

        if (userId.isEmpty() || userId.startsWith("unknown_")) {
            Log.w(TAG, "submitScore: Geçersiz veya bilinmeyen kullanıcı ID'si ile Firestore'a kayıt yapılamadı. UID: $userId")
            Toast.makeText(this, "Skorunuz kaydedilemedi (geçersiz kullanıcı ID).", Toast.LENGTH_SHORT).show()
            return
        }

        val userDocRef = firestore.collection(FIRESTORE_SCORES_COLLECTION).document(userId)

        userDocRef.get()
            .addOnSuccessListener { document ->
                val existingHighScore = document.getLong("correctAnswersCount")?.toInt() ?: 0

                Log.d(TAG, "submitScore: Firebase'den mevcut yüksek skor çekildi: $existingHighScore (UID: $userId). Yeni skor: $finalCorrectAnswers")

                if (finalCorrectAnswers > existingHighScore) {
                    Log.d(TAG, "submitScore: Yeni yüksek skor bulundu! $finalCorrectAnswers > $existingHighScore. Kaydediliyor.")
                    val scoreData = hashMapOf(
                        "correctAnswersCount" to finalCorrectAnswers,
                        "googleSignIn" to isGoogleSignedIn,
                        "playerName" to playerName,
                        "timestamp" to FieldValue.serverTimestamp(),
                        "userId" to userId
                    )

                    userDocRef.set(scoreData, SetOptions.merge())
                        .addOnSuccessListener {
                            Log.d(TAG, "submitScore: Firestore'a yeni yüksek skor başarıyla kaydedildi (UID: $userId).")
                            highScoreCorrectAnswers = finalCorrectAnswers
                            updateHighScoreDisplayInGame()
                            Toast.makeText(this, "Yeni yüksek skorunuz kaydedildi!", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "submitScore: Firestore'a skor kaydedilirken hata oluştu (UID: $userId): ${e.message}", e)
                            Toast.makeText(this, "Skor kaydedilirken hata oluştu: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                } else {
                    Log.d(TAG, "submitScore: Mevcut skor ($finalCorrectAnswers), Firebase'deki önceki yüksek skordan ($existingHighScore) daha yüksek değil. Firestore'a kaydedilmedi.")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "submitScore: Firebase'den mevcut skoru çekerken hata oluştu: ${e.message}", e)
                Toast.makeText(this, "Skor kaydedilirken hata oluştu: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun String.toTurkishLowercaseAndNormalize(): String {
        return this
            .replace("İ", "i", ignoreCase = true)
            .replace("ı", "i", ignoreCase = true)
            .replace("Ş", "s", ignoreCase = true)
            .replace("ş", "s", ignoreCase = true)
            .replace("Ç", "c", ignoreCase = true)
            .replace("ç", "c", ignoreCase = true)
            .replace("Ğ", "g", ignoreCase = true)
            .replace("ğ", "g", ignoreCase = true)
            .replace("Ü", "u", ignoreCase = true)
            .replace("ü", "u", ignoreCase = true)
            .replace("Ö", "o", ignoreCase = true)
            .replace("ö", "o", ignoreCase = true)
            .lowercase()
    }

    private fun generateAllQuestions(): List<Question> {
        val allQuestions = mutableListOf<Question>()

        val citiesWithPhotos = mapOf(
            "Adana" to listOf(1, 2, 3, 4, 5, 6, 7, 8),
            "Ağrı" to listOf(1, 2, 3, 4, 5),
            "Aksaray" to listOf(1, 2, 3, 4, 5),
            "Amasya" to listOf(1, 2, 3, 4, 5, 6),
            "Ankara" to listOf(1, 2, 3, 4, 5),
            "Antalya" to listOf(1, 2, 3, 4, 5, 6, 7),
            "Aydın" to listOf(1, 2, 3, 4, 5),
            "Balıkesir" to listOf(1, 2, 3, 4, 5),
            "Bilecik" to listOf(1, 2, 3, 4),
            "Bolu" to listOf(1, 2, 3, 4),
            "Bursa" to listOf(1, 2, 3, 4, 5),
            "Çanakkale" to listOf(1, 2, 3, 4, 5, 6),
            "Denizli" to listOf(1, 2, 3, 4),
            "Diyarbakır" to listOf(1, 2, 3, 4, 5, 6),
            "Düzce" to listOf(1, 2),
            "Edirne" to listOf(1, 2, 3, 4),
            "Erzurum" to listOf(1, 2, 3, 4, 5),
            "Eskişehir" to listOf(1, 2, 3, 4, 5),
            "Gaziantep" to listOf(1, 2, 3, 4, 5),
            "Giresun" to listOf(1, 2, 3, 4),
            "Hakkari" to listOf(1, 2, 3, 4, 5),
            "İstanbul" to listOf(1, 2, 3, 4, 5, 6, 7),
            "İzmir" to listOf(1, 2, 3, 4, 5, 6),
            "Kahramanmaraş" to listOf(1, 2),
            "Kastamonu" to listOf(1, 2, 3, 4, 5),
            "Kayseri" to listOf(1, 2, 3, 4, 5),
            "Kırşehir" to listOf(1, 2),
            "Kocaeli" to listOf(1, 2, 3, 4),
            "Konya" to listOf(1, 2, 3, 4, 5),
            "Malatya" to listOf(1, 2, 3, 4, 5),
            "Mardin" to listOf(1, 2, 3, 4, 5),
            "Mersin" to listOf(1, 2, 3, 4, 5),
            "Muğla" to listOf(1, 2, 3, 4, 5),
            "Nevşehir" to listOf(1, 2, 3, 4, 5, 6),
            "Niğde" to listOf(1, 2, 3),
            "Ordu" to listOf(1, 2, 3, 4),
            "Sakarya" to listOf(1, 2, 3, 4),
            "Samsun" to listOf(1, 2, 3, 4, 5),
            "Sinop" to listOf(1, 2, 3, 4),
            "Sivas" to listOf(1, 2, 3, 4),
            "Şanlıurfa" to listOf(1, 2, 3, 4, 5),
            "Trabzon" to listOf(1, 2, 3, 4, 5, 6),
            "Van" to listOf(1, 2, 3),
            "Afyonkarahisar" to listOf(1, 2, 3, 4),
            "Adıyaman" to listOf(1, 2, 3),
            "Artvin" to listOf(1, 2, 3),
            "Bingöl" to listOf(1, 2, 3),
            "Bitlis" to listOf(1, 2),
            "Çankırı" to listOf(1, 2),
            "Gümüşhane" to listOf(1, 2, 3),
            "Isparta" to listOf(1, 2, 3, 4),
            "Kars" to listOf(1, 2, 3),
            "Muş" to listOf(1),
            "Rize" to listOf(1, 2),
            "Siirt" to listOf(2)
        )

        val cityHints = mapOf(
            "Adana" to "Kebaplarıyla meşhur, Seyhan Nehri kenarında bir Akdeniz şehri.",
            "Ağrı" to "Türkiye'nin en yüksek dağına ev sahipliği yapar.",
            "Aksaray" to "Ihlara Vadisi ile ünlü, İç Anadolu'da bir şehir.",
            "Amasya" to "Yeşilırmak kıyısındaki yalı evleri ve elmasıyla bilinir.",
            "Ankara" to "Türkiye'nin başkenti ve Anıtkabir'e ev sahipliği yapar.",
            "Antalya" to "Turizmin başkenti, Akdeniz'in incisi.",
            "Aydın" to "İnciri ve zeytiniyle meşhur, Ege'de tarihi bir şehir.",
            "Balıkesir" to "Marmara ve Ege'ye kıyısı olan, zeytin ve peyniriyle ünlü.",
            "Bilecik" to "Osmanlı Devleti'nin temellerinin atıldığı şehirlerden biri.",
            "Bolu" to "Abant ve Yedigöller gibi doğal güzellikleriyle bilinen bir orman şehri.",
            "Bursa" to "Osmanlı'nın ilk başkenti, Uludağ'a ev sahipliği yapar.",
            "Çanakkale" to "Tarihi Gelibolu Yarımadası ve Truva Atı ile ünlü.",
            "Denizli" to "Pamukkale travertenleriyle dünya çapında tanınır.",
            "Diyarbakır" to "Tarihi surları ve karpuzuyla meşhur, Güneydoğu'da bir şehir.",
            "Düzce" to "Karadeniz'e yakın, doğal güzellikleriyle ön plana çıkan genç bir il.",
            "Edirne" to "Selimiye Camii'ne ev sahipliği yapan, Trakya'nın tarihi şehri.",
            "Erzurum" to "Doğu Anadolu'nun önemli merkezi, kış turizmiyle bilinir.",
            "Eskişehir" to "Öğrenci şehri, Porsuk Çayı ve lületaşıyla ünlü.",
            "Gaziantep" to "Gastronomi şehri, baklavası ve fıstığıyla meşhur.",
            "Giresun" to "Fındığın başkenti, Karadeniz'de yeşil bir şehir.",
            "Hakkari" to "Türkiye'nin en doğusunda, dağlık ve zorlu coğrafyaya sahip.",
            "İstanbul" to "İki kıtayı birleştiren, tarihi ve kültürel zenginlikleriyle dünya şehri.",
            "İzmir" to "Ege'nin incisi, Kordon'u ve tarihi mekanlarıyla popüler.",
            "Kahramanmaraş" to "Dondurmasıyla meşhur, Kurtuluş Savaşı'nda önemli rol oynamış.",
            "Kastamonu" to "Karadeniz'in iç kesimlerinde, doğal güzellikleri ve tarihi evleriyle bilinir.",
            "Kayseri" to "Mantısı ve pastırmasıyla ünlü, Erciyes Dağı'na ev sahipliği yapar.",
            "Kırşehir" to "Neşet Ertaş'ın memleketi, Ahi Evran'ın şehri.",
            "Kocaeli" to "Sanayi ve liman şehri, Marmara Bölgesi'nde yer alır.",
            "Konya" to "Mevlana'nın şehri, hoşgörü ve tasavvufun merkezi.",
            "Malatya" to "Kayısısıyla meşhur, Doğu Anadolu'da bir şehir.",
            "Mardin" to "Taş evleri ve Süryani kültürüyle ünlü, Güneydoğu'da tarihi bir şehir.",
            "Mersin" to "Akdeniz'de liman şehri, tantunisiyle bilinir.",
            "Muğla" to "Bodrum, Marmaris gibi tatil belgelerine ev sahipliği yapan Ege şehri.",
            "Nevşehir" to "Kapadokya'nın kalbi, peri bacalarıyla ünlüdür.",
            "Niğde" to "Bor madenleriyle bilinen, İç Anadolu'da tarihi bir şehir.",
            "Ordu" to "Karadeniz'de fındığın ve yeşilin bol olduğu bir sahil şehri.",
            "Sakarya" to "Doğal güzellikleri ve sanayisiyle bilinen, Marmara'da bir şehir.",
            "Samsun" to "Kurtuluş Savaşı'nın başlangıç noktası, Karadeniz'in önemli liman şehri.",
            "Sinop" to "Türkiye'nin en kuzey ucu, doğal limanıyla bilinen Karadeniz şehri.",
            "Sivas" to "Cumhuriyet'in temellerinin atıldığı şehir, İç Anadolu'da yer alır.",
            "Şanlıurfa" to "Peygamberler şehri, Balıklıgöl'e ev sahipliği yapar.",
            "Trabzon" to "Karadeniz'in incisi, Sümela Manastırı'yla ünlüdür.",
            "Van" to "Van Gölü'ne ev sahipliği yapan, kedisiyle meşhur Doğu Anadolu şehri.",
            "Afyonkarahisar" to "Mermeri, sucuğu ve termal kaynaklarıyla bilinen İç Anadolu şehri.",
            "Adıyaman" to "Nemrut Dağı Milli Parkı'na ev sahipliği yapan Güneydoğu şehri.",
            "Artvin" to "Yeşil doğası, yaylaları ve Çoruh Nehri ile ünlü Karadeniz şehri.",
            "Bingöl" to "Yüzen Adaları ile meşhur, Doğu Anadolu'da dağlık bir şehir.",
            "Bitlis" to "Nemrut Krater Gölü ve tarihi yapılarıyla bilinen Doğu Anadolu şehri.",
            "Çankırı" to "Tuz mağarası ve doğal güzellikleriyle bilinen İç Anadolu şehri.",
            "Gümüşhane" to "Kürtün Baraj Gölü ve Karaca Mağarası ile bilinen Karadeniz şehri.",
            "Isparta" to "Gül bahçeleri ve lavanta tarlalarıyla ünlü Akdeniz şehri.",
            "Kars" to "Ani Harabeleri ve kaz etiyle meşhur, Doğu Anadolu'da tarihi bir şehir.",
            "Muş" to "Doğu Anadolu'da geniş ovalara sahip, tarihi ve kültürel bir şehir.",
            "Rize" to "Çay bahçeleri, yaylaları ve Fırtına Deresi ile ünlü Karadeniz şehri.",
            "Siirt" to "Fıstığı ve büryan kebabıyla meşhur, Güneydoğu'da tarihi bir şehir."
        )

        val allCityNames = citiesWithPhotos.keys.toList()

        for ((city, photoNumbers) in citiesWithPhotos) {
            val baseImageName = city.toTurkishLowercaseAndNormalize()
            val hintForCity = cityHints[city] ?: "Bu şehir hakkında bir ipucu yok."

            for (i in photoNumbers) {
                val drawableName = "${baseImageName}_${i}"
                val imageResId = resources.getIdentifier(drawableName, "drawable", packageName)

                if (imageResId == 0) {
                    Log.e(TAG, "HATA: Drawable kaynağı bulunamadı: $drawableName. Lütfen resim adını ve konumunu kontrol edin!")
                    continue
                } else {
                    val incorrectOptions = allCityNames.minus(city).shuffled().take(3)
                    val options = (incorrectOptions + city).shuffled()
                    allQuestions.add(Question(imageResId, city, options, hintForCity))
                }
            }
        }
        return allQuestions.shuffled()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

data class Question(
    val imageResId: Int,
    val correctCityName: String,
    val options: List<String>,
    val hint: String
)