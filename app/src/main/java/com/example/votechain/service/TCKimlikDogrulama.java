package com.example.votechain.service;

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;


public class TCKimlikDogrulama {

    public interface TCKimlikDogrulamaListener {
        void onTCKimlikDogrulamaResult(boolean isValid);
        void onTCKimlikDogrulamaError(String errorMessage);
    }

    public void dogrula(String tcKimlikNo, String ad, String soyad, int dogumYili, TCKimlikDogrulamaListener listener) {
        new Thread(() -> {
            try {
                String soapXml =
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                "<soap:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                                "xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" " +
                                "xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                                "  <soap:Body>\n" +
                                "    <TCKimlikNoDogrula xmlns=\"http://tckimlik.nvi.gov.tr/WS\">\n" +
                                "      <TCKimlikNo>" + tcKimlikNo + "</TCKimlikNo>\n" +
                                "      <Ad>" + ad.toUpperCase() + "</Ad>\n" +
                                "      <Soyad>" + soyad.toUpperCase() + "</Soyad>\n" +
                                "      <DogumYili>" + dogumYili + "</DogumYili>\n" +
                                "    </TCKimlikNoDogrula>\n" +
                                "  </soap:Body>\n" +
                                "</soap:Envelope>";

                URL url = new URL("https://tckimlik.nvi.gov.tr/Service/KPSPublic.asmx");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
                conn.setRequestProperty("SOAPAction", "http://tckimlik.nvi.gov.tr/WS/TCKimlikNoDogrula");
                conn.setDoOutput(true);

                OutputStream os = conn.getOutputStream();
                os.write(soapXml.getBytes());
                os.flush();
                os.close();

                InputStream is = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder responseBuilder = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    responseBuilder.append(line);
                }

                reader.close();
                conn.disconnect();

                String responseXml = responseBuilder.toString();
                boolean result = responseXml.contains("<TCKimlikNoDogrulaResult>true</TCKimlikNoDogrulaResult>");

                new Handler(Looper.getMainLooper()).post(() -> listener.onTCKimlikDogrulamaResult(result));

            } catch (Exception e) {
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(() -> listener.onTCKimlikDogrulamaError("Hata oluştu: " + e.getMessage()));
            }
        }).start();
    }
}