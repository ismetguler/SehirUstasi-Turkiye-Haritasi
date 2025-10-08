// com.ismetguler.sehirtahmin/LeaderboardPagerAdapter.kt
package com.ismetguler.sehirtahmin

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class LeaderboardPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
    override fun getItemCount(): Int = 2 // İki sekme: Fotoğraf ve Harita

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> PhotoLeaderboardFragment()
            1 -> MapLeaderboardFragment()
            else -> throw IllegalStateException("Geçersiz pozisyon: $position")
        }
    }
}