package com.example.votechain.ui;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.votechain.R;
import com.example.votechain.model.Candidate;

import java.util.List;

public class ResultsAdapter extends RecyclerView.Adapter<ResultsAdapter.ResultViewHolder> {

    private static final String TAG = "ResultsAdapter";
    private List<Candidate> candidateList;
    private int totalVotes = 0;

    public ResultsAdapter(List<Candidate> candidateList) {
        this.candidateList = candidateList;
        calculateTotalVotes();
    }

    private void calculateTotalVotes() {
        totalVotes = 0;
        for (Candidate candidate : candidateList) {
            totalVotes += candidate.getVoteCount();
        }
        Log.d(TAG, "📊 Toplam oy sayısı: " + totalVotes);
    }

    @NonNull
    @Override
    public ResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_result, parent, false);
        return new ResultViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ResultViewHolder holder, int position) {
        Candidate candidate = candidateList.get(position);

        holder.tvCandidateName.setText(candidate.getName());
        holder.tvParty.setText(candidate.getParty());
        holder.tvVoteCount.setText(String.valueOf(candidate.getVoteCount()));

        // Yüzde hesapla
        float percentage = 0f;
        if (totalVotes > 0) {
            percentage = ((float) candidate.getVoteCount() / (float) totalVotes) * 100f;
        }

        // Debug log
        Log.d(TAG, "👤 " + candidate.getName() +
                " | Oy: " + candidate.getVoteCount() +
                " | Toplam: " + totalVotes +
                " | Yüzde: " + percentage);

        // Yüzdeyi göster
        if (percentage == 0f && candidate.getVoteCount() == 0) {
            holder.tvPercentage.setText("0%");
        } else if (percentage < 0.1f && candidate.getVoteCount() > 0) {
            holder.tvPercentage.setText("<0.1%");
        } else {
            holder.tvPercentage.setText(String.format("%.1f%%", percentage));
        }

        // Progress bar'ı ayarla
        holder.progressBar.setProgress(Math.round(percentage));

        // Progress bar rengi (kazanan için farklı renk)
        if (candidate.getVoteCount() > 0 && isWinner(candidate)) {
            holder.progressBar.setProgressTintList(
                    holder.itemView.getContext().getColorStateList(R.color.green));
        } else {
            holder.progressBar.setProgressTintList(
                    holder.itemView.getContext().getColorStateList(R.color.colorPrimary));
        }
    }

    @Override
    public int getItemCount() {
        return candidateList.size();
    }

    /**
     * Listeyi günceller ve yüzdeleri yeniden hesaplar
     */
    public void updateCandidates(List<Candidate> newCandidateList) {
        this.candidateList = newCandidateList;
        calculateTotalVotes();
        notifyDataSetChanged();
    }

    /**
     * Adayın kazanan olup olmadığını kontrol eder
     */
    private boolean isWinner(Candidate candidate) {
        if (totalVotes == 0) return false;

        int maxVotes = 0;
        for (Candidate c : candidateList) {
            if (c.getVoteCount() > maxVotes) {
                maxVotes = c.getVoteCount();
            }
        }
        return candidate.getVoteCount() == maxVotes && maxVotes > 0;
    }

    public static class ResultViewHolder extends RecyclerView.ViewHolder {
        TextView tvCandidateName, tvParty, tvVoteCount, tvPercentage;
        ProgressBar progressBar;

        public ResultViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCandidateName = itemView.findViewById(R.id.tvCandidateName);
            tvParty = itemView.findViewById(R.id.tvParty);
            tvVoteCount = itemView.findViewById(R.id.tvVoteCount);
            tvPercentage = itemView.findViewById(R.id.tvPercentage);
            progressBar = itemView.findViewById(R.id.progressBar);
        }
    }
}