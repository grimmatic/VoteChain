package com.example.votechain;

import android.app.Application;
import android.util.Log;
import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class VoteChainApplication extends Application {
    private static final String TAG = "VoteChainApplication";

    @Override
    public void onCreate() {
        super.onCreate();

        // BouncyCastle provider'ını ekle
        setupBouncyCastle();
    }

    private void setupBouncyCastle() {
        try {
            // Mevcut provider'ı kaldır (varsa)
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);

            // Yeni provider'ı en başa ekle
            Security.insertProviderAt(new BouncyCastleProvider(), 1);

            Log.d(TAG, "BouncyCastle provider başarıyla eklendi");

            // Test et
            String[] providers = Security.getProviders()[0].getServices().stream()
                    .filter(s -> s.getAlgorithm().contains("ECDSA"))
                    .map(s -> s.getAlgorithm())
                    .toArray(String[]::new);

            Log.d(TAG, "ECDSA algoritmaları mevcut: " + providers.length);

        } catch (Exception e) {
            Log.e(TAG, "BouncyCastle provider eklenirken hata: " + e.getMessage(), e);
        }
    }
}