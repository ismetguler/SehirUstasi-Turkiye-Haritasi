// app/src/main/java/com/ismetguler/sehirtahmin/SessionManager.kt

package com.ismetguler.sehirtahmin

import android.app.Application
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions // Bu satır eklendi
import java.util.UUID

// Bu sınıfı uygulamanızın ana Application sınıfında initialize etmeniz önerilir.
// Örneğin: class MyApplication : Application() { override fun onCreate() { super.onCreate(); SessionManager.init(this) } }
// Veya her Activity'de SessionManager.init(this) çağrılabilir, ancak Application sınıfında tek seferlik yapmak daha iyidir.

object SessionManager {

    private const val TAG = "SessionManager"
    private var currentUserSession: UserSession? = null
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // Uygulama başlatıldığında çağrılmalı
    fun init(application: Application) {
        // FirebaseAuth durum değişikliklerini dinle
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            updateUserSession(user)
            Log.d(TAG, "Auth State Changed: User: ${user?.uid ?: "null"}")
        }

        // Uygulama ilk açıldığında mevcut oturumu kontrol et
        updateUserSession(auth.currentUser)
    }

    private fun updateUserSession(firebaseUser: FirebaseUser?) {
        currentUserSession = if (firebaseUser != null) {
            val userId = firebaseUser.uid
            val playerName = firebaseUser.displayName
            // Google ile giriş mi yapıldı?
            val isGoogleSignIn = firebaseUser.providerData.any { it.providerId == "google.com" }
            Log.d(TAG, "User session updated: UID=$userId, Name=$playerName, Google SignIn: $isGoogleSignIn")
            UserSession(userId, playerName, isGoogleSignIn)
        } else {
            null
        }
    }

    fun getCurrentUser(): UserSession? {
        // Her çağrıldığında güncel Firebase kullanıcısını kontrol edelim
        updateUserSession(auth.currentUser)
        return currentUserSession
    }

    fun startGuestSession() {
        if (currentUserSession == null || !currentUserSession!!.isGoogleSignIn) { // Eğer zaten Google ile giriş yapılmadıysa
            val guestId = "guest_" + UUID.randomUUID().toString().take(8)
            val guestName = "Misafir Kullanıcı"
            currentUserSession = UserSession(guestId, guestName, false)
            Log.d(TAG, "Misafir oturumu başlatıldı: UID=${guestId}, Name=$guestName")

            // Misafir kullanıcının bilgilerini Firestore'a kaydet (opsiyonel, skor tutmak için)
            firestore.collection("users").document(guestId).set(
                hashMapOf(
                    "playerName" to guestName,
                    "isGuest" to true,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).addOnSuccessListener {
                Log.d(TAG, "Misafir kullanıcı Firestore'a kaydedildi: $guestId")
            }.addOnFailureListener { e ->
                Log.e(TAG, "Misafir kullanıcı Firestore'a kaydedilirken hata: ${e.message}", e)
            }
        } else {
            Log.d(TAG, "Zaten bir oturum var (${currentUserSession?.userId}), misafir oturumu başlatılmadı.")
        }
    }

    // YENİ EKLENEN METOT: Google ile giriş yapıldığında oturumu başlatır
    fun startGoogleSession(user: FirebaseUser) {
        val userId = user.uid
        val playerName = user.displayName
        // Zaten bir Google oturumu varsa tekrar başlatmaya gerek yok
        if (currentUserSession?.userId == userId && currentUserSession?.isGoogleSignIn == true) {
            Log.d(TAG, "Google oturumu zaten etkin: ${userId}")
            return
        }
        currentUserSession = UserSession(userId, playerName, true)
        Log.d(TAG, "Google oturumu başlatıldı: UID=${userId}, Name=$playerName")

        // Firestore'a kullanıcı bilgilerini kaydet/güncelle
        firestore.collection("users").document(userId).set(
            hashMapOf(
                "playerName" to playerName,
                "isGuest" to false, // Bu bir misafir değil
                "lastLogin" to FieldValue.serverTimestamp()
            ), SetOptions.merge() // Mevcut verileri koruyarak güncelle
        ).addOnSuccessListener {
            Log.d(TAG, "Google kullanıcısı Firestore'a kaydedildi/güncellendi: $userId")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Google kullanıcısı Firestore'a kaydedilirken hata: ${e.message}", e)
        }
    }

    // YENİ EKLENEN METOT: Oturumu tamamen sonlandırır
    fun endSession() {
        auth.signOut() // Firebase Auth oturumunu da kapat
        currentUserSession = null
        Log.d(TAG, "Kullanıcı çıkış yaptı. Oturum sıfırlandı.")
    }

    // Google ile giriş sonrası kullanıcı adını güncellemek için
    fun updatePlayerNameForGoogleUser(firebaseUser: FirebaseUser, newName: String) {
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(newName)
            .build()

        firebaseUser.updateProfile(profileUpdates)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Firebase DisplayName güncellendi: $newName")
                    // Firestore'daki oyuncu adını da güncelle
                    firestore.collection("users").document(firebaseUser.uid)
                        .update("playerName", newName)
                        .addOnSuccessListener {
                            Log.d(TAG, "Firestore'daki oyuncu adı güncellendi: $newName")
                            updateUserSession(firebaseUser) // SessionManager'ı da güncelle
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Firestore'daki oyuncu adı güncellenirken hata: ${e.message}", e)
                        }
                } else {
                    Log.e(TAG, "Firebase DisplayName güncellenirken hata: ${task.exception?.message}")
                }
            }
    }
}

data class UserSession(
    val userId: String,
    val playerName: String?,
    val isGoogleSignIn: Boolean
)