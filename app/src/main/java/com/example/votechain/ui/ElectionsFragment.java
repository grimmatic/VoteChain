package com.example.votechain.ui;

import static android.content.ContentValues.TAG;

import android.app.AlertDialog;
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
import com.example.votechain.blockchain.BlockchainElectionManager;
import com.example.votechain.model.Candidate;
import com.example.votechain.model.Election;
import com.example.votechain.model.User;
import com.example.votechain.model.Vote;
import com.example.votechain.util.Utils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * seçim + oy
 */
public class ElectionsFragment extends Fragment {

    private RecyclerView recyclerViewElections, recyclerViewCandidates;
    private ProgressBar progressBar;
    private TextView tvNoElections, tvCurrentElection, tvBlockchainStatus;
    private Button btnBackToElections, btnSubmitVote;
    private View layoutElectionsList, layoutVoting;

    private List<Election> electionList;
    private List<Candidate> candidateList;
    private ElectionAdapter electionAdapter;
    private CandidateAdapter candidateAdapter;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private BlockchainElectionManager electionManager;

    private Election currentElection;
    private String selectedCandidateId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_elections_combined, container, false);

        initViews(view);
        setupFirebaseAndBlockchain();
        setupRecyclerViews();
        setupClickListeners();

        // Başlangıçta seçim listesini göster
        showElectionsList();
        loadElections();

        return view;
    }

    private void initViews(View view) {
        // Seçim listesi görünümleri
        layoutElectionsList = view.findViewById(R.id.layoutElectionsList);
        recyclerViewElections = view.findViewById(R.id.recyclerViewElections);
        tvNoElections = view.findViewById(R.id.tvNoElections);

        // Oy verme görünümleri
        layoutVoting = view.findViewById(R.id.layoutVoting);
        tvCurrentElection = view.findViewById(R.id.tvCurrentElection);
        recyclerViewCandidates = view.findViewById(R.id.recyclerViewCandidates);
        btnBackToElections = view.findViewById(R.id.btnBackToElections);
        btnSubmitVote = view.findViewById(R.id.btnSubmitVote);

        progressBar = view.findViewById(R.id.progressBar);
    }

    private void setupFirebaseAndBlockchain() {
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        electionManager = BlockchainElectionManager.getInstance();


    }

    private void setupRecyclerViews() {
        // Seçim listesi RecyclerView
        electionList = new ArrayList<>();
        electionAdapter = new ElectionAdapter(electionList, this::onElectionClick);
        recyclerViewElections.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewElections.setAdapter(electionAdapter);

        // Aday listesi RecyclerView
        candidateList = new ArrayList<>();
        candidateAdapter = new CandidateAdapter(candidateList, this::onCandidateClick);
        recyclerViewCandidates.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewCandidates.setAdapter(candidateAdapter);
    }

    private void setupClickListeners() {
        btnBackToElections.setOnClickListener(v -> showElectionsList());
        btnSubmitVote.setOnClickListener(v -> {
            if (selectedCandidateId != null && !selectedCandidateId.isEmpty()) {
                showVoteConfirmationDialog();
            } else {
                Toast.makeText(getContext(), "Lütfen bir aday seçin", Toast.LENGTH_SHORT).show();
            }
        });
    }



    /**
     * Seçim listesini göster
     */
    public void showElectionsList() {
        layoutElectionsList.setVisibility(View.VISIBLE);
        layoutVoting.setVisibility(View.GONE);
        currentElection = null;
        selectedCandidateId = null;
    }

    /**
     * Oy verme ekranını göster
     */
    private void showVotingScreen(Election election) {
        currentElection = election;
        selectedCandidateId = null;

        layoutElectionsList.setVisibility(View.GONE);
        layoutVoting.setVisibility(View.VISIBLE);

        tvCurrentElection.setText("🗳️ " + election.getName());

        // Kullanıcının daha önce oy verip vermediğini kontrol et
        checkIfUserAlreadyVoted(election.getId());

        // Adayları yükle
        loadCandidates(election.getId());
    }

    /**
     * Seçim tıklandığında
     */
    private void onElectionClick(Election election) {
        // Seçim zamanı kontrolü
        Date now = new Date();

        if (election.getStartDate() != null && election.getStartDate().after(now)) {
            Toast.makeText(getContext(),
                    "Bu seçim henüz başlamamıştır.\nBaşlangıç: " + Utils.formatDateTime(election.getStartDate()),
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (election.getEndDate() != null && election.getEndDate().before(now)) {
            Toast.makeText(getContext(),
                    "Bu seçimin süresi geçmiştir.\nBitiş: " + Utils.formatDateTime(election.getEndDate()),
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Oy verme ekranına geç
        showVotingScreen(election);
    }

    /**
     * Aday tıklandığında
     */
    private void onCandidateClick(Candidate candidate) {
        selectedCandidateId = candidate.getId();
    }

    private void loadElections() {
        progressBar.setVisibility(View.VISIBLE);
        tvNoElections.setVisibility(View.GONE);
        recyclerViewElections.setVisibility(View.GONE);

        db.collection("elections")
                .get()
                .addOnCompleteListener(task -> {
                    if (getContext() == null || getActivity() == null || isDetached()) {
                        return;
                    }

                    progressBar.setVisibility(View.GONE);

                    if (task.isSuccessful()) {
                        electionList.clear();

                        for (QueryDocumentSnapshot document : task.getResult()) {
                            try {
                                Election election = document.toObject(Election.class);
                                election.setId(document.getId());

                                Date now = new Date();
                                boolean isActive = election.isActive();

                                Log.d("ElectionsFragment", "Seçim: " + election.getName() +
                                        " | Aktif: " + isActive +
                                        " | Başlangıç: " + election.getStartDate() +
                                        " | Bitiş: " + election.getEndDate());


                                if (!isActive) {
                                    Log.d("ElectionsFragment", "⚠️ Seçim pasif, listeye eklenmedi: " + election.getName());
                                    continue; // Pasif seçimleri atla
                                }


                                if (election.getStartDate() != null && election.getEndDate() != null) {
                                    if (election.getEndDate().after(now)) {
                                        electionList.add(election);
                                    } else {
                                        Log.d("ElectionsFragment", "⏰ Seçim süresi dolmuş: " + election.getName());
                                    }
                                } else {

                                    electionList.add(election);
                                }
                            } catch (Exception e) {
                                Log.e("ElectionsFragment", "Seçim parse hatası", e);
                                continue;
                            }
                        }

                        electionAdapter.notifyDataSetChanged();

                        if (electionList.isEmpty()) {
                            tvNoElections.setText("Şu anda aktif seçim bulunmamaktadır.\n\n" +
                                    "Yeni seçimler duyurulduğunda burada görünecektir.");
                            tvNoElections.setVisibility(View.VISIBLE);
                            recyclerViewElections.setVisibility(View.GONE);
                        } else {
                            tvNoElections.setVisibility(View.GONE);
                            recyclerViewElections.setVisibility(View.VISIBLE);
                        }

                        Log.d("ElectionsFragment", "📊 Toplam aktif seçim sayısı: " + electionList.size());
                    } else {
                        tvNoElections.setText("Seçimler yüklenirken hata oluştu.\n" +
                                "Lütfen internet bağlantınızı kontrol edin.");
                        tvNoElections.setVisibility(View.VISIBLE);
                        recyclerViewElections.setVisibility(View.GONE);

                        if (getContext() != null) {
                            Toast.makeText(getContext(),
                                    "Seçimler yüklenirken hata oluştu: " +
                                            (task.getException() != null ? task.getException().getMessage() : "Bilinmeyen hata"),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void checkIfUserAlreadyVoted(String electionId) {
        String userId = mAuth.getCurrentUser().getUid();

        db.collection("votes")
                .whereEqualTo("userId", userId)
                .whereEqualTo("electionId", electionId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        // Daha önce oy kullanılmış
                        btnSubmitVote.setEnabled(false);
                        btnSubmitVote.setText("✅ Bu Seçimde Oy Kullandınız");
                        btnSubmitVote.setBackgroundTintList(getResources().getColorStateList(R.color.gray));

                        Toast.makeText(getContext(),
                                "Bu seçimde daha önce oy kullandınız. Tekrar oy veremezsiniz.",
                                Toast.LENGTH_LONG).show();
                    } else {
                        // Henüz oy kullanılmamış
                        btnSubmitVote.setEnabled(true);
                        btnSubmitVote.setText("🗳️ Oyumu Gönder");
                        btnSubmitVote.setBackgroundTintList(getResources().getColorStateList(R.color.colorPrimary));
                    }
                })
                .addOnFailureListener(e -> {
                    // Hata durumunda güvenli tarafta kal - oy vermeyi engelle
                    btnSubmitVote.setEnabled(false);
                    btnSubmitVote.setText("❌ Oy Durumu Kontrol Edilemiyor");
                    Toast.makeText(getContext(), "Oy durumu kontrol edilemedi: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
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

                        candidateAdapter.notifyDataSetChanged();

                        if (candidateList.isEmpty()) {
                            Toast.makeText(getContext(),
                                    "Bu seçim için henüz aday eklenmemiştir.",
                                    Toast.LENGTH_SHORT).show();
                            btnSubmitVote.setEnabled(false);
                        }
                    } else {
                        Toast.makeText(getContext(),
                                "Adaylar yüklenirken hata oluştu: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showVoteConfirmationDialog() {
        Candidate selectedCandidate = null;
        for (Candidate candidate : candidateList) {
            if (candidate.getId().equals(selectedCandidateId)) {
                selectedCandidate = candidate;
                break;
            }
        }

        if (selectedCandidate == null) {
            Toast.makeText(getContext(), "Seçilen aday bulunamadı", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("🗳️ Oy Onayı");
        builder.setMessage("Aşağıdaki aday için oyunuz blockchain üzerinde güvenli bir şekilde kaydedilecektir:\n\n" +
                "👤 Aday: " + selectedCandidate.getName() + "\n" +
                "🏛️ Parti: " + selectedCandidate.getParty() + "\n" +
                "🗳️ Seçim: " + currentElection.getName() + "\n\n" +
                "Bu işlem geri alınamaz. Devam etmek istiyor musunuz?");

        builder.setPositiveButton("Evet, Oyumu Kullan", (dialog, which) -> {
            submitVoteWithBlockchain();
        });

        builder.setNegativeButton("İptal", (dialog, which) -> {
            dialog.dismiss();
        });

        builder.create().show();
    }

    private void submitVoteWithBlockchain() {
        String userId = mAuth.getCurrentUser().getUid();

        // Önce tekrar oy kontrolü yap (double-check)
        db.collection("votes")
                .whereEqualTo("userId", userId)
                .whereEqualTo("electionId", currentElection.getId())
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        Toast.makeText(getContext(),
                                "Bu seçimde zaten oy kullandınız!",
                                Toast.LENGTH_LONG).show();
                        checkIfUserAlreadyVoted(currentElection.getId());
                        return;
                    }

                    // Oy kullanılmamış, devam et
                    progressBar.setVisibility(View.VISIBLE);
                    btnSubmitVote.setEnabled(false);

                    // Kullanıcı bilgilerini al
                    db.collection("users").document(userId)
                            .get()
                            .addOnSuccessListener(documentSnapshot -> {
                                if (documentSnapshot.exists()) {
                                    User user = documentSnapshot.toObject(User.class);
                                    if (user != null && user.getTcKimlikNo() != null) {
                                        // Direkt blockchain'e oy ver - TC hash kaydedilecek
                                        processBlockchainVoteSimple(user.getTcKimlikNo());
                                    } else {
                                        progressBar.setVisibility(View.GONE);
                                        btnSubmitVote.setEnabled(true);
                                        Toast.makeText(getContext(), "TC Kimlik bilgisi bulunamadı",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                } else {
                                    progressBar.setVisibility(View.GONE);
                                    btnSubmitVote.setEnabled(true);
                                    Toast.makeText(getContext(), "Kullanıcı bilgileri bulunamadı",
                                            Toast.LENGTH_SHORT).show();
                                }
                            })
                            .addOnFailureListener(e -> {
                                progressBar.setVisibility(View.GONE);
                                btnSubmitVote.setEnabled(true);
                                Toast.makeText(getContext(), "Kullanıcı bilgileri alınamadı: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Oy durumu kontrol edilemedi: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }
    private void processBlockchainVoteSimple(String tcKimlikNo) {
        electionManager.castVote(currentElection.getId(), selectedCandidateId, tcKimlikNo)
                .thenAccept(transactionHash -> {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Log.d(TAG, "✅ Blockchain oy verme başarılı: " + transactionHash);
                            // Blockchain başarılı, Firebase'e kaydet
                            saveVoteToFirebase(transactionHash, tcKimlikNo);
                        });
                    }
                })
                .exceptionally(e -> {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Log.w(TAG, "⚠️ Blockchain oy verme başarısız: " + e.getMessage());
                            // Blockchain başarısız, sadece Firebase'e kaydet
                            saveVoteToFirebase(null, tcKimlikNo);
                        });
                    }
                    return null;
                });
    }


    private void saveVoteToFirebase(String transactionHash, String tcKimlikNo) {
        String userId = mAuth.getCurrentUser().getUid();

        // Son kontrol - tekrar oy kontrolü
        db.collection("votes")
                .whereEqualTo("userId", userId)
                .whereEqualTo("electionId", currentElection.getId())
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        // Bu arada başka yerden oy kullanılmış
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(getContext(),
                                "Oy işlemi sırasında sistemde bir oy kaydı tespit edildi!",
                                Toast.LENGTH_LONG).show();
                        checkIfUserAlreadyVoted(currentElection.getId());
                        return;
                    }

                    // Güvenli, oy kaydı yap
                    Vote vote = new Vote(userId, currentElection.getId(), selectedCandidateId);
                    vote.setTimestamp(new Date());

                    if (transactionHash != null) {
                        vote.setTransactionHash(transactionHash);
                    }

                    // Firebase'e kaydet
                    db.collection("votes").add(vote)
                            .addOnSuccessListener(documentReference -> {
                                // Aday oy sayısını artır
                                updateCandidateVoteCount();

                                progressBar.setVisibility(View.GONE);

                                String message = "✅ Oyunuz başarıyla kaydedildi!";
                                if (transactionHash != null) {
                                    message += "\n🔗 Blockchain: Doğrulandı";
                                } else {
                                    message += "\n📝 Veritabanı: Kaydedildi";
                                }

                                Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();

                                // 2 saniye sonra seçim listesine dön
                                new android.os.Handler().postDelayed(() -> {
                                    if (getActivity() != null && !isDetached()) {
                                        showElectionsList();
                                    }
                                }, 2000);
                            })
                            .addOnFailureListener(e -> {
                                progressBar.setVisibility(View.GONE);
                                btnSubmitVote.setEnabled(true);

                                Toast.makeText(getContext(),
                                        "❌ Oy kaydedilemedi: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            });
                });
    }

    private void updateCandidateVoteCount() {
        db.collection("elections").document(currentElection.getId())
                .collection("candidates").document(selectedCandidateId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Candidate candidate = documentSnapshot.toObject(Candidate.class);
                        if (candidate != null) {
                            int newVoteCount = candidate.getVoteCount() + 1;

                            // Debug log ekle
                            Log.d("VoteCount", "Aday: " + candidate.getName() +
                                    " | Eski oy: " + candidate.getVoteCount() +
                                    " | Yeni oy: " + newVoteCount);

                            // Firebase'de güncelle
                            db.collection("elections").document(currentElection.getId())
                                    .collection("candidates").document(selectedCandidateId)
                                    .update("voteCount", newVoteCount)
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d("VoteCount", "Oy sayısı başarıyla güncellendi!");
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e("VoteCount", "Oy sayısı güncellenemedi: " + e.getMessage());
                                    });
                        }
                    } else {
                        Log.e("VoteCount", "Aday bulunamadı: " + selectedCandidateId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("VoteCount", "Aday bilgileri alınamadı: " + e.getMessage());
                });
    }


    @Override
    public void onResume() {
        super.onResume();
        if (layoutElectionsList.getVisibility() == View.VISIBLE) {
            loadElections();
        }

    }

    /**
     * Geri tuşu basıldığında
     */
    public boolean onBackPressed() {
        if (layoutVoting.getVisibility() == View.VISIBLE) {
            showElectionsList();
            return true; // Geri tuşunu yakala
        }
        return false;
    }
}