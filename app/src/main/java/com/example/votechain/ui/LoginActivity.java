package com.example.votechain.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.votechain.R;
import com.example.votechain.model.User;
import com.example.votechain.service.TCKimlikDogrulama;
import com.example.votechain.util.TCKimlikValidator;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private EditText etTCKimlikNo, etAd, etSoyad, etDogumYili, etPassword;
    private Button btnLogin, btnRegister;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private TCKimlikValidator tcValidator;
    private TCKimlikDogrulama tcDogrulama;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Firebase başlat
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // TC Kimlik doğrulama servisi ve validatörü
        tcDogrulama = new TCKimlikDogrulama();
        tcValidator = new TCKimlikValidator();

        // UI bileşenlerini bağla
        etTCKimlikNo = findViewById(R.id.etTCKimlikNo);
        etAd = findViewById(R.id.etAd);
        etSoyad = findViewById(R.id.etSoyad);
        etDogumYili = findViewById(R.id.etDogumYili);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);
        progressBar = findViewById(R.id.progressBar);

        // Giriş butonuna tıklama
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loginUser();
            }
        });

        // Kayıt butonuna tıklama
        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                registerUser();
            }
        });
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

        // TC Kimlik No formatını kontrol et
        if (!tcValidator.isValid(tcKimlikNo)) {
            etTCKimlikNo.setError("Geçersiz TC Kimlik No");
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

    private void registerUser() {
        final String tcKimlikNo = etTCKimlikNo.getText().toString().trim();
        final String ad = etAd.getText().toString().trim();
        final String soyad = etSoyad.getText().toString().trim();
        final String dogumYiliStr = etDogumYili.getText().toString().trim();
        final String password = etPassword.getText().toString().trim();

        // Alanları kontrol et
        if (TextUtils.isEmpty(tcKimlikNo) || TextUtils.isEmpty(ad) ||
                TextUtils.isEmpty(soyad) || TextUtils.isEmpty(dogumYiliStr) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Tüm alanları doldurunuz", Toast.LENGTH_SHORT).show();
            return;
        }

        // TC Kimlik No formatını kontrol et
        if (!tcValidator.isValid(tcKimlikNo)) {
            etTCKimlikNo.setError("Geçersiz TC Kimlik No");
            return;
        }

        // Şifre uzunluğunu kontrol et
        if (password.length() < 6) {
            etPassword.setError("Şifre en az 6 karakter olmalıdır");
            return;
        }

        // Doğum yılını int'e çevir
        final int dogumYili;
        try {
            dogumYili = Integer.parseInt(dogumYiliStr);
        } catch (NumberFormatException e) {
            etDogumYili.setError("Geçerli bir doğum yılı giriniz");
            return;
        }

        // Yükleniyor göster
        progressBar.setVisibility(View.VISIBLE);

        // TC Kimlik doğrulama
        tcDogrulama.dogrula(tcKimlikNo, ad, soyad, dogumYili, new TCKimlikDogrulama.TCKimlikDogrulamaListener() {
            @Override
            public void onTCKimlikDogrulamaResult(boolean isValid) {
                if (isValid) {
                    // TC Kimlik doğrulandı, Firebase'e kaydet
                    createFirebaseUser(tcKimlikNo, ad, soyad, dogumYili, password);
                } else {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(LoginActivity.this, "TC Kimlik doğrulaması başarısız", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onTCKimlikDogrulamaError(String errorMessage) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(LoginActivity.this, "Doğrulama hatası: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void createFirebaseUser(final String tcKimlikNo, final String ad, final String soyad,
                                    final int dogumYili, String password) {
        String email = tcKimlikNo + "@votechain.com";

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Kullanıcı başarıyla oluşturuldu, Firestore'a bilgileri kaydet
                            String userId = mAuth.getCurrentUser().getUid();
                            User user = new User(tcKimlikNo, ad, soyad, dogumYili);
                            user.setUserId(userId);

                            db.collection("users").document(userId)
                                    .set(user)
                                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                                        @Override
                                        public void onComplete(@NonNull Task<Void> task) {
                                            progressBar.setVisibility(View.GONE);

                                            if (task.isSuccessful()) {
                                                Toast.makeText(LoginActivity.this, "Kayıt başarılı", Toast.LENGTH_SHORT).show();
                                                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                                                finish();
                                            } else {
                                                Toast.makeText(LoginActivity.this, "Kullanıcı bilgileri kaydedilemedi: "
                                                        + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                                            }
                                        }
                                    });
                        } else {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(LoginActivity.this, "Kayıt başarısız: " + task.getException().getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }
}