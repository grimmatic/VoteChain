package com.example.votechain.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.example.votechain.R;
import com.example.votechain.blockchain.BlockchainManager;
import com.example.votechain.model.Candidate;
import com.example.votechain.model.Election;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Admin paneli - Seçim oluşturma ve yönetme
 */
public class AdminActivity extends AppCompatActivity {

    private static final String TAG = "AdminActivity";

    // UI Elemanları
    private EditText etElectionName, etElectionDescription;
    private DatePicker dpStartDate, dpEndDate;
    private TimePicker tpStartTime, tpEndTime;
    private EditText etCandidateName, etCandidateParty;
    private ListView lvCandidates;
    private Button btnCreateElection, btnAddCandidate, btnStartElection;
    private TextView tvStatus;

    // Data
    private BlockchainManager blockchainManager;
    private ArrayAdapter<String> candidatesAdapter;
    private BigInteger currentElectionId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        initViews();
        setupListeners();

        blockchainManager = BlockchainManager.getInstance();

        // Admin cüzdanını başlat
        initializeAdminWallet();
    }

    private void initViews() {
        etElectionName = findViewById(R.id.etElectionName);
        etElectionDescription = findViewById(R.id.etElectionDescription);
        dpStartDate = findViewById(R.id.dpStartDate);
        dpEndDate = findViewById(R.id.dpEndDate);
        tpStartTime = findViewById(R.id.tpStartTime);
        tpEndTime = findViewById(R.id.tpEndTime);
        etCandidateName = findViewById(R.id.etCandidateName);
        etCandidateParty = findViewById(R.id.etCandidateParty);
        lvCandidates = findViewById(R.id.lvCandidates);
        btnCreateElection = findViewById(R.id.btnCreateElection);
        btnAddCandidate = findViewById(R.id.btnAddCandidate);
        btnStartElection = findViewById(R.id.btnStartElection);
        tvStatus = findViewById(R.id.tvStatus);

        // ListView adapter
        candidatesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        lvCandidates.setAdapter(candidatesAdapter);

        // Başlangıçta sadece seçim oluşturma aktif
        btnAddCandidate.setEnabled(false);
        btnStartElection.setEnabled(false);
    }

    private void setupListeners() {
        btnCreateElection.setOnClickListener(v -> createElection());
        btnAddCandidate.setOnClickListener(v -> addCandidate());
        btnStartElection.setOnClickListener(v -> startElection());
    }

    /**
     * Admin cüzdanını başlat
     */
    private void initializeAdminWallet() {
        updateStatus("🔐 Admin cüzdanı başlatılıyor...");

        boolean success = blockchainManager.initializeWallet(this, "admin123");

        if (success) {
            String address = blockchainManager.getWalletAddress();
            updateStatus("✅ Admin cüzdanı hazır!\n" +
                    "📍 Adres: " + address.substring(0, 10) + "...\n\n" +
                    "Seçim oluşturmaya başlayabilirsiniz!");
            btnCreateElection.setEnabled(true);
        } else {
            updateStatus("❌ Admin cüzdanı başlatılamadı!");
        }
    }

    /**
     * Yeni seçim oluştur
     */
    private void createElection() {
        String name = etElectionName.getText().toString().trim();
        String description = etElectionDescription.getText().toString().trim();

        if (name.isEmpty()) {
            Toast.makeText(this, "Seçim adı gerekli!", Toast.LENGTH_SHORT).show();
            return;
        }

        updateStatus("🗳️ Seçim oluşturuluyor: " + name);

        // Tarih ve saat bilgilerini al
        Calendar startCalendar = Calendar.getInstance();
        startCalendar.set(dpStartDate.getYear(), dpStartDate.getMonth(),
                dpStartDate.getDayOfMonth(), tpStartTime.getCurrentHour(),
                tpStartTime.getCurrentMinute(), 0);

        Calendar endCalendar = Calendar.getInstance();
        endCalendar.set(dpEndDate.getYear(), dpEndDate.getMonth(),
                dpEndDate.getDayOfMonth(), tpEndTime.getCurrentHour(),
                tpEndTime.getCurrentMinute(), 0);

        // Zaman kontrolü
        if (startCalendar.after(endCalendar)) {
            Toast.makeText(this, "Bitiş zamanı başlangıçtan sonra olmalı!", Toast.LENGTH_SHORT).show();
            return;
        }

        Election election = new Election(name, description,
                startCalendar.getTime(),
                endCalendar.getTime(),
                true);

        blockchainManager.createElection(election)
                .thenAccept(transactionHash -> {
                    runOnUiThread(() -> {
                        // Seçim ID'sini güncelle (genellikle sıradaki sayı)
                        currentElectionId = BigInteger.ONE; // İlk seçim için

                        updateStatus("✅ Seçim oluşturuldu!\n" +
                                "📋 Ad: " + name + "\n" +
                                "🔗 İşlem: " + transactionHash.substring(0, 10) + "...\n\n" +
                                "Şimdi adayları ekleyebilirsiniz!");

                        btnAddCandidate.setEnabled(true);
                        btnCreateElection.setEnabled(false);
                    });
                })
                .exceptionally(e -> {
                    runOnUiThread(() -> {
                        updateStatus("❌ Seçim oluşturma hatası: " + e.getMessage());
                        Log.e(TAG, "Election creation error", e);
                    });
                    return null;
                });
    }

    /**
     * Seçime aday ekle
     */
    private void addCandidate() {
        String name = etCandidateName.getText().toString().trim();
        String party = etCandidateParty.getText().toString().trim();

        if (name.isEmpty()) {
            Toast.makeText(this, "Aday adı gerekli!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (party.isEmpty()) {
            party = "Bağımsız";
        }

        // Final değişkenler oluştur
        final String finalName = name;
        final String finalParty = party;

        updateStatus("👤 Aday ekleniyor: " + finalName + " (" + finalParty + ")");

        Candidate candidate = new Candidate(currentElectionId.toString(), finalName, finalParty);

        blockchainManager.addCandidate(currentElectionId, candidate)
                .thenAccept(transactionHash -> {
                    runOnUiThread(() -> {
                        // Listeye ekle
                        candidatesAdapter.add(finalName + " - " + finalParty);
                        candidatesAdapter.notifyDataSetChanged();

                        updateStatus("✅ Aday eklendi: " + finalName + "\n" +
                                "🔗 İşlem: " + transactionHash.substring(0, 10) + "...\n\n" +
                                "Toplam aday: " + candidatesAdapter.getCount());

                        // Form temizle
                        etCandidateName.setText("");
                        etCandidateParty.setText("");

                        // Seçimi başlatma butonunu aktif et
                        if (candidatesAdapter.getCount() >= 2) {
                            btnStartElection.setEnabled(true);
                        }
                    });
                })
                .exceptionally(e -> {
                    runOnUiThread(() -> {
                        updateStatus("❌ Aday ekleme hatası: " + e.getMessage());
                        Log.e(TAG, "Candidate addition error", e);
                    });
                    return null;
                });
    }

    /**
     * Seçimi başlat (aktif hale getir)
     */
    private void startElection() {
        updateStatus("🚀 Seçim başlatılıyor...");

        // Seçimi aktif hale getir
        blockchainManager.setElectionActive(currentElectionId, true)
                .thenAccept(transactionHash -> {
                    runOnUiThread(() -> {
                        updateStatus("🎉 SEÇİM BAŞLADI!\n\n" +
                                "✅ Vatandaşlar artık oy verebilir\n" +
                                "📊 Toplam aday: " + candidatesAdapter.getCount() + "\n" +
                                "🔗 İşlem: " + transactionHash.substring(0, 10) + "...\n\n" +
                                "Seçim uygulamasına geçebilirsiniz!");

                        btnStartElection.setEnabled(false);

                        // Ana uygulamaya yönlendir
                        Toast.makeText(AdminActivity.this,
                                "Seçim başladı! Ana uygulamayı açabilirsiniz.",
                                Toast.LENGTH_LONG).show();
                    });
                })
                .exceptionally(e -> {
                    runOnUiThread(() -> {
                        updateStatus("❌ Seçim başlatma hatası: " + e.getMessage());
                        Log.e(TAG, "Election start error", e);
                    });
                    return null;
                });
    }

    /**
     * Durum metnini güncelle
     */
    private void updateStatus(String status) {
        tvStatus.setText(status);
        Log.d(TAG, status);
    }
}