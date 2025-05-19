package com.example.votechain.ui;

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
        float percentage = totalVotes > 0 ?
                (float) candidate.getVoteCount() / totalVotes * 100 : 0;
        holder.tvPercentage.setText(String.format("%.1f%%", percentage));

        // Progress bar'ı ayarla
        holder.progressBar.setProgress((int) percentage);
    }

    @Override
    public int getItemCount() {
        return candidateList.size();
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