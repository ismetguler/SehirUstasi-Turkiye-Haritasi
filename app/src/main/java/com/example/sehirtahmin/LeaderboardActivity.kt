// com.ismetguler.sehirtahmin/LeaderboardActivity.kt
package com.ismetguler.sehirtahmin

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class LeaderboardActivity : AppCompatActivity() {

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var buttonBackToMenuFromLeaderboard: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leaderboard) // Bu layout'u sonra güncelleyeceğiz

        tabLayout = findViewById(R.id.tabLayout) // Yeni eklenecek ID
        viewPager = findViewById(R.id.viewPager) // Yeni eklenecek ID
        buttonBackToMenuFromLeaderboard = findViewById(R.id.buttonBackToMenuFromLeaderboard)

        val adapter = LeaderboardPagerAdapter(this)
        viewPager.adapter = adapter

        // TabLayout ile ViewPager2'yi bağla
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Fotoğraf Tahmin"
                1 -> "Harita Tahmin"
                else -> ""
            }
        }.attach()

        // Ana Menüye Dön butonuna tıklama dinleyicisi
        buttonBackToMenuFromLeaderboard.setOnClickListener {
            val intent = Intent(this, MainMenuActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}