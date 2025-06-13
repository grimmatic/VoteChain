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
import java.util.Date;
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


        Calendar startCalendar = Calendar.getInstance();
        startCalendar.set(dpStartDate.getYear(), dpStartDate.getMonth(),
                dpStartDate.getDayOfMonth(), tpStartTime.getCurrentHour(),
                tpStartTime.getCurrentMinute(), 0);
        startCalendar.set(Calendar.SECOND, 0);
        startCalendar.set(Calendar.MILLISECOND, 0);

        Calendar endCalendar = Calendar.getInstance();
        endCalendar.set(dpEndDate.getYear(), dpEndDate.getMonth(),
                dpEndDate.getDayOfMonth(), tpEndTime.getCurrentHour(),
                tpEndTime.getCurrentMinute(), 0);
        endCalendar.set(Calendar.SECOND, 0);
        endCalendar.set(Calendar.MILLISECOND, 0);

        if (!startCalendar.before(endCalendar)) {
            Toast.makeText(this, "Bitiş zamanı başlangıçtan sonra olmalı!", Toast.LENGTH_SHORT).show();
            return;
        }


        String finalDescription = description;
        electionManager.getCurrentBlockchainTime()
                .thenAccept(blockchainCurrentTime -> {
                    runOnUiThread(() -> {
                        // Timezone dönüştürme işlemi
                        long startTimeUnix = convertToBlockchainTime(startCalendar);
                        long endTimeUnix = convertToBlockchainTime(endCalendar);

                        Log.d(TAG, "🕐 ZAMAN KONTROLÜ (Düzeltilmiş):");
                        Log.d(TAG, "⛓️ Blockchain Şimdiki Zaman: " + blockchainCurrentTime);
                        Log.d(TAG, "📅 Hesaplanan Start Unix: " + startTimeUnix);
                        Log.d(TAG, "📅 Hesaplanan End Unix: " + endTimeUnix);
                        Log.d(TAG, "📅 Start Türkiye: " + formatDateTime(startCalendar));
                        Log.d(TAG, "📅 End Türkiye: " + formatDateTime(endCalendar));


                        if (endTimeUnix <= blockchainCurrentTime) {

                            long newEndTime = blockchainCurrentTime + (24 * 3600);
                            Log.w(TAG, "⚠️ Bitiş zamanı geçmişte kaldı, otomatik düzeltiliyor:");
                            Log.w(TAG, "🔧 Yeni bitiş zamanı: " + newEndTime + " (" + new Date(newEndTime * 1000) + ")");
                            endTimeUnix = newEndTime;
                        }

                        if (startTimeUnix <= blockchainCurrentTime) {
                            // Başlangıç zamanını da düzelt
                            startTimeUnix = blockchainCurrentTime + 300; // 5 dakika sonra başlasın
                            Log.w(TAG, "🔧 Başlangıç zamanı da düzeltildi: " + startTimeUnix);
                        }

                        long currentTimeUnix = System.currentTimeMillis() / 1000;

                        Log.d(TAG, "📊 SON ZAMAN DURUMU:");
                        Log.d(TAG, "⏰ Start farkı (blockchain): " + (startTimeUnix - blockchainCurrentTime) + " saniye");
                        Log.d(TAG, "⏰ End farkı (blockchain): " + (endTimeUnix - blockchainCurrentTime) + " saniye");

                        // Seçim süresinin minimum 1 saat olmasını sağla
                        if ((endTimeUnix - startTimeUnix) < 3600) {
                            endTimeUnix = startTimeUnix + 3600; // 1 saat
                            Log.w(TAG, "🔧 Minimum seçim süresi için bitiş zamanı ayarlandı");
                        }

                        updateStatus("🗳️ Seçim oluşturuluyor...\n" +
                                "📋 Ad: " + name + "\n" +
                                "⏰ Başlangıç (Blockchain UTC): " + new Date(startTimeUnix * 1000) + "\n" +
                                "🏁 Bitiş (Blockchain UTC): " + new Date(endTimeUnix * 1000) + "\n" +
                                "🔢 Start Unix: " + startTimeUnix + "\n" +
                                "🔢 End Unix: " + endTimeUnix + "\n" +
                                "⛓️ Blockchain zamanına göre düzeltildi\n\n" +
                                "Blockchain işlemi başlıyor...");

                        Election election = new Election(name, finalDescription,
                                startCalendar.getTime(),
                                endCalendar.getTime(),
                                false);

                        // Final değişkenler oluştur
                        final long finalStartTimeUnix = startTimeUnix;
                        final long finalEndTimeUnix = endTimeUnix;

                        electionManager.createElectionWithCustomTimes(election, finalStartTimeUnix, finalEndTimeUnix)
                                .thenAccept(electionId -> {
                                    runOnUiThread(() -> {
                                        currentElectionId = electionId;

                                        updateStatus("✅ Seçim başarıyla oluşturuldu!\n\n" +
                                                "📋 Seçim: " + name + "\n" +
                                                "🆔 ID: " + electionId + "\n" +
                                                "🔗 Blockchain: Entegre edildi\n" +
                                                "⏰ Zamanlar blockchain'e uygun düzeltildi\n" +
                                                "🌍 UTC zamanları kullanıldı\n\n" +
                                                "Şimdi adayları ekleyebilirsiniz!");

                                        btnCreateElection.setEnabled(false);
                                        btnAddCandidate.setEnabled(true);
                                        etElectionName.setText("");
                                        etElectionDescription.setText("");

                                        // Oluşturulduktan sonra blockchain zamanını tekrar doğrula
                                        verifyBlockchainTime(finalStartTimeUnix, finalEndTimeUnix);
                                    });
                                })
                                .exceptionally(e -> {
                                    runOnUiThread(() -> {
                                        updateStatus("❌ Seçim oluşturma hatası:\n" + e.getMessage());
                                        Log.e(TAG, "Election creation error", e);
                                    });
                                    return null;
                                });
                    });
                })
                .exceptionally(e -> {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Blockchain zamanı alınamadı: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                    return null;
                });
    }
    /**
     * Calendar'ı blockchain için doğru Unix timestamp'e çevirir
     * Türkiye saatinden UTC'ye dönüştürür
     */

    private long convertToBlockchainTime(Calendar calendar) {

        long localTimeUnix = calendar.getTimeInMillis() / 1000;


        TimeZone turkeyTimeZone = TimeZone.getTimeZone("Europe/Istanbul");
        int offsetInMilliseconds = turkeyTimeZone.getOffset(calendar.getTimeInMillis());
        long utcTimeUnix = localTimeUnix - (offsetInMilliseconds / 1000);

        Log.d(TAG, "🔄 Timezone Dönüşümü (Düzeltilmiş):");
        Log.d(TAG, "📅 Local Time: " + formatDateTime(calendar));
        Log.d(TAG, "📅 Local Unix: " + localTimeUnix);
        Log.d(TAG, "🌐 Offset (saniye): " + (offsetInMilliseconds / 1000));
        Log.d(TAG, "🌐 UTC Unix: " + utcTimeUnix);

        long currentTimeUnix = System.currentTimeMillis() / 1000;
        Log.d(TAG, "⏰ Current Unix: " + currentTimeUnix);
        Log.d(TAG, "⏰ Fark: " + (utcTimeUnix - currentTimeUnix) + " saniye");


        if (utcTimeUnix <= currentTimeUnix) {

            utcTimeUnix = currentTimeUnix + 3600; // 1 saat sonra
            Log.d(TAG, "🔧 Zaman gelecekte tutuldu: " + utcTimeUnix);
            Log.d(TAG, "📅 Yeni UTC Zaman: " + new Date(utcTimeUnix * 1000));
        }

        return utcTimeUnix;
    }
    private void verifyBlockchainTime(long expectedStartTime, long expectedEndTime) {
        if (electionManager == null) {
            Log.e(TAG, "❌ ElectionManager null!");
            return;
        }

        electionManager.getCurrentBlockchainTime()
                .thenAccept(blockchainTime -> {
                    runOnUiThread(() -> {
                        Log.d(TAG, "🕐 BLOCKCHAIN ZAMAN DOĞRULAMA:");
                        Log.d(TAG, "⛓️ Blockchain Şimdiki Zaman: " + blockchainTime);
                        Log.d(TAG, "📅 Blockchain Tarihi: " + new Date(blockchainTime * 1000));
                        Log.d(TAG, "⏰ Seçim Başlangıç: " + expectedStartTime + " (" + new Date(expectedStartTime * 1000) + ")");
                        Log.d(TAG, "🏁 Seçim Bitiş: " + expectedEndTime + " (" + new Date(expectedEndTime * 1000) + ")");
                        Log.d(TAG, "✅ Şu Anda Oy Verilebilir Mi: " +
                                (blockchainTime >= expectedStartTime && blockchainTime <= expectedEndTime));

                        if (blockchainTime > expectedEndTime) {
                            Log.e(TAG, "❌ UYARI: Blockchain zamanı seçim bitiş zamanını geçmiş!");
                            long hoursDiff = (blockchainTime - expectedEndTime) / 3600;
                            Log.e(TAG, "🔧 Seçim bitiş zamanını " + hoursDiff + " saat ileriye alın");

                            // Kullanıcıya uyarı göster
                            Toast.makeText(AdminActivity.this,
                                    "⚠️ Uyarı: Seçim süresi blockchain zamanına göre dolmuş!\n" +
                                            "Seçimi " + hoursDiff + " saat uzatmanız gerekebilir.",
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .exceptionally(e -> {
                    runOnUiThread(() -> {
                        Log.w(TAG, "⚠️ Blockchain zamanı kontrol edilemedi: " + e.getMessage());
                        Toast.makeText(AdminActivity.this,
                                "Blockchain zamanı kontrol edilemedi", Toast.LENGTH_SHORT).show();
                    });
                    return null;
                });
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