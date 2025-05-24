package com.example.votechain;

import android.app.Application;
import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * Uygulama başlatılırken BouncyCastle provider'ını ekler
 * Bu blockchain crypto işlemleri için gereklidir
 */
public class VoteChainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // BouncyCastle crypto provider'ını ekle
        // Bu Web3j'nin Ethereum cüzdanları oluşturabilmesi için gerekli
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
}