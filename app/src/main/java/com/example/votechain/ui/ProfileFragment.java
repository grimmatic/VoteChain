package com.example.votechain.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.votechain.R;
import com.example.votechain.model.Election;
import com.example.votechain.model.User;
import com.example.votechain.model.Vote;
import com.example.votechain.util.Utils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class ProfileFragment extends Fragment {

    private TextView tvName, tvTcNo, tvNoVotes;
    private Button btnLogout;
    private RecyclerView recyclerViewVoteHistory;
    private ProgressBar progressBar;

    private VoteHistoryAdapter adapter;
    private List<VoteHistoryItem> voteHistoryList;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        // UI bileşenleri
        tvName = view.findViewById(R.id.tvName);
        tvTcNo = view.findViewById(R.id.tvTcNo);
        tvNoVotes = view.findViewById(R.id.tvNoVotes);
        btnLogout = view.findViewById(R.id.btnLogout);
        recyclerViewVoteHistory = view.findViewById(R.id.recyclerViewVoteHistory);
        progressBar = view.findViewById(R.id.progressBar);

        // Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // RecyclerView ayarları
        voteHistoryList = new ArrayList<>();
        adapter = new VoteHistoryAdapter(voteHistoryList);
        recyclerViewVoteHistory.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewVoteHistory.setAdapter(adapter);

        // Kullanıcı bilgilerini yükle
        loadUserInfo();

        // Oy geçmişini yükle
        loadVoteHistory();

        // Çıkış yapma butonu
        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            startActivity(new Intent(getActivity(), LoginActivity.class));
            getActivity().finish();
        });

        return view;
    }

    private void loadUserInfo() {
        String userId = mAuth.getCurrentUser().getUid();
        progressBar.setVisibility(View.VISIBLE);

        db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    progressBar.setVisibility(View.GONE);

                    if (documentSnapshot.exists()) {
                        // User nesnesine çevirmeden önce doğrudan alanları oku
                        String ad = documentSnapshot.getString("ad");
                        String soyad = documentSnapshot.getString("soyad");
                        String tcKimlikNo = documentSnapshot.getString("tcKimlikNo");

                        if (ad != null && soyad != null) {
                            tvName.setText(ad + " " + soyad);
                        } else {
                            tvName.setText("Ad Soyad bilgisi bulunamadı");
                        }

                        if (tcKimlikNo != null) {
                            tvTcNo.setText("TC: " + tcKimlikNo);
                        } else {
                            tvTcNo.setText("TC Kimlik bilgisi bulunamadı");
                        }

                    } else {
                        tvName.setText("Kullanıcı bilgisi bulunamadı");
                        tvTcNo.setText("TC: Bilgi yok");
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    tvName.setText("Bilgiler yüklenemedi");
                    tvTcNo.setText("TC: Bilgi yok");


                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Kullanıcı bilgileri yüklenemedi: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadVoteHistory() {
        String userId = mAuth.getCurrentUser().getUid();
        progressBar.setVisibility(View.VISIBLE);

        db.collection("votes")
                .whereEqualTo("userId", userId)
                .get()
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);

                    if (task.isSuccessful()) {
                        if (task.getResult().isEmpty()) {
                            tvNoVotes.setVisibility(View.VISIBLE);
                            recyclerViewVoteHistory.setVisibility(View.GONE);
                        } else {
                            tvNoVotes.setVisibility(View.GONE);
                            recyclerViewVoteHistory.setVisibility(View.VISIBLE);

                            voteHistoryList.clear();
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                Vote vote = document.toObject(Vote.class);
                                vote.setId(document.getId());

                                // Seçim adını al
                                db.collection("elections").document(vote.getElectionId())
                                        .get()
                                        .addOnSuccessListener(electionSnapshot -> {
                                            Election election = electionSnapshot.toObject(Election.class);
                                            String electionName = election != null ? election.getName() : "Bilinmeyen Seçim";

                                            // Aday adını al
                                            db.collection("elections").document(vote.getElectionId())
                                                    .collection("candidates").document(vote.getCandidateId())
                                                    .get()
                                                    .addOnSuccessListener(candidateSnapshot -> {
                                                        String candidateName = candidateSnapshot.exists() ?
                                                                candidateSnapshot.getString("name") : "Bilinmeyen Aday";

                                                        // Oy geçmişi öğesi oluştur
                                                        VoteHistoryItem item = new VoteHistoryItem(
                                                                vote.getId(),
                                                                electionName,
                                                                candidateName,
                                                                Utils.formatDateTime(vote.getTimestamp())
                                                        );

                                                        voteHistoryList.add(item);
                                                        adapter.notifyDataSetChanged();
                                                    });
                                        });
                            }
                        }
                    } else {
                        Toast.makeText(getContext(), "Oy geçmişi yüklenemedi: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // Oy geçmişi modeli
    public static class VoteHistoryItem {
        private String id;
        private String electionName;
        private String candidateName;
        private String voteDate;

        public VoteHistoryItem(String id, String electionName, String candidateName, String voteDate) {
            this.id = id;
            this.electionName = electionName;
            this.candidateName = candidateName;
            this.voteDate = voteDate;
        }

        public String getId() {
            return id;
        }

        public String getElectionName() {
            return electionName;
        }

        public String getCandidateName() {
            return candidateName;
        }

        public String getVoteDate() {
            return voteDate;
        }
    }

    // Oy geçmişi adapter
    private static class VoteHistoryAdapter extends RecyclerView.Adapter<VoteHistoryAdapter.ViewHolder> {
        private List<VoteHistoryItem> items;

        public VoteHistoryAdapter(List<VoteHistoryItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_vote_history, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            VoteHistoryItem item = items.get(position);
            holder.tvElectionName.setText(item.getElectionName());
            holder.tvCandidateName.setText("Oy: " + item.getCandidateName());
            holder.tvVoteDate.setText("Tarih: " + item.getVoteDate());
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvElectionName, tvCandidateName, tvVoteDate;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvElectionName = itemView.findViewById(R.id.tvElectionName);
                tvCandidateName = itemView.findViewById(R.id.tvCandidateName);
                tvVoteDate = itemView.findViewById(R.id.tvVoteDate);
            }
        }
    }
}