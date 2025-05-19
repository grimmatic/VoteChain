package com.example.votechain.util;

public class TCKimlikValidator {

    /**
     * T.C. Kimlik numarasının geçerli olup olmadığını kontrol eder.
     * @param tcKimlik Kontrol edilecek T.C. Kimlik numarası
     * @return Geçerli ise true, değil ise false
     */
    public boolean isValid(String tcKimlik) {
        // 11 haneli ve rakamlardan oluşmalı
        if (tcKimlik.length() != 11 || !tcKimlik.matches("\\d+")) {
            return false;
        }

        // İlk rakam 0 olamaz
        if (tcKimlik.charAt(0) == '0') {
            return false;
        }

        // Son iki hane kontrol
        int[] digits = new int[11];
        for (int i = 0; i < 11; i++) {
            digits[i] = tcKimlik.charAt(i) - '0';
        }

        // 10. hane kontrolü: İlk 9 hanenin toplamının mod 10'u
        int sumOfFirst9 = 0;
        for (int i = 0; i < 9; i++) {
            sumOfFirst9 += digits[i];
        }

        if (sumOfFirst9 % 10 != digits[9]) {
            return false;
        }

        // 11. hane kontrolü: İlk 10 hanenin özel algoritması
        int sumOdd = digits[0] + digits[2] + digits[4] + digits[6] + digits[8];
        int sumEven = digits[1] + digits[3] + digits[5] + digits[7];
        int tenthDigitCheck = ((sumOdd * 7) - sumEven) % 10;

        if (tenthDigitCheck < 0) {
            tenthDigitCheck += 10;
        }

        if (tenthDigitCheck != digits[9]) {
            return false;
        }

        // 11. hane kontrolü: İlk 10 hanenin toplamının mod 10'u
        int sumOfFirst10 = sumOfFirst9 + digits[9];
        if (sumOfFirst10 % 10 != digits[10]) {
            return false;
        }

        return true;
    }
}