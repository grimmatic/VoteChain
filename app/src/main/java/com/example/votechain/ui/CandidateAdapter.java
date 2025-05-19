package com.example.votechain.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.votechain.R;
import com.example.votechain.model.Candidate;

import java.util.List;

public class CandidateAdapter extends RecyclerView.Adapter<CandidateAdapter.CandidateViewHolder> {

    private List<Candidate> candidateList;
    private OnCandidateClickListener listener;
    private int selectedPosition = -1;

    public interface OnCandidateClickListener {
        void onCandidateClick(Candidate candidate);
    }

    public CandidateAdapter(List<Candidate> candidateList, OnCandidateClickListener listener) {
        this.candidateList = candidateList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CandidateViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_candidate, parent, false);
        return new CandidateViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CandidateViewHolder holder, int position) {
        // 'position' parametresini doğrudan kullanmak yerine final yapın
        final int adapterPosition = holder.getAdapterPosition();
        if (adapterPosition == RecyclerView.NO_POSITION) return;

        Candidate candidate = candidateList.get(adapterPosition);
        holder.tvCandidateName.setText(candidate.getName());
        holder.tvParty.setText(candidate.getParty());

        // RadioButton durumunu ayarla
        holder.radioButton.setChecked(selectedPosition == adapterPosition);

        holder.itemView.setOnClickListener(v -> {
            // Önceki seçimi temizle ve yeni seçimi işaretle
            selectedPosition = holder.getAdapterPosition();
            if (selectedPosition != RecyclerView.NO_POSITION) {
                notifyDataSetChanged();
                listener.onCandidateClick(candidateList.get(selectedPosition));
            }
        });

        holder.radioButton.setOnClickListener(v -> {
            // Önceki seçimi temizle ve yeni seçimi işaretle
            selectedPosition = holder.getAdapterPosition();
            if (selectedPosition != RecyclerView.NO_POSITION) {
                notifyDataSetChanged();
                listener.onCandidateClick(candidateList.get(selectedPosition));
            }
        });
    }

    @Override
    public int getItemCount() {
        return candidateList.size();
    }

    public static class CandidateViewHolder extends RecyclerView.ViewHolder {
        RadioButton radioButton;
        TextView tvCandidateName, tvParty;

        public CandidateViewHolder(@NonNull View itemView) {
            super(itemView);
            radioButton = itemView.findViewById(R.id.radioButton);
            tvCandidateName = itemView.findViewById(R.id.tvCandidateName);
            tvParty = itemView.findViewById(R.id.tvParty);
        }
    }
}