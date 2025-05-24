package com.example.votechain.blockchain;

import android.content.Context;
import android.util.Log;

import com.example.votechain.model.Candidate;
import com.example.votechain.model.Election;

import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.DefaultGasProvider;

import java.io.File;
import java.math.BigInteger;
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

    // Ethereum ağı için Infura URL (buraya kendi API anahtarınızı ekleyin)
    private static final String INFURA_URL = "https://sepolia.infura.io/v3/0530f4db5185496891f0ca7c39b8092c";

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
            Log.d(TAG, "Wallet initialization started...");

            // Cüzdan dizinini oluştur
            File walletDir = new File(context.getFilesDir(), "ethereum");
            if (!walletDir.exists()) {
                boolean created = walletDir.mkdirs();
                Log.d(TAG, "Wallet directory created: " + created);
            }

            File[] walletFiles = walletDir.listFiles();
            if (walletFiles != null && walletFiles.length > 0) {
                // Mevcut cüzdanı yükle
                Log.d(TAG, "Loading existing wallet...");
                credentials = WalletUtils.loadCredentials(password, walletFiles[0].getAbsolutePath());
                Log.d(TAG, "Existing wallet loaded successfully");
            } else {
                // Yeni cüzdan oluştur
                Log.d(TAG, "Creating new wallet...");

                // BouncyCastle provider kontrolü
                if (java.security.Security.getProvider("BC") == null) {
                    Log.e(TAG, "BouncyCastle provider not found!");
                    return false;
                }

                String walletFileName = WalletUtils.generateLightNewWalletFile(password, walletDir);
                credentials = WalletUtils.loadCredentials(password, new File(walletDir, walletFileName).getAbsolutePath());
                Log.d(TAG, "New wallet created successfully");
            }

            Log.d(TAG, "Wallet initialized: " + credentials.getAddress());

            // Kontrat bağlantısını başlat
            initializeContract();
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error initializing wallet: " + e.getMessage(), e);

            // Detaylı hata bilgisi
            if (e instanceof java.security.NoSuchAlgorithmException) {
                Log.e(TAG, "Crypto algorithm not found. Check BouncyCastle provider.");
            } else if (e instanceof java.security.NoSuchProviderException) {
                Log.e(TAG, "Crypto provider not found. Check BouncyCastle installation.");
            }

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
            // Unix timestamp'e dönüştür
            BigInteger startTime = BigInteger.valueOf(election.getStartDate().getTime() / 1000);
            BigInteger endTime = BigInteger.valueOf(election.getEndDate().getTime() / 1000);

            // Asenkron kontrat çağrısı
            votingContract.createElection(
                            election.getName(),
                            election.getDescription(),
                            startTime,
                            endTime
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

    /**
     * Oy kullanır
     * @param electionId Seçim ID'si
     * @param candidateId Aday ID'si
     * @param tcKimlikNo TC Kimlik Numarası
     * @return İşlem hash'i
     */
    public CompletableFuture<String> vote(BigInteger electionId, BigInteger candidateId, String tcKimlikNo) {
        CompletableFuture<String> future = new CompletableFuture<>();

        try {
            // TC Kimlik numarasını hash'le (güvenlik için)
            String tcIdHash = Hash.sha3String(tcKimlikNo);

            votingContract.vote(
                            electionId,
                            candidateId,
                            tcIdHash
                    ).sendAsync()
                    .thenAccept(receipt -> {
                        String txHash = receipt.getTransactionHash();
                        Log.d(TAG, "Vote cast: " + txHash);
                        future.complete(txHash);
                    })
                    .exceptionally(e -> {
                        Log.e(TAG, "Error casting vote", e);
                        future.completeExceptionally(e);
                        return null;
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error casting vote", e);
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * Seçim sonuçlarını getirir
     * @param electionId Seçim ID'si
     * @return Aday listesi
     */
    public CompletableFuture<List<Candidate>> getElectionResults(BigInteger electionId) {
        CompletableFuture<List<Candidate>> future = new CompletableFuture<>();

        try {
            votingContract.getElectionResults(electionId).sendAsync()
                    .thenAccept(result -> {
                        try {
                            List<Candidate> candidates = new ArrayList<>();

                            // Sonuçları doğru şekilde parse et
                            @SuppressWarnings("unchecked")
                            List<BigInteger> ids = (List<BigInteger>) result.get(0).getValue();
                            @SuppressWarnings("unchecked")
                            List<String> names = (List<String>) result.get(1).getValue();
                            @SuppressWarnings("unchecked")
                            List<String> parties = (List<String>) result.get(2).getValue();
                            @SuppressWarnings("unchecked")
                            List<BigInteger> voteCounts = (List<BigInteger>) result.get(3).getValue();

                            for (int i = 0; i < ids.size(); i++) {
                                Candidate candidate = new Candidate(
                                        electionId.toString(),
                                        names.get(i),
                                        parties.get(i)
                                );
                                candidate.setId(ids.get(i).toString());
                                candidate.setVoteCount(voteCounts.get(i).intValue());
                                candidates.add(candidate);
                            }

                            future.complete(candidates);
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing election results", e);
                            future.completeExceptionally(e);
                        }
                    })
                    .exceptionally(e -> {
                        Log.e(TAG, "Error getting election results", e);
                        future.completeExceptionally(e);
                        return null;
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error getting election results", e);
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