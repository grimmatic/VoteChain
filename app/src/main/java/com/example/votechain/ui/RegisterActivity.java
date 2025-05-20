package com.example.votechain.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.votechain.R;
import com.example.votechain.model.User;
import com.example.votechain.service.TCKimlikDogrulama;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText etTCKimlikNo, etAd, etSoyad, etDogumYili, etPassword;
    private Button btnRegister;
    private TextView tvGoToLogin;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private TCKimlikDogrulama tcDogrulama;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        tcDogrulama = new TCKimlikDogrulama();

        etTCKimlikNo = findViewById(R.id.etTCKimlikNo);
        etAd = findViewById(R.id.etAd);
        etSoyad = findViewById(R.id.etSoyad);
        etDogumYili = findViewById(R.id.etDogumYili);
        etPassword = findViewById(R.id.etPassword);
        btnRegister = findViewById(R.id.btnRegister);
        tvGoToLogin = findViewById(R.id.tvGoToLogin);

        btnRegister.setOnClickListener(view -> registerUser());
        setupGoToLoginClick();

    }

    private void registerUser() {
        final String tcKimlikNo = etTCKimlikNo.getText().toString().trim();
        final String ad = etAd.getText().toString().trim();
        final String soyad = etSoyad.getText().toString().trim();
        final String dogumYiliStr = etDogumYili.getText().toString().trim();
        final String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(tcKimlikNo) || TextUtils.isEmpty(ad) ||
                TextUtils.isEmpty(soyad) || TextUtils.isEmpty(dogumYiliStr) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Tüm alanları doldurunuz", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            etPassword.setError("Şifre en az 6 karakter olmalıdır");
            return;
        }

        final int dogumYili;
        try {
            dogumYili = Integer.parseInt(dogumYiliStr);
        } catch (NumberFormatException e) {
            etDogumYili.setError("Geçerli bir doğum yılı giriniz");
            return;
        }

        // ✅ 18 yaş kontrolü
        int currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        int age = currentYear - dogumYili;

        if (age < 18) {
            Toast.makeText(this, "Kayıt olmak için en az 18 yaşında olmalısınız", Toast.LENGTH_LONG).show();
            return;
        }

        // TC Kimlik doğrulama
        tcDogrulama.dogrula(tcKimlikNo, ad, soyad, dogumYili, new TCKimlikDogrulama.TCKimlikDogrulamaListener() {
            @Override
            public void onTCKimlikDogrulamaResult(boolean isValid) {
                if (isValid) {
                    createFirebaseUser(tcKimlikNo, ad, soyad, dogumYili, password);
                } else {
                    Toast.makeText(RegisterActivity.this, "TC Kimlik doğrulaması başarısız", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onTCKimlikDogrulamaError(String errorMessage) {
                Toast.makeText(RegisterActivity.this, "Doğrulama hatası: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }


    private void createFirebaseUser(final String tcKimlikNo, final String ad, final String soyad,
                                    final int dogumYili, String password) {
        String email = tcKimlikNo + "@votechain.com";

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String userId = mAuth.getCurrentUser().getUid();

                        Map<String, Object> userData = new HashMap<>();
                        userData.put("tcKimlikNo", tcKimlikNo);
                        userData.put("ad", ad);
                        userData.put("soyad", soyad);
                        userData.put("dogumYili", dogumYili);
                        userData.put("userId", userId);
                        userData.put("role", "user");

                        db.collection("users").document(userId)
                                .set(userData)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(RegisterActivity.this, "Kayıt başarılı", Toast.LENGTH_SHORT).show();
                                    startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                                    finish();
                                })
                                .addOnFailureListener(e -> Toast.makeText(RegisterActivity.this,
                                        "Kullanıcı bilgileri kaydedilemedi: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    } else {
                        Toast.makeText(RegisterActivity.this,
                                "Kayıt başarısız: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }


    private void setupGoToLoginClick() {
        tvGoToLogin.setOnClickListener(view -> {
            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        });
    }

}
