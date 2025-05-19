package com.example.votechain.model;

public class Candidate {
    private String id;
    private String electionId;
    private String name;
    private String party;
    private int voteCount;

    public Candidate() {

    }

    public Candidate(String electionId, String name, String party) {
        this.electionId = electionId;
        this.name = name;
        this.party = party;
        this.voteCount = 0;
    }


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getElectionId() {
        return electionId;
    }

    public void setElectionId(String electionId) {
        this.electionId = electionId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getParty() {
        return party;
    }

    public void setParty(String party) {
        this.party = party;
    }

    public int getVoteCount() {
        return voteCount;
    }

    public void setVoteCount(int voteCount) {
        this.voteCount = voteCount;
    }
}