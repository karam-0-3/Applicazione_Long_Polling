package com.example.mobifog_longpolling;


import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import androidx.work.ForegroundInfo;
import androidx.core.app.NotificationCompat;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;

public class LongPollingWorker extends Worker {

    private static final String TAG = "LongPollingWorker";
    private final OkHttpClient client;
    private final String ForchUrl;
    private static final String CHANNEL_ID = "LongPollingWorkerChannel";

    public LongPollingWorker(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
        super(context, workerParameters);
        this.client = HttpClientSingleton.INSTANCE.getClient();
        SharedPreferences sharedPreferences = context.getSharedPreferences("MyPrefs",
                Context.MODE_PRIVATE);
        this.ForchUrl = sharedPreferences.getString("ForchUrl", "");
        createNotificationChannel();
    }

    @NonNull
    @Override
    public Result doWork() {

        createNotificationChannel();
        setForegroundAsync(createForegroundInfo());


        SharedPreferences sharedPreferences = getApplicationContext().getSharedPreferences
                ("MyPrefs", Context.MODE_PRIVATE);
        boolean isPollingActive = sharedPreferences.getBoolean("isPollingActive", true);

        if (!isPollingActive) {
            Log.d(TAG, "Polling interrotto");
            return Result.failure();
        }

        fetchTask();

        Log.d(TAG, "Long Polling completato, riparte la connessione.");
        return Result.success();
    }

    // Metodo aggiornato per utilizzare il long polling

    private void fetchTask() {
        String pollingUrl = ForchUrl + "/polling-startup";
        Request request = new Request.Builder().url(pollingUrl).build();

        Log.d(TAG, "Avvio long polling con il server: " + pollingUrl);

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Errore nella connessione: " + e.getMessage(), e);
                retryLongPolling();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    Log.d(TAG, "Risposta dal server ricevuta: " + responseBody);

                    // Chiamata del metodo doWork con la risposta ricevuta
                    processTaskResponse(responseBody);

                    // Riavvia il long polling
                    retryLongPolling();
                } else {
                    Log.e(TAG, "Errore durante il long polling, codice risposta: " + response.code());
                    retryLongPolling();
                }
            }
        });
    }

    // Metodo per riprovare il long polling
    private void retryLongPolling() {
        // Pianifica il prossimo polling dopo 10 secondi
        WorkRequest nextWorkRequest = new OneTimeWorkRequest.Builder(LongPollingWorker.class)
                .setInitialDelay(10, TimeUnit.SECONDS)
                .build();

        // Enqueue il nuovo lavoro con WorkManager
        WorkManager.getInstance(getApplicationContext()).enqueue(nextWorkRequest);
        Log.d(TAG, "Scheduling della prossima richiesta effettuato");
    }


    // Metodo per ritentare il long polling dopo un breve intervallo

    private String getTaskId(String response) {
        try {
            JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();
            return jsonResponse.get("task_id").getAsString();
        } catch (Exception e) {
            Log.e(TAG, "Errore nell'estrazione del task_id dalla risposta.", e);
            return null;
        }
    }

    private boolean executeTask(String taskId) {
        int taskIdInt = Integer.parseInt(taskId);

        if (taskIdInt != 0) {
            Log.d(TAG, "Task " + taskId + " completata con successo.");
            return true;
        } else {
            Log.d(TAG, "Task " + taskId + " fallita.");
            return false;
        }
    }

    private String getData() {
        return "{\"message\": \"Non ci sono dati aggiuntivi\"}";
    }

    public void sendTaskResponse(String taskId, String status, String Data) {

        MediaType JSON = MediaType.get("application/json; charset=utf-8");

        TaskResponseBuilder taskResponse = new TaskResponseBuilder(taskId, status, Data);

        Gson gson = new Gson();
        Object json = gson.fromJson(taskResponse.toString(), Object.class);
        String json_request = gson.toJson(json);

        RequestBody body = RequestBody.create(json_request, JSON);

        Request request = new Request.Builder()
                .url(ForchUrl + "/polling-response")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                Log.d(TAG, "Risposta inviata al server con successo.");
            } else {
                Log.e(TAG, "Errore nell'invio della risposta al server: " + response.code());
            }
        } catch (IOException e) {
            Log.e(TAG, "Errore di rete durante l'invio della risposta al server.", e);
        }
    }

    private void processTaskResponse(String responseBody) {
        String taskId = getTaskId(responseBody);
        boolean taskResult = executeTask(taskId);
        String status = taskResult ? "task completata" : "task fallita";
        String data = getData();

        sendTaskResponse(taskId, status, data);
    }

    private ForegroundInfo createForegroundInfo() {
        String title = "Long Polling";
        String content = "Long polling in esecuzione";

        Intent notificationIntent = new Intent(getApplicationContext(), MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                getApplicationContext(), 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(pendingIntent)
                .build();

            return new ForegroundInfo(1, notification);
    }

    private void createNotificationChannel() {
        CharSequence name = "LongPollingWorker Channel";
        String description = "Canale per le notifiche del LongPollingWorker";
        int importance = NotificationManager.IMPORTANCE_HIGH;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
        channel.setDescription(description);

        NotificationManager notificationManager = getApplicationContext()
                .getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }
}



