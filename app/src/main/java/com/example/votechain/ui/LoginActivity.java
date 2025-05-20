package com.example.votechain.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.votechain.R;
import com.example.votechain.model.User;
import com.example.votechain.service.TCKimlikDogrulama;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.ActionCodeSettings;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private EditText etTCKimlikNo, etAd, etSoyad, etDogumYili, etPassword;
    private Button btnLogin, btnRegister;

    private TextView tvGoToRegister;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private TCKimlikDogrulama tcDogrulama;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Firebase başlat
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // TC Kimlik doğrulama servisi ve validatörü
        //tcDogrulama = new TCKimlikDogrulama();

        // UI bileşenlerini bağla
        etTCKimlikNo = findViewById(R.id.etTCKimlikNo);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvGoToRegister = findViewById(R.id.tvGoToRegister);
        progressBar = findViewById(R.id.progressBar);

        // Giriş butonuna tıklama
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loginUser();
            }
        });

        setupGoToRegisterClick();


    }

    private void loginUser() {
        String tcKimlikNo = etTCKimlikNo.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // Alanları kontrol et
        if (TextUtils.isEmpty(tcKimlikNo)) {
            etTCKimlikNo.setError("TC Kimlik No gereklidir");
            return;
        }

        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Şifre gereklidir");
            return;
        }


        // Yükleniyor göster
        progressBar.setVisibility(View.VISIBLE);

        // Firebase ile giriş yap
        String email = tcKimlikNo + "@votechain.com";
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        progressBar.setVisibility(View.GONE);

                        if (task.isSuccessful()) {
                            // Giriş başarılı
                            Toast.makeText(LoginActivity.this, "Giriş başarılı", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            finish();
                        } else {
                            // Giriş başarısız
                            Toast.makeText(LoginActivity.this, "Giriş başarısız: " + task.getException().getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void setupGoToRegisterClick() {
        tvGoToRegister.setOnClickListener(view -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
            finish();
        });
    }




}