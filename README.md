# 🇹🇷 Şehir Ustası - Türkiye Haritası
> Fotoğraf ve harita bazlı iki farklı mod ile Türkiye'nin coğrafi ve kültürel bilgisini test eden, Firebase destekli eğlenceli ve rekabetçi bir mobil tahmin oyunu.

## ✨ Oyun Modları ve Temel Özellikler
Uygulama, rekabeti ve eğlenceyi artırmak için iki ana modda çalışır:

| Mod Adı | Açıklama | Ana Mekanik |
| :--- | :--- | :--- |
| **Fotoğraf Tahmin Modu** | Şehrin bir simgesini (yapı, yemek, manzara) gösterir. Kullanıcı şehri tahmin eder. | Görsel ipuçları ile tahmin |
| **Harita İşaretleme Modu** | Rastgele bir şehrin adını verir. Kullanıcı bu şehri Türkiye haritası üzerinde işaretler. | Hassas coğrafi işaretleme |

### 🏆 Diğer Özellikler
* **Küresel Liderlik Tablosu:** Firebase altyapısı ile gerçek zamanlı, dünya çapında (veya ülke çapında) sıralama sistemi.
* **Joker Sistemi:** Zorlayıcı sorularda kullanıcıya yardımcı olmak için ipucu mekanizması (Harf açma, hatalı seçenek eleme vb.).
* **Puanlama:** Harita modunda işaretleme hassasiyetine dayalı detaylı puan hesaplaması.

---

## 💻 Kullanılan Teknolojiler
Projenin temel teknolojileri ve kullanılan kütüphaneler:

| Kategori | Teknoloji | Amaç |
| :--- | :--- | :--- |
| **Geliştirme Dili** | **Kotlin** | Modern, güvenli ve performanslı kodlama. |
| **Mimarî** | MVVM (Model-View-ViewModel) | Temiz kod ve test edilebilir yapının sağlanması. |
| **Backend / Veritabanı** | **Google Firebase** | Liderlik tablosu ve kullanıcı verilerinin yönetimi. |
| **Harita / Konum** | **Google Maps SDK** | Harita modunda interaktif harita gösterimi ve işaretleme. |
| **Asenkron İşlemler** | Kotlin Coroutines & Flow | Kullanıcı arayüzünü bloke etmeyen verimli işlemler. |
| **Görsel Yükleme** | Glide / Coil | Fotoğraf tahmin modunda görsellerin hızlı yüklenmesi. |

---

## 🚀 Başlangıç ve Kurulum (Geliştiriciler İçin)
Projeyi yerel ortamınızda sorunsuz çalıştırmak için aşağıdaki adımları takip edin:

1.  Projeyi klonlayın:
    ```bash
    git clone [https://github.com/ismetguler/SehirUstasi-Turkiye-Haritasi.git](https://github.com/ismetguler/SehirUstasi-Turkiye-Haritasi.git)
    ```
2.  Android Studio'yu açın ve projeyi `File -> Open` yoluyla açın.
3.  **Firebase Entegrasyonu (ÇOK ÖNEMLİ):**
    * Kendi Firebase projenizi oluşturun.
    * Uygulamanızın paket adı ile Firebase'de yeni bir Android uygulaması kaydedin.
    * Oluşturulan **`google-services.json`** dosyasını projenin **`app/`** dizinine kopyalayın. *(Firebase servisleri bu dosya olmadan çalışmayacaktır.)*
4.  Gerekli tüm kütüphanelerin indirilmesi için Gradle senkronizasyonunun bitmesini bekleyin.


---

## 🔗 Uygulamayı İndir
Uygulama mağazalarında yayınlandığında:

Linkten Oyunu İndirebilirsiniz! https://play.google.com/store/apps/details?id=com.ismetguler.sehirtahmin

---

## 🧑‍💻 İletişim ve Lisans
Bu proje Ismet Guler tarafından geliştirilmiş olup açık kaynaklıdır.

* **GitHub:** [@ismetguler](https://github.com/ismetguler)
* **Lisans:** Proje [MIT Lisansı](LICENSE) ile lisanslanmıştır.
