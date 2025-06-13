package com.example.votechain.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.votechain.R;
import com.example.votechain.model.Election;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Mevcut seçimleri görüntüleme ve yönetme activity'si
 */
public class ManageElectionsActivity extends AppCompatActivity {

    private RecyclerView recyclerViewElections;
    private ProgressBar progressBar;
    private TextView tvNoElections;

    private FirebaseFirestore db;
    private List<Election> electionList;
    private ManageElectionAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_elections);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("⚙️ Seçimleri Yönet");
        }
        initViews();
        setupRecyclerView();
        loadElections();
    }
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    private void initViews() {
        recyclerViewElections = findViewById(R.id.recyclerViewElections);
        progressBar = findViewById(R.id.progressBar);
        tvNoElections = findViewById(R.id.tvNoElections);

        db = FirebaseFirestore.getInstance();
    }

    private void setupRecyclerView() {
        electionList = new ArrayList<>();
        adapter = new ManageElectionAdapter(electionList, new ManageElectionAdapter.OnElectionActionListener() {
            @Override
            public void onElectionEdit(Election election) {
                // Seçim düzenleme işlemi
                editElection(election);
            }

            @Override
            public void onElectionDelete(Election election) {
                // Seçim silme işlemi
                deleteElection(election);
            }

            @Override
            public void onElectionToggle(Election election) {
                // Seçim aktif/pasif durumu değiştirme
                toggleElectionStatus(election);
            }
        });

        recyclerViewElections.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewElections.setAdapter(adapter);
    }

    private void loadElections() {
        progressBar.setVisibility(View.VISIBLE);
        tvNoElections.setVisibility(View.GONE);

        db.collection("elections")
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

                        if (electionList.isEmpty()) {
                            tvNoElections.setVisibility(View.VISIBLE);
                            tvNoElections.setText("Henüz hiç seçim oluşturulmamış");
                        } else {
                            tvNoElections.setVisibility(View.GONE);
                        }
                    } else {
                        Toast.makeText(this,
                                "Seçimler yüklenirken hata oluştu: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Seçim düzenleme dialog'u
     */
    private void editElection(Election election) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Seçim Düzenle: " + election.getName());

        // Custom layout oluştur
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_election, null);

        EditText etName = dialogView.findViewById(R.id.etElectionName);
        EditText etDescription = dialogView.findViewById(R.id.etElectionDescription);

        // Mevcut değerleri doldur
        etName.setText(election.getName());
        etDescription.setText(election.getDescription());

        builder.setView(dialogView);

        builder.setPositiveButton("Güncelle", (dialog, which) -> {
            String newName = etName.getText().toString().trim();
            String newDescription = etDescription.getText().toString().trim();

            if (newName.isEmpty()) {
                Toast.makeText(this, "Seçim adı boş olamaz", Toast.LENGTH_SHORT).show();
                return;
            }

            // Firebase'de güncelle
            updateElectionInFirebase(election.getId(), newName, newDescription);
        });

        builder.setNegativeButton("İptal", null);
        builder.show();
    }

    /**
     * Firebase'de seçim bilgilerini günceller
     */
    private void updateElectionInFirebase(String electionId, String newName, String newDescription) {
        progressBar.setVisibility(View.VISIBLE);

        db.collection("elections").document(electionId)
                .update(
                        "name", newName,
                        "description", newDescription
                )
                .addOnSuccessListener(aVoid -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Seçim başarıyla güncellendi", Toast.LENGTH_SHORT).show();
                    loadElections(); // Listeyi yenile
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Güncelleme başarısız: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void deleteElection(Election election) {
        new AlertDialog.Builder(this)
                .setTitle("Seçimi Sil")
                .setMessage("'" + election.getName() + "' seçimini silmek istediğinize emin misiniz?\n\nBu işlem geri alınamaz!")
                .setPositiveButton("Evet, Sil", (dialog, which) -> {
                    progressBar.setVisibility(View.VISIBLE);

                    db.collection("elections").document(election.getId())
                            .delete()
                            .addOnSuccessListener(aVoid -> {
                                progressBar.setVisibility(View.GONE);
                                Toast.makeText(this, "Seçim başarıyla silindi", Toast.LENGTH_SHORT).show();
                                loadElections();
                            })
                            .addOnFailureListener(e -> {
                                progressBar.setVisibility(View.GONE);
                                Toast.makeText(this, "Seçim silinemedi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    private void toggleElectionStatus(Election election) {
        progressBar.setVisibility(View.VISIBLE);

        boolean newStatus = !election.isActive();
        String statusText = newStatus ? "aktif edildi" : "pasif edildi";

        db.collection("elections").document(election.getId())
                .update("active", newStatus)
                .addOnSuccessListener(aVoid -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this,
                            "Seçim " + statusText,
                            Toast.LENGTH_SHORT).show();
                    loadElections(); // Listeyi yenile
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Durum değiştirilemedi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}