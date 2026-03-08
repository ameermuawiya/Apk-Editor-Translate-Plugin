package com.gmail.heagoo.apkeditor.translate;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

public class TranslationEngine {

    private static final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private static final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    /*
     * Interface for handling translation result callbacks.
     */
    public interface TranslationCallback {
        void onSuccess(String result);
        void onError(String errorMsg);
    }

    /*
     * Interface for handling configuration fetching callbacks.
     */
    public interface ConfigCallback {
        void onConfigSuccess(Map<String, String> config);
        void onConfigError(String errorMsg);
    }

    /*
     * Main dispatcher method to execute either Google or Yandex translation asynchronously.
     * Uses ExecutorService as a modern replacement for deprecated AsyncTask.
     */
    public static void executeTranslation(Context context, boolean isGoogle, String text, String sourceLang, String targetLang, String userAgent, Map<String, String> configMap, TranslationCallback callback) {
        if (isGoogle) {
            executeGoogleTranslation(text, sourceLang, targetLang, userAgent, configMap, callback);
        } else {
            executeYandexTranslation(text, sourceLang, targetLang, userAgent, callback);
        }
    }

    /*
     * Fetches required tokens for Google Translate API using modern background threads.
     */
    public static void fetchGoogleConfig(String userAgent, boolean bypassConfig, ConfigCallback callback) {
        if (bypassConfig) {
            mainThreadHandler.post(() -> callback.onConfigSuccess(new HashMap<>()));
            return;
        }

        executorService.execute(() -> {
            if (TranslateActivity.isTaskCancelled) {
                mainThreadHandler.post(() -> callback.onConfigError("Task Cancelled"));
                return;
            }

            HttpURLConnection connection = null;
            BufferedReader reader = null;
            Map<String, String> configMap = new HashMap<>();

            try {
                URL url = new URL("https://translate.google.com/?hl=en-US");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("User-Agent", userAgent);
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);

                InputStream is = connection.getInputStream();
                if (is == null) {
                    mainThreadHandler.post(() -> callback.onConfigError("Error O-99"));
                    return;
                }

                reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line).append("\n");
                }

                String resString = response.toString();
                configMap.put("rpcids", "MkEWBc");
                configMap.put("f.sid", extractToken("FdrFJe", resString));
                configMap.put("bl", extractToken("cfb2h", resString));
                configMap.put("hl", "en-US");
                configMap.put("soc-app", "1");
                configMap.put("soc-platform", "1");
                configMap.put("soc-device", "1");
                configMap.put("rt", "c");

                mainThreadHandler.post(() -> {
                    if (TranslateActivity.isTaskCancelled) {
                        callback.onConfigError("Error O-99");
                    } else {
                        callback.onConfigSuccess(configMap);
                    }
                });

            } catch (Exception e) {
                Log.e("TranslationEngine", "Error fetching Google Config", e);
                mainThreadHandler.post(() -> callback.onConfigError("Error O-99"));
            } finally {
                if (connection != null) connection.disconnect();
                if (reader != null) {
                    try { reader.close(); } catch (Exception ignored) {}
                }
            }
        });
    }

    /*
     * Extracts specific token strings using regex matcher.
     */
    private static String extractToken(String key, String body) {
        Matcher matcher = Pattern.compile("\"" + key + "\":\"([^\"]+)\"").matcher(body);
        if (!matcher.find()) return "";
        return matcher.group(1);
    }

    /*
     * Executes text translation via Google API securely on a background thread.
     */
    private static void executeGoogleTranslation(String text, String sourceLang, String targetLang, String userAgent, Map<String, String> configMap, TranslationCallback callback) {
        executorService.execute(() -> {
            if (TranslateActivity.isTaskCancelled) {
                mainThreadHandler.post(() -> callback.onError("Error G-137"));
                return;
            }

            HttpURLConnection connection = null;
            BufferedReader reader = null;

            try {
                // Build Payload exactly as provided
                JSONArray innerArr = new JSONArray();
                innerArr.put(0, text);
                innerArr.put(1, sourceLang);
                innerArr.put(2, targetLang);
                innerArr.put(3, true);

                JSONArray nullArr = new JSONArray();
                nullArr.put(0, (Object) null);

                JSONArray combinedArr = new JSONArray();
                combinedArr.put(0, innerArr);
                combinedArr.put(1, nullArr);

                JSONArray wrapperArr = new JSONArray();
                wrapperArr.put(0, "MkEWBc");
                wrapperArr.put(1, combinedArr.toString());
                wrapperArr.put(2, (Object) null);
                wrapperArr.put(3, "generic");

                JSONArray finalInner = new JSONArray();
                finalInner.put(0, wrapperArr);

                JSONArray finalOuter = new JSONArray();
                finalOuter.put(0, finalInner);
                String payload = finalOuter.toString();

                int reqId = TranslationUtils.getRandomNumber(100000, 999999);
                configMap.put("_reqid", String.valueOf(reqId));

                StringBuilder queryParams = new StringBuilder();
                for (Map.Entry<String, String> entry : configMap.entrySet()) {
                    queryParams.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
                }

                String postData = "f.req=" + TranslationUtils.urlEncode(payload) + "&";
                byte[] postDataBytes = postData.getBytes("UTF-8");

                URL url = new URL("https://translate.google.com/_/TranslateWebserverUi/data/batchexecute?" + queryParams.toString());
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("User-Agent", userAgent);
                connection.setRequestProperty("X-Same-Domain", "1");
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");
                connection.setRequestProperty("X-Requested-With", "XMLHttpRequest");
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(5000);
                connection.setDoOutput(true);

                OutputStream os = connection.getOutputStream();
                os.write(postDataBytes);
                os.flush();
                os.close();

                InputStream is = connection.getInputStream();
                if (is == null) {
                    mainThreadHandler.post(() -> callback.onError("Error G-137"));
                    return;
                }

                reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line).append("\n");
                }

                String responseStr = response.toString();

                if (responseStr.length() > 0) {
                    String resultText = "";
                    String[] split = responseStr.split("\n", 4);
                    JSONArray jSONArray;
                    try {
                        jSONArray = new JSONArray(split[3].substring(0, Integer.parseInt(split[2], 10) - 2));
                    } catch (Exception e) {
                        jSONArray = new JSONArray(TranslationUtils.joinStrings(Arrays.copyOfRange(split, 2, split.length), ""));
                    }
                    
                    JSONArray jSONArray2 = (JSONArray) ((JSONArray) ((JSONArray) new JSONArray((String) ((JSONArray) jSONArray.get(0)).get(2)).get(1)).get(0)).get(0);
                    if (jSONArray2.length() <= 5 || jSONArray2.isNull(5)) {
                        String finalResult = (String) jSONArray2.get(0);
                        mainThreadHandler.post(() -> callback.onSuccess(finalResult));
                    } else {
                        JSONArray jSONArray3 = (JSONArray) jSONArray2.get(5);
                        for (int i = 0; i < jSONArray3.length(); i++) {
                            JSONArray jSONArray4 = (JSONArray) jSONArray3.get(i);
                            if (i > 0 && !resultText.endsWith("\n")) {
                                resultText += " ";
                            }
                            resultText += ((String) jSONArray4.get(0));
                        }
                        String finalResultMulti = resultText;
                        mainThreadHandler.post(() -> callback.onSuccess(finalResultMulti));
                    }
                } else {
                    mainThreadHandler.post(() -> callback.onError("Error G-172"));
                }

            } catch (Exception e) {
                Log.e("TranslationEngine", "Error in Google request", e);
                mainThreadHandler.post(() -> callback.onError(e.getMessage()));
            } finally {
                if (connection != null) connection.disconnect();
                if (reader != null) {
                    try { reader.close(); } catch (Exception ignored) {}
                }
            }
        });
    }

    /*
     * Executes text translation via Yandex API using modern background threads.
     */
    private static void executeYandexTranslation(String text, String sourceLang, String targetLang, String userAgent, TranslationCallback callback) {
        executorService.execute(() -> {
            if (TranslateActivity.isTaskCancelled) {
                mainThreadHandler.post(() -> callback.onError("Error Y-95"));
                return;
            }

            HttpURLConnection connection = null;
            BufferedReader reader = null;

            try {
                String uuid = UUID.randomUUID().toString().replace("-", "");
                String langParam = sourceLang.equals("auto") ? targetLang : sourceLang + "-" + targetLang;
                String urlString = "https://translate.yandex.net/api/v1/tr.json/translate?srv=android&ucid=" + uuid + "&lang=" + langParam + "&text=" + TranslationUtils.urlEncode(text);

                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("User-Agent", userAgent);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(5000);
                connection.setDoOutput(true);

                byte[] postDataBytes = new JSONObject().toString().getBytes("UTF-8");
                OutputStream os = connection.getOutputStream();
                os.write(postDataBytes);
                os.flush();
                os.close();

                InputStream is = connection.getInputStream();
                if (is == null) {
                    mainThreadHandler.post(() -> callback.onError("Error Y-95"));
                    return;
                }

                reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line).append("\n");
                }
                
                String responseStr = response.toString();

                JSONObject json = new JSONObject(responseStr);
                if (json.getInt("code") != 200) {
                    String msg = json.getString("message");
                    mainThreadHandler.post(() -> callback.onError(msg));
                } else {
                    String resultText = "";
                    JSONArray textArray = json.getJSONArray("text");
                    for (int i = 0; i < textArray.length(); i++) {
                        resultText += textArray.getString(i);
                    }
                    if (resultText.length() > 0) {
                        String finalResult = resultText;
                        mainThreadHandler.post(() -> callback.onSuccess(finalResult));
                    } else {
                        mainThreadHandler.post(() -> callback.onError("Error Y-111"));
                    }
                }
            } catch (Exception e) {
                Log.e("TranslationEngine", "Error in Yandex request", e);
                mainThreadHandler.post(() -> callback.onError(e.getMessage()));
            } finally {
                if (connection != null) connection.disconnect();
                if (reader != null) {
                    try { reader.close(); } catch (Exception ignored) {}
                }
            }
        });
    }
}
