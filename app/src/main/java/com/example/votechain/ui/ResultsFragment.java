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
import com.example.votechain.model.Candidate;
import com.example.votechain.model.Election;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResultsFragment extends Fragment {

    private Spinner spinnerElections;
    private RecyclerView recyclerViewResults;
    private ProgressBar progressBar;
    private TextView tvNoResults;
    private List<Election> electionList;
    private Map<String, Election> electionMap;
    private List<Candidate> resultsList;
    private ResultsAdapter adapter;
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_results, container, false);

        // UI bileşenleri
        spinnerElections = view.findViewById(R.id.spinnerElections);
        recyclerViewResults = view.findViewById(R.id.recyclerViewResults);
        progressBar = view.findViewById(R.id.progressBar);
        tvNoResults = view.findViewById(R.id.tvNoResults);

        // Firebase
        db = FirebaseFirestore.getInstance();

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
                    loadResults(electionId);
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


                        List<String> electionNames = new ArrayList<>();
                        electionNames.add("Seçim Seçiniz");
                        for (Election election : electionList) {
                            electionNames.add(election.getName());
                        }


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

    private void loadResults(String electionId) {
        progressBar.setVisibility(View.VISIBLE);
        tvNoResults.setVisibility(View.GONE);
        recyclerViewResults.setVisibility(View.GONE);

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


                        Collections.sort(resultsList, new Comparator<Candidate>() {
                            @Override
                            public int compare(Candidate c1, Candidate c2) {
                                return Integer.compare(c2.getVoteCount(), c1.getVoteCount());
                            }
                        });


                        if (adapter == null) {
                            adapter = new ResultsAdapter(resultsList);
                            recyclerViewResults.setAdapter(adapter);
                        } else {
                            adapter.updateCandidates(resultsList);
                        }

                        if (resultsList.isEmpty()) {
                            tvNoResults.setText("Bu seçim için sonuç bulunamadı");
                            tvNoResults.setVisibility(View.VISIBLE);
                            recyclerViewResults.setVisibility(View.GONE);
                        } else {
                            tvNoResults.setVisibility(View.GONE);
                            recyclerViewResults.setVisibility(View.VISIBLE);
                        }

                        progressBar.setVisibility(View.GONE);
                    } else {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(getContext(), "Sonuçlar yüklenirken hata oluştu: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
}