package com.example.votechain.ui;

import android.os.Bundle;
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
import com.example.votechain.model.Candidate;
import com.example.votechain.model.Election;
import com.example.votechain.model.Vote;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class VoteFragment extends Fragment {

    private TextView tvElectionName, tvNoElection;
    private RecyclerView recyclerViewCandidates;
    private Button btnSubmitVote;
    private ProgressBar progressBar;

    private CandidateAdapter adapter;
    private List<Candidate> candidateList;
    private String selectedCandidateId;
    private String electionId;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_vote, container, false);

        // UI bileşenleri
        tvElectionName = view.findViewById(R.id.tvElectionName);
        tvNoElection = view.findViewById(R.id.tvNoElection);
        recyclerViewCandidates = view.findViewById(R.id.recyclerViewCandidates);
        btnSubmitVote = view.findViewById(R.id.btnSubmitVote);
        progressBar = view.findViewById(R.id.progressBar);

        // Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Arguments'ten seçim bilgilerini al
        Bundle args = getArguments();
        if (args != null) {
            electionId = args.getString("electionId", "");
            String electionName = args.getString("electionName", "");
            tvElectionName.setText(electionName);

            if (!electionId.isEmpty()) {
                // RecyclerView ayarları
                candidateList = new ArrayList<>();
                adapter = new CandidateAdapter(candidateList, candidate -> {
                    // Adaya tıklandığında
                    selectedCandidateId = candidate.getId();
                });
                recyclerViewCandidates.setLayoutManager(new LinearLayoutManager(getContext()));
                recyclerViewCandidates.setAdapter(adapter);

                // Adayları yükle
                loadCandidates(electionId);

                // Seçim görünümleri
                tvNoElection.setVisibility(View.GONE);
                recyclerViewCandidates.setVisibility(View.VISIBLE);
                btnSubmitVote.setVisibility(View.VISIBLE);
            }
        } else {
            // Seçim ID'si yok, seçim seç mesajı göster
            tvNoElection.setVisibility(View.VISIBLE);
            recyclerViewCandidates.setVisibility(View.GONE);
            btnSubmitVote.setVisibility(View.GONE);
        }

        // Oy verme butonu tıklama
        btnSubmitVote.setOnClickListener(v -> {
            if (selectedCandidateId != null && !selectedCandidateId.isEmpty()) {
                submitVote();
            } else {
                Toast.makeText(getContext(), "Lütfen bir aday seçin", Toast.LENGTH_SHORT).show();
            }
        });

        return view;
    }

    private void loadCandidates(String electionId) {
        progressBar.setVisibility(View.VISIBLE);

        db.collection("elections").document(electionId)
                .collection("candidates")
                .get()
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);

                    if (task.isSuccessful()) {
                        candidateList.clear();

                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Candidate candidate = document.toObject(Candidate.class);
                            candidate.setId(document.getId());
                            candidateList.add(candidate);
                        }

                        adapter.notifyDataSetChanged();
                    } else {
                        Toast.makeText(getContext(), "Adaylar yüklenirken hata oluştu: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void submitVote() {
        // Kullanıcı daha önce oy kullanmış mı kontrol et
        String userId = mAuth.getCurrentUser().getUid();

        db.collection("votes")
                .whereEqualTo("userId", userId)
                .whereEqualTo("electionId", electionId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        if (task.getResult().isEmpty()) {
                            // Daha önce oy kullanmamış, yeni oy kaydı oluştur
                            Vote vote = new Vote(userId, electionId, selectedCandidateId);
                            vote.setTimestamp(new Date());

                            db.collection("votes").add(vote)
                                    .addOnSuccessListener(documentReference -> {
                                        Toast.makeText(getContext(), "Oyunuz başarıyla kaydedildi", Toast.LENGTH_SHORT).show();

                                        // Ana sayfaya dön
                                        getActivity().getSupportFragmentManager().beginTransaction()
                                                .replace(R.id.fragmentContainer, new ElectionsFragment())
                                                .commit();
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(getContext(), "Oy kaydedilirken hata oluştu: " + e.getMessage(),
                                                Toast.LENGTH_SHORT).show();
                                    });
                        } else {
                            // Daha önce oy kullanmış
                            Toast.makeText(getContext(), "Bu seçimde daha önce oy kullandınız",
                                    Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(getContext(), "Oy kontrolü yapılırken hata oluştu: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
}