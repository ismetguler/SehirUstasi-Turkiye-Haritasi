package com.ismetguler.sehirtahmin

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Region
import android.graphics.Typeface // Typeface eklendi
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import kotlin.math.tan
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.max
import kotlin.random.Random

// CityData Veri Sınıfı
data class CityData(
    val plaka: Int,
    val ilAdi: String,
    val geoCoordinates: List<List<List<DoubleArray>>> // MultiPolygon/Polygon koordinatları
) {
    var centerLat: Double = 0.0
    var centerLon: Double = 0.0
    var pixelPolygons: List<List<PointF>> = emptyList()
    // Şehrin haritadaki merkezi piksel koordinatları, zoom ve pan uygulanmamış hali
    var pixelCenterX: Float = 0f
    var pixelCenterY: Float = 0f


    fun calculateCenterCoordinates() {
        if (geoCoordinates.isEmpty()) {
            Log.w("CityData", "Şehir ${ilAdi} için coğrafi koordinat bulunamadı, merkez hesaplanamıyor.")
            return
        }

        val allLats = mutableListOf<Double>()
        val allLons = mutableListOf<Double>()

        geoCoordinates.forEach { polyList ->
            polyList.forEach { ring ->
                ring.forEach { coordPair ->
                    allLons.add(coordPair[0]) // Lon (boylam)
                    allLats.add(coordPair[1]) // Lat (enlem)
                }
            }
        }

        if (allLats.isNotEmpty() && allLons.isNotEmpty()) {
            centerLat = allLats.average()
            centerLon = allLons.average()
            // Log.d("CityData", "${ilAdi} için merkez koordinatlar hesaplandı: Lat=${String.format("%.4f", centerLat)}, Lon=${String.format("%.4f", centerLon)}")
        } else {
            Log.w("CityData", "Şehir ${ilAdi} için geçerli coğrafi koordinat bulunamadı, merkez 0.0 olarak kalacak.")
        }
    }
}

// MapView sınıfı
class MapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // MapGuessActivity'nin dinleyeceği arayüzler
    interface OnCityClickListener {
        fun onCityClicked(cityData: CityData?)
    }

    interface OnDataLoadedListener {
        fun onGeoJsonDataLoaded()
    }

    var onCityClickListener: OnCityClickListener? = null
    var onDataLoadedListener: OnDataLoadedListener? = null

    // Harita üzerindeki mevcut durumları tutan değişkenler
    private var currentQuestionCity: CityData? = null // Halihazırda sorulan şehir
    // lastClickedCity kaldırıldı, çünkü doğrudan vurgu mantığı daha iyi yönetiliyor
    // isCorrectAnswerDisplayed ve isWrongAnswerDisplayed de kaldırıldı, vurgular updateHighlights üzerinden yönetilecek

    // Sabit kalan vurgular için bir liste
    // Map'in değeri bir çift olsun: Paint ve o şehir isminin yazılıp yazılmayacağı bilgisi
    private val permanentlyHighlightedCities: MutableMap<CityData, Pair<Paint, Boolean>> = mutableMapOf()

    internal val allCities: MutableList<CityData> = mutableListOf()

    // Türkiye haritasının yaklaşık coğrafi sınırları
    private val MIN_LON = 25.5
    private val MAX_LON = 45.5
    private val MIN_LAT = 34.5
    private val MAX_LAT = 42.5

    private val MIN_MERCATOR_Y = latToMercatorY(MIN_LAT)
    private val MAX_MERCATOR_Y = latToMercatorY(MAX_LAT)

    // Paint nesneleri (Renkler ve stiller burada daha detaylı ayarlanacak)
    private val defaultCityPaint: Paint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.default_city_color)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val cityOutlinePaint: Paint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.city_outline_color)
        style = Paint.Style.STROKE
        strokeWidth = 2.0f // Çizgi kalınlığı biraz azaltıldı
        isAntiAlias = true
    }

    // Şehir isimleri için Paint objesi - BOYUT VE KALINLIK BURADA AYARLANDI!
    private val cityNamePaint: Paint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.city_name_text_color)
        textSize = 18f // Metin boyutu küçültüldü (örneğin 30f'den 18f'ye)
        textAlign = Paint.Align.CENTER
        // Typeface'i DEFAULT yaparak aşırı kalınlığı azalttım.
        // Eğer daha da ince istersen, Typeface.createFromAsset ile özel font kullanabilirsin.
        typeface = Typeface.DEFAULT
        isAntiAlias = true
        // Gölge efekti daha belirgin hale getirildi, okunabilirliği artırır
        setShadowLayer(3f, 1f, 1f, Color.argb(150, 0, 0, 0)) // Daha belirgin siyah gölge
    }

    // Vurgulama renkleri
    private val correctHighlightPaint: Paint = Paint().apply {
        style = Paint.Style.FILL
        alpha = 220 // Hafif transparanlık
        isAntiAlias = true
    }
    private val wrongHighlightPaint: Paint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.wrong_city_color) // Yanlış cevap vurgusu (kırmızı)
        style = Paint.Style.FILL
        alpha = 220
        isAntiAlias = true
    }
    private val correctLocationHighlightPaint: Paint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.correct_location_color) // Doğru konum vurgusu (mavi)
        style = Paint.Style.FILL
        alpha = 220
        isAntiAlias = true
    }

    // Yakınlaştırma ve Kaydırma Değişkenleri
    private var scaleFactor = 1.0f
    private var offsetX = 0f
    private var offsetY = 0f

    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var gestureDetector: GestureDetector

    init {
        Log.d("MapViewLifecycle", "MapView init block BAŞLADI.")
        loadGeoJsonData()

        scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetector(context, PanListener())

        Log.d("MapViewLifecycle", "MapView init block BİTTİ.")
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // İlk olarak scale ve pan dedektörlerine olayı gönder
        val handledByScale = scaleGestureDetector.onTouchEvent(event)
        val handledByGesture = gestureDetector.onTouchEvent(event)

        // Eğer olay herhangi bir dedektör tarafından işlendiyse, true döndür
        if (handledByScale || handledByGesture) {
            // Log.d("MapViewTouch", "onTouchEvent: Ölçekleme veya Kaydırma/Tek Dokunuş işlendi. (${event.actionMasked})")
            return true
        }

        // Eğer hiçbir dedektör olayı işlemediyse, varsayılan View davranışını çağır
        // Log.d("MapViewTouch", "onTouchEvent: Dedektörler olayı işlemedi, varsayılan davranış. (${event.actionMasked})")
        return super.onTouchEvent(event)
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val oldScaleFactor = scaleFactor
            scaleFactor *= detector.scaleFactor
            scaleFactor = max(1.0f, min(scaleFactor, 10.0f)) // Zoom sınırları 1x'den 10x'e kadar

            // Odak noktasına göre kaydırma ayarı
            offsetX = detector.focusX - ((detector.focusX - offsetX) / oldScaleFactor * scaleFactor)
            offsetY = detector.focusY - ((detector.focusY - offsetY) / oldScaleFactor * scaleFactor)

            // Log.d("ScaleListener", "onScale: Scale Factor: $oldScaleFactor -> $scaleFactor, OffsetX: $offsetX, OffsetY: $offsetY")
            invalidate() // Haritayı yeniden çiz
            return true
        }
    }

    private inner class PanListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent?, // Başlangıç olayı (ilk dokunuş)
            e2: MotionEvent,  // Güncel olay (parmak hareket ettikçe)
            distanceX: Float, // X eksenindeki kaydırma mesafesi
            distanceY: Float  // Y eksenindeki kaydırma mesafesi
        ): Boolean {
            offsetX -= distanceX
            offsetY -= distanceY

            // Harita kenarlarında kaydırmayı sınırlama
            val maxOffsetX = 0f
            val maxOffsetY = 0f

            val minOffsetX = width - (width * scaleFactor)
            val minOffsetY = height - (height * scaleFactor)

            // Eğer harita View'den daha küçükse kaydırmayı tamamen engelle
            val clampedMinOffsetX = if (minOffsetX > 0) 0f else minOffsetX
            val clampedMinOffsetY = if (minOffsetY > 0) 0f else minOffsetY

            offsetX = max(clampedMinOffsetX, min(offsetX, maxOffsetX))
            offsetY = max(clampedMinOffsetY, min(offsetY, maxOffsetY))

            // Log.d("PanListener", "onScroll: distanceX: $distanceX, distanceY: $distanceY, OffsetX: $offsetX, OffsetY: $offsetY")
            invalidate() // Haritayı yeniden çiz
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val clickedX = e.x
            val clickedY = e.y

            // Log.d("PanListener", "onSingleTapUp: Tek tıklama algılandı. Ekran Piksel: X: $clickedX, Y: $clickedY")

            if (width <= 0 || height <= 0) {
                Log.e("PanListener", "onSingleTapUp: View boyutu geçersiz (width=$width, height=$height), şehir bulunamıyor.")
                return false
            }

            // Yakınlaştırma ve kaydırma etkilerini tersine çevirerek tıklanan noktanın gerçek harita koordinatlarını bul
            val transformedX = (clickedX - offsetX) / scaleFactor
            val transformedY = (clickedY - offsetY) / scaleFactor

            // Log.d("PanListener", "onSingleTapUp: Dönüştürülmüş Piksel (Harita Koord.): X: $transformedX, Y: $transformedY")

            val clickedCity = findCityAtPixel(transformedX, transformedY)

            if (clickedCity != null) {
                // Log.d("PanListener", "onSingleTapUp: Tıklanan şehir tespit edildi: ${clickedCity.ilAdi}")
            } else {
                // Log.d("PanListener", "onSingleTapUp: Tıklanan koordinatta şehir bulunamadı ($clickedX, $clickedY).")
            }

            onCityClickListener?.onCityClicked(clickedCity) // MapGuessActivity'ye bildir
            return true
        }

        override fun onDown(e: MotionEvent): Boolean {
            // GestureDetector'ın kaydırma ve diğer hareketleri algılaması için onDown true döndürmeli
            // Log.d("PanListener", "onDown: Olay başlatıldı. (X: ${e.x}, Y: ${e.y})")
            return true
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        Log.d("MapViewDebug", "onSizeChanged çağrıldı: w=$w, h=$h")
        if (w > 0 && h > 0) {
            calculateAllCityPixelCoordinates(w.toFloat(), h.toFloat())
            invalidate() // Boyut değiştiğinde haritayı yeniden çiz
        } else {
            Log.w("MapViewDebug", "onSizeChanged: Genişlik veya yükseklik sıfır veya daha küçük. Harita çizilemez/tıklanamaz. w=$w, h=$h")
        }
    }

    /**
     * Tıklanan piksel koordinatında hangi şehrin bulunduğunu kontrol eder.
     * @param transformedX Kaydırma ve yakınlaştırma sonrası harita üzerindeki X pikseli.
     * @param transformedY Kaydırma ve yakınlaştırma sonrası harita üzerindeki Y pikseli.
     * @return Tıklanan şehir (CityData) veya null.
     */
    private fun findCityAtPixel(transformedX: Float, transformedY: Float): CityData? {
        // Tıklama tespiti için görünümün geçerli boyutlarını kullanıyoruz
        val clipBounds = Region(0, 0, width, height)

        if (width <= 0 || height <= 0) {
            Log.e("MapTouchDebug", "findCityAtPixel: View boyutları sıfır veya negatif! Tıklama algılanamıyor. (width=$width, height=$height)")
            return null
        }
        if (allCities.isEmpty()) {
            Log.w("MapTouchDebug", "findCityAtPixel: allCities listesi boş. Şehirler yüklenmemiş olabilir.")
            return null
        }

        // Şehirleri tersten döngüye alıyoruz ki en üstteki (son çizilen) şehirler önce kontrol edilsin
        // Bu, bindirme durumunda doğru şehrin tıklanmasını sağlar.
        for (city in allCities.reversed()) {
            if (city.pixelPolygons.isEmpty()) {
                // Log.w("MapTouchDebug", "${city.ilAdi} için pixelPolygons boş. Bu şehre tıklanamaz.")
                continue // Bir sonraki şehre geç
            }

            city.pixelPolygons.forEachIndexed { index, points ->
                if (points.size < 3) {
                    // Log.w("MapTouchDebug", "${city.ilAdi} (${index}. poligonu) geçerli sayıda noktaya sahip değil (${points.size}). Atlandı.")
                    return@forEachIndexed // Bu poligonu atla
                }
                val path = Path()
                path.moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) {
                    path.lineTo(points[i].x, points[i].y)
                }
                path.close()

                val region = Region()
                try {
                    // Path'i bir Region'a dönüştür. clipBounds, region'ın boyutunu sınırlar.
                    if (!region.setPath(path, clipBounds)) {
                        Log.e("MapTouchDebug", "Region setPath false döndürdü for ${city.ilAdi} (${index}. poligon). Path boş veya geçersiz olabilir.")
                        return@forEachIndexed
                    }
                } catch (e: Exception) {
                    Log.e("MapTouchDebug", "Region setPath hatası ${city.ilAdi} (${index}. poligon): ${e.message}", e)
                    return@forEachIndexed
                }

                if (region.isEmpty) {
                    // Log.w("MapTouchDebug", "${city.ilAdi} (${index}. poligon) için oluşturulan Region boş, tıklama algılanamayacak. Path çok küçük veya görünür alanda değil.")
                    return@forEachIndexed
                }

                // Tıklanan noktanın bu bölge içinde olup olmadığını kontrol et
                if (region.contains(transformedX.toInt(), transformedY.toInt())) {
                    // Log.d("MapTouchDebug", "Şehir bulundu: ${city.ilAdi} (Dönüştürülmüş Tıklanan: $transformedX, $transformedY)")
                    return city // Şehir bulundu, hemen geri dön
                }
            }
        }
        // Log.d("MapTouchDebug", "Hiçbir şehir bulunamadı ($transformedX, $transformedY).")
        return null // Şehir bulunamadı
    }

    /**
     * Enlem (latitude) değerini Mercator Y koordinatına dönüştürür.
     * Web harita sistemlerinde yaygın olarak kullanılır.
     */
    private fun latToMercatorY(latitude: Double): Double {
        val latRad = Math.toRadians(latitude)
        return Math.log(tan((PI / 4) + (latRad / 2)))
    }

    /**
     * Enlem ve boylamı (lon, lat) ekran piksel koordinatlarına dönüştürür.
     * Bu fonksiyon zoom ve pan uygulamadan önce kullanılır.
     */
    private fun lonLatToPixel(lon: Double, lat: Double, viewWidth: Float, viewHeight: Float): PointF {
        if (viewWidth <= 0 || viewHeight <= 0) {
            Log.e("MapViewPixel", "lonLatToPixel: Geçersiz View boyutları! w=$viewWidth, h=$viewHeight. Varsayılan döndürüldü.")
            return PointF(viewWidth / 2f, viewHeight / 2f) // Güvenli varsayılan değer
        }

        // Boylamı (X) piksel koordinatına dönüştür
        val x = ((lon - MIN_LON) / (MAX_LON - MIN_LON)).toFloat() * viewWidth

        // Enlemi (Y) Mercator'a dönüştür, sonra piksel koordinatına ölçekle
        val mercatorY = latToMercatorY(lat)
        val mercatorYRange = MAX_MERCATOR_Y - MIN_MERCATOR_Y

        if (mercatorYRange == 0.0) {
            Log.e("MapViewPixel", "Mercator Y aralığı sıfır (MAX_LAT ve MIN_LAT değerleri aynı olabilir?). Piksel hesaplaması hatalı!")
            return PointF(x, viewHeight / 2f) // Güvenli varsayılan değer
        }

        val normalizedMercatorY = (mercatorY - MIN_MERCATOR_Y) / mercatorYRange
        // Y değerini 0 ile 1 arasında sınırla (clamp)
        val clampedNormalizedMercatorY = max(0.0, min(1.0, normalizedMercatorY)).toFloat()
        // Mercator Y ekseninin tersine olduğu için (harita yukarıdan aşağıya çizilir) 1.0f'den çıkarılır
        val y = (1.0f - clampedNormalizedMercatorY) * viewHeight

        return PointF(x, y)
    }

    /**
     * Tüm şehirlerin coğrafi koordinatlarını ekran piksel koordinatlarına dönüştürür
     * ve CityData nesnelerine kaydeder.
     */
    private fun calculateAllCityPixelCoordinates(viewWidth: Float, viewHeight: Float) {
        Log.d("MapViewDebug", "calculateAllCityPixelCoordinates çağrıldı. viewWidth=$viewWidth, viewHeight=$viewHeight")
        if (viewWidth <= 0 || viewHeight <= 0) {
            Log.e("MapViewDebug", "calculateAllCityPixelCoordinates: Geçersiz View boyutları! w=$viewWidth, h=$viewHeight. Şehir piksel koordinatları hesaplanamıyor.")
            return
        }

        allCities.forEach { city ->
            // Şehir merkezinin piksel koordinatlarını da hesapla ve kaydet
            val centerPixel = lonLatToPixel(city.centerLon, city.centerLat, viewWidth, viewHeight)
            city.pixelCenterX = centerPixel.x
            city.pixelCenterY = centerPixel.y

            val newPixelPolygons = mutableListOf<List<PointF>>()
            city.geoCoordinates.forEach { geoPolygonList ->
                // Her bir dış halka ve iç halka için
                geoPolygonList.forEach { ring ->
                    if (ring.size < 2) {
                        Log.w("MapViewDebug", "${city.ilAdi} için boş veya yetersiz noktaya sahip bir coğrafi poligon halkası tespit edildi (${ring.size} nokta). Atlandı.")
                        return@forEach // Bu halkayı atla
                    }
                    val newPolygonPoints = mutableListOf<PointF>()
                    ring.forEach { coordPair ->
                        if (coordPair.size == 2) {
                            val lonCoord = coordPair[0]
                            val latCoord = coordPair[1]
                            val pixelPoint = lonLatToPixel(lonCoord, latCoord, viewWidth, viewHeight)
                            newPolygonPoints.add(pixelPoint)
                        } else {
                            Log.w("MapViewDebug", "${city.ilAdi} için geçersiz koordinat çifti tespit edildi (boyut: ${coordPair.size}). Atlandı.")
                        }
                    }
                    if (newPolygonPoints.isNotEmpty()) {
                        newPixelPolygons.add(newPolygonPoints)
                    } else {
                        Log.w("MapViewDebug", "${city.ilAdi} için boş piksel poligon halkası oluşturuldu. GeoJSON verisini kontrol edin.")
                    }
                }
            }
            city.pixelPolygons = newPixelPolygons
            if (city.pixelPolygons.isEmpty() && city.geoCoordinates.isNotEmpty()) {
                Log.w("MapViewDebug", "UYARI: ${city.ilAdi} için hesaplanan pixelPolygons boş olmasına rağmen coğrafi koordinatları var! Bu, harita üzerinde tıklanamamasına neden olabilir.")
            }
        }
        val citiesWithPixels = allCities.count { it.pixelPolygons.isNotEmpty() }
        Log.d("MapViewDebug", "Tüm şehir piksel koordinatları yeniden hesaplandı: ${viewWidth}x$viewHeight. Toplam pixel koordinatı olan şehir: $citiesWithPixels / ${allCities.size}")
        if (citiesWithPixels != allCities.size) {
            Log.w("MapViewDebug", "Bazı şehirler için piksel koordinatları hesaplanamadı! Toplam ${allCities.size} şehirden ${citiesWithPixels} şehir için hesaplama başarılı.")
        }
    }

    /**
     * GeoJSON verisini `turkey_provinces.geojson` dosyasından yükler ve şehir nesnelerini ayrıştırır.
     */
    private fun loadGeoJsonData() {
        var jsonString: String = ""
        try {
            jsonString = context.assets.open("turkey_provinces.geojson")
                .bufferedReader().use { it.readText() }

            val rootObject = JSONObject(jsonString)
            val featuresArray = rootObject.getJSONArray("features")

            for (i in 0 until featuresArray.length()) {
                val featureObject = featuresArray.getJSONObject(i)
                val properties = featureObject.optJSONObject("properties")

                if (properties == null) {
                    Log.w("MapViewDataLoad", "GeoJSON'da 'properties' objesi bulunmayan bir özellik atlandı (index: $i).")
                    continue
                }

                val plaka = properties.optInt("plaka", -1)
                val ilAdi = properties.optString("name", "").trim()

                if (ilAdi.isEmpty() || ilAdi.equals("Bilinmiyor", ignoreCase = true)) {
                    Log.w("MapViewDataLoad", "GeoJSON'da boş veya 'Bilinmiyor' il adına sahip bir şehir atlandı (name: '$ilAdi', plaka: $plaka, index: $i).")
                    continue
                }

                val geometry = featureObject.optJSONObject("geometry")
                if (geometry == null) {
                    Log.w("MapViewDataLoad", "GeoJSON'da 'geometry' objesi bulunmayan bir özellik atlandı (il: $ilAdi, index: $i).")
                    continue
                }

                val type = geometry.optString("type", "")
                val coordinates = geometry.optJSONArray("coordinates")

                if (type.isEmpty() || coordinates == null) {
                    Log.w("MapViewDataLoad", "GeoJSON'da geçersiz geometri tipi ('$type') veya boş koordinatlar (il: $ilAdi, index: $i). Atlandı.")
                    continue
                }

                val geoCoordinatesList = mutableListOf<List<List<DoubleArray>>>()

                when (type) {
                    "Polygon" -> {
                        // Polygon tek bir halka listesi içerir
                        val polygonRings = parsePolygonCoordinates(coordinates)
                        if (polygonRings.isNotEmpty()) {
                            geoCoordinatesList.add(polygonRings)
                        } else {
                            Log.w("MapViewDataLoad", "Polygon için geçerli ring bulunamadı (il: $ilAdi).")
                        }
                    }
                    "MultiPolygon" -> {
                        // MultiPolygon birden fazla poligon (her biri halka listeleri) içerir
                        for (polyIndex in 0 until coordinates.length()) {
                            val singlePolygon = coordinates.optJSONArray(polyIndex)
                            if (singlePolygon != null) {
                                val polygonRings = parsePolygonCoordinates(singlePolygon)
                                if (polygonRings.isNotEmpty()) {
                                    geoCoordinatesList.add(polygonRings)
                                } else {
                                    Log.w("MapViewDataLoad", "MultiPolygon için geçerli iç poligon bulunamadı (il: $ilAdi, polyIndex: $polyIndex).")
                                }
                            } else {
                                Log.w("MapViewDataLoad", "MultiPolygon için boş singlePolygon (il: $ilAdi, polyIndex: $polyIndex). Atlandı.")
                            }
                        }
                    }
                    else -> Log.w("MapViewDataLoad", "Desteklenmeyen geometri tipi: $type (il: $ilAdi)")
                }

                if (geoCoordinatesList.isNotEmpty()) {
                    val city = CityData(plaka, ilAdi, geoCoordinatesList)
                    city.calculateCenterCoordinates()
                    allCities.add(city)
                } else {
                    Log.w("MapViewDataLoad", "Şehir (${ilAdi}) için hiçbir geçerli coğrafi koordinat bulunamadı. Harita üzerinde çizilemeyecek.")
                }
            }

            Log.d("MapViewDataLoad", "GeoJSON verisi başarıyla yüklendi. ${allCities.size} şehir bulundu.")
            post {
                onDataLoadedListener?.onGeoJsonDataLoaded()
                Log.d("MapViewDataLoad", "onDataLoadedListener tetiklendi (post edilmiş).")
            }

        } catch (e: IOException) {
            Log.e("MapViewDataLoad", "GeoJSON dosyası okunurken hata: ${e.message}. Dosyanın 'assets' klasöründe 'turkey_provinces.geojson' adıyla bulunduğundan emin olun.", e)
        } catch (e: JSONException) {
            Log.e("MapViewDataLoad", "GeoJSON ayrıştırılırken hata: ${e.message}. JSON dosyanızın formatını kontrol edin.", e)
            Log.e("MapViewDataLoad", "Hatalı JSON String'in ilk 500 karakteri: ${jsonString.take(min(jsonString.length, 500))}", e)
        } catch (e: Exception) {
            Log.e("MapViewDataLoad", "Beklenmeyen hata GeoJSON yüklenirken: ${e.message}", e)
        }
    }

    // Yardımcı fonksiyon: Polygon koordinatlarını ayrıştırır
    private fun parsePolygonCoordinates(jsonArray: JSONArray): List<List<DoubleArray>> {
        val polygonRings = mutableListOf<List<DoubleArray>>()
        for (ringIndex in 0 until jsonArray.length()) {
            val ringArray = jsonArray.optJSONArray(ringIndex)
            if (ringArray == null) {
                continue
            }
            val ringCoords = mutableListOf<DoubleArray>()
            for (j in 0 until ringArray.length()) {
                val coordPair = ringArray.optJSONArray(j)
                if (coordPair != null && coordPair.length() == 2) {
                    ringCoords.add(doubleArrayOf(coordPair.getDouble(0), coordPair.getDouble(1))) // [lon, lat]
                }
            }
            if (ringCoords.isNotEmpty()) {
                polygonRings.add(ringCoords)
            }
        }
        return polygonRings
    }

    // Harita çizimini yönetir
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Harita arka plan rengi (Deniz veya boş alanlar için)
        canvas.drawColor(ContextCompat.getColor(context, R.color.map_background_color))

        canvas.save()

        // Yakınlaştırma ve kaydırma dönüşümlerini uygula
        canvas.translate(offsetX, offsetY)
        canvas.scale(scaleFactor, scaleFactor)

        // Tüm şehirleri varsayılan renk ve dış çizgi ile çiz
        // Sadece kalıcı olarak vurgulanmayan ve anlık soru olmayan şehirleri çiz
        allCities.forEach { city ->
            val isPermanentlyHighlighted = permanentlyHighlightedCities.containsKey(city)
            val isCurrentQuestion = city == currentQuestionCity

            if (!isPermanentlyHighlighted && !isCurrentQuestion) {
                drawCityShape(canvas, city, defaultCityPaint, cityOutlinePaint)
            }
        }

        // Anlık soru olan şehri vurgula (eğer varsa)
        currentQuestionCity?.let { questionCity ->
            // Doğru veya yanlış bilindiyse, ilgili vurgu rengini kullan
            val highlightPaintForQuestion = when {
                permanentlyHighlightedCities.containsKey(questionCity) -> // Eğer zaten kalıcı olarak vurgulanmışsa (cevap verilmişse)
                    permanentlyHighlightedCities[questionCity]?.first ?: correctLocationHighlightPaint
                else -> // Soru henüz cevaplanmadıysa, varsayılan vurgu rengini kullan (isteğe bağlı, şu an bu senaryo yok gibi)
                    correctLocationHighlightPaint // Veya farklı bir renk, örneğin sarımsı bir renk
            }
            drawCityShape(canvas, questionCity, highlightPaintForQuestion, cityOutlinePaint)
            // Sadece vurgu durumunda ismini çiz (cevap verildiyse)
            if (permanentlyHighlightedCities.containsKey(questionCity)) {
                drawCityName(canvas, questionCity)
            }
        }

        // Kalıcı olarak vurgulanan şehirleri çiz (doğru cevap verilenler)
        permanentlyHighlightedCities.forEach { (city, paintInfo) ->
            val paint = paintInfo.first
            val drawName = paintInfo.second
            // Eğer bu şehir currentQuestionCity ise, yukarıda zaten çizildiği için burada tekrar çizme
            if (city != currentQuestionCity) {
                drawCityShape(canvas, city, paint, cityOutlinePaint)
                if (drawName) {
                    drawCityName(canvas, city)
                }
            }
        }

        canvas.restore()
    }

    // Şehir poligonunu çizen yardımcı metot (isim çizmez)
    private fun drawCityShape(canvas: Canvas, city: CityData, fillPaint: Paint, outlinePaint: Paint) {
        city.pixelPolygons.forEach { points ->
            val path = Path()
            if (points.isNotEmpty()) {
                path.moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) {
                    path.lineTo(points[i].x, points[i].y)
                }
                path.close()
            } else {
                return@forEach // Bu boş poligonu atla
            }
            canvas.drawPath(path, fillPaint)
            canvas.drawPath(path, outlinePaint)
        }
    }

    // Şehir ismini çizen yardımcı metot
    private fun drawCityName(canvas: Canvas, city: CityData) {
        // Pixel merkez koordinatları hesaplanmış olmalı
        if (city.pixelCenterX != 0f || city.pixelCenterY != 0f) {
            // Zoom seviyesine göre metin boyutunu ayarla
            // scaleFactor büyüdükçe metin boyutu küçülür, bu da zoom yapınca metnin devasa olmasını engeller
            // Ancak amacımız zoom yaptıkça metnin okunabilirliğini artırmaksa,
            // tam tersi bir mantıkla textPaint.textSize = originalTextSize / scaleFactor değil de
            // textPaint.textSize = originalTextSize * scaleFactor veya sabit tutulabilir.
            // Amaç "küçültmek" olduğu için, 1.0f / scaleFactor mantığı doğru olabilir.
            // Daha iyi bir yaklaşım, metin boyutunu "viewport"a göre ayarlamaktır.
            // Şimdilik sadece küçültme isteğine göre ayarlıyorum:
            val currentTextSize = 18f / scaleFactor // Başlangıç 18f, zoom yaptıkça küçülecek
            // Eğer zoom yapıldığında metin büyüsün isteniyorsa, "18f * scaleFactor" kullanılmalıydı
            // Ya da metnin sabit kalması isteniyorsa, sadece "18f" olarak kalırdı.
            // Not: Min/Max boyut sınırları da eklenebilir.
            cityNamePaint.textSize = currentTextSize

            // Metin konumu ayarı: drawText, y koordinatını metnin alt taban çizgisi olarak kullanır.
            // Bu yüzden metni ortalamak için ascent ve descent değerleri kullanılır.
            val textY = city.pixelCenterY - ((cityNamePaint.descent() + cityNamePaint.ascent()) / 2)

            canvas.drawText(city.ilAdi, city.pixelCenterX, textY, cityNamePaint)
        }
    }


    /**
     * Haritada şehirlerin vurgu durumunu günceller.
     * @param questionCity Anlık sorulan şehir.
     * @param clickedCity Tıklanan şehir.
     * @param isCorrect Cevap doğruysa true, yanlışsa false, sıfırlanacaksa null.
     */
    fun updateHighlights(questionCity: CityData?, clickedCity: CityData?, isCorrect: Boolean?) {
        this.currentQuestionCity = questionCity // Anlık sorulan şehir set ediliyor

        if (isCorrect == true && questionCity != null) {
            // Doğru cevap durumunda, rastgele renkte vurgula ve ismini göster
            correctHighlightPaint.color = Color.argb(255, Random.nextInt(256), Random.nextInt(256), Random.nextInt(256))
            permanentlyHighlightedCities[questionCity] = Pair(correctHighlightPaint, true)
            Log.d("MapViewHighlight", "${questionCity.ilAdi} kalıcı olarak vurgulananlara eklendi (doğru cevap).")
        } else if (isCorrect == false && questionCity != null) {
            // Yanlış cevap durumunda, doğru şehri mavi (correctLocationHighlightPaint) ile vurgula ve ismini göster
            permanentlyHighlightedCities[questionCity] = Pair(correctLocationHighlightPaint, true)
            Log.d("MapViewHighlight", "${questionCity.ilAdi} kalıcı olarak vurgulananlara eklendi (yanlış bilindiği için mavi).")

            // Ek olarak, yanlış tıklanan şehri de kırmızıyla vurgulamak isteyebilirsin.
            // Şu an bu mantıkta sadece doğru cevabın yeri vurgulanıyor.
            // Eğer yanlış tıklanan şehri de vurgulamak istersen:
            clickedCity?.let {
                if (it != questionCity) { // Zaten doğru şehir değilse
                    permanentlyHighlightedCities[it] = Pair(wrongHighlightPaint, false) // Adını gösterme
                    Log.d("MapViewHighlight", "Yanlış tıklanan ${it.ilAdi} kırmızıyla vurgulandı.")
                }
            }
        } else if (isCorrect == null) {
            // Vurguyu sıfırlama talebi geldiğinde
            // currentQuestionCity'yi null yapmamak, yeni soru gelene kadar eski sorunun vurgusunun kalmasını sağlar
            // Bu yüzden null yapmıyorum, sadece cevap durumlarını sıfırlıyorum.
            Log.d("MapViewHighlight", "Vurgu durumu sıfırlandı. Kalıcı vurgular korunuyor.")
        }
        invalidate() // Değişiklikleri ekrana yansıt
    }

    /**
     * Tüm geçici ve kalıcı vurguları temizler, harita konumunu sıfırlar.
     */
    fun clearAllHighlights() {
        permanentlyHighlightedCities.clear()
        currentQuestionCity = null
        //lastClickedCity = null // Artık kullanılmıyor

        // isCorrectAnswerDisplayed ve isWrongAnswerDisplayed kaldırıldığı için bunlara gerek kalmadı

        scaleFactor = 1.0f // Zoom sıfırlandı
        offsetX = 0f       // Kaydırma sıfırlandı
        offsetY = 0f       // Kaydırma sıfırlandı

        invalidate() // Haritayı yeniden çiz
        Log.d("MapViewHighlight", "Tüm vurgular ve harita durumu temizlendi, görünüm sıfırlandı.")
    }

    fun getAllLoadedCities(): List<CityData> {
        return allCities.toList()
    }
}