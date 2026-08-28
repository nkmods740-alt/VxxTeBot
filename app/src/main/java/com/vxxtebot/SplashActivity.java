package com.vxxtebot;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private TextView splashTerminalText;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isNavigated = false;

    private final String[] bootLogs = new String[]{
            "> [SYS_INIT] Loading OtpTracker Security Protocols v3.0...",
            "> [FIREWALL] Intercepting Gateway Gateway Ports [OK]",
            "> [ENCRYPT] AES-256 OTP Decryption Engine Initialized...",
            "> [TARGET] Target Secured Endpoint: @NawabKingMods",
            "> [STATUS] Cyber Tunnel Active // SYSTEM READY [100%]"
    };

    private final StringBuilder logBuilder = new StringBuilder();
    private int currentLogIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        splashTerminalText = findViewById(R.id.splashTerminalText);
        View logoLayout = findViewById(R.id.splashLogoLayout);
        if (logoLayout != null) {
            HackerAnimationHelper.startPulseGlow(logoLayout);
        }

        findViewById(android.R.id.content).setOnClickListener(v -> navigateToMain());

        startBootSequence();
    }

    private void startBootSequence() {
        if (currentLogIndex < bootLogs.length) {
            logBuilder.append(bootLogs[currentLogIndex]).append("\n");
            splashTerminalText.setText(logBuilder.toString() + "> █");
            currentLogIndex++;
            handler.postDelayed(this::startBootSequence, 360);
        } else {
            splashTerminalText.setText(logBuilder.toString() + "> [OTP_TUNNEL_READY]");
            handler.postDelayed(this::navigateToMain, 500);
        }
    }

    private void navigateToMain() {
        if (isNavigated) return;
        isNavigated = true;
        handler.removeCallbacksAndMessages(null);

        Intent intent = new Intent(SplashActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
