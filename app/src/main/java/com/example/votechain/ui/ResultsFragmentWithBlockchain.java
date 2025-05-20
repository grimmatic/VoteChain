package com.example.votechain.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Seçim sonuçlarını gösteren fragment.
 * Blockchain entegrasyonu eklenmiştir.
 */
public class ResultsFragmentWithBlockchain extends Fragment {

    private Spinner spinnerElections;
    private RecyclerView recyclerViewResults;
    private ProgressBar progressBar;
    private TextView tvNoResults, tvBlockchainStatus;

    private List<Election> electionList;
    private Map<String, Election> electionMap;
    private List<Candidate> resultsList;
    private ResultsAdapter adapter;

    private FirebaseFirestore db;
    private BlockchainManager blockchainManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_results, container, false);

        // UI bileşenleri
        spinnerElections = view.findViewById(R.id.spinnerElections);
        recyclerViewResults = view.findViewById(R.id.recyclerViewResults);
        progressBar = view.findViewById(R.id.progressBar);
        tvNoResults = view.findViewById(R.id.tvNoResults);

        // Blockchain durumunu göstermek için yeni TextView ekledik
        tvBlockchainStatus = new TextView(getContext());
        tvBlockchainStatus.setId(View.generateViewId());
        tvBlockchainStatus.setText("Blockchain Durumu: Kontrol ediliyor...");
        ViewGroup parentView = (ViewGroup) recyclerViewResults.getParent();
        parentView.addView(tvBlockchainStatus, parentView.indexOfChild(recyclerViewResults));

        // Firebase
        db = FirebaseFirestore.getInstance();

        // Blockchain Manager instance al
        blockchainManager = BlockchainManager.getInstance();

        // Blockchain durumunu kontrol et
        String contractAddress = blockchainManager.getContractAddress();
        if (contractAddress != null) {
            tvBlockchainStatus.setText("Blockchain Kontrat Adresi: " +
                    contractAddress.substring(0, 8) + "...");
        } else {
            tvBlockchainStatus.setText("Blockchain Durumu: Kontrat bağlantısı yok");
        }

        // RecyclerView ayarları
        resultsList = new ArrayList<>();
        adapter = new ResultsAdapter(resultsList);
        recyclerViewResults.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewResults.setAdapter(adapter);

        // Seçimleri yükle
        electionList = new ArrayList<>();
        electionMap = new HashMap<>();
        loadElections();

        // Spinner değişikliği dinleyicisi
        spinnerElections.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    String electionId = electionList.get(position - 1).getId();
                    loadResultsFromBlockchain(electionId);
                } else {
                    // "Seçim Seçiniz" seçildi
                    resultsList.clear();
                    adapter.notifyDataSetChanged();
                    tvNoResults.setVisibility(View.VISIBLE);
                    recyclerViewResults.setVisibility(View.GONE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        return view;
    }

    private void loadElections() {
        progressBar.setVisibility(View.VISIBLE);

        db.collection("elections")
                .get()
                .addOnCompleteListener(task -> {
                    // Context'in null olup olmadığını kontrol et
                    if (getContext() == null || getActivity() == null || isDetached()) {
                        return; // Fragment bağlı değilse veya yok olmuşsa işlemi sonlandır
                    }

                    progressBar.setVisibility(View.GONE);

                    if (task.isSuccessful()) {
                        electionList.clear();
                        electionMap.clear();

                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Election election = document.toObject(Election.class);
                            election.setId(document.getId());
                            electionList.add(election);
                            electionMap.put(election.getId(), election);
                        }

                        // Spinner için isim listesi oluştur
                        List<String> electionNames = new ArrayList<>();
                        electionNames.add("Seçim Seçiniz");
                        for (Election election : electionList) {
                            electionNames.add(election.getName());
                        }

                        // Context'i güvenli bir şekilde kullan
                        Context context = getContext();
                        if (context != null) {
                            // Spinner adapter
                            ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                                    context, android.R.layout.simple_spinner_item, electionNames);
                            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                            spinnerElections.setAdapter(spinnerAdapter);
                        }

                        if (electionList.isEmpty()) {
                            tvNoResults.setText("Hiç seçim bulunamadı");
                            tvNoResults.setVisibility(View.VISIBLE);
                            recyclerViewResults.setVisibility(View.GONE);
                        }
                    } else {
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "Seçimler yüklenirken hata oluştu: " + task.getException().getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    /**
     * Blockchain'den seçim sonuçlarını yükler
     * @param electionId Seçim ID'si
     */
    private void loadResultsFromBlockchain(String electionId) {
        progressBar.setVisibility(View.VISIBLE);
        tvNoResults.setVisibility(View.GONE);
        recyclerViewResults.setVisibility(View.GONE);

        // Blockchain'den sonuçları almaya çalış
        try {
            blockchainManager.getElectionResults(new BigInteger(electionId))
                    .thenAccept(candidates -> {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                progressBar.setVisibility(View.GONE);

                                resultsList.clear();
                                resultsList.addAll(candidates);

                                // Oy sayılarına göre azalan sıralama
                                Collections.sort(resultsList, (c1, c2) ->
                                        Integer.compare(c2.getVoteCount(), c1.getVoteCount()));

                                adapter.notifyDataSetChanged();

                                if (resultsList.isEmpty()) {
                                    tvNoResults.setText("Bu seçim için blockchain üzerinde sonuç bulunamadı");
                                    tvNoResults.setVisibility(View.VISIBLE);
                                    recyclerViewResults.setVisibility(View.GONE);
                                } else {
                                    tvNoResults.setVisibility(View.GONE);
                                    recyclerViewResults.setVisibility(View.VISIBLE);
                                    tvBlockchainStatus.setText("Blockchain Durumu: Sonuçlar blockchain üzerinden alındı");
                                }
                            });
                        }
                    })
                    .exceptionally(e -> {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                // Blockchain'den veri alınamazsa Firebase'e yönlendir
                                Toast.makeText(getContext(),
                                        "Blockchain'den sonuçlar alınamadı, Firebase kullanılıyor: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show();

                                tvBlockchainStatus.setText("Blockchain Durumu: Hata oluştu, Firebase kullanılıyor");
                                loadResultsFromFirebase(electionId);
                            });
                        }
                        return null;
                    });
        } catch (Exception e) {
            // Hata durumunda Firebase'den yükle
            if (getContext() != null) {
                Toast.makeText(getContext(),
                        "Blockchain işlemi başlatılamadı: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
            loadResultsFromFirebase(electionId);
        }
    }

    /**
     * Firebase'den seçim sonuçlarını yükler
     * @param electionId Seçim ID'si
     */
    private void loadResultsFromFirebase(String electionId) {
        progressBar.setVisibility(View.VISIBLE);

        db.collection("elections").document(electionId)
                .collection("candidates")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        resultsList.clear();

                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Candidate candidate = document.toObject(Candidate.class);
                            candidate.setId(document.getId());
                            resultsList.add(candidate);
                        }

                        // Oy sayılarına göre azalan sıralama
                        Collections.sort(resultsList, new Comparator<Candidate>() {
                            @Override
                            public int compare(Candidate c1, Candidate c2) {
                                return Integer.compare(c2.getVoteCount(), c1.getVoteCount());
                            }
                        });

                        adapter.notifyDataSetChanged();

                        if (resultsList.isEmpty()) {
                            tvNoResults.setText("Bu seçim için Firebase'de sonuç bulunamadı");
                            tvNoResults.setVisibility(View.VISIBLE);
                            recyclerViewResults.setVisibility(View.GONE);
                        } else {
                            tvNoResults.setVisibility(View.GONE);
                            recyclerViewResults.setVisibility(View.VISIBLE);
                            tvBlockchainStatus.setText("Blockchain Durumu: Sonuçlar Firebase'den alındı");
                        }

                        progressBar.setVisibility(View.GONE);
                    } else {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(getContext(), "Sonuçlar yüklenirken hata oluştu: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Seçim sonuçlarını karşılaştır - Blockchain ve Firebase arasında
     * @param electionId Seçim ID'si
     */
    private void compareResults(String electionId) {
        progressBar.setVisibility(View.VISIBLE);

        // Önce Firebase'den sonuçları al
        db.collection("elections").document(electionId)
                .collection("candidates")
                .get()
                .addOnCompleteListener(firebaseTask -> {
                    if (firebaseTask.isSuccessful()) {
                        List<Candidate> firebaseCandidates = new ArrayList<>();

                        for (QueryDocumentSnapshot document : firebaseTask.getResult()) {
                            Candidate candidate = document.toObject(Candidate.class);
                            candidate.setId(document.getId());
                            firebaseCandidates.add(candidate);
                        }

                        // Şimdi Blockchain'den sonuçları al ve karşılaştır
                        try {
                            blockchainManager.getElectionResults(new BigInteger(electionId))
                                    .thenAccept(blockchainCandidates -> {
                                        if (getActivity() != null) {
                                            getActivity().runOnUiThread(() -> {
                                                progressBar.setVisibility(View.GONE);

                                                // Karşılaştırma sonuçlarını göster
                                                showComparisonResults(firebaseCandidates, blockchainCandidates);
                                            });
                                        }
                                    })
                                    .exceptionally(e -> {
                                        if (getActivity() != null) {
                                            getActivity().runOnUiThread(() -> {
                                                progressBar.setVisibility(View.GONE);
                                                Toast.makeText(getContext(),
                                                        "Blockchain sonuçları alınamadı: " + e.getMessage(),
                                                        Toast.LENGTH_SHORT).show();
                                            });
                                        }
                                        return null;
                                    });
                        } catch (Exception e) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(getContext(),
                                    "Blockchain işlemi başlatılamadı: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(getContext(),
                                "Firebase sonuçları alınamadı: " + firebaseTask.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Blockchain ve Firebase arasındaki karşılaştırma sonuçlarını gösterir
     */
    private void showComparisonResults(List<Candidate> firebaseCandidates, List<Candidate> blockchainCandidates) {
        // Dialog ile karşılaştırma sonuçlarını göster
        StringBuilder message = new StringBuilder("Sonuç Karşılaştırması:\n\n");

        // Eşleşen adayları bul ve oy sayılarını karşılaştır
        for (Candidate fbCandidate : firebaseCandidates) {
            message.append(fbCandidate.getName()).append(":\n");
            message.append("- Firebase: ").append(fbCandidate.getVoteCount()).append(" oy\n");

            // Blockchain'deki eşleşen adayı bul
            Candidate bcCandidate = null;
            for (Candidate candidate : blockchainCandidates) {
                if (candidate.getId().equals(fbCandidate.getId())) {
                    bcCandidate = candidate;
                    break;
                }
            }

            if (bcCandidate != null) {
                message.append("- Blockchain: ").append(bcCandidate.getVoteCount()).append(" oy\n");

                // Eşleşiyor mu?
                if (fbCandidate.getVoteCount() == bcCandidate.getVoteCount()) {
                    message.append("✓ Eşleşiyor\n\n");
                } else {
                    message.append("❌ Eşleşmiyor!\n\n");
                }
            } else {
                message.append("- Blockchain'de bulunamadı\n\n");
            }
        }

        // Dialog göster
        new android.app.AlertDialog.Builder(getContext())
                .setTitle("Blockchain ve Firebase Karşılaştırması")
                .setMessage(message.toString())
                .setPositiveButton("Tamam", null)
                .show();
    }
}
