package com.example.votechain.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import com.example.votechain.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_TIMEOUT = 2000; // 2 saniye
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Tam ekran
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Firebase Auth başlat
        mAuth = FirebaseAuth.getInstance();

        // Splash süresi dolduktan sonra yönlendirme yap
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                checkUserSession();
            }
        }, SPLASH_TIMEOUT);
    }

    private void checkUserSession() {
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser != null) {
            // Kullanıcı giriş yapmış, ana ekrana yönlendir
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
        } else {
            // Kullanıcı giriş yapmamış, giriş ekranına yönlendir
            startActivity(new Intent(SplashActivity.this, RegisterActivity.class));
        }
        finish();
    }
}