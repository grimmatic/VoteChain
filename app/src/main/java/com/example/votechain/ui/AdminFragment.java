package com.example.votechain.ui;

import android.app.AlertDialog;
import android.os.Bundle;
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
import com.example.votechain.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class AdminFragment extends Fragment {

    private RecyclerView recyclerView;
    private UserAdapter adapter;
    private List<User> userList;
    private FirebaseFirestore db;
    private ProgressBar progressBar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin, container, false);

        recyclerView = view.findViewById(R.id.recyclerViewUsers);
        progressBar = view.findViewById(R.id.progressBar);

        db = FirebaseFirestore.getInstance();
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

        loadUsers();

        return view;
    }

    private void loadUsers() {
        progressBar.setVisibility(View.VISIBLE);

        db.collection("users")
                .get()  // Tüm kullanıcıları almak için herhangi bir filtreleme yapma
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);

                    if (task.isSuccessful() && getContext() != null) {
                        userList.clear();
                        for (DocumentSnapshot document : task.getResult()) {
                            User user = document.toObject(User.class);
                            if (user != null) {
                                userList.add(user);
                            }
                        }
                        adapter.notifyDataSetChanged();

                        // Hiç kullanıcı yoksa bunu göster
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

        // Mevcut kullanıcı ID'si
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // Kendini silememe kontrolü
        if (currentUserId.equals(user.getUserId())) {
            Toast.makeText(getContext(), "Kendinizi silemezsiniz!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Kullanıcı silme onayı al
        new AlertDialog.Builder(getContext())
                .setTitle("Kullanıcıyı Sil")
                .setMessage(user.getAd() + " " + user.getSoyad() + " kullanıcısını silmek istediğinize emin misiniz?")
                .setPositiveButton("Evet", (dialog, which) -> {
                    progressBar.setVisibility(View.VISIBLE);

                    // 1. Firestore'dan kullanıcı bilgilerini sil
                    db.collection("users").document(user.getUserId())
                            .delete()
                            .addOnSuccessListener(aVoid -> {
                                progressBar.setVisibility(View.GONE);
                                Toast.makeText(getContext(),
                                        "Kullanıcı başarıyla silindi",
                                        Toast.LENGTH_SHORT).show();
                                loadUsers(); // Listeyi yenile
                            })
                            .addOnFailureListener(e -> {
                                progressBar.setVisibility(View.GONE);
                                if (getContext() != null) {
                                    Toast.makeText(getContext(),
                                            "Kullanıcı silinemedi: " + e.getMessage(),
                                            Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .setNegativeButton("İptal", null)
                .show();
    }
    private void makeAdmin(User user) {
        if (getContext() == null) return;

        new AlertDialog.Builder(getContext())
                .setTitle("Yönetici Yap")
                .setMessage(user.getAd() + " " + user.getSoyad() + " kullanıcısını yönetici yapmak istediğinize emin misiniz?")
                .setPositiveButton("Evet", (dialog, which) -> {
                    progressBar.setVisibility(View.VISIBLE);

                    // Kullanıcının rolünü "admin" olarak güncelle
                    db.collection("users").document(user.getUserId())
                            .update("role", "admin")
                            .addOnSuccessListener(aVoid -> {
                                progressBar.setVisibility(View.GONE);
                                if (getContext() != null) {
                                    Toast.makeText(getContext(),
                                            "Kullanıcı başarıyla yönetici yapıldı",
                                            Toast.LENGTH_SHORT).show();
                                    loadUsers(); // Listeyi yenile
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
    // Kullanıcı Adapter Sınıfı
    static class UserAdapter extends RecyclerView.Adapter<UserAdapter.ViewHolder> {
        private List<User> userList;
        private OnUserActionListener listener;

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

            // Kullanıcı zaten admin ise butonu gizle
            if ("admin".equals(user.getRole())) {
                holder.btnMakeAdmin.setVisibility(View.GONE);
                holder.tvName.setText(user.getAd() + " " + user.getSoyad() + " (Yönetici)");
            } else {
                holder.btnMakeAdmin.setVisibility(View.VISIBLE);
            }

            holder.btnDelete.setOnClickListener(v -> {
                listener.onUserDelete(user);
            });

            holder.btnMakeAdmin.setOnClickListener(v -> {
                listener.onMakeAdmin(user);
            });
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
