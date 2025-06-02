package com.example.votechain.blockchain;
import android.content.Context;
import android.util.Log;
import com.example.votechain.model.Candidate;
import com.example.votechain.model.Election;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.DefaultGasProvider;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Blockchain işlemlerini yöneten sınıf.
 * Bu sınıf Ethereum blockchain'i ile etkileşim için Web3j kütüphanesini kullanır.
 */
public class BlockchainManager {
    private static final String TAG = "BlockchainManager";

    private static final String INFURA_URL = "https://sepolia.infura.io/v3/0530f4db5185496891f0ca7c39b8092c";
    private static final String PRIVATE_KEY = "1dfde3cdaf870377d55de8bf4bc62e7f589d492eb986fef2a9b98076b8d4db20";

    // Singleton instance
    private static BlockchainManager instance;

    private Web3j web3j;
    private Credentials credentials;
    private VotingContract votingContract;
    private String contractAddress;

    private BlockchainManager() {
        // Web3j bağlantısını başlat
        web3j = Web3j.build(new HttpService(INFURA_URL));
    }

    /**
     * Singleton instance'ı döndürür
     */
    public static synchronized BlockchainManager getInstance() {
        if (instance == null) {
            instance = new BlockchainManager();
        }
        return instance;
    }

    /**
     * Ethereum cüzdanını başlatır
     * @param context Uygulama context'i
     * @param password Cüzdan şifresi
     * @return İşlem başarısı
     */
    public boolean initializeWallet(Context context, String password) {
        try {
            Log.d(TAG, "🔧 Cüzdan başlatılıyor...");

            // Web3j bağlantısını kontrol et
            if (web3j == null) {
                Log.d(TAG, "🌐 Web3j bağlantısı oluşturuluyor...");
                web3j = Web3j.build(new HttpService(INFURA_URL));
            }

            // Credentials oluştur
            if (credentials == null) {
                Log.d(TAG, "🔑 Credentials oluşturuluyor...");
                credentials = Credentials.create(PRIVATE_KEY);
                Log.d(TAG, "✅ Cüzdan yüklendi: " + credentials.getAddress());
            }

            // Bakiye kontrol et
            checkWalletBalance();

            // Kontrat bağlantısını başlat
            if (votingContract == null) {
                Log.d(TAG, "📜 Kontrat yükleniyor...");
                initializeContract();
            }

            // Son durum kontrolü
            boolean ready = isSystemReady();
            Log.d(TAG, "🏁 Wallet initialization result: " + (ready ? "✅ BAŞARILI" : "❌ BAŞARISIZ"));

            if (!ready) {
                Log.w(TAG, "⚠️ Sistem hazır değil, bileşenleri kontrol et:");
                Log.w(TAG, "  - Web3j: " + (web3j != null ? "✅" : "❌"));
                Log.w(TAG, "  - Credentials: " + (credentials != null ? "✅" : "❌"));
                Log.w(TAG, "  - VotingContract: " + (votingContract != null ? "✅" : "❌"));
            }

            return ready;

        } catch (Exception e) {
            Log.e(TAG, "❌ Cüzdan yükleme hatası: " + e.getMessage(), e);
            return false;
        }
    }
    /**
     * Akıllı kontrat bağlantısını başlatır
     */
    private void initializeContract() {
        try {
            Log.d(TAG, "🔧 Kontrat başlatılıyor...");

            if (credentials == null) {
                Log.e(TAG, "❌ Credentials null, kontrat başlatılamıyor");
                return;
            }

            if (web3j == null) {
                Log.e(TAG, "❌ Web3j null, kontrat başlatılamıyor");
                return;
            }

            ContractGasProvider gasProvider = new DefaultGasProvider();
            contractAddress = "0x9588945a6185b61deb9204c59ccafd12098fdbfa";

            Log.d(TAG, "📜 Kontrat adresi: " + contractAddress);
            Log.d(TAG, "🔑 Cüzdan adresi: " + credentials.getAddress());

            votingContract = VotingContract.load(
                    contractAddress,
                    web3j,
                    credentials,
                    gasProvider
            );

            if (votingContract != null) {
                Log.d(TAG, "✅ Kontrat başarıyla yüklendi: " + contractAddress);

                // Kontrat çalışıyor mu test et
                try {
                    votingContract.electionCount().sendAsync()
                            .thenAccept(count -> {
                                Log.d(TAG, "🔍 Kontrat test başarılı, seçim sayısı: " + count);
                            })
                            .exceptionally(e -> {
                                Log.w(TAG, "⚠️ Kontrat test başarısız: " + e.getMessage());
                                return null;
                            });
                } catch (Exception e) {
                    Log.w(TAG, "⚠️ Kontrat test yapılamadı: " + e.getMessage());
                }
            } else {
                Log.e(TAG, "❌ Kontrat yüklenemedi!");
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Kontrat başlatma hatası", e);
            votingContract = null;
        }
    }
    /**
     * Akıllı kontrat dağıtır ve adresini döndürür
     * @return Kontrat adresi
     */
    public CompletableFuture<String> deployContract() {
        CompletableFuture<String> future = new CompletableFuture<>();

        try {
            ContractGasProvider gasProvider = new DefaultGasProvider();

            // Asenkron olarak kontrat dağıt
            VotingContract.deploy(
                            web3j,
                            credentials,
                            gasProvider
                    ).sendAsync()
                    .thenAccept(contract -> {
                        contractAddress = contract.getContractAddress();
                        votingContract = contract;
                        Log.d(TAG, "Contract deployed at: " + contractAddress);
                        future.complete(contractAddress);
                    })
                    .exceptionally(e -> {
                        Log.e(TAG, "Error deploying contract", e);
                        future.completeExceptionally(e);
                        return null;
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error deploying contract", e);
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * Yeni bir seçim oluşturur
     * @param election Seçim nesnesi
     * @return İşlem hash'i
     */
    public CompletableFuture<String> createElection(Election election) {
        CompletableFuture<String> future = new CompletableFuture<>();

        try {
            // Unix timestamp'leri hesapla
            long startTime = election.getStartDate().getTime() / 1000;
            long endTime = election.getEndDate().getTime() / 1000;
            long currentTime = System.currentTimeMillis() / 1000;

            Log.d(TAG, "🕐 ZAMAN DEBUG:");
            Log.d(TAG, "📅 Current timestamp: " + currentTime);
            Log.d(TAG, "📅 Start timestamp: " + startTime);
            Log.d(TAG, "📅 End timestamp: " + endTime);
            Log.d(TAG, "📅 Start - Current = " + (startTime - currentTime) + " seconds");
            Log.d(TAG, "📅 Current - Start = " + (currentTime - startTime) + " seconds");

            if (startTime > currentTime) {
                Log.w(TAG, "⚠️ UYARI: Seçim gelecekte başlıyor!");
            } else {
                Log.i(TAG, "✅ Seçim geçmişte başlamış, iyi!");
            }

            // isActive parametresini kaldırdım
            votingContract.createElection(
                            election.getName(),
                            election.getDescription(),
                            BigInteger.valueOf(startTime),
                            BigInteger.valueOf(endTime)
                    ).sendAsync()
                    .thenAccept(receipt -> {
                        String txHash = receipt.getTransactionHash();
                        Log.d(TAG, "Election created: " + txHash);
                        future.complete(txHash);
                    })
                    .exceptionally(e -> {
                        Log.e(TAG, "Error creating election", e);
                        future.completeExceptionally(e);
                        return null;
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error creating election", e);
            future.completeExceptionally(e);
        }

        return future;
    }
    public boolean isSystemReady() {
        boolean walletOK = (credentials != null && getWalletAddress() != null);
        boolean contractOK = (votingContract != null && getContractAddress() != null);
        boolean web3jOK = (web3j != null);

        Log.d(TAG, "📊 BlockchainManager Sistem Durumu:");
        Log.d(TAG, "🔑 Cüzdan: " + (walletOK ? "✅" : "❌"));
        Log.d(TAG, "📜 Kontrat: " + (contractOK ? "✅" : "❌"));
        Log.d(TAG, "🌐 Web3j: " + (web3jOK ? "✅" : "❌"));

        return walletOK && contractOK && web3jOK;
    }
    /**
     * Bir seçime aday ekler
     * @param electionId Seçim ID'si
     * @param candidate Aday nesnesi
     * @return İşlem hash'i
     */
    public CompletableFuture<String> addCandidate(BigInteger electionId, Candidate candidate) {
        CompletableFuture<String> future = new CompletableFuture<>();

        try {
            votingContract.addCandidate(
                            electionId,
                            candidate.getName(),
                            candidate.getParty()
                    ).sendAsync()
                    .thenAccept(receipt -> {
                        String txHash = receipt.getTransactionHash();
                        Log.d(TAG, "Candidate added: " + txHash);
                        future.complete(txHash);
                    })
                    .exceptionally(e -> {
                        Log.e(TAG, "Error adding candidate", e);
                        future.completeExceptionally(e);
                        return null;
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error adding candidate", e);
            future.completeExceptionally(e);
        }

        return future;
    }

    public void checkWalletBalance() {
        if (web3j == null || credentials == null) {
            Log.w(TAG, "Web3j veya credentials null");
            return;
        }
        try {
            web3j.ethGetBalance(credentials.getAddress(), DefaultBlockParameter.valueOf("latest"))
                    .sendAsync()
                    .thenAccept(balance -> {
                        double ethBalance = balance.getBalance().doubleValue() / 1e18;
                        Log.d(TAG, "💰 Cüzdan Bakiyesi: " + ethBalance + " ETH");

                        if (ethBalance < 0.001) {
                            Log.w(TAG, "⚠️ UYARI: Düşük ETH bakiyesi! Gas için yeterli olmayabilir.");
                            Log.w(TAG, "🚰 Test ether almak için: https://sepoliafaucet.com/");
                        } else {
                            Log.d(TAG, "✅ ETH bakiyesi yeterli");
                        }
                    })
                    .exceptionally(e -> {
                        Log.e(TAG, "❌ Bakiye kontrol hatası: " + e.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            Log.e(TAG, "❌ Bakiye kontrol exception: " + e.getMessage());
        }
    }
    /**
     * Seçim sonuçlarını getirir
     * @param electionId Seçim ID'si
     * @return Aday listesi
     */
    public CompletableFuture<List<Candidate>> getElectionResults(BigInteger electionId) {
        CompletableFuture<List<Candidate>> future = new CompletableFuture<>();

        try {
            Log.d(TAG, "📊 Seçim sonuçları getiriliyor: " + electionId);

            if (votingContract == null) {
                Log.e(TAG, "❌ VOTING CONTRACT NULL!");
                future.completeExceptionally(new Exception("Voting contract başlatılamadı"));
                return future;
            }

            votingContract.getElectionResults(electionId).sendAsync()
                    .thenAccept(result -> {
                        try {
                            List<Candidate> candidates = new ArrayList<>();

                            @SuppressWarnings("unchecked")
                            List<BigInteger> ids = (List<BigInteger>) result.get(0).getValue();
                            @SuppressWarnings("unchecked")
                            List<String> names = (List<String>) result.get(1).getValue();
                            @SuppressWarnings("unchecked")
                            List<String> parties = (List<String>) result.get(2).getValue();
                            @SuppressWarnings("unchecked")
                            List<BigInteger> voteCounts = (List<BigInteger>) result.get(3).getValue();

                            Log.d(TAG, "📊 Sonuç sayısı: " + ids.size());

                            for (int i = 0; i < ids.size(); i++) {
                                Candidate candidate = new Candidate(
                                        electionId.toString(),
                                        names.get(i),
                                        parties.get(i)
                                );
                                candidate.setId(ids.get(i).toString());
                                candidate.setVoteCount(voteCounts.get(i).intValue());
                                candidates.add(candidate);

                                Log.d(TAG, "👤 Aday: " + names.get(i) + " | Oy: " + voteCounts.get(i));
                            }

                            Log.d(TAG, "✅ Seçim sonuçları başarıyla alındı");
                            future.complete(candidates);
                        } catch (Exception e) {
                            Log.e(TAG, "❌ Seçim sonuçları parse hatası", e);
                            future.completeExceptionally(e);
                        }
                    })
                    .exceptionally(e -> {
                        Log.e(TAG, "❌ Seçim sonuçları alma hatası", e);
                        future.completeExceptionally(e);
                        return null;
                    });
        } catch (Exception e) {
            Log.e(TAG, "❌ Seçim sonuçları genel hatası", e);
            future.completeExceptionally(e);
        }
        return future;
    }
    /**
     * Seçim sonuçlarını getirir
     * @param electionId Seçim ID'si
     * @return Aday listesi
     */
    public CompletableFuture<String> vote(BigInteger electionId, BigInteger candidateId, String tcKimlikNo) {
        CompletableFuture<String> future = new CompletableFuture<>();

        try {
            Log.d(TAG, "🗳️ OY VERME İŞLEMİ BAŞLADI");
            Log.d(TAG, "📊 Election ID: " + electionId);
            Log.d(TAG, "👤 Candidate ID: " + candidateId);
            Log.d(TAG, "🆔 TC Kimlik: " + tcKimlikNo);

            // TC Kimlik hash kontrolü ekle
            String tcIdHash = Hash.sha3String(tcKimlikNo);
            Log.d(TAG, "🔐 TC Hash: " + tcIdHash);

            // Önce TC ID'nin valid olup olmadığını kontrol et
            votingContract.isValidTCId(tcIdHash).sendAsync()
                    .thenAccept(isValid -> {
                        Log.d(TAG, "🔍 TC ID Valid Check: " + isValid);

                        if (!isValid) {
                            Log.w(TAG, "⚠️ TC ID geçerli değil, önce ekleniyor...");
                            // TC ID'yi ekle, sonra oy ver
                            votingContract.addValidTCId(tcIdHash).sendAsync()
                                    .thenAccept(addReceipt -> {
                                        Log.d(TAG, "✅ TC ID eklendi: " + addReceipt.getTransactionHash());
                                        // Şimdi oy ver
                                        performVote(electionId, candidateId, tcIdHash, future);
                                    })
                                    .exceptionally(e -> {
                                        Log.e(TAG, "❌ TC ID eklenemedi: " + e.getMessage());
                                        future.completeExceptionally(e);
                                        return null;
                                    });
                        } else {
                            // Direkt oy ver
                            performVote(electionId, candidateId, tcIdHash, future);
                        }
                    })
                    .exceptionally(e -> {
                        Log.e(TAG, "❌ TC ID kontrol hatası: " + e.getMessage());
                        // Yine de oy vermeyi dene
                        performVote(electionId, candidateId, tcIdHash, future);
                        return null;
                    });

        } catch (Exception e) {
            Log.e(TAG, "❌ OY VERME HAZIRLIK HATASI!", e);
            future.completeExceptionally(e);
        }

        return future;
    }

    private void performVote(BigInteger electionId, BigInteger candidateId, String tcIdHash, CompletableFuture<String> future) {
        ContractGasProvider gasProvider = new DefaultGasProvider() {
            @Override
            public BigInteger getGasLimit(String contractFunc) {
                return BigInteger.valueOf(500000); // 500k gas
            }
            @Override
            public BigInteger getGasPrice(String contractFunc) {
                return BigInteger.valueOf(30000000000L); // 30 Gwei
            }
        };

        try {
            votingContract.vote(electionId, candidateId, tcIdHash)
                    .sendAsync()
                    .thenAccept(receipt -> {
                        String txHash = receipt.getTransactionHash();
                        Log.d(TAG, "✅ BLOCKCHAIN OY İŞLEMİ BAŞARILI!");
                        Log.d(TAG, "🔗 Transaction Hash: " + txHash);
                        Log.d(TAG, "⛽ Gas Used: " + receipt.getGasUsed());

                        future.complete(txHash);
                    })
                    .exceptionally(e -> {
                        Log.e(TAG, "❌ BLOCKCHAIN OY İŞLEMİ BAŞARISIZ!");
                        Log.e(TAG, "🚨 Hata Detayı: " + e.getMessage());
                        future.completeExceptionally(e);
                        return null;
                    });
        } catch (Exception e) {
            Log.e(TAG, "❌ Vote transaction oluşturma hatası: " + e.getMessage());
            future.completeExceptionally(e);
        }
    }
    /**
     * TC Kimlik doğrulaması için geçerli bir TC Kimlik numarası ekler
     * @param tcKimlikNo TC Kimlik Numarası
     * @return İşlem hash'i
     */
    public CompletableFuture<String> addValidTCId(String tcKimlikNo) {
        CompletableFuture<String> future = new CompletableFuture<>();

        try {
            // NULL CHECK EKLE
            if (votingContract == null) {
                Log.e(TAG, "❌ VOTING CONTRACT NULL! Kontrat başlatılmamış.");
                future.completeExceptionally(new Exception("Voting contract başlatılmamış"));
                return future;
            }

            if (web3j == null) {
                Log.e(TAG, "❌ WEB3J NULL! Blockchain bağlantısı yok.");
                future.completeExceptionally(new Exception("Blockchain bağlantısı yok"));
                return future;
            }

            if (credentials == null) {
                Log.e(TAG, "❌ CREDENTIALS NULL! Cüzdan yüklenememiş.");
                future.completeExceptionally(new Exception("Cüzdan yüklenememiş"));
                return future;
            }

            Log.d(TAG, "✅ Kontrat durumu: OK");
            Log.d(TAG, "🔐 TC Kimlik ekleniyor: " + tcKimlikNo);

            // TC Kimlik numarasını hash'le
            String tcIdHash = Hash.sha3String(tcKimlikNo);
            Log.d(TAG, "🔐 TC Hash: " + tcIdHash);

            votingContract.addValidTCId(tcIdHash).sendAsync()
                    .thenAccept(receipt -> {
                        String txHash = receipt.getTransactionHash();
                        Log.d(TAG, "✅ TC ID eklendi: " + txHash);
                        future.complete(txHash);
                    })
                    .exceptionally(e -> {
                        Log.e(TAG, "❌ TC ID ekleme hatası", e);
                        future.completeExceptionally(e);
                        return null;
                    });
        } catch (Exception e) {
            Log.e(TAG, "❌ TC ID ekleme genel hatası", e);
            future.completeExceptionally(e);
        }

        return future;
    }
    public CompletableFuture<String> setElectionActive(BigInteger electionId, boolean active) {
        CompletableFuture<String> future = new CompletableFuture<>();

        try {
            votingContract.setElectionActive(electionId, active).sendAsync()
                    .thenAccept(receipt -> {
                        String txHash = receipt.getTransactionHash();
                        Log.d(TAG, "Election status updated: " + txHash);
                        future.complete(txHash);
                    })
                    .exceptionally(e -> {
                        Log.e(TAG, "Error updating election status", e);
                        future.completeExceptionally(e);
                        return null;
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error updating election status", e);
            future.completeExceptionally(e);
        }

        return future;
    }
    /**
     * Mevcut cüzdan adresini döndürür
     * @return Ethereum cüzdan adresi
     */
    public String getWalletAddress() {
        if (credentials != null) {
            return credentials.getAddress();
        }
        return null;
    }

    /**
     * Kontrat adresini döndürür
     * @return Ethereum kontrat adresi
     */
    public String getContractAddress() {
        return contractAddress;
    }
    /**
     * Belirtilen Unix timestamp'ler ile seçim oluşturur
     * Timezone düzeltmesi yapılmış zamanları kullanır
     */
    public CompletableFuture<String> createElectionWithSpecificTimes(Election election, long startTimeUnix, long endTimeUnix) {
        CompletableFuture<String> future = new CompletableFuture<>();

        try {
            Log.d(TAG, "🕐 BLOCKCHAIN SEÇİM OLUŞTURMA - ÖZELLEŞTİRİLMİŞ:");
            Log.d(TAG, "📋 Seçim Adı: " + election.getName());
            Log.d(TAG, "📅 Start Unix (Düzeltilmiş): " + startTimeUnix);
            Log.d(TAG, "📅 End Unix (Düzeltilmiş): " + endTimeUnix);

            // Şimdiki blockchain zamanını kontrol et
            getCurrentBlockchainTime()
                    .thenAccept(blockchainCurrentTime -> {
                        Log.d(TAG, "⛓️ Blockchain Şimdiki Zaman: " + blockchainCurrentTime);
                        Log.d(TAG, "📊 Start - Current: " + (startTimeUnix - blockchainCurrentTime) + " saniye");
                        Log.d(TAG, "📊 End - Current: " + (endTimeUnix - blockchainCurrentTime) + " saniye");

                        // Zaman kontrolü
                        if (endTimeUnix <= blockchainCurrentTime) {
                            Log.w(TAG, "⚠️ UYARI: Seçim süresi blockchain zamanına göre dolmuş!");
                        } else if (startTimeUnix <= blockchainCurrentTime && blockchainCurrentTime < endTimeUnix) {
                            Log.i(TAG, "✅ Seçim şu anda aktif sürede!");
                        } else if (startTimeUnix > blockchainCurrentTime) {
                            Log.i(TAG, "🕐 Seçim gelecekte başlayacak: " +
                                    ((startTimeUnix - blockchainCurrentTime) / 60) + " dakika sonra");
                        }

                        // Blockchain'de oluştur
                        votingContract.createElection(
                                        election.getName(),
                                        election.getDescription(),
                                        BigInteger.valueOf(startTimeUnix),
                                        BigInteger.valueOf(endTimeUnix)
                                ).sendAsync()
                                .thenAccept(receipt -> {
                                    String txHash = receipt.getTransactionHash();
                                    Log.d(TAG, "✅ Seçim blockchain'de özel zamanlarla oluşturuldu!");
                                    Log.d(TAG, "🔗 Transaction Hash: " + txHash);
                                    Log.d(TAG, "⛽ Gas Used: " + receipt.getGasUsed());
                                    future.complete(txHash);
                                })
                                .exceptionally(e -> {
                                    Log.e(TAG, "❌ Blockchain seçim oluşturma hatası", e);
                                    future.completeExceptionally(e);
                                    return null;
                                });
                    })
                    .exceptionally(e -> {
                        Log.w(TAG, "⚠️ Blockchain zamanı alınamadı, direkt oluşturulmaya devam ediliyor");

                        // Fallback: Blockchain zamanı alamasak da devam et
                        try {
                            votingContract.createElection(
                                            election.getName(),
                                            election.getDescription(),
                                            BigInteger.valueOf(startTimeUnix),
                                            BigInteger.valueOf(endTimeUnix)
                                    ).sendAsync()
                                    .thenAccept(receipt -> {
                                        String txHash = receipt.getTransactionHash();
                                        Log.d(TAG, "✅ Seçim fallback ile oluşturuldu: " + txHash);
                                        future.complete(txHash);
                                    })
                                    .exceptionally(ex -> {
                                        Log.e(TAG, "❌ Fallback seçim oluşturma hatası", ex);
                                        future.completeExceptionally(ex);
                                        return null;
                                    });
                        } catch (Exception ex) {
                            future.completeExceptionally(ex);
                        }
                        return null;
                    });

        } catch (Exception e) {
            Log.e(TAG, "❌ createElectionWithSpecificTimes genel hatası", e);
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * Mevcut blockchain zamanını alır
     */
    private CompletableFuture<Long> getCurrentBlockchainTime() {
        CompletableFuture<Long> future = new CompletableFuture<>();

        try {
            web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf("latest"), false)
                    .sendAsync()
                    .thenAccept(block -> {
                        if (block.getBlock() != null) {
                            long blockTime = block.getBlock().getTimestamp().longValue();
                            future.complete(blockTime);
                        } else {
                            future.completeExceptionally(new Exception("Latest block alınamadı"));
                        }
                    })
                    .exceptionally(e -> {
                        future.completeExceptionally(e);
                        return null;
                    });
        } catch (Exception e) {
            future.completeExceptionally(e);
        }

        return future;
    }
}