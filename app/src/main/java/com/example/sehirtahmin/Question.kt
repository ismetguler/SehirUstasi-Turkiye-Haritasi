// MainActivity sınıfının dışında bir yere (dosyanın en altına) ekle
data class Question(
    val imageResId: Int, // Resim kaynağı ID'si (örn: R.drawable.adana_1)
    val options: List<String>, // Seçenekler listesi
    val correctCityName: String // Doğru şehrin adı
)