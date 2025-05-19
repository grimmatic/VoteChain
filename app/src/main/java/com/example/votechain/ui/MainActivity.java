package com.example.votechain.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.votechain.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Firebase Auth kontrolü
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            // Kullanıcı giriş yapmamış, giriş ekranına yönlendir
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // Bottom Navigation View
        bottomNavigationView = findViewById(R.id.bottomNavigationView);
        bottomNavigationView.setOnNavigationItemSelectedListener(navListener);

        // Varsayılan fragment
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, new ElectionsFragment())
                    .commit();
        }
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