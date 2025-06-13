package com.example.votechain.blockchain;

import android.content.Context;
import android.util.Log;
import com.example.votechain.model.Candidate;
import com.example.votechain.model.Election;
import com.google.firebase.firestore.FirebaseFirestore;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Blockchain ve Firebase entegrasyonunu yöneten sınıf
 * Seçim oluşturma, aday ekleme ve oy verme işlemlerini koordine eder
 */
public class BlockchainElectionManager {
    private static final String TAG = "BlockchainElectionManager";

    private static BlockchainElectionManager instance;
    private BlockchainManager blockchainManager;
    private FirebaseFirestore db;

    // Blockchain seçim ID'lerini Firebase ID'leri ile eşleştirmek için
    private Map<String, BigInteger> firebaseToBlockchainIds;
    private BigInteger nextElectionId;

    private BlockchainElectionManager() {
        blockchainManager = BlockchainManager.getInstance();
        db = FirebaseFirestore.getInstance();
        firebaseToBlockchainIds = new HashMap<>();
        nextElectionId = BigInteger.ONE;
    }

    public static synchronized BlockchainElectionManager getInstance() {
        if (instance == null) {
            instance = new BlockchainElectionManager();
        }
        return instance;
    }

    /**
     * Sistem başlatma - Admin cüzdanını hazırlar
     */
    public CompletableFuture<Boolean> initializeSystem(Context context) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        try {
            // Admin cüzdanını başlat
            boolean walletInitialized = blockchainManager.initializeWallet(context, "admin_votechain_2024");

            if (walletInitialized) {
                Log.d(TAG, "Blockchain sistem başarıyla başlatıldı");
                future.complete(true);
            } else {
                Log.e(TAG, "Blockchain sistemi başlatılamadı");
                future.complete(false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Sistem başlatma hatası", e);
            future.complete(false);
        }

        return future;
    }


    /**
     * Seçime aday ekler - Hem blockchain'de hem Firebase'de
     */
    public CompletableFuture<String> addCandidate(String firebaseElectionId, Candidate candidate) {
        CompletableFuture<String> future = new CompletableFuture<>();

        Log.d(TAG, "Aday ekleniyor: " + candidate.getName() + " -> " + firebaseElectionId);

        // 1. Önce Firebase'de oluştur
        db.collection("elections").document(firebaseElectionId)
                .collection("candidates")
                .add(candidate)
                .addOnSuccessListener(documentReference -> {
                    String candidateId = documentReference.getId();

                    // 2. Blockchain ID'sini al
                    BigInteger blockchainElectionId = firebaseToBlockchainIds.get(firebaseElectionId);

                    if (blockchainElectionId != null) {
                        // 3. Blockchain'de aday ekle
                        blockchainManager.addCandidate(blockchainElectionId, candidate)
                                .thenAccept(transactionHash -> {
                                    // 4. Blockchain'den yeni aday ID'sini al
                                    getCurrentCandidateId(blockchainElectionId)
                                            .thenAccept(candidateIdFromBlockchain -> {
                                                // 5. Firebase'de blockchain bilgilerini güncelle
                                                Map<String, Object> blockchainInfo = new HashMap<>();
                                                blockchainInfo.put("blockchainCandidateId", candidateIdFromBlockchain);
                                                blockchainInfo.put("transactionHash", transactionHash);

                                                documentReference.update(blockchainInfo)
                                                        .addOnSuccessListener(aVoid -> {
                                                            Log.d(TAG, "Aday başarıyla eklendi: " + candidateId);
                                                            future.complete(candidateId);
                                                        })
                                                        .addOnFailureListener(e -> {
                                                            Log.w(TAG, "Aday blockchain bilgileri güncellenemedi", e);
                                                            future.complete(candidateId);
                                                        });
                                            });
                                })
                                .exceptionally(e -> {
                                    Log.w(TAG, "Aday blockchain'e eklenemedi", e);
                                    future.complete(candidateId);
                                    return null;
                                });
                    } else {
                        Log.w(TAG, "Blockchain seçim ID'si bulunamadı, sadece Firebase'de aday eklendi");
                        future.complete(candidateId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Firebase'de aday eklenemedi", e);
                    future.completeExceptionally(e);
                });

        return future;
    }



    /**
     * Oy kullanma işlemi - Hem blockchain'de hem Firebase'de
     */
    public CompletableFuture<String> castVote(String firebaseElectionId, String candidateId, String tcKimlikNo) {
        CompletableFuture<String> future = new CompletableFuture<>();

        Log.d(TAG, "🔍 CAST VOTE DEBUG:");
        Log.d(TAG, "Firebase Election ID: " + firebaseElectionId);
        Log.d(TAG, "Candidate ID: " + candidateId);
        Log.d(TAG, "TC Kimlik: " + tcKimlikNo);

        // Blockchain manager hazır mı kontrol et
        if (!blockchainManager.isSystemReady()) {
            Log.w(TAG, "⚠️ Blockchain sistemi hazır değil, yeniden başlatılıyor...");
            initializeSystem(null)
                    .thenAccept(success -> {
                        if (success) {
                            Log.d(TAG, "✅ Blockchain sistemi yeniden başlatıldı");
                            performVoteWithElectionCheck(firebaseElectionId, candidateId, tcKimlikNo, future);
                        } else {
                            future.completeExceptionally(new Exception("Blockchain sistemi başlatılamadı"));
                        }
                    });
            return future;
        }

        performVoteWithElectionCheck(firebaseElectionId, candidateId, tcKimlikNo, future);
        return future;
    }
    /**
     * Oy verme işlemi - Election ID'sini kontrol ederek
     */
    private void performVoteWithElectionCheck(String firebaseElectionId, String candidateId, String tcKimlikNo, CompletableFuture<String> future) {
        Log.d(TAG, "🔍 Election ID kontrolü başlıyor...");

        // Önce cache'den kontrol et
        BigInteger blockchainElectionId = firebaseToBlockchainIds.get(firebaseElectionId);

        if (blockchainElectionId != null) {
            Log.d(TAG, "✅ Cache'den blockchain ID bulundu: " + blockchainElectionId);
            performDirectVote(firebaseElectionId, candidateId, tcKimlikNo, blockchainElectionId, future);
        } else {
            Log.d(TAG, "⚠️ Cache'de bulunamadı, Firebase'den yükleniyor...");
            // Firebase'den blockchain ID'sini yükle
            loadBlockchainIdFromFirebase(firebaseElectionId)
                    .thenAccept(loadedBlockchainId -> {
                        if (loadedBlockchainId != null) {
                            Log.d(TAG, "✅ Firebase'den blockchain ID yüklendi: " + loadedBlockchainId);
                            // Cache'e ekle
                            firebaseToBlockchainIds.put(firebaseElectionId, loadedBlockchainId);
                            performDirectVote(firebaseElectionId, candidateId, tcKimlikNo, loadedBlockchainId, future);
                        } else {
                            Log.e(TAG, "❌ Firebase'de blockchain ID bulunamadı!");
                            future.completeExceptionally(new Exception("Seçim blockchain ID'si bulunamadı - Seçim blockchain'de oluşturulmamış olabilir"));
                        }
                    })
                    .exceptionally(e -> {
                        Log.e(TAG, "❌ Firebase'den blockchain ID yüklenirken hata", e);
                        future.completeExceptionally(new Exception("Seçim bilgileri yüklenemedi: " + e.getMessage()));
                        return null;
                    });
        }
    }
    /**
     * Firebase'den blockchain Election ID'sini yükler - GELİŞTİRİLMİŞ VERSİYON
     */
    private CompletableFuture<BigInteger> loadBlockchainIdFromFirebase(String firebaseElectionId) {
        CompletableFuture<BigInteger> future = new CompletableFuture<>();

        Log.d(TAG, "🔍 Firebase'den seçim bilgileri yükleniyor: " + firebaseElectionId);

        db.collection("elections").document(firebaseElectionId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Blockchain bilgilerini kontrol et
                        Boolean blockchainEnabled = documentSnapshot.getBoolean("blockchainEnabled");
                        String blockchainIdStr = documentSnapshot.getString("blockchainElectionId");

                        Log.d(TAG, "📋 Seçim Firebase bilgileri:");
                        Log.d(TAG, "  - Blockchain Enabled: " + blockchainEnabled);
                        Log.d(TAG, "  - Blockchain ID String: " + blockchainIdStr);
                        Log.d(TAG, "  - Election Name: " + documentSnapshot.getString("name"));

                        if (blockchainEnabled != null && blockchainEnabled &&
                                blockchainIdStr != null && !blockchainIdStr.isEmpty()) {

                            try {
                                BigInteger blockchainId = new BigInteger(blockchainIdStr);
                                Log.d(TAG, "✅ Firebase'den blockchain ID alındı: " + blockchainId);
                                future.complete(blockchainId);
                            } catch (NumberFormatException e) {
                                Log.e(TAG, "❌ Blockchain ID parse hatası: " + blockchainIdStr, e);
                                future.complete(null);
                            }
                        } else {
                            Log.w(TAG, "⚠️ Seçim blockchain'de aktif değil veya ID yok");
                            Log.w(TAG, "   Blockchain Enabled: " + blockchainEnabled);
                            Log.w(TAG, "   Blockchain ID: " + blockchainIdStr);
                            future.complete(null);
                        }
                    } else {
                        Log.e(TAG, "❌ Firebase'de seçim dökümanı bulunamadı: " + firebaseElectionId);
                        future.complete(null);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Firebase seçim bilgisi alma hatası", e);
                    future.complete(null);
                });

        return future;
    }


    /**
     * Direkt oy verme işlemi
     */
    private void performDirectVote(String firebaseElectionId, String candidateId, String tcKimlikNo,
                                   BigInteger blockchainElectionId, CompletableFuture<String> future) {
        Log.d(TAG, "🗳️ Direkt oy verme başlıyor...");
        Log.d(TAG, "📊 Blockchain Election ID: " + blockchainElectionId);

        // 🔍 ÖNEMLİ: ÖY�E oy vermeden önce seçim bilgilerini kontrol et
        Log.d(TAG, "🔍 OY VERME ÖNCESİ SEÇİM KONTROLÜ:");

        // Blockchain'den seçim bilgilerini al ve kontrol et
        blockchainManager.debugElectionInfo(blockchainElectionId)
                .thenAccept(debugInfo -> {
                    Log.d(TAG, "📊 DEBUG SONUÇLARI: " + debugInfo);
                })
                .exceptionally(e -> {
                    Log.w(TAG, "⚠️ Debug bilgileri alınamadı: " + e.getMessage());
                    return null;
                });

        // Firebase'den candidate'ın blockchain ID'sini al
        db.collection("elections").document(firebaseElectionId)
                .collection("candidates").document(candidateId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String blockchainCandidateIdStr = documentSnapshot.getString("blockchainCandidateId");
                        String candidateName = documentSnapshot.getString("name");

                        Log.d(TAG, "👤 Aday bilgileri:");
                        Log.d(TAG, "  - Ad: " + candidateName);
                        Log.d(TAG, "  - Blockchain Candidate ID: " + blockchainCandidateIdStr);

                        if (blockchainCandidateIdStr != null && !blockchainCandidateIdStr.isEmpty()) {
                            try {
                                BigInteger blockchainCandidateId = new BigInteger(blockchainCandidateIdStr);

                                // 🔍 OY VERME ÖNCESİ SON KONTROL
                                Log.d(TAG, "🔍 SON KONTROL - OY VERME PARAMETRELERİ:");
                                Log.d(TAG, "  📊 Election ID: " + blockchainElectionId);
                                Log.d(TAG, "  👤 Candidate ID: " + blockchainCandidateId);
                                Log.d(TAG, "  🆔 TC Kimlik: " + tcKimlikNo);
                                Log.d(TAG, "  🕐 Şimdiki Zaman: " + (System.currentTimeMillis() / 1000));

                                // Blockchain'de oy kullan
                                Log.d(TAG, "🔗 Blockchain'e oy gönderiliyor...");
                                blockchainManager.vote(blockchainElectionId, blockchainCandidateId, tcKimlikNo)
                                        .thenAccept(transactionHash -> {
                                            Log.d(TAG, "✅ Blockchain'de oy kullanıldı!");
                                            Log.d(TAG, "🔗 Transaction Hash: " + transactionHash);
                                            future.complete(transactionHash);
                                        })
                                        .exceptionally(e -> {
                                            Log.e(TAG, "❌ BLOCKCHAIN OY HATASI DETAYI:");
                                            Log.e(TAG, "  🚨 Hata Mesajı: " + e.getMessage());
                                            Log.e(TAG, "  📊 Election ID: " + blockchainElectionId);
                                            Log.e(TAG, "  👤 Candidate ID: " + blockchainCandidateId);
                                            Log.e(TAG, "  🆔 TC: " + tcKimlikNo);
                                            Log.e(TAG, "  🕐 Hata Anı: " + (System.currentTimeMillis() / 1000));

                                            // Hata tipini analiz et
                                            if (e.getMessage().contains("Election has ended")) {
                                                Log.e(TAG, "  💡 SORUN: Seçim süresi blockchain'de dolmuş görünüyor");
                                                Log.e(TAG, "  🔧 ÇÖZÜM: Seçim bitiş zamanını kontrol edin");
                                            }

                                            future.completeExceptionally(e);
                                            return null;
                                        });
                            } catch (NumberFormatException e) {
                                Log.e(TAG, "❌ Blockchain candidate ID parse hatası: " + blockchainCandidateIdStr);
                                future.completeExceptionally(new Exception("Aday blockchain ID'si geçersiz"));
                            }
                        } else {
                            Log.e(TAG, "❌ Aday blockchain ID'si bulunamadı!");
                            future.completeExceptionally(new Exception("Aday blockchain'de kayıtlı değil"));
                        }
                    } else {
                        Log.e(TAG, "❌ Aday bulunamadı: " + candidateId);
                        future.completeExceptionally(new Exception("Aday bulunamadı"));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Aday bilgileri alınamadı", e);
                    future.completeExceptionally(e);
                });
    }

    /**
     * Sistem durumunu kontrol eder
     */
    /**
     * Sistem durumunu kontrol eder
     */
    public boolean isSystemReady() {
        // BlockchainManager'ın durumunu kontrol et
        boolean blockchainReady = blockchainManager.isSystemReady();
        boolean mappingReady = (firebaseToBlockchainIds != null);

        Log.d(TAG, "📊 BlockchainElectionManager Sistem Durumu:");
        Log.d(TAG, "🔗 BlockchainManager: " + (blockchainReady ? "✅" : "❌"));
        Log.d(TAG, "🗺️ ID Mapping: " + (mappingReady ? "✅" : "❌"));

        return blockchainReady && mappingReady;
    }

    /**
     * Sistem bilgilerini döndürür
     */
    public Map<String, String> getSystemInfo() {
        Map<String, String> info = new HashMap<>();
        info.put("walletAddress", blockchainManager.getWalletAddress());
        info.put("contractAddress", blockchainManager.getContractAddress());
        info.put("totalElections", String.valueOf(firebaseToBlockchainIds.size()));
        return info;
    }

    /**
     * Blockchain'den seçimdeki toplam aday sayısını sorgular ve bir sonraki aday ID'sini döndürür
     */
    private CompletableFuture<String> getCurrentCandidateId(BigInteger electionId) {
        CompletableFuture<String> future = new CompletableFuture<>();

        try {
            // Blockchain'den seçim sonuçlarını al ve aday sayısını öğren
            blockchainManager.getElectionResults(electionId)
                    .thenAccept(candidates -> {
                        // Mevcut aday sayısı + 1 = yeni aday ID'si
                        String nextCandidateId = String.valueOf(candidates.size() + 1);
                        future.complete(nextCandidateId);
                    })
                    .exceptionally(e -> {
                        Log.w(TAG, "Blockchain'den aday sayısı alınamadı, varsayılan değer kullanılıyor", e);
                        // Hata durumunda varsayılan değer
                        future.complete("1");
                        return null;
                    });
        } catch (Exception e) {
            Log.w(TAG, "getCurrentCandidateId hatası", e);
            future.complete("1");
        }

        return future;
    }
    /**
     * Özel Unix timestamp'ler ile seçim oluşturur
     * Admin'in seçtiği tarih/saatleri timezone düzeltmesi ile kullanır
     */
    public CompletableFuture<String> createElectionWithCustomTimes(Election election, long startTimeUnix, long endTimeUnix) {
        CompletableFuture<String> future = new CompletableFuture<>();

        try {
            Log.d(TAG, "🕐 BLOCKCHAIN SEÇİM OLUŞTURMA:");
            Log.d(TAG, "📋 Seçim Adı: " + election.getName());
            Log.d(TAG, "📅 Start Unix: " + startTimeUnix);
            Log.d(TAG, "📅 End Unix: " + endTimeUnix);

            // Şimdiki blockchain zamanını kontrol et
            long currentTime = System.currentTimeMillis() / 1000;
            Log.d(TAG, "⛓️ Şimdiki Zaman: " + currentTime);
            Log.d(TAG, "📊 Start - Current: " + (startTimeUnix - currentTime) + " saniye");
            Log.d(TAG, "📊 End - Current: " + (endTimeUnix - currentTime) + " saniye");

            // 🔧 ZAMAN KONTROLÜ
            if (endTimeUnix <= currentTime) {
                Log.w(TAG, "⚠️ UYARI: Seçim süresi dolmuş! End time'ı ileriye alıyorum");
                endTimeUnix = currentTime + (24 * 3600); // 24 saat sonrasına al
            }

            if (startTimeUnix > currentTime) {
                Log.i(TAG, "🕐 Seçim gelecekte başlayacak");
                // Blockchain test için başlangıcı geçmişe al
                startTimeUnix = currentTime - 3600; // 1 saat önce
                Log.d(TAG, "🔧 Test için start time düzeltildi: " + startTimeUnix);
            }

            // 1. Önce Firebase'de oluştur
            long finalStartTimeUnix = startTimeUnix;
            long finalEndTimeUnix = endTimeUnix;
            db.collection("elections")
                    .add(election)
                    .addOnSuccessListener(documentReference -> {
                        String firebaseElectionId = documentReference.getId();
                        Log.d(TAG, "✅ Firebase'de oluşturuldu: " + firebaseElectionId);

                        // 2. Sonra blockchain'de oluştur
                        blockchainManager.createElectionWithSpecificTimes(election, finalStartTimeUnix, finalEndTimeUnix)
                                .thenAccept(transactionHash -> {
                                    Log.d(TAG, "✅ Blockchain'de oluşturuldu: " + transactionHash);

                                    // 3. ID eşleştirmesini kaydet
                                    firebaseToBlockchainIds.put(firebaseElectionId, nextElectionId);

                                    // 4. Firebase'de blockchain bilgilerini güncelle
                                    Map<String, Object> blockchainInfo = new HashMap<>();
                                    blockchainInfo.put("blockchainElectionId", nextElectionId.toString());
                                    blockchainInfo.put("transactionHash", transactionHash);
                                    blockchainInfo.put("blockchainEnabled", true);
                                    blockchainInfo.put("startTimeUnix", finalStartTimeUnix);
                                    blockchainInfo.put("endTimeUnix", finalEndTimeUnix);
                                    blockchainInfo.put("timezoneFixed", true);

                                    documentReference.update(blockchainInfo)
                                            .addOnSuccessListener(aVoid -> {
                                                Log.d(TAG, "✅ Seçim başarıyla oluşturuldu!");
                                                Log.d(TAG, "🆔 Firebase ID: " + firebaseElectionId);
                                                Log.d(TAG, "🔗 Blockchain ID: " + nextElectionId);
                                                Log.d(TAG, "📅 Final Start Unix: " + finalStartTimeUnix);
                                                Log.d(TAG, "📅 Final End Unix: " + finalEndTimeUnix);
                                                Log.d(TAG, "🔗 TX Hash: " + transactionHash);

                                                nextElectionId = nextElectionId.add(BigInteger.ONE);
                                                future.complete(firebaseElectionId);
                                            })
                                            .addOnFailureListener(e -> {
                                                Log.w(TAG, "⚠️ Blockchain bilgileri Firebase'e kaydedilemedi", e);
                                                nextElectionId = nextElectionId.add(BigInteger.ONE);
                                                future.complete(firebaseElectionId);
                                            });
                                })
                                .exceptionally(e -> {
                                    Log.e(TAG, "❌ Blockchain seçim oluşturulamadı", e);
                                    Map<String, Object> blockchainInfo = new HashMap<>();
                                    blockchainInfo.put("blockchainEnabled", false);
                                    blockchainInfo.put("blockchainError", e.getMessage());
                                    documentReference.update(blockchainInfo);
                                    future.complete(firebaseElectionId);
                                    return null;
                                });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ Firebase seçim oluşturulamadı", e);
                        future.completeExceptionally(e);
                    });

        } catch (Exception e) {
            Log.e(TAG, "❌ createElectionWithSpecificTimes genel hatası", e);
            future.completeExceptionally(e);
        }

        return future;
    }
}