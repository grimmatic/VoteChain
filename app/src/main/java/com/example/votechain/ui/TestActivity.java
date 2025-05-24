package com.example.votechain.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.votechain.R;
import com.example.votechain.blockchain.BlockchainManager;
import com.example.votechain.model.Candidate;
import com.example.votechain.model.Election;

import java.math.BigInteger;
import java.util.Calendar;
import java.util.List;

/**
 * Blockchain işlemlerini test etmek için basit bir activity
 */
public class TestActivity extends AppCompatActivity {

    private static final String TAG = "TestActivity";

    private Button btnInitWallet, btnCreateElection, btnAddCandidate, btnVote, btnGetResults;
    private TextView tvStatus;
    private BlockchainManager blockchainManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        initViews();
        setupClickListeners();

        blockchainManager = BlockchainManager.getInstance();
    }

    private void initViews() {
        btnInitWallet = findViewById(R.id.btnInitWallet);
        btnCreateElection = findViewById(R.id.btnCreateElection);
        btnAddCandidate = findViewById(R.id.btnAddCandidate);
        btnVote = findViewById(R.id.btnVote);
        btnGetResults = findViewById(R.id.btnGetResults);
        tvStatus = findViewById(R.id.tvStatus);
    }

    private void setupClickListeners() {
        btnInitWallet.setOnClickListener(v -> initializeWallet());
        btnCreateElection.setOnClickListener(v -> createTestElection());
        btnAddCandidate.setOnClickListener(v -> addTestCandidate());
        btnVote.setOnClickListener(v -> castTestVote());
        btnGetResults.setOnClickListener(v -> getTestResults());
    }

    /**
     * 1. Adım: Cüzdanı başlat
     * Ethereum cüzdanı oluşturur ve blockchain işlemleri için hazır hale getirir
     */
    private void initializeWallet() {
        updateStatus("🔐 Ethereum cüzdanı oluşturuluyor...\n" +
                "Bu işlem blockchain işlemleri için gereklidir.\n" +
                "Lütfen bekleyin...");

        boolean success = blockchainManager.initializeWallet(this, "test123");

        if (success) {
            String address = blockchainManager.getWalletAddress();
            updateStatus("✅ Cüzdan başarıyla oluşturuldu!\n\n" +
                    "📍 Ethereum Adresiniz:\n" +
                    (address != null ? address : "null") + "\n\n" +
                    "Artık blockchain işlemleri yapabilirsiniz!");
            btnCreateElection.setEnabled(true);
        } else {
            updateStatus("❌ Cüzdan oluşturulamadı!\n\n" +
                    "Olası nedenler:\n" +
                    "- BouncyCastle kütüphanesi eksik\n" +
                    "- Crypto provider hatası\n" +
                    "- Depolama izin sorunu\n\n" +
                    "Lütfen uygulamayı yeniden başlatın.");
        }
    }

    /**
     * 2. Adım: Test seçimi oluştur
     */
    private void createTestElection() {
        updateStatus("Test seçimi oluşturuluyor...");

        // Şimdi + 1 saat başlangıç
        Calendar startCal = Calendar.getInstance();
        startCal.add(Calendar.HOUR, 1);

        // Şimdi + 3 saat bitiş
        Calendar endCal = Calendar.getInstance();
        endCal.add(Calendar.HOUR, 3);

        Election testElection = new Election(
                "Test Seçimi 2025",
                "Bu bir test seçimidir",
                startCal.getTime(),
                endCal.getTime(),
                true
        );

        blockchainManager.createElection(testElection)
                .thenAccept(transactionHash -> {
                    runOnUiThread(() -> {
                        updateStatus("Seçim oluşturuldu!\nİşlem Hash: " +
                                transactionHash.substring(0, 10) + "...");
                        btnAddCandidate.setEnabled(true);
                    });
                })
                .exceptionally(e -> {
                    runOnUiThread(() -> {
                        updateStatus("Seçim oluşturma hatası: " + e.getMessage());
                        Log.e(TAG, "Election creation error", e);
                    });
                    return null;
                });
    }

    /**
     * 3. Adım: Test adayı ekle
     */
    private void addTestCandidate() {
        updateStatus("Test adayı ekleniyor...");

        Candidate testCandidate = new Candidate("1", "Test Aday", "Test Parti");

        blockchainManager.addCandidate(BigInteger.ONE, testCandidate)
                .thenAccept(transactionHash -> {
                    runOnUiThread(() -> {
                        updateStatus("Aday eklendi!\nİşlem Hash: " +
                                transactionHash.substring(0, 10) + "...");

                        // Test TC ID'sini de ekle
                        addTestTCId();
                    });
                })
                .exceptionally(e -> {
                    runOnUiThread(() -> {
                        updateStatus("Aday ekleme hatası: " + e.getMessage());
                        Log.e(TAG, "Candidate addition error", e);
                    });
                    return null;
                });
    }

    /**
     * Test TC ID'si ekle
     */
    private void addTestTCId() {
        String testTCId = "12345678901";

        blockchainManager.addValidTCId(testTCId)
                .thenAccept(transactionHash -> {
                    runOnUiThread(() -> {
                        updateStatus("TC ID eklendi!\nİşlem Hash: " +
                                transactionHash.substring(0, 10) + "...\n\nArtık oy verebilirsiniz!");
                        btnVote.setEnabled(true);
                    });
                })
                .exceptionally(e -> {
                    runOnUiThread(() -> {
                        updateStatus("TC ID ekleme hatası: " + e.getMessage());
                        Log.e(TAG, "TC ID addition error", e);
                    });
                    return null;
                });
    }

    /**
     * 4. Adım: Test oylaması
     */
    private void castTestVote() {
        updateStatus("Test oylaması yapılıyor...");

        String testTCId = "12345678901";

        blockchainManager.vote(BigInteger.ONE, BigInteger.ONE, testTCId)
                .thenAccept(transactionHash -> {
                    runOnUiThread(() -> {
                        updateStatus("Oy kullanıldı!\nİşlem Hash: " +
                                transactionHash.substring(0, 10) + "...");
                        btnGetResults.setEnabled(true);
                    });
                })
                .exceptionally(e -> {
                    runOnUiThread(() -> {
                        updateStatus("Oy kullanma hatası: " + e.getMessage());
                        Log.e(TAG, "Voting error", e);
                    });
                    return null;
                });
    }

    /**
     * 5. Adım: Sonuçları getir
     */
    private void getTestResults() {
        updateStatus("Sonuçlar getiriliyor...");

        blockchainManager.getElectionResults(BigInteger.ONE)
                .thenAccept(candidates -> {
                    runOnUiThread(() -> {
                        StringBuilder results = new StringBuilder("Seçim Sonuçları:\n\n");

                        for (Candidate candidate : candidates) {
                            results.append(candidate.getName())
                                    .append(" (").append(candidate.getParty()).append("): ")
                                    .append(candidate.getVoteCount()).append(" oy\n");
                        }

                        updateStatus(results.toString());
                    });
                })
                .exceptionally(e -> {
                    runOnUiThread(() -> {
                        updateStatus("Sonuç alma hatası: " + e.getMessage());
                        Log.e(TAG, "Results retrieval error", e);
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