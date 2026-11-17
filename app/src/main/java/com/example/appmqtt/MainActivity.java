package com.example.appmqtt;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import info.mqtt.android.service.Ack;
import info.mqtt.android.service.MqttAndroidClient;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String MQTT_BROKER_URL = "wss://h0bc12ce.ala.us-east-1.emqxsl.com:8884/mqtt";
    private static final String CLIENT_ID = "android-device";
    private static final String MQTT_USERNAME = "android-username";
    private static final String MQTT_PASSWORD = "android-username";

    private MqttAndroidClient client;
    private TextView statusTextView;
    private SeekBar tempSlider;
    private TextView tempValueTextView;
    private TextView subscriptionLogTextView;
    private final StringBuilder logBuilder = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusTextView = findViewById(R.id.status);
        tempSlider = findViewById(R.id.tempSlider);
        tempValueTextView = findViewById(R.id.tempValue);
        subscriptionLogTextView = findViewById(R.id.subscriptionLog);

        setupButtons();
        setupSlider();

        client = new MqttAndroidClient(getApplicationContext(), MQTT_BROKER_URL, CLIENT_ID, Ack.AUTO_ACK);

        connectToMqttBroker();
    }

    private void setupButtons() {
        // Living Luz
        findViewById(R.id.btnLivingLuzOn).setOnClickListener(v -> publishWithConfirmation("hogar/living/luz/set", "on", "Luz del living encendida"));
        findViewById(R.id.btnLivingLuzOff).setOnClickListener(v -> publishWithConfirmation("hogar/living/luz/set", "off", "Luz del living apagada"));

        // Puerta Principal
        findViewById(R.id.btnPuertaOpen).setOnClickListener(v -> publishWithConfirmation("hogar/puerta/principal/set", "open", "Puerta principal abierta"));
        findViewById(R.id.btnPuertaClose).setOnClickListener(v -> publishWithConfirmation("hogar/puerta/principal/set", "close", "Puerta principal cerrada"));

        // Alarma
        findViewById(R.id.btnAlarmaOn).setOnClickListener(v -> publishWithConfirmation("hogar/alarma/set", "enabled", "Alarma activada"));
        findViewById(R.id.btnAlarmaOff).setOnClickListener(v -> publishWithConfirmation("hogar/alarma/set", "disabled", "Alarma desactivada"));

        // TV
        findViewById(R.id.btnTVOn).setOnClickListener(v -> publishWithConfirmation("hogar/living/tv/set", "on", "TV encendida"));
        findViewById(R.id.btnTVOff).setOnClickListener(v -> publishWithConfirmation("hogar/living/tv/set", "off", "TV apagada"));

        // Termostato
        findViewById(R.id.btnSetTemp).setOnClickListener(v -> {
            String temp = String.valueOf(tempSlider.getProgress());
            publishWithConfirmation("hogar/clima/temperatura/set", temp, "Temperatura establecida a " + temp + "°C");
        });

        // Limpiar visor de suscripción
        findViewById(R.id.btnClearLog).setOnClickListener(v -> {
            logBuilder.setLength(0);
            subscriptionLogTextView.setText("Historial limpiado. Esperando mensajes en hogar/#...");
        });
    }

    private void setupSlider() {
        tempSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tempValueTextView.setText(progress + "°C");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void connectToMqttBroker() {
        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                if (reconnect) {
                    Log.d(TAG, "Reconectado a: " + serverURI);
                } else {
                    Log.d(TAG, "Conectado a: " + serverURI);
                }
                subscribeToTopics();
            }

            @Override
            public void connectionLost(Throwable cause) {
                Log.e(TAG, "Conexión perdida", cause);
                runOnUiThread(() -> {
                    statusTextView.setText("❌ Desconectado");
                    statusTextView.setBackgroundColor(Color.parseColor("#F44336"));
                });
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                String payload = new String(message.getPayload());
                Log.d(TAG, "Mensaje recibido: " + topic + " = " + payload);
                runOnUiThread(() -> {
                    String time = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
                    String entry = "[" + time + "] " + topic + " → " + payload + "\n";
                    logBuilder.insert(0, entry);
                    subscriptionLogTextView.setText(logBuilder.toString().trim());
                    Toast.makeText(MainActivity.this, "Recibido: " + payload, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // Not used
            }
        });

        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(MQTT_USERNAME);
        options.setPassword(MQTT_PASSWORD.toCharArray());
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);

        // The connect call is asynchronous, so we handle results in the callbacks.
        // No try-catch block is needed here.
        client.connect(options, null, new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
                Log.d(TAG, "Conexión MQTT iniciada!");
                runOnUiThread(() -> {
                    statusTextView.setText("✅ Conectado");
                    statusTextView.setBackgroundColor(Color.parseColor("#4CAF50"));
                    Toast.makeText(MainActivity.this, "Conectado a MQTT", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                Log.e(TAG, "Error en la conexión MQTT", exception);
                runOnUiThread(() -> {
                    statusTextView.setText("❌ Error de conexión");
                    statusTextView.setBackgroundColor(Color.parseColor("#F44336"));
                    Toast.makeText(MainActivity.this, "Error al conectar con MQTT", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void subscribeToTopics() {
        // The subscribe call is asynchronous.
        // The try-catch is not needed because MqttException is not thrown directly.
        client.subscribe("hogar/#", 1, null, new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
                Log.d(TAG, "Suscrito a 'hogar/#'!");
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                Log.e(TAG, "Error al suscribir a 'hogar/#'", exception);
            }
        });
    }

    private void publishWithConfirmation(String topic, String payload, String confirmationMessage) {
        if (client == null || !client.isConnected()) {
            Toast.makeText(this, "No conectado. Revisa la conexión.", Toast.LENGTH_SHORT).show();
            return;
        }

        // The MqttMessage constructor can throw an exception, so we keep the try-catch for that.
        // However, the client.publish() call is asynchronous.
        try {
            MqttMessage message = new MqttMessage(payload.getBytes());
            message.setQos(1);
            client.publish(topic, message, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "Publicado: " + topic + " = " + payload);
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, confirmationMessage, Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "Error al publicar en " + topic, exception);
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error al enviar comando", Toast.LENGTH_SHORT).show());
                }
            });
        } catch (Exception e) {
            // This catch block will handle exceptions from the MqttMessage constructor or synchronous publish calls
            Log.e(TAG, "Error al crear o publicar el mensaje", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (client != null) {
            // It's good practice to keep the try-catch here, as synchronous versions of these
            // methods (or issues during the process) could potentially throw an MqttException.
            try {
                if(client.isConnected()){
                    client.unsubscribe("hogar/#");
                    client.disconnect();
                }
                client.close();
            } catch (Exception e) {
                Log.e(TAG, "Error al desconectar MQTT", e);
            }
        }
    }
}
