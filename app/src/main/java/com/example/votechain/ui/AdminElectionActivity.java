package com.example.votechain.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.votechain.R;
import com.example.votechain.model.Election;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Yeni seçim oluşturma activity'si - Sadece adminler erişebilir
 */
public class AdminElectionActivity extends AppCompatActivity {

    private EditText etElectionName, etElectionDescription;
    private TextView tvStartDate, tvEndDate;
    private Button btnSelectStartDate, btnSelectEndDate, btnCreateElection;
    private ProgressBar progressBar;

    private FirebaseFirestore db;

    private Date startDate, endDate;
    private SimpleDateFormat dateFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_election);

        initViews();
        initFirebase();
        setupDateFormat();
        setupClickListeners();
    }

    private void initViews() {
        etElectionName = findViewById(R.id.etElectionName);
        etElectionDescription = findViewById(R.id.etElectionDescription);
        tvStartDate = findViewById(R.id.tvStartDate);
        tvEndDate = findViewById(R.id.tvEndDate);
        btnSelectStartDate = findViewById(R.id.btnSelectStartDate);
        btnSelectEndDate = findViewById(R.id.btnSelectEndDate);
        btnCreateElection = findViewById(R.id.btnCreateElection);
        progressBar = findViewById(R.id.progressBar);
    }

    private void initFirebase() {
        db = FirebaseFirestore.getInstance();
    }

    private void setupDateFormat() {
        dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
    }

    private void setupClickListeners() {
        btnSelectStartDate.setOnClickListener(v -> selectDate(true));
        btnSelectEndDate.setOnClickListener(v -> selectDate(false));
        btnCreateElection.setOnClickListener(v -> createElection());
    }

    private void selectDate(boolean isStartDate) {
        Calendar calendar = Calendar.getInstance();

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar selectedDate = Calendar.getInstance();
                    selectedDate.set(year, month, dayOfMonth);

                    // Saat seçimi
                    TimePickerDialog timePickerDialog = new TimePickerDialog(
                            this,
                            (timeView, hourOfDay, minute) -> {
                                selectedDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                selectedDate.set(Calendar.MINUTE, minute);

                                if (isStartDate) {
                                    startDate = selectedDate.getTime();
                                    tvStartDate.setText("Başlangıç: " + dateFormat.format(startDate));
                                } else {
                                    endDate = selectedDate.getTime();
                                    tvEndDate.setText("Bitiş: " + dateFormat.format(endDate));
                                }
                            },
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE),
                            true
                    );
                    timePickerDialog.show();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        datePickerDialog.show();
    }

    private void createElection() {
        String name = etElectionName.getText().toString().trim();
        String description = etElectionDescription.getText().toString().trim();

        // Validasyon
        if (name.isEmpty() || description.isEmpty()) {
            Toast.makeText(this, "Lütfen tüm alanları doldurun", Toast.LENGTH_SHORT).show();
            return;
        }

        if (startDate == null || endDate == null) {
            Toast.makeText(this, "Lütfen başlangıç ve bitiş tarihlerini seçin", Toast.LENGTH_SHORT).show();
            return;
        }

        if (endDate.before(startDate)) {
            Toast.makeText(this, "Bitiş tarihi başlangıç tarihinden sonra olmalıdır", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        // Firebase'e kaydet
        createElectionInFirebase(name, description);
    }

    private void createElectionInFirebase(String name, String description) {
        Election election = new Election(name, description, startDate, endDate, true);

        db.collection("elections")
                .add(election)
                .addOnSuccessListener(documentReference -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Seçim başarıyla oluşturuldu!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Seçim oluşturma hatası: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}