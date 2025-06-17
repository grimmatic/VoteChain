package com.example.votechain.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.votechain.R;
import com.example.votechain.blockchain.BlockchainElectionManager;
import com.example.votechain.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminFragment extends Fragment {

    private RecyclerView recyclerView;
    private UserAdapter adapter;
    private List<User> userList;
    private FirebaseFirestore db;
    private ProgressBar progressBar;

    // Admin işlemleri için butonlar
    private Button btnSystemStatus;
    private Button btnCreateElection;
    private Button btnManageElections;
    private TextView tvSystemStatus;

    private BlockchainElectionManager electionManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin, container, false);

        recyclerView = view.findViewById(R.id.recyclerViewUsers);
        progressBar = view.findViewById(R.id.progressBar);

        // Admin işlemleri butonları
        btnSystemStatus = view.findViewById(R.id.btnBlockchainTest);
        btnCreateElection = view.findViewById(R.id.btnCreateElection);
        btnManageElections = view.findViewById(R.id.btnManageElections);

        // Sistem durumu için TextView ekle
        ViewGroup parentView = view.findViewById(R.id.cardAdminActions);
        tvSystemStatus = new TextView(getContext());
        tvSystemStatus.setTextSize(12);
        tvSystemStatus.setPadding(16, 8, 16, 8);
        tvSystemStatus.setText("Sistem durumu kontrol ediliyor...");
        ((ViewGroup) parentView.getChildAt(0)).addView(tvSystemStatus, 1);

        db = FirebaseFirestore.getInstance();
        electionManager = BlockchainElectionManager.getInstance();

        userList = new ArrayList<>();
        adapter = new UserAdapter(userList, new UserAdapter.OnUserActionListener() {
            @Override
            public void onUserDelete(User user) {
                deleteUser(user);
            }

            @Override
            public void onMakeAdmin(User user) {
                makeAdmin(user);
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        setupAdminButtons();
        checkSystemStatus();
        loadUsers();

        return view;
    }

    private void setupAdminButtons() {
        // Sistem Durumu butonu
        btnSystemStatus.setText("🔧 SİSTEM DURUMU");
        btnSystemStatus.setOnClickListener(v -> checkSystemStatus());

        // Seçim Oluştur butonu - Direkt AdminActivity'ye yönlendir
        btnCreateElection.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), AdminActivity.class);
            startActivity(intent);
        });

        // Seçimleri Yönet butonu
        btnManageElections.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), ManageElectionsActivity.class);
            startActivity(intent);
        });
    }

    private void checkSystemStatus() {
        progressBar.setVisibility(View.VISIBLE);
        tvSystemStatus.setText("🔄 Sistem durumu kontrol ediliyor...");

        // Blockchain sistemini başlat/kontrol et
        electionManager.initializeSystem(getContext())
                .thenAccept(success -> {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);

                            if (success) {
                                Map<String, String> systemInfo = electionManager.getSystemInfo();
                                showSystemInfo(systemInfo);
                                btnCreateElection.setEnabled(true);
                            } else {
                                tvSystemStatus.setText("❌ Blockchain sistemi başlatılamadı");
                                btnCreateElection.setEnabled(false);
                                Toast.makeText(getContext(),
                                        "Blockchain sistemi başlatılamadı. İnternet bağlantınızı kontrol edin.",
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                })
                .exceptionally(e -> {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            tvSystemStatus.setText("❌ Sistem hatası: " + e.getMessage());
                            btnCreateElection.setEnabled(false);
                        });
                    }
                    return null;
                });
    }

    private void showSystemInfo(Map<String, String> systemInfo) {
        String walletAddress = systemInfo.get("walletAddress");
        String contractAddress = systemInfo.get("contractAddress");
        String totalElections = systemInfo.get("totalElections");

        tvSystemStatus.setText("✅ VoteChain Sistemi Hazır!\n" +
                "🔐 Cüzdan: " + truncateAddress(walletAddress) + "\n" +
                "📜 Kontrat: " + truncateAddress(contractAddress) + "\n" +
                "📊 Toplam Seçim: " + totalElections);

        // Detaylı bilgi dialog'u göster
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("🏛️ VoteChain Sistem Durumu");
        builder.setMessage("✅ Blockchain Sistemi: Aktif\n\n" +
                "📋 Sistem Bilgileri:\n" +
                "🔐 Ethereum Cüzdan: " + walletAddress + "\n\n" +
                "📜 Akıllı Kontrat: " + contractAddress + "\n\n" +
                "📊 Toplam Seçim: " + totalElections + "\n\n" +
                "🚀 Sistem seçim oluşturmaya hazır!");
        builder.setPositiveButton("Tamam", null);
        builder.setNeutralButton("Seçim Oluştur", (dialog, which) -> {
            Intent intent = new Intent(getActivity(), AdminActivity.class);
            startActivity(intent);
        });
        builder.show();
    }

    private String truncateAddress(String address) {
        if (address != null && address.length() > 10) {
            return address.substring(0, 6) + "..." + address.substring(address.length() - 4);
        }
        return address != null ? address : "N/A";
    }

    private void loadUsers() {
        progressBar.setVisibility(View.VISIBLE);

        db.collection("users")
                .get()
                .addOnCompleteListener(task -> {
                    if (getContext() == null || getActivity() == null || isDetached()) {
                        return;
                    }

                    progressBar.setVisibility(View.GONE);

                    if (task.isSuccessful()) {
                        userList.clear();
                        for (DocumentSnapshot document : task.getResult()) {
                            User user = document.toObject(User.class);
                            if (user != null) {
                                userList.add(user);
                            }
                        }
                        adapter.notifyDataSetChanged();

                        if (userList.isEmpty()) {
                            Toast.makeText(getContext(),
                                    "Hiç kullanıcı bulunamadı",
                                    Toast.LENGTH_SHORT).show();
                        }
                    } else if (getContext() != null) {
                        Toast.makeText(getContext(),
                                "Kullanıcılar yüklenirken hata oluştu: " +
                                        (task.getException() != null ? task.getException().getMessage() : "Bilinmeyen hata"),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void deleteUser(User user) {
        if (getContext() == null) return;

        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        if (currentUserId.equals(user.getUserId())) {
            Toast.makeText(getContext(), "Kendinizi silemezsiniz!", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(getContext())
                .setTitle("⚠️ Kullanıcıyı Tamamen Sil")
                .setMessage(user.getAd() + " " + user.getSoyad() + " kullanıcısını tamamen silmek istediğinize emin misiniz?\n\n" +
                        "🔥 Bu işlem:\n" +
                        "• Firebase Authentication'dan\n" +
                        "• Firestore veritabanından\n" +
                        "• Tüm oy kayıtlarından\n" +
                        "kullanıcıyı kalıcı olarak kaldıracaktır.\n\n" +
                        "❌ Bu işlem GERİ ALINAMAZ!")
                .setPositiveButton("Evet, Tamamen Sil", (dialog, which) -> {
                    deleteUserWithCloudFunction(user);
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    private void deleteUserWithCloudFunction(User user) {
        progressBar.setVisibility(View.VISIBLE);

        // Cloud Function çağır
        FirebaseFunctions functions = FirebaseFunctions.getInstance();

        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getUserId());

        Log.d("AdminFragment", "Cloud Function çağrılıyor: deleteUser");
        Log.d("AdminFragment", "User ID: " + user.getUserId());

        functions.getHttpsCallable("deleteUser")
                .call(data)
                .addOnSuccessListener(result -> {
                    progressBar.setVisibility(View.GONE);

                    Log.d("AdminFragment", "Cloud Function başarılı: " + result.getData());

                    Map<String, Object> resultData = (Map<String, Object>) result.getData();
                    String message = (String) resultData.get("message");
                    Object deletedVotesObj = resultData.get("deletedVotes");

                    String successMessage = "✅ " + message;
                    if (deletedVotesObj != null) {

                        Number deletedVotesNumber = (Number) deletedVotesObj;
                        int deletedVotes = deletedVotesNumber.intValue();

                        if (deletedVotes > 0) {
                            successMessage += "\n🗳️ " + deletedVotes + " oy kaydı da silindi";
                        }
                    }

                    Toast.makeText(getContext(), successMessage, Toast.LENGTH_LONG).show();
                    loadUsers(); // Listeyi yenile
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);

                    String errorMessage = "❌ Kullanıcı silinemedi";
                    if (e instanceof FirebaseFunctionsException) {
                        FirebaseFunctionsException ffe = (FirebaseFunctionsException) e;
                        errorMessage += ":\n" + ffe.getMessage();

                        Log.e("AdminFragment", "Cloud Function hatası: " + ffe.getCode() + " - " + ffe.getMessage());
                    } else {
                        errorMessage += ":\n" + e.getMessage();
                    }

                    Log.e("AdminFragment", "Kullanıcı silme hatası", e);
                    Toast.makeText(getContext(), errorMessage, Toast.LENGTH_LONG).show();
                });
    }

    private void makeAdmin(User user) {
        if (getContext() == null) return;

        new AlertDialog.Builder(getContext())
                .setTitle("Yönetici Yap")
                .setMessage(user.getAd() + " " + user.getSoyad() + " kullanıcısını yönetici yapmak istediğinize emin misiniz?")
                .setPositiveButton("Evet", (dialog, which) -> {
                    progressBar.setVisibility(View.VISIBLE);

                    db.collection("users").document(user.getUserId())
                            .update("role", "admin")
                            .addOnSuccessListener(aVoid -> {
                                progressBar.setVisibility(View.GONE);
                                if (getContext() != null) {
                                    Toast.makeText(getContext(),
                                            "Kullanıcı başarıyla yönetici yapıldı",
                                            Toast.LENGTH_SHORT).show();
                                    loadUsers();
                                }
                            })
                            .addOnFailureListener(e -> {
                                progressBar.setVisibility(View.GONE);
                                if (getContext() != null) {
                                    Toast.makeText(getContext(),
                                            "İşlem başarısız: " + e.getMessage(),
                                            Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Fragment görünür olduğunda sistem durumunu kontrol et
        if (electionManager.isSystemReady()) {
            Map<String, String> systemInfo = electionManager.getSystemInfo();
            showSystemInfoBrief(systemInfo);
        }
    }

    private void showSystemInfoBrief(Map<String, String> systemInfo) {
        tvSystemStatus.setText("✅ Sistem Hazır | Cüzdan: " +
                truncateAddress(systemInfo.get("walletAddress")));
        btnCreateElection.setEnabled(true);
    }

    // UserAdapter sınıfı aynı kalacak...
    static class UserAdapter extends RecyclerView.Adapter<UserAdapter.ViewHolder> {
        private final List<User> userList;
        private final OnUserActionListener listener;

        interface OnUserActionListener {
            void onUserDelete(User user);
            void onMakeAdmin(User user);
        }

        UserAdapter(List<User> userList, OnUserActionListener listener) {
            this.userList = userList;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_user, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            User user = userList.get(position);
            holder.tvName.setText(user.getAd() + " " + user.getSoyad());
            holder.tvTcNo.setText("TC: " + user.getTcKimlikNo());
            holder.tvBirthYear.setText("Doğum Yılı: " + user.getDogumYili());

            if ("admin".equals(user.getRole())) {
                holder.btnMakeAdmin.setVisibility(View.GONE);
                holder.tvName.setText(user.getAd() + " " + user.getSoyad() + " (Yönetici)");
            } else {
                holder.btnMakeAdmin.setVisibility(View.VISIBLE);
            }

            holder.btnDelete.setOnClickListener(v -> listener.onUserDelete(user));
            holder.btnMakeAdmin.setOnClickListener(v -> listener.onMakeAdmin(user));
        }

        @Override
        public int getItemCount() {
            return userList.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvTcNo, tvBirthYear;
            Button btnDelete, btnMakeAdmin;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvName);
                tvTcNo = itemView.findViewById(R.id.tvTcNo);
                tvBirthYear = itemView.findViewById(R.id.tvBirthYear);
                btnDelete = itemView.findViewById(R.id.btnDelete);
                btnMakeAdmin = itemView.findViewById(R.id.btnMakeAdmin);
            }
        }
    }
}