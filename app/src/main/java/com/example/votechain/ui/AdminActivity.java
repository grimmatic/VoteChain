package com.example.votechain.ui;

import android.os.Bundle;
import android.util.Log;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.example.votechain.R;
import com.example.votechain.blockchain.BlockchainElectionManager;
import com.example.votechain.model.Candidate;
import com.example.votechain.model.Election;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;


public class AdminActivity extends AppCompatActivity {

    private static final String TAG = "AdminActivity";

    // UI Elemanları
    private EditText etElectionName, etElectionDescription;
    private DatePicker dpStartDate, dpEndDate;
    private TimePicker tpStartTime, tpEndTime;
    private EditText etCandidateName, etCandidateParty;
    private ListView lvCandidates;
    private Button btnCreateElection, btnAddCandidate, btnRegisterTCIds, btnActivateElection;
    private TextView tvStatus;

    // Data
    private BlockchainElectionManager electionManager;
    private ArrayAdapter<String> candidatesAdapter;
    private List<Candidate> candidatesList;
    private String currentElectionId;
    private boolean systemReady = false;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        initViews();
        setupListeners();

        electionManager = BlockchainElectionManager.getInstance();
        candidatesList = new ArrayList<>();
        db = FirebaseFirestore.getInstance();

        // Blockchain sistemini başlat
        initializeBlockchainSystem();
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
        btnActivateElection = findViewById(R.id.btnStartElection);
        tvStatus = findViewById(R.id.tvStatus);

        // ListView adapter
        candidatesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        lvCandidates.setAdapter(candidatesAdapter);

        // Başlangıçta tüm butonları deaktif et
        setButtonsEnabled(false);
    }

    private void setupListeners() {
        btnCreateElection.setOnClickListener(v -> createElection());
        btnAddCandidate.setOnClickListener(v -> addCandidate());
        btnActivateElection.setOnClickListener(v -> activateElection());
    }

    /**
     * Blockchain sistemini başlatır
     */
    private void initializeBlockchainSystem() {
        updateStatus("🔧 Blockchain sistemi başlatılıyor...\n" +
                "Ethereum ağına bağlanıyor...");

        electionManager.initializeSystem(this)
                .thenAccept(success -> {
                    runOnUiThread(() -> {
                        if (success) {
                            systemReady = true;
                            showSystemInfo();
                            btnCreateElection.setEnabled(true);
                        } else {
                            updateStatus("❌ Blockchain sistemi başlatılamadı!\n" +
                                    "Lütfen internet bağlantınızı kontrol edin.");
                        }
                    });
                })
                .exceptionally(e -> {
                    runOnUiThread(() -> {
                        updateStatus("❌ Sistem başlatma hatası:\n" + e.getMessage());
                        Log.e(TAG, "System initialization error", e);
                    });
                    return null;
                });
    }

    /**
     * Sistem bilgilerini gösterir
     */
    private void showSystemInfo() {
        Map<String, String> systemInfo = electionManager.getSystemInfo();

        updateStatus("✅ VoteChain Sistemi Hazır!\n\n" +
                "🏛️ Admin Paneli Aktif\n" +
                "🔐 Cüzdan: " + truncateAddress(systemInfo.get("walletAddress")) + "\n" +
                "📜 Kontrat: " + truncateAddress(systemInfo.get("contractAddress")) + "\n\n" +
                "Artık seçim oluşturabilirsiniz!");
    }

    /**
     * Yeni seçim oluşturur
     */
    private void createElection() {
        String name = etElectionName.getText().toString().trim();
        String description = etElectionDescription.getText().toString().trim();

        if (name.isEmpty()) {
            Toast.makeText(this, "Seçim adı gerekli!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (description.isEmpty()) {
            description = name + " seçimi";
        }

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
        if (!startCalendar.before(endCalendar)) {
            Toast.makeText(this, "Bitiş zamanı başlangıçtan sonra olmalı!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Timezone düzeltmesi yap
        long startTimeUnix = convertToBlockchainTime(startCalendar);
        long endTimeUnix = convertToBlockchainTime(endCalendar);
        long currentTimeUnix = System.currentTimeMillis() / 1000;

        Log.d(TAG, "🕐 ZAMAN DEBUG (Düzeltilmiş):");
        Log.d(TAG, "📅 Admin Seçimi - Başlangıç: " + formatDateTime(startCalendar));
        Log.d(TAG, "📅 Admin Seçimi - Bitiş: " + formatDateTime(endCalendar));
        Log.d(TAG, "📅 Current Unix: " + currentTimeUnix);
        Log.d(TAG, "📅 Start Unix (Düzeltilmiş): " + startTimeUnix);
        Log.d(TAG, "📅 End Unix (Düzeltilmiş): " + endTimeUnix);
        Log.d(TAG, "🌍 Timezone: " + TimeZone.getDefault().getID());
        Log.d(TAG, "⏰ UTC Offset: " + (TimeZone.getDefault().getOffset(System.currentTimeMillis()) / (1000 * 60 * 60)) + " saat");


        boolean isValidTime = (startTimeUnix <= currentTimeUnix) || (startTimeUnix > currentTimeUnix && startTimeUnix < endTimeUnix);
        Log.d(TAG, "✅ Zaman geçerli mi: " + isValidTime);

        if (endTimeUnix <= currentTimeUnix) {
            Toast.makeText(this, "⚠️ Uyarı: Bitiş zamanı geçmişte! Seçim hemen bitecek.", Toast.LENGTH_LONG).show();
        }

        updateStatus("🗳️ Seçim oluşturuluyor...\n" +
                "📋 Ad: " + name + "\n" +
                "⏰ Başlangıç: " + formatDateTime(startCalendar) + "\n" +
                "🏁 Bitiş: " + formatDateTime(endCalendar) + "\n" +
                "🔢 Start Unix: " + startTimeUnix + "\n" +
                "🔢 End Unix: " + endTimeUnix + "\n" +
                "🌍 Timezone Düzeltmesi Uygulandı\n\n" +
                "Blockchain işlemi devam ediyor...");


        Election election = new Election(name, description,
                startCalendar.getTime(),
                endCalendar.getTime(),
                false);


        electionManager.createElectionWithCustomTimes(election, startTimeUnix, endTimeUnix)
                .thenAccept(electionId -> {
                    runOnUiThread(() -> {
                        currentElectionId = electionId;

                        updateStatus("✅ Seçim başarıyla oluşturuldu!\n\n" +
                                "📋 Seçim: " + name + "\n" +
                                "🆔 ID: " + electionId + "\n" +
                                "🔗 Blockchain: Entegre edildi\n" +
                                "⏰ Zamanlar düzeltildi\n\n" +
                                "Şimdi adayları ekleyebilirsiniz!");

                        // UI güncellemeleri
                        btnCreateElection.setEnabled(false);
                        btnAddCandidate.setEnabled(true);

                        // Form temizle
                        etElectionName.setText("");
                        etElectionDescription.setText("");
                    });
                })
                .exceptionally(e -> {
                    runOnUiThread(() -> {
                        updateStatus("❌ Seçim oluşturma hatası:\n" + e.getMessage());
                        Log.e(TAG, "Election creation error", e);
                    });
                    return null;
                });
    }
    /**
     * Calendar'ı blockchain için doğru Unix timestamp'e çevirir
     * Timezone farkını düzeltir
     */
    private long convertToBlockchainTime(Calendar calendar) {
        // Yöntem 1: UTC'ye çevir
        long javaTimeUnix = calendar.getTimeInMillis() / 1000;

        // Timezone offset'ini hesapla (milisaniye cinsinden)
        TimeZone timeZone = TimeZone.getDefault();
        int offsetMs = timeZone.getOffset(calendar.getTimeInMillis());

        // UTC timestamp hesapla
        long utcTimeUnix = javaTimeUnix - (offsetMs / 1000);

        Log.d(TAG, "🔄 Timezone Dönüşümü:");
        Log.d(TAG, "  📅 Yerel Zaman Unix: " + javaTimeUnix);
        Log.d(TAG, "  🌍 Timezone Offset: " + (offsetMs / (1000 * 60 * 60)) + " saat");
        Log.d(TAG, "  🌐 UTC Unix: " + utcTimeUnix);
        Log.d(TAG, "  ⏰ Fark: " + (javaTimeUnix - utcTimeUnix) + " saniye");

        return utcTimeUnix;
    }

    /**
     * Tarihi kullanıcı dostu formatta gösterir
     */
    private String formatDateTime(Calendar calendar) {
        SimpleDateFormat formatter = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
        return formatter.format(calendar.getTime());
    }

    /**
     * Seçime aday ekler
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

        updateStatus("👤 Aday ekleniyor...\n" +
                "📝 Ad: " + finalName + "\n" +
                "🏛️ Parti: " + finalParty + "\n\n" +
                "Blockchain kaydı yapılıyor...");

        Candidate candidate = new Candidate(currentElectionId, finalName, finalParty);

        electionManager.addCandidate(currentElectionId, candidate)
                .thenAccept(candidateId -> {
                    runOnUiThread(() -> {
                        // Listeye ekle
                        candidatesList.add(candidate);
                        candidatesAdapter.add(finalName + " - " + finalParty);
                        candidatesAdapter.notifyDataSetChanged();

                        updateStatus("✅ Aday başarıyla eklendi!\n\n" +
                                "👤 " + finalName + " (" + finalParty + ")\n" +
                                "🆔 ID: " + candidateId + "\n" +
                                "📊 Toplam Aday: " + candidatesList.size() + "\n\n" +
                                (candidatesList.size() >= 2 ?
                                        "✨ Seçimi aktifleştirmeye hazır!" :
                                        "En az 2 aday gerekli"));

                        // Form temizle
                        etCandidateName.setText("");
                        etCandidateParty.setText("");

                        // 2 aday olunca aktifleştirme butonunu aç
                        if (candidatesList.size() >= 2) {
                            btnActivateElection.setEnabled(true);
                        }
                    });
                })
                .exceptionally(e -> {
                    runOnUiThread(() -> {
                        updateStatus("❌ Aday ekleme hatası:\n" + e.getMessage());
                        Log.e(TAG, "Candidate addition error", e);
                    });
                    return null;
                });
    }

    /**
     * Seçimi aktifleştirir
     */
    private void activateElection() {
        updateStatus("🚀 Seçim aktifleştiriliyor...\n" +
                "📊 " + candidatesList.size() + " aday ile seçim başlatılıyor\n\n" +
                "Son işlemler yapılıyor...");


        activateElectionInFirebase();
    }

    /**
     * TC ID'lerini sırayla ekler
     */
    private void addTCIdRecursively(final List<String> tcIds, int index) {
        if (index >= tcIds.size()) {
            // Tüm TC ID'ler eklendi, seçimi Firebase'de aktif hale getir
            activateElectionInFirebase();
            return;
        }

        String tcId = tcIds.get(index);
        electionManager.addValidTCId(tcId)
                .thenAccept(transactionHash -> {
                    Log.d(TAG, "TC ID eklendi: " + tcId + " -> " + transactionHash);
                    // Bir sonraki TC ID'yi ekle
                    addTCIdRecursively(tcIds, index + 1);
                })
                .exceptionally(e -> {
                    Log.w(TAG, "TC ID eklenirken hata: " + tcId, e);
                    // Hata olsa da devam et
                    addTCIdRecursively(tcIds, index + 1);
                    return null;
                });
    }

    /**
     * Seçimi Firebase'de aktif hale getirir
     */
    private void activateElectionInFirebase() {
        if (currentElectionId == null) {
            updateStatus("❌ Seçim ID'si bulunamadı!");
            return;
        }

        // Firebase'de seçimi aktif hale getir
        db.collection("elections").document(currentElectionId)
                .update("active", true)
                .addOnSuccessListener(aVoid -> {
                    runOnUiThread(() -> {
                        updateStatus("🎉 SEÇİM AKTİF!\n\n" +
                                "✅ Vatandaşlar artık oy verebilir\n" +
                                "📊 Aday Sayısı: " + candidatesList.size() + "\n" +
                                "🔐 Blockchain Güvenliği: Aktif\n" +
                                "🗳️ Şeffaf Oylama: Hazır\n\n" +
                                "Seçim kullanıcılara görünür hale geldi!");

                        // Tüm butonları deaktif et
                        setButtonsEnabled(false);
                        btnActivateElection.setEnabled(false);

                        Toast.makeText(AdminActivity.this,
                                "Seçim başarıyla aktifleştirildi! Kullanıcılar artık seçimi görebilir ve oy verebilir.",
                                Toast.LENGTH_LONG).show();
                    });
                })
                .addOnFailureListener(e -> {
                    runOnUiThread(() -> {
                        updateStatus("❌ Seçim aktifleştirme hatası:\n" + e.getMessage());
                        Log.e(TAG, "Firebase activation error", e);
                    });
                });
    }

    /**
     * Butonların aktif/pasif durumunu ayarlar
     */
    private void setButtonsEnabled(boolean enabled) {
        btnCreateElection.setEnabled(enabled && systemReady);
        btnAddCandidate.setEnabled(enabled && currentElectionId != null);
    }

    /**
     * Durum metnini günceller
     */
    private void updateStatus(String status) {
        tvStatus.setText(status);
        Log.d(TAG, status);
    }

    /**
     * Ethereum adresini kısaltır
     */
    private String truncateAddress(String address) {
        if (address != null && address.length() > 10) {
            return address.substring(0, 6) + "..." + address.substring(address.length() - 4);
        }
        return address;
    }

    /**
     * Tarih ve saati formatlar
     */

}