package com.example.votechain.model;

import java.util.Date;

public class Vote {
    private String id;
    private String userId;
    private String electionId;
    private String candidateId;
    private Date timestamp;
    private String transactionHash;

    public Vote() {

    }

    public Vote(String userId, String electionId, String candidateId) {
        this.userId = userId;
        this.electionId = electionId;
        this.candidateId = candidateId;
        this.timestamp = new Date();
    }


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getElectionId() {
        return electionId;
    }

    public void setElectionId(String electionId) {
        this.electionId = electionId;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(String candidateId) {
        this.candidateId = candidateId;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public String getTransactionHash() {
        return transactionHash;
    }

    public void setTransactionHash(String transactionHash) {
        this.transactionHash = transactionHash;
    }
}