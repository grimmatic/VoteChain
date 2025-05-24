package com.example.votechain.blockchain;

import android.content.Context;
import android.util.Log;

import com.example.votechain.model.Candidate;
import com.example.votechain.model.Election;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.DefaultGasProvider;

import java.io.File;
import java.math.BigInteger;
import java.security.Security;
import java.util.ArrayList;
import java.util.Date;
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
            Log.d(TAG, "Using existing wallet with ETH...");

            // Yeni cüzdan oluşturmak yerine, mevcut cüzdanınızı kullanın
            credentials = Credentials.create(PRIVATE_KEY);

            Log.d(TAG, "Wallet loaded: " + credentials.getAddress());
            checkWalletBalance();
            // Kontrat bağlantısını başlat
            initializeContract();
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error loading wallet: " + e.getMessage(), e);
            return false;
        }
    }
    /**
     * Akıllı kontrat bağlantısını başlatır
     */
    private void initializeContract() {
        try {
            ContractGasProvider gasProvider = new DefaultGasProvider();


            contractAddress = "0x9588945a6185b61deb9204c59ccafd12098fdbfa";


            votingContract = VotingContract.load(
                    contractAddress,
                    web3j,
                    credentials,
                    gasProvider
            );

            Log.d(TAG, "Contract initialized at address: " + contractAddress);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing contract", e);
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

            // DEBUG LOG
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

            // TC Kimlik numarasını hash'le (güvenlik için)
            String tcIdHash = Hash.sha3String(tcKimlikNo);
            Log.d(TAG, "🔐 TC Hash: " + tcIdHash);

            // Kontrat adresini kontrol et
            if (contractAddress == null || contractAddress.isEmpty()) {
                Log.e(TAG, "❌ KONTRAT ADRESİ YOK!");
                future.completeExceptionally(new Exception("Kontrat adresi bulunamadı"));
                return future;
            }

            Log.d(TAG, "📜 Kontrat Adresi: " + contractAddress);
            Log.d(TAG, "🔑 Cüzdan Adresi: " + (credentials != null ? credentials.getAddress() : "NULL"));

            // Gas limit ve gas price'ı artır
            ContractGasProvider gasProvider = new DefaultGasProvider() {
                @Override
                public BigInteger getGasLimit(String contractFunc) {
                    return BigInteger.valueOf(300000); // 300k gas limit
                }

                @Override
                public BigInteger getGasPrice(String contractFunc) {
                    return BigInteger.valueOf(20000000000L); // 20 Gwei
                }
            };

            if (votingContract == null) {
                Log.e(TAG, "❌ VOTING CONTRACT NULL!");
                future.completeExceptionally(new Exception("Voting contract başlatılamadı"));
                return future;
            }

            Log.d(TAG, "⚡ Gas Limit: 300000, Gas Price: 20 Gwei");
            Log.d(TAG, "🚀 Blockchain işlemi gönderiliyor...");

            votingContract.vote(electionId, candidateId, tcIdHash)
                    .sendAsync()
                    .thenAccept(receipt -> {
                        String txHash = receipt.getTransactionHash();
                        Log.d(TAG, "✅ BLOCKCHAIN İŞLEMİ BAŞARILI!");
                        Log.d(TAG, "🔗 Transaction Hash: " + txHash);
                        Log.d(TAG, "⛽ Gas Used: " + receipt.getGasUsed());
                        Log.d(TAG, "📋 Block Number: " + receipt.getBlockNumber());

                        future.complete(txHash);
                    })
                    .exceptionally(e -> {
                        Log.e(TAG, "❌ BLOCKCHAIN İŞLEMİ BAŞARISIZ!");
                        Log.e(TAG, "🚨 Hata Detayı: " + e.getMessage());
                        Log.e(TAG, "🔍 Hata Tipi: " + e.getClass().getSimpleName());

                        if (e.getCause() != null) {
                            Log.e(TAG, "🔍 Alt Hata: " + e.getCause().getMessage());
                        }

                        future.completeExceptionally(e);
                        return null;
                    });
        } catch (Exception e) {
            Log.e(TAG, "❌ OY VERME HAZIRLIK HATASI!");
            Log.e(TAG, "🚨 Exception: " + e.getMessage(), e);
            future.completeExceptionally(e);
        }

        return future;
    }
    /**
     * TC Kimlik doğrulaması için geçerli bir TC Kimlik numarası ekler
     * @param tcKimlikNo TC Kimlik Numarası
     * @return İşlem hash'i
     */
    public CompletableFuture<String> addValidTCId(String tcKimlikNo) {
        CompletableFuture<String> future = new CompletableFuture<>();

        try {
            // TC Kimlik numarasını hash'le (güvenlik için)
            String tcIdHash = Hash.sha3String(tcKimlikNo);

            votingContract.addValidTCId(tcIdHash).sendAsync()
                    .thenAccept(receipt -> {
                        String txHash = receipt.getTransactionHash();
                        Log.d(TAG, "TC ID added: " + txHash);
                        future.complete(txHash);
                    })
                    .exceptionally(e -> {
                        Log.e(TAG, "Error adding TC ID", e);
                        future.completeExceptionally(e);
                        return null;
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error adding TC ID", e);
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
}