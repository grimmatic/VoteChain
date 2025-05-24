package com.example.votechain.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.votechain.R;
import com.example.votechain.model.Election;
import com.example.votechain.util.Utils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;
import java.util.List;

public class ElectionAdapter extends RecyclerView.Adapter<ElectionAdapter.ElectionViewHolder> {

    private List<Election> electionList;
    private OnElectionClickListener listener;

    public interface OnElectionClickListener {
        void onElectionClick(Election election);
    }

    public ElectionAdapter(List<Election> electionList, OnElectionClickListener listener) {
        this.electionList = electionList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ElectionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_election_combined, parent, false);
        return new ElectionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ElectionViewHolder holder, int position) {
        Election election = electionList.get(position);
        holder.bind(election, listener);
    }

    @Override
    public int getItemCount() {
        return electionList.size();
    }

    public static class ElectionViewHolder extends RecyclerView.ViewHolder {
        private TextView tvElectionName, tvElectionDescription, tvElectionDate, tvElectionStatus;

        public ElectionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvElectionName = itemView.findViewById(R.id.tvElectionName);
            tvElectionDescription = itemView.findViewById(R.id.tvElectionDescription);
            tvElectionDate = itemView.findViewById(R.id.tvElectionDate);
            tvElectionStatus = itemView.findViewById(R.id.tvElectionStatus);
        }

        public void bind(final Election election, final OnElectionClickListener listener) {
            tvElectionName.setText(election.getName());
            tvElectionDescription.setText(election.getDescription());

            String dateRange = Utils.formatDate(election.getStartDate()) + " - " + Utils.formatDate(election.getEndDate());
            tvElectionDate.setText(dateRange);

            // Seçim durumunu belirle
            Date now = new Date();
            String defaultStatus;
            int defaultStatusColor;

            if (election.getStartDate() != null && election.getStartDate().after(now)) {
                defaultStatus = "🕒 Yakında Başlayacak";
                defaultStatusColor = R.color.colorAccent;

                tvElectionStatus.setText(defaultStatus);
                tvElectionStatus.setTextColor(itemView.getContext().getColor(defaultStatusColor));
            } else if (election.getEndDate() != null && election.getEndDate().before(now)) {
                defaultStatus = "⏰ Süresi Dolmuş";
                defaultStatusColor = R.color.red;

                tvElectionStatus.setText(defaultStatus);
                tvElectionStatus.setTextColor(itemView.getContext().getColor(defaultStatusColor));
            } else {
                // Kullanıcının oy verip vermediğini kontrol et
                checkUserVoteStatus(election.getId(), result -> {
                    tvElectionStatus.setText(result.text);
                    tvElectionStatus.setTextColor(itemView.getContext().getColor(result.color));
                });
            }

            itemView.setOnClickListener(v -> {
                listener.onElectionClick(election);
            });
        }

        private void checkUserVoteStatus(String electionId, VoteStatusCallback callback) {
            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

            FirebaseFirestore.getInstance().collection("votes")
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("electionId", electionId)
                    .get()
                    .addOnCompleteListener(task -> {
                        VoteStatusResult result;
                        if (task.isSuccessful() && !task.getResult().isEmpty()) {
                            // Oy kullanılmış
                            result = new VoteStatusResult("✅ Oy Kullandınız", R.color.blue);
                        } else {
                            // Oy kullanılmamış
                            result = new VoteStatusResult("🗳️ Oy Verilebilir", R.color.green);
                        }
                        callback.onResult(result);
                    })
                    .addOnFailureListener(e -> {
                        // Hata durumunda varsayılan
                        VoteStatusResult result = new VoteStatusResult("🗳️ Oy Verilebilir", R.color.green);
                        callback.onResult(result);
                    });
        }

        interface VoteStatusCallback {
            void onResult(VoteStatusResult result);
        }

        static class VoteStatusResult {
            String text;
            int color;

            VoteStatusResult(String text, int color) {
                this.text = text;
                this.color = color;
            }
        }
    }
}