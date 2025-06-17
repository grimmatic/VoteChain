package com.example.votechain.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Utils {



    /**
     * Tarihi string formatına dönüştürür.
     * @param date Dönüştürülecek tarih
     * @return String formatında tarih
     */
    public static String formatDate(Date date) {
        if (date == null) return "";
        SimpleDateFormat formatter = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        return formatter.format(date);
    }

    /**
     * Tarihi ve saati string formatına dönüştürür.
     * @param date Dönüştürülecek tarih ve saat
     * @return String formatında tarih ve saat
     */
    public static String formatDateTime(Date date) {
        if (date == null) return "";
        SimpleDateFormat formatter = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
        return formatter.format(date);
    }
}