// com.ismetguler.sehirtahmin/MapLeaderboardFragment.kt
package com.ismetguler.sehirtahmin

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class MapLeaderboardFragment : Fragment() {

    private lateinit var leaderboardRecyclerView: RecyclerView
    private lateinit var progressBarLeaderboard: ProgressBar
    private lateinit var textViewNoScores: TextView

    private lateinit var firestore: FirebaseFirestore
    private val scoresList = mutableListOf<PlayerScore>()
    private lateinit var leaderboardAdapter: LeaderboardAdapter

    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_leaderboard_content, container, false)

        // XML bileşenlerini ID'leriyle bağlıyoruz (fragment_leaderboard_content.xml'deki ID'leri kullandım)
        leaderboardRecyclerView = view.findViewById(R.id.recyclerViewLeaderboard)
        progressBarLeaderboard = view.findViewById(R.id.progressBarLeaderboard)
        textViewNoScores = view.findViewById(R.id.textViewNoScores)

        leaderboardRecyclerView.layoutManager = LinearLayoutManager(context)

        leaderboardAdapter = LeaderboardAdapter(scoresList)
        leaderboardRecyclerView.adapter = leaderboardAdapter

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Burada view'ler zaten initialize edildiği için güvenle erişebiliriz.
        checkLoginStatusAndLoadScores("mapScores") // "mapScores" koleksiyonunu yüklüyoruz
    }

    private fun checkLoginStatusAndLoadScores(collectionPath: String) {
        if (auth.currentUser == null) {
            // Misafir kullanıcı
            leaderboardRecyclerView.visibility = View.GONE
            progressBarLeaderboard.visibility = View.GONE
            textViewNoScores.visibility = View.VISIBLE
            textViewNoScores.text = "Liderlik tablosunu görmek için Google ile giriş yapmalısın."
        } else {
            // Google ile giriş yapmış kullanıcı
            textViewNoScores.visibility = View.GONE // Gizle
            loadLeaderboard(collectionPath)
        }
    }

    private fun loadLeaderboard(collectionPath: String) {
        progressBarLeaderboard.visibility = View.VISIBLE
        textViewNoScores.visibility = View.GONE
        leaderboardRecyclerView.visibility = View.GONE

        firestore.collection(collectionPath)
            .orderBy("correctAnswersCount", Query.Direction.DESCENDING) // En yüksek skordan düşüğe sırala
            .limit(50) // En yüksek 50 skoru getir
            .addSnapshotListener { snapshots, e ->
                progressBarLeaderboard.visibility = View.GONE // Yükleme bitti

                if (e != null) {
                    Log.w("LeaderboardFragment", "Skorları yükleme hatası: ", e)
                    textViewNoScores.text = "Skorlar yüklenirken bir hata oluştu."
                    textViewNoScores.visibility = View.VISIBLE
                    return@addSnapshotListener
                }

                scoresList.clear()
                if (snapshots != null && !snapshots.isEmpty) {
                    for (doc in snapshots) {
                        val score = doc.toObject(PlayerScore::class.java)
                        scoresList.add(score)
                    }
                    leaderboardAdapter.notifyDataSetChanged()
                    leaderboardRecyclerView.visibility = View.VISIBLE
                    textViewNoScores.visibility = View.GONE
                } else {
                    textViewNoScores.text = "Liderlik tablosunda henüz skor yok."
                    textViewNoScores.visibility = View.VISIBLE
                    leaderboardRecyclerView.visibility = View.GONE
                }
            }
    }
}