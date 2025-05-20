package com.example.votechain.ui;

import android.app.AlertDialog;
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
import com.example.votechain.blockchain.BlockchainManager;
import com.example.votechain.model.Candidate;
import com.example.votechain.model.Election;
import com.example.votechain.model.Vote;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Oy verme işlemlerini gerçekleştiren fragment.
 * Blockchain entegrasyonu eklenmiştir.
 */
public class VoteFragmentWithBlockchain extends Fragment {

    private TextView tvElectionName, tvNoElection, tvBlockchainStatus;
    private RecyclerView recyclerViewCandidates;
    private Button btnSubmitVote;
    private ProgressBar progressBar;

    private CandidateAdapter adapter;
    private List<Candidate> candidateList;
    private String selectedCandidateId;
    private String electionId;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private BlockchainManager blockchainManager;

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

        // Blockchain durumunu göstermek için yeni TextView ekledik
        tvBlockchainStatus = new TextView(getContext());
        tvBlockchainStatus.setId(View.generateViewId());
        tvBlockchainStatus.setText("Blockchain Durumu: Hazırlanıyor...");
        ViewGroup parentView = (ViewGroup) recyclerViewCandidates.getParent();
        parentView.addView(tvBlockchainStatus, parentView.indexOfChild(recyclerViewCandidates));

        // Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Blockchain Manager instance al
        blockchainManager = BlockchainManager.getInstance();

        // Blockchain durumunu kontrol et
        String walletAddress = blockchainManager.getWalletAddress();
        if (walletAddress != null) {
            tvBlockchainStatus.setText("Blockchain Durumu: Bağlı (Cüzdan: " +
                    walletAddress.substring(0, 8) + "...)");
        } else {
            tvBlockchainStatus.setText("Blockchain Durumu: Cüzdan başlatılamadı");
            // Cüzdanı başlatmayı dene
            initializeWallet();
        }

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
                // Blockchain entegrasyonu ile oy verme
                submitVoteWithBlockchain();
            } else {
                Toast.makeText(getContext(), "Lütfen bir aday seçin", Toast.LENGTH_SHORT).show();
            }
        });

        return view;
    }

    /**
     * Ethereum cüzdanını başlatır
     */
    private void initializeWallet() {
        // Basit bir şifre kullanıyoruz, gerçek uygulamada kullanıcıdan alınmalı
        String password = "votechain123";

        boolean success = blockchainManager.initializeWallet(getContext(), password);
        if (success) {
            String walletAddress = blockchainManager.getWalletAddress();
            tvBlockchainStatus.setText("Blockchain Durumu: Bağlı (Cüzdan: " +
                    walletAddress.substring(0, 8) + "...)");
        } else {
            tvBlockchainStatus.setText("Blockchain Durumu: Cüzdan başlatılamadı");
            // Blockchain olmadan devam etmeyi seçebiliriz - Firebase'e yedekleme yapacağız
        }
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

    /**
     * Blockchain entegrasyonu ile oy verme işlemi
     */
    private void submitVoteWithBlockchain() {
        // Kullanıcının daha önce oy kullanıp kullanmadığını kontrol et
        String userId = mAuth.getCurrentUser().getUid();

        db.collection("votes")
                .whereEqualTo("userId", userId)
                .whereEqualTo("electionId", electionId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        if (task.getResult().isEmpty()) {
                            // Daha önce oy kullanmamış, blockchain işlemini başlat
                            // Kullanıcının TC Kimlik numarasını al
                            db.collection("users").document(userId)
                                    .get()
                                    .addOnSuccessListener(documentSnapshot -> {
                                        if (documentSnapshot.exists()) {
                                            String tcKimlikNo = documentSnapshot.getString("tcKimlikNo");

                                            if (tcKimlikNo != null && !tcKimlikNo.isEmpty()) {
                                                // Kullanıcı onayı al
                                                showVoteConfirmationDialog(tcKimlikNo);
                                            } else {
                                                Toast.makeText(getContext(), "TC Kimlik bilgisi bulunamadı",
                                                        Toast.LENGTH_SHORT).show();
                                            }
                                        } else {
                                            Toast.makeText(getContext(), "Kullanıcı bilgileri bulunamadı",
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(getContext(), "Kullanıcı bilgileri alınamadı: " + e.getMessage(),
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

    /**
     * Oy vermeden önce kullanıcıdan onay al
     */
    private void showVoteConfirmationDialog(String tcKimlikNo) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Oy Onayı");
        builder.setMessage("Seçtiğiniz aday için oyunuzu blockchain üzerinde kaydedilecektir. Devam etmek istiyor musunuz?");
        builder.setPositiveButton("Evet, Oyumu Kullan", (dialog, which) -> {
            // Oy verme işlemini gerçekleştir
            processVoteOnBlockchain(tcKimlikNo);
        });
        builder.setNegativeButton("İptal", (dialog, which) -> {
            dialog.dismiss();
        });
        builder.create().show();
    }

    /**
     * Blockchain üzerinde oy verme işlemini gerçekleştirir
     */
    private void processVoteOnBlockchain(String tcKimlikNo) {
        progressBar.setVisibility(View.VISIBLE);

        try {
            // Blockchain üzerinde oy kullan
            blockchainManager.vote(
                            new BigInteger(electionId),
                            new BigInteger(selectedCandidateId),
                            tcKimlikNo)
                    .thenAccept(transactionHash -> {
                        // Blockchain işlemi başarılı, Firebase'e kaydet
                        saveVoteToFirebase(transactionHash);
                    })
                    .exceptionally(e -> {
                        getActivity().runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(getContext(), "Blockchain işlemi sırasında hata: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();

                            // Blockchain başarısız olursa yine de Firebase'e kaydet
                            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                            builder.setTitle("Blockchain Hatası");
                            builder.setMessage("Blockchain üzerinde oy kaydedilirken hata oluştu. Oyunuzu sadece Firebase'e kaydetmek ister misiniz?");
                            builder.setPositiveButton("Evet", (dialog, which) -> {
                                saveVoteToFirebase(null);
                            });
                            builder.setNegativeButton("İptal", (dialog, which) -> {
                                dialog.dismiss();
                            });
                            builder.create().show();
                        });
                        return null;
                    });
        } catch (Exception e) {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(getContext(), "Blockchain işlemi sırasında hata: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Oyu Firebase'e kaydeder
     */
    private void saveVoteToFirebase(String transactionHash) {
        String userId = mAuth.getCurrentUser().getUid();

        // Oy nesnesi oluştur
        Vote vote = new Vote(userId, electionId, selectedCandidateId);
        vote.setTimestamp(new Date());

        // Eğer blockchain işlemi başarılıysa, işlem hash'ini kaydet
        if (transactionHash != null) {
            vote.setTransactionHash(transactionHash);
        }

        // Firebase'e kaydet
        db.collection("votes").add(vote)
                .addOnSuccessListener(documentReference -> {
                    // Adayın oy sayısını artır
                    updateCandidateVoteCount();

                    getActivity().runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);

                        String message = "Oyunuz başarıyla kaydedildi";
                        if (transactionHash != null) {
                            message += " ve blockchain üzerinde doğrulandı";
                        }

                        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();

                        // Ana sayfaya dön
                        getActivity().getSupportFragmentManager().beginTransaction()
                                .replace(R.id.fragmentContainer, new ElectionsFragment())
                                .commit();
                    });
                })
                .addOnFailureListener(e -> {
                    getActivity().runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(getContext(), "Firebase kaydı sırasında hata: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
                });
    }

    /**
     * Adayın oy sayısını artırır
     */
    private void updateCandidateVoteCount() {
        db.collection("elections").document(electionId)
                .collection("candidates").document(selectedCandidateId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Candidate candidate = documentSnapshot.toObject(Candidate.class);
                        if (candidate != null) {
                            // Oy sayısını artır
                            int voteCount = candidate.getVoteCount() + 1;

                            // Firebase'de güncelle
                            db.collection("elections").document(electionId)
                                    .collection("candidates").document(selectedCandidateId)
                                    .update("voteCount", voteCount);
                        }
                    }
                });
    }
}