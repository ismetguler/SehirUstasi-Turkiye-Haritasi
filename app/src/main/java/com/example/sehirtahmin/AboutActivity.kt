package com.ismetguler.sehirtahmin

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge // Burası yeni
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ismetguler.sehirtahmin.databinding.ActivityAboutBinding


class AboutGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // enableEdgeToEdge'i setContentView'dan önce çağırın
        enableEdgeToEdge() // <-- Bu satır eklendi

        super.onCreate(savedInstanceState)

        // enableEdgeToEdge() zaten çoğu şeyi halleder,
        // ancak özel davranışlar için WindowInsetsControllerCompat'ı hala kullanabilirsiniz.
        // Eğer sistem çubuklarını tamamen gizlemek istiyorsanız bu kısım hala geçerlidir.
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // enableEdgeToEdge() genellikle durum ve navigasyon çubuklarını transparan yapar.
        // Bu yüzden aşağıdaki manuel renk ayarları genellikle artık gerekli olmaz
        // veya farklı bir renk istiyorsanız kullanabilirsiniz.
        // Ancak eğer sistem çubuklarını gizliyorsanız, renk ayarının çok bir önemi kalmaz.
        /*
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.apply {
                decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                statusBarColor = Color.TRANSPARENT
                navigationBarColor = Color.TRANSPARENT
            }
        }
        */

        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonBackFromAboutGame.setOnClickListener {
            finish()
        }
    }
}