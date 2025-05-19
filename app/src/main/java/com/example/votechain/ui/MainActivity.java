package com.example.votechain.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.votechain.R;
import com.example.votechain.model.User;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Firebase Auth kontrolü
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // Bottom Navigation View
        bottomNavigationView = findViewById(R.id.nav_bottom_view);
        bottomNavigationView.setOnNavigationItemSelectedListener(navListener);

        // Varsayılan fragment
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, new ElectionsFragment())
                    .commit();
        }

        bottomNavigationView.getMenu().findItem(R.id.nav_admin).setVisible(false);
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore.getInstance().collection("users").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    User user = documentSnapshot.toObject(User.class);
                    if (user != null&&"admin".equals(user.getRole())) {

                            bottomNavigationView.getMenu().findItem(R.id.nav_admin).setVisible(true);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(MainActivity.this,
                            "Kullanıcı bilgileri alınamadı: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private BottomNavigationView.OnNavigationItemSelectedListener navListener =
            new BottomNavigationView.OnNavigationItemSelectedListener() {
                @Override
                public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                    Fragment selectedFragment = null;

                    int itemId = item.getItemId();
                    if (itemId == R.id.nav_elections) {
                        selectedFragment = new ElectionsFragment();
                    } else if (itemId == R.id.nav_vote) {
                        selectedFragment = new VoteFragment();
                    } else if (itemId == R.id.nav_results) {
                        selectedFragment = new ResultsFragment();
                    } else if (itemId == R.id.nav_profile) {
                        selectedFragment = new ProfileFragment();
                    } else if (itemId == R.id.nav_admin) {
                        selectedFragment = new AdminFragment();
                    }

                    if (selectedFragment != null) {
                        getSupportFragmentManager().beginTransaction()
                                .replace(R.id.fragmentContainer, selectedFragment)
                                .commit();
                    }

                    return true;
                }
            };
}