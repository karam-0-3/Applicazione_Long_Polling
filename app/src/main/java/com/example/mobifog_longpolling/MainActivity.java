package com.example.mobifog_longpolling;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.content.SharedPreferences;

import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText ForchUrlEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ForchUrlEditText = findViewById(R.id.forch_url);

        // Recupero dell'URL dell'orchestratore dalle SharedPreferences all'avvio
        SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        String savedForchUrl = sharedPreferences.getString("ForchUrl", "");
        ForchUrlEditText.setText(savedForchUrl);

        BatteryOptimizationRequest();
    }

    @Override
    protected void onResume() {
        super.onResume();
        BatteryOptimizationRequest();
    }

    private void BatteryOptimizationRequest() {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    public void onRegisterWebhookClick(View v) {
        String ForchUrl = ForchUrlEditText.getText().toString().trim();

        if (ForchUrl.isEmpty()) {
            Toast.makeText(MainActivity.this, "Inserisci l'URL dell'orchestratore",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("ForchUrl", ForchUrl);
        editor.apply();

        WebhookClient webhookClient = new WebhookClient(MainActivity.this);
        webhookClient.registerWebhook();
        Toast.makeText(MainActivity.this, "Registrazione Webhook avviata",
                Toast.LENGTH_SHORT).show();
    }

    public void onStartLongPollingClick(View v) {
        SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        String ForchUrl = sharedPreferences.getString("ForchUrl", "");

        if (ForchUrl.isEmpty()) {
            Toast.makeText(MainActivity.this,
                    "Inserisci l'URL dell'orchestratore", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("isLongPollingActive", true);
        editor.apply();

        WebhookClient webhookClient = new WebhookClient(getApplicationContext());
        webhookClient.startLongPolling();

        Toast.makeText(MainActivity.this, "Long polling avviato.", Toast.LENGTH_SHORT).show();
    }

    public void onStopLongPollingClick(View v) {
        SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        boolean isLongPollingActive = sharedPreferences.getBoolean("isLongPollingActive", false);

        if (!isLongPollingActive) {
            Toast.makeText(MainActivity.this, "Il long polling non è attivo.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        WebhookClient.stopLongPolling(MainActivity.this);

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("isLongPollingActive", false);
        editor.apply();

        Toast.makeText(MainActivity.this, "Long polling interrotto.", Toast.LENGTH_SHORT).show();
    }
}
