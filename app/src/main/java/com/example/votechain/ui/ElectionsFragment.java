package com.example.votechain.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.example.votechain.util.Utils;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ElectionsFragment extends Fragment {

    private RecyclerView recyclerView;
    private ElectionAdapter adapter;
    private ProgressBar progressBar;
    private TextView tvNoElections;

    private List<Election> electionList;
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_elections, container, false);

        recyclerView = view.findViewById(R.id.recyclerViewElections);
        progressBar = view.findViewById(R.id.progressBar);
        tvNoElections = view.findViewById(R.id.tvNoElections);

        // RecyclerView ayarları
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        electionList = new ArrayList<>();
        adapter = new ElectionAdapter(electionList, new ElectionAdapter.OnElectionClickListener() {
            @Override
            public void onElectionClick(Election election) {
                // Seçime tıklandığında
                navigateToVoteFragment(election);
            }
        });
        recyclerView.setAdapter(adapter);

        // Firestore bağlantısı
        db = FirebaseFirestore.getInstance();

        loadElections();

        return view;
    }

    private void loadElections() {
        progressBar.setVisibility(View.VISIBLE);

        db.collection("elections")
                .whereGreaterThan("endDate", new Date()) // Süresi geçmemiş seçimler
                .get()
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);

                    if (task.isSuccessful()) {
                        electionList.clear();

                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Election election = document.toObject(Election.class);
                            election.setId(document.getId());
                            electionList.add(election);
                        }

                        adapter.notifyDataSetChanged();

                        // Hiç seçim yoksa mesaj göster
                        if (electionList.isEmpty()) {
                            tvNoElections.setVisibility(View.VISIBLE);
                            recyclerView.setVisibility(View.GONE);
                        } else {
                            tvNoElections.setVisibility(View.GONE);
                            recyclerView.setVisibility(View.VISIBLE);
                        }
                    } else {
                        Toast.makeText(getContext(), "Seçimler yüklenirken hata oluştu: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void navigateToVoteFragment(Election election) {
        // Seçim bilgilerini VoteFragment'a aktar
        VoteFragment voteFragment = new VoteFragment();
        Bundle args = new Bundle();
        args.putString("electionId", election.getId());
        args.putString("electionName", election.getName());
        voteFragment.setArguments(args);

        // Fragment geçişi
        getActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, voteFragment)
                .addToBackStack(null)
                .commit();
    }
}