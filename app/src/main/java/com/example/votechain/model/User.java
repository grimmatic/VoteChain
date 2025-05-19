package com.example.votechain.model;

public class User {
    private String tcKimlikNo;
    private String ad;
    private String soyad;
    private int dogumYili;
    private String userId;
    private String role;

    public User() {

    }

    public User(String tcKimlikNo, String ad, String soyad, int dogumYili) {
        this.tcKimlikNo = tcKimlikNo;
        this.ad = ad;
        this.soyad = soyad;
        this.dogumYili = dogumYili;
        this.role = "user";
    }


    public String getTcKimlikNo() {
        return tcKimlikNo;
    }

    public void setTcKimlikNo(String tcKimlikNo) {
        this.tcKimlikNo = tcKimlikNo;
    }

    public String getAd() {
        return ad;
    }

    public void setAd(String ad) {
        this.ad = ad;
    }

    public String getSoyad() {
        return soyad;
    }

    public void setSoyad(String soyad) {
        this.soyad = soyad;
    }

    public int getDogumYili() {
        return dogumYili;
    }

    public void setDogumYili(int dogumYili) {
        this.dogumYili = dogumYili;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}