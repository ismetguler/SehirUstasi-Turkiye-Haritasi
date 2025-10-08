// app/src/main/java/com/example/sehirtahminoyunu/PlayerScore.kt (Kendi paket adınla değiştir!)
package com.ismetguler.sehirtahmin // Burayı kendi paket adınla AYNI yap

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class PlayerScore(
    val userId: String = "",
    val playerName: String = "Anonim",
    val correctAnswersCount: Int = 0, // Bu senin skor alanın
    val googleSignIn: Boolean = false, // Google ile giriş yapıldı mı?
    @ServerTimestamp // Firestore'a kaydedilirken sunucu zamanını otomatik atar
    val timestamp: Date? = null
) {
    // Firestore'dan veri okurken (toObject) varsayılan parametresiz constructor gereklidir
    constructor() : this("", "", 0, false, null)
}