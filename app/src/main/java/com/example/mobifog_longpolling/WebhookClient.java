package com.example.mobifog_longpolling;

import android.content.Context;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

public class WebhookClient {

    private static final String TAG = "WebhookClient"; // Tag per il log
    private final Context context; // Contesto dell'applicazione

    /* Costruttore della classe: assegna il contesto dell'applicazione */
    public WebhookClient(Context context) {
        this.context = context;
    }

    /**
     * Metodo per registrare un webhook con l'orchestratore.
     * Invia una richiesta HTTP POST per comunicare gli eventi da monitorare.
     */
    public void registerWebhook() {
        WorkRequest registerWebhookWorkRequest = new OneTimeWorkRequest.Builder(RegisterWebhookWorker.class)
                .setConstraints(new Constraints.Builder() // Vincolo: connessione internet necessaria
                        .setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();

        WorkManager.getInstance(context).enqueue(registerWebhookWorkRequest);
        Log.d(TAG, "Webhook registrato.");
    }

    /**
     * Metodo per avviare il long polling.
     * Lancia un worker che gestisce una connessione persistente con il server fino alla ricezione
     * di una risposta o timeout.
     */
    public void startLongPolling() {
        WorkRequest longPollingWorkRequest = new OneTimeWorkRequest.Builder(LongPollingWorker.class)
                .setConstraints(new Constraints.Builder() // Vincolo: connessione internet necessaria
                        .setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();

        WorkManager.getInstance(context).enqueue(longPollingWorkRequest);
        Log.d(TAG, "Long polling avviato.");
    }

    /**
     * Metodo per fermare qualsiasi processo di long polling attivo.
     */
    public static void stopLongPolling(Context context) {
        WorkManager.getInstance(context).cancelAllWorkByTag("LongPollingWork");
        Log.d(TAG, "Long polling fermato.");
    }
}
