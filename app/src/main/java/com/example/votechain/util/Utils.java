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
     * İnternet bağlantısının olup olmadığını kontrol eder.
     * @param context Uygulama konteksti
     * @return Bağlantı varsa true, yoksa false
     */
    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    /**
     * Toast mesajı gösterir.
     * @param context Uygulama konteksti
     * @param message Gösterilecek mesaj
     */
    public static void showToast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

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