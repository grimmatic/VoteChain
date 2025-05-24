package com.example.votechain.blockchain;

import android.content.Context;
import android.util.Log;

import com.example.votechain.model.Candidate;
import com.example.votechain.model.Election;
import com.google.firebase.firestore.FirebaseFirestore;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
     * Yeni seçim oluşturur - Hem blockchain'de hem Firebase'de
     */
    public CompletableFuture<String> createElection(Election election) {
        CompletableFuture<String> future = new CompletableFuture<>();

        Log.d(TAG, "Seçim oluşturuluyor: " + election.getName());

        // 1. Önce Firebase'de oluştur
        db.collection("elections")
                .add(election)
                .addOnSuccessListener(documentReference -> {
                    String firebaseElectionId = documentReference.getId();

                    // 2. Sonra blockchain'de oluştur
                    blockchainManager.createElection(election)
                            .thenAccept(transactionHash -> {
                                // 3. ID eşleştirmesini kaydet
                                firebaseToBlockchainIds.put(firebaseElectionId, nextElectionId);

                                // 4. Firebase'de blockchain bilgilerini güncelle
                                Map<String, Object> blockchainInfo = new HashMap<>();
                                blockchainInfo.put("blockchainElectionId", nextElectionId.toString());
                                blockchainInfo.put("transactionHash", transactionHash);
                                blockchainInfo.put("blockchainEnabled", true);

                                documentReference.update(blockchainInfo)
                                        .addOnSuccessListener(aVoid -> {
                                            Log.d(TAG, "Seçim başarıyla oluşturuldu: FB=" + firebaseElectionId +
                                                    ", BC=" + nextElectionId + ", TX=" + transactionHash);

                                            nextElectionId = nextElectionId.add(BigInteger.ONE);
                                            future.complete(firebaseElectionId);
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.w(TAG, "Blockchain bilgileri Firebase'e kaydedilemedi", e);
                                            // Yine de başarılı say, seçim oluşturuldu
                                            nextElectionId = nextElectionId.add(BigInteger.ONE);
                                            future.complete(firebaseElectionId);
                                        });
                            })
                            .exceptionally(e -> {
                                Log.w(TAG, "Blockchain seçim oluşturulamadı, sadece Firebase'de oluşturuldu", e);

                                // Blockchain başarısız olsa da Firebase'de oluşturuldu
                                Map<String, Object> blockchainInfo = new HashMap<>();
                                blockchainInfo.put("blockchainEnabled", false);
                                blockchainInfo.put("blockchainError", e.getMessage());

                                documentReference.update(blockchainInfo);
                                future.complete(firebaseElectionId);
                                return null;
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Firebase seçim oluşturulamadı", e);
                    future.completeExceptionally(e);
                });

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
     * TC Kimlik numarasını blockchain'de geçerli hale getirir
     */
    public CompletableFuture<String> addValidTCId(String tcKimlikNo) {
        return blockchainManager.addValidTCId(tcKimlikNo);
    }

    /**
     * Oy kullanma işlemi - Hem blockchain'de hem Firebase'de
     */
    public CompletableFuture<String> castVote(String firebaseElectionId, String candidateId, String tcKimlikNo) {
        CompletableFuture<String> future = new CompletableFuture<>();

        Log.d(TAG, "Oy kullanılıyor: " + firebaseElectionId + " -> " + candidateId);

        // Blockchain ID'lerini al
        BigInteger blockchainElectionId = firebaseToBlockchainIds.get(firebaseElectionId);

        if (blockchainElectionId != null) {
            // Firebase'den candidate'ın blockchain ID'sini al
            db.collection("elections").document(firebaseElectionId)
                    .collection("candidates").document(candidateId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String blockchainCandidateIdStr = documentSnapshot.getString("blockchainCandidateId");

                            if (blockchainCandidateIdStr != null) {
                                BigInteger blockchainCandidateId = new BigInteger(blockchainCandidateIdStr);

                                // Blockchain'de oy kullan
                                blockchainManager.vote(blockchainElectionId, blockchainCandidateId, tcKimlikNo)
                                        .thenAccept(transactionHash -> {
                                            Log.d(TAG, "Blockchain'de oy kullanıldı: " + transactionHash);
                                            future.complete(transactionHash);
                                        })
                                        .exceptionally(e -> {
                                            Log.e(TAG, "Blockchain'de oy kullanılamadı", e);
                                            future.completeExceptionally(e);
                                            return null;
                                        });
                            } else {
                                future.completeExceptionally(new Exception("Aday blockchain ID'si bulunamadı"));
                            }
                        } else {
                            future.completeExceptionally(new Exception("Aday bulunamadı"));
                        }
                    })
                    .addOnFailureListener(future::completeExceptionally);
        } else {
            future.completeExceptionally(new Exception("Seçim blockchain ID'si bulunamadı"));
        }

        return future;
    }

    /**
     * Seçim sonuçlarını blockchain'den getirir
     */
    public CompletableFuture<List<Candidate>> getElectionResults(String firebaseElectionId) {
        BigInteger blockchainElectionId = firebaseToBlockchainIds.get(firebaseElectionId);

        if (blockchainElectionId != null) {
            return blockchainManager.getElectionResults(blockchainElectionId);
        } else {
            CompletableFuture<List<Candidate>> future = new CompletableFuture<>();
            future.completeExceptionally(new Exception("Blockchain seçim ID'si bulunamadı"));
            return future;
        }
    }

    /**
     * Sistem durumunu kontrol eder
     */
    public boolean isSystemReady() {
        return blockchainManager.getWalletAddress() != null &&
                blockchainManager.getContractAddress() != null;
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
}