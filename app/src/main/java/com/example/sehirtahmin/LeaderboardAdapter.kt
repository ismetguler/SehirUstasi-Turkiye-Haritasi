// app/src/main/java/com/example/sehirtahminoyunu/LeaderboardAdapter.kt (Kendi paket adınla değiştir!)
package com.ismetguler.sehirtahmin // Burayı kendi paket adınla AYNI yap

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ismetguler.sehirtahmin.PlayerScore // PlayerScore sınıfını import etmeyi unutma

class LeaderboardAdapter(private val scores: List<PlayerScore>) :
    RecyclerView.Adapter<LeaderboardAdapter.ScoreViewHolder>() {

    class ScoreViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // item_leaderboard_score.xml'deki ID'lerle eşleştiğinden emin ol
        val rankTextView: TextView = itemView.findViewById(R.id.textViewRank)
        val nameTextView: TextView = itemView.findViewById(R.id.textViewPlayerName)
        val scoreTextView: TextView = itemView.findViewById(R.id.textViewScore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScoreViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_leaderboard_score, parent, false)
        return ScoreViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScoreViewHolder, position: Int) {
        val currentScore = scores[position]
        holder.rankTextView.text = (position + 1).toString() // Sıralama (1., 2., 3. vb.)
        holder.nameTextView.text = currentScore.playerName
        holder.scoreTextView.text = currentScore.correctAnswersCount.toString() // Skor bilgisini göster
    }

    override fun getItemCount(): Int {
        return scores.size
    }
}