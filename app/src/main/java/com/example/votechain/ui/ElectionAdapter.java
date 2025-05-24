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
            String status;
            int statusColor;

            if (election.getStartDate() != null && election.getStartDate().after(now)) {
                status = "🕒 Yakında Başlayacak";
                statusColor = R.color.colorAccent;
            } else if (election.getEndDate() != null && election.getEndDate().before(now)) {
                status = "⏰ Süresi Dolmuş";
                statusColor = R.color.red;
            } else {
                status = "🗳️ Oy Verilebilir";
                statusColor = R.color.green;
            }

            tvElectionStatus.setText(status);
            tvElectionStatus.setTextColor(itemView.getContext().getColor(statusColor));

            itemView.setOnClickListener(v -> {
                listener.onElectionClick(election);
            });
        }
    }
}