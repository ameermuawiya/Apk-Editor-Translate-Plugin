package com.gmail.heagoo.apkeditor.translate;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Random;

public class TranslationUtils {
    public static final String HEX_CHARS = "0123456789ABCDEF";

    /*
     * Encodes a given string into URL safe format manually.
     */
    public static String urlEncode(String str) {
        if (str == null) {
            return null;
        }
        byte[] bytes = str.getBytes(Charset.defaultCharset());
        StringBuilder sb = new StringBuilder(bytes.length);
        for (byte b : bytes) {
            if ((b >= 97 && b <= 122) || (b >= 65 && b <= 90) || (b >= 48 && b <= 57) || b == 45 || b == 46 || b == 95 || b == 126) {
                sb.append((char) b);
            } else {
                sb.append('%').append(HEX_CHARS.charAt((b >> 4) & 15)).append(HEX_CHARS.charAt(b & 15));
            }
        }
        return sb.toString();
    }

    /*
     * Generates a random integer within a specific minimum and maximum range.
     */
    public static int getRandomNumber(int min, int max) {
        return new Random().nextInt((max - min) + 1) + min;
    }

    /*
     * Joins an array of strings into a single string using a provided separator.
     */
    public static String joinStrings(String[] strArr, String separator) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < strArr.length; i++) {
            if (i > 0) {
                sb.append(separator);
            }
            sb.append(strArr[i]);
        }
        return sb.toString();
    }

    /*
     * Joins a list of strings into a single string using a provided separator.
     */
    public static String joinList(List<String> list, String separator) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(separator);
            }
            sb.append(list.get(i));
        }
        return sb.toString();
    }
}
