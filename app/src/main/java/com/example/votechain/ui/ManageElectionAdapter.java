package com.example.votechain.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.votechain.R;
import com.example.votechain.model.Election;
import com.example.votechain.util.Utils;

import java.util.List;

/**
 * Admin seçim yönetimi için adapter
 */
public class ManageElectionAdapter extends RecyclerView.Adapter<ManageElectionAdapter.ViewHolder> {

    private final List<Election> electionList;
    private final OnElectionActionListener listener;

    public interface OnElectionActionListener {
        void onElectionEdit(Election election);
        void onElectionDelete(Election election);
        void onElectionToggle(Election election);
    }

    public ManageElectionAdapter(List<Election> electionList, OnElectionActionListener listener) {
        this.electionList = electionList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_manage_election, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Election election = electionList.get(position);

        holder.tvElectionName.setText(election.getName());
        holder.tvElectionDescription.setText(election.getDescription());

        String dateRange = Utils.formatDate(election.getStartDate()) + " - " +
                Utils.formatDate(election.getEndDate());
        holder.tvElectionDate.setText(dateRange);

        // Durum göstergesi
        if (election.isActive()) {
            holder.tvStatus.setText("Aktif");
            holder.tvStatus.setTextColor(holder.itemView.getContext().getColor(R.color.green));
            holder.btnToggleStatus.setText("Pasif Et");
        } else {
            holder.tvStatus.setText("Pasif");
            holder.tvStatus.setTextColor(holder.itemView.getContext().getColor(R.color.red));
            holder.btnToggleStatus.setText("Aktif Et");
        }

        // Click listeners
        holder.btnEdit.setOnClickListener(v -> listener.onElectionEdit(election));
        holder.btnDelete.setOnClickListener(v -> listener.onElectionDelete(election));
        holder.btnToggleStatus.setOnClickListener(v -> listener.onElectionToggle(election));
    }

    @Override
    public int getItemCount() {
        return electionList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvElectionName, tvElectionDescription, tvElectionDate, tvStatus;
        Button btnEdit, btnDelete, btnToggleStatus;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvElectionName = itemView.findViewById(R.id.tvElectionName);
            tvElectionDescription = itemView.findViewById(R.id.tvElectionDescription);
            tvElectionDate = itemView.findViewById(R.id.tvElectionDate);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
            btnToggleStatus = itemView.findViewById(R.id.btnToggleStatus);
        }
    }
}