package com.vxxtebot;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    // -------------------------------------------------------------
    // SharedPreferences & Bot Configuration
    // -------------------------------------------------------------
    private static final String PREFS_NAME = "OtpTrackerConfig";
    private static final String KEY_SAVED_TOKEN = "saved_bot_token";
    public static final String DEFAULT_BOT_TOKEN = "7731278146:AAGf7zO_R2F4j_vW3Z1q4G_jK7y9L0M1n2O";

    private String currentBotToken;
    private SharedPreferences sharedPreferences;

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    // OkHttp Client
    private OkHttpClient httpClient;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // UI Elements
    private MaterialCardView statusCard;
    private TextView connectionStatusText;
    private TextView connectionDetailText;
    private ImageButton btnRefreshConnection;

    private TextInputLayout phoneInputLayout;
    private TextInputEditText phoneInput;
    private MaterialButton submitButton;

    // 30-Second Termux Hacker Loading Elements
    private MaterialCardView loadingLayout;
    private TextView loadingTimeText;
    private TextView loadingTimerCountdownText;
    private TextView loadingDotsText;
    private ProgressBar loadingProgressBar;
    private TextView loadingProgressBarText;
    private TextView termuxConsoleText;

    // Reply Elements
    private MaterialCardView replyLayout;
    private TextView replySenderText;
    private TextView replyContentText;
    private TextView replyTimeText;
    private LinearLayout otpHighlightBox;
    private TextView otpCodeText;
    private MaterialButton btnCopyReply;

    private TextView statusText;

    private TextView historyEmptyText;
    private LinearLayout historyContainer;
    private TextView historyCountText;
    private MaterialButton btnClearHistory;

    // App State
    private String connectedChatId = null;
    private String connectedChatTitle = null;
    private long lastUpdateId = 0;
    private boolean isCheckingConnection = false;
    private boolean isPollingForReply = false;
    private int pollingAttempts = 0;
    private static final int MAX_POLLING_ATTEMPTS = 150;

    // 30-Second Timer & Sync State
    private static final long REQUIRED_30S_LOADING_MS = 30000; // 30 seconds
    private long loadingStartTime = 0;
    private boolean isTermux30sActive = false;
    private String pendingReplyContent = null;
    private int loadingDotCycle = 0;
    private Runnable termuxAnimationRunnable;

    private int activeSentMessageId = -1;
    private final List<HistoryItem> sentHistoryList = new ArrayList<>();

    private Runnable retryConnectionRunnable;
    private Runnable pollReplyRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Load persistently saved token from SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentBotToken = sharedPreferences.getString(KEY_SAVED_TOKEN, DEFAULT_BOT_TOKEN);

        initHttpClient();
        initViews();
        setupListeners();

        // Automatically connect to Telegram Bot on app launch
        checkBotConnection();
    }

    private void initHttpClient() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    private void initViews() {
        statusCard = findViewById(R.id.statusCard);
        connectionStatusText = findViewById(R.id.connectionStatusText);
        connectionDetailText = findViewById(R.id.connectionDetailText);
        btnRefreshConnection = findViewById(R.id.btnRefreshConnection);

        phoneInputLayout = findViewById(R.id.phoneInputLayout);
        phoneInput = findViewById(R.id.phoneInput);
        submitButton = findViewById(R.id.submitButton);

        // 30s Termux Hacker Loading Elements
        loadingLayout = findViewById(R.id.loadingLayout);
        loadingTimeText = findViewById(R.id.loadingTimeText);
        loadingTimerCountdownText = findViewById(R.id.loadingTimerCountdownText);
        loadingDotsText = findViewById(R.id.loadingDotsText);
        loadingProgressBar = findViewById(R.id.loadingProgressBar);
        loadingProgressBarText = findViewById(R.id.loadingProgressBarText);
        termuxConsoleText = findViewById(R.id.termuxConsoleText);

        // Reply & OTP elements
        replyLayout = findViewById(R.id.replyLayout);
        replySenderText = findViewById(R.id.replySenderText);
        replyContentText = findViewById(R.id.replyContentText);
        replyTimeText = findViewById(R.id.replyTimeText);
        otpHighlightBox = findViewById(R.id.otpHighlightBox);
        otpCodeText = findViewById(R.id.otpCodeText);
        btnCopyReply = findViewById(R.id.btnCopyReply);

        statusText = findViewById(R.id.statusText);
        historyEmptyText = findViewById(R.id.historyEmptyText);
        historyContainer = findViewById(R.id.historyContainer);
        historyCountText = findViewById(R.id.historyCountText);
        btnClearHistory = findViewById(R.id.btnClearHistory);

        // Long press header to configure custom BotFather Token
        findViewById(R.id.headerCard).setOnLongClickListener(v -> {
            showTokenSettingsDialog();
            return true;
        });

        // Pulse glow on header icon
        View headerIcon = findViewById(R.id.headerIcon);
        if (headerIcon != null) {
            HackerAnimationHelper.startPulseGlow(headerIcon);
        }
    }

    private void setupListeners() {
        btnRefreshConnection.setOnClickListener(v -> {
            if (isCheckingConnection) return;
            cancelPendingAutoRetry();
            checkBotConnection();
        });

        submitButton.setOnClickListener(v -> handleSendPhoneNumber());

        btnClearHistory.setOnClickListener(v -> showClearHistoryConfirmationDialog());

        if (btnCopyReply != null) {
            btnCopyReply.setOnClickListener(v -> {
                String replyContent = replyContentText.getText() != null ? replyContentText.getText().toString() : "";
                if (!TextUtils.isEmpty(replyContent)) {
                    copyToClipboard(replyContent);
                }
            });
        }
    }

    private String getApiBaseUrl() {
        return "https://api.telegram.org/bot" + currentBotToken.trim();
    }

    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
            }
        } catch (Exception ignored) {}
        return true;
    }

    // -------------------------------------------------------------
    // Connection Check (getUpdates)
    // -------------------------------------------------------------
    private void checkBotConnection() {
        if (isCheckingConnection) return;
        isCheckingConnection = true;

        if (!isNetworkAvailable()) {
            isCheckingConnection = false;
            updateConnectionUiNetworkError();
            return;
        }

        updateConnectionUiChecking();

        String url = getApiBaseUrl() + "/getUpdates?timeout=10&allowed_updates=[\"message\"]";
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> {
                    isCheckingConnection = false;
                    updateConnectionUiNetworkError();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                final String responseBody = response.body() != null ? response.body().string() : "";
                final int responseCode = response.code();

                mainHandler.post(() -> {
                    isCheckingConnection = false;
                    handleConnectionResponse(responseCode, responseBody);
                });
            }
        });
    }

    private void handleConnectionResponse(int statusCode, String jsonString) {
        try {
            if (statusCode != 200 || TextUtils.isEmpty(jsonString)) {
                updateConnectionUiApiError(statusCode);
                return;
            }

            JSONObject jsonObject = new JSONObject(jsonString);
            boolean ok = jsonObject.optBoolean("ok", false);

            if (!ok) {
                updateConnectionUiApiError(statusCode);
                return;
            }

            JSONArray result = jsonObject.optJSONArray("result");
            if (result != null && result.length() > 0) {
                String foundChatId = null;
                String foundTitle = null;

                for (int i = result.length() - 1; i >= 0; i--) {
                    JSONObject update = result.getJSONObject(i);
                    long updateId = update.optLong("update_id", 0);
                    if (updateId > lastUpdateId) {
                        lastUpdateId = updateId;
                    }

                    if (update.has("message")) {
                        JSONObject msg = update.getJSONObject("message");
                        if (msg.has("chat")) {
                            JSONObject chat = msg.getJSONObject("chat");
                            foundChatId = String.valueOf(chat.optLong("id", 0));
                            String type = chat.optString("type", "private");
                            if ("private".equals(type)) {
                                String fn = chat.optString("first_name", "");
                                String ln = chat.optString("last_name", "");
                                String un = chat.optString("username", "");
                                foundTitle = !TextUtils.isEmpty(un) ? "@" + un : (fn + " " + ln).trim();
                            } else {
                                foundTitle = chat.optString("title", "Group Chat");
                            }
                            break;
                        }
                    }
                }

                if (foundChatId != null && !foundChatId.equals("0")) {
                    connectedChatId = foundChatId;
                    connectedChatTitle = !TextUtils.isEmpty(foundTitle) ? foundTitle : foundChatId;
                    updateConnectionUiSuccess();
                } else {
                    updateConnectionUiNeedStart();
                }
            } else {
                updateConnectionUiNeedStart();
            }

        } catch (JSONException e) {
            updateConnectionUiNetworkError();
        }
    }

    private void updateConnectionUiChecking() {
        statusCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.status_green_bg));
        statusCard.setStrokeColor(ContextCompat.getColor(this, R.color.status_green_stroke));
        connectionStatusText.setTextColor(ContextCompat.getColor(this, R.color.status_green_text));
        connectionStatusText.setText(R.string.connection_checking);
        connectionDetailText.setText(R.string.connection_checking_detail);
        submitButton.setEnabled(false);
        statusText.setText("Connecting to @NawabKingMods...");
    }

    private void updateConnectionUiSuccess() {
        statusCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.status_green_bg));
        statusCard.setStrokeColor(ContextCompat.getColor(this, R.color.status_green_stroke));
        connectionStatusText.setTextColor(ContextCompat.getColor(this, R.color.status_green_text));
        
        connectionStatusText.setText(R.string.connection_connected);
        connectionDetailText.setText(R.string.connection_connected_detail);
        
        submitButton.setEnabled(true);
        statusText.setText(R.string.status_ready);
    }

    private void updateConnectionUiNeedStart() {
        statusCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.status_yellow_bg));
        statusCard.setStrokeColor(ContextCompat.getColor(this, R.color.status_yellow_stroke));
        connectionStatusText.setTextColor(ContextCompat.getColor(this, R.color.status_yellow_text));
        connectionStatusText.setText(R.string.connection_need_start);
        connectionDetailText.setText(R.string.connection_need_start_detail);
        submitButton.setEnabled(false);
        statusText.setText("Waiting for /start in Telegram...");

        scheduleAutoRetry(5000);
    }

    private void updateConnectionUiNetworkError() {
        statusCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.status_red_bg));
        statusCard.setStrokeColor(ContextCompat.getColor(this, R.color.status_red_stroke));
        connectionStatusText.setTextColor(ContextCompat.getColor(this, R.color.status_red_text));
        connectionStatusText.setText(R.string.connection_network_error);
        connectionDetailText.setText(R.string.connection_network_error_detail);
        submitButton.setEnabled(false);
        statusText.setText("Network error: Please connect to internet");

        scheduleAutoRetry(6000);
    }

    private void updateConnectionUiApiError(int statusCode) {
        statusCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.status_red_bg));
        statusCard.setStrokeColor(ContextCompat.getColor(this, R.color.status_red_stroke));
        connectionStatusText.setTextColor(ContextCompat.getColor(this, R.color.status_red_text));
        connectionStatusText.setText(R.string.connection_api_error);
        connectionDetailText.setText(R.string.connection_api_error_detail);
        submitButton.setEnabled(false);
        statusText.setText("Token Error. Long press header to reconfigure.");
    }

    private void scheduleAutoRetry(long delayMillis) {
        cancelPendingAutoRetry();
        retryConnectionRunnable = () -> {
            if (!isDestroyed() && !isFinishing() && connectedChatId == null) {
                checkBotConnection();
            }
        };
        mainHandler.postDelayed(retryConnectionRunnable, delayMillis);
    }

    private void cancelPendingAutoRetry() {
        if (retryConnectionRunnable != null) {
            mainHandler.removeCallbacks(retryConnectionRunnable);
            retryConnectionRunnable = null;
        }
    }

    // -------------------------------------------------------------
    // Phone Number Input, Validation & Send
    // -------------------------------------------------------------
    private void handleSendPhoneNumber() {
        phoneInputLayout.setError(null);

        String rawPhone = phoneInput.getText() != null ? phoneInput.getText().toString().trim() : "";

        if (TextUtils.isEmpty(rawPhone) || rawPhone.equals("+")) {
            phoneInputLayout.setError(getString(R.string.error_empty_phone));
            return;
        }

        if (!rawPhone.startsWith("+")) {
            phoneInputLayout.setError(getString(R.string.error_start_with_plus));
            return;
        }

        if (rawPhone.length() < 6) {
            phoneInputLayout.setError("Enter complete phone number with country code");
            return;
        }

        if (TextUtils.isEmpty(connectedChatId)) {
            checkBotConnection();
            return;
        }

        // Hide previous reply
        replyLayout.setVisibility(View.GONE);

        // Cyber glitch shake animation
        View inputCard = findViewById(R.id.inputCard);
        if (inputCard != null) {
            HackerAnimationHelper.animateCyberGlitch(inputCard);
        }

        // Reset buffer and start 30-Second Termux Hacker Loading
        pendingReplyContent = null;
        start30SecondTermuxLoading();

        // Construct message payload
        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String messageText = "📞 New Target Number: " + rawPhone + "\n⏰ Time: " + currentTime + "\n⚡ Tunnel: @NawabKingMods";

        sendTelegramMessage(connectedChatId, messageText, rawPhone);
    }

    private void sendTelegramMessage(String chatId, String textContent, String originalPhone) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("chat_id", chatId);
            payload.put("text", textContent);
        } catch (JSONException e) {
            handleSendFailure("Payload Error");
            return;
        }

        RequestBody body = RequestBody.create(payload.toString(), JSON_MEDIA_TYPE);
        String url = getApiBaseUrl() + "/sendMessage";

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> handleSendFailure("Check your network"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                final String respStr = response.body() != null ? response.body().string() : "";
                final int respCode = response.code();

                mainHandler.post(() -> {
                    try {
                        if (respCode != 200 || TextUtils.isEmpty(respStr)) {
                            handleSendFailure("Transmission Failed (HTTP " + respCode + ")");
                            return;
                        }

                        JSONObject json = new JSONObject(respStr);
                        if (!json.optBoolean("ok", false)) {
                            String desc = json.optString("description", "Failed to send");
                            handleSendFailure(desc);
                            return;
                        }

                        JSONObject result = json.getJSONObject("result");
                        int messageId = result.getInt("message_id");

                        // Successfully sent to Telegram
                        handleSendSuccess(originalPhone, messageId);

                    } catch (JSONException e) {
                        handleSendFailure("Transmission Error");
                    }
                });
            }
        });
    }

    private void handleSendSuccess(String phoneNumber, int messageId) {
        activeSentMessageId = messageId;

        // Add to history
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        sentHistoryList.add(0, new HistoryItem(phoneNumber, messageId, timestamp));
        updateHistoryUi();

        // Start background polling for reply
        startReplyPolling(messageId);
    }

    private void handleSendFailure(String error) {
        stop30SecondTermuxLoading();
        statusText.setText("❌ Failed: " + error);
    }

    // -------------------------------------------------------------
    // 30-Second Termux Hacker Loading Animation Engine
    // -------------------------------------------------------------
    private void start30SecondTermuxLoading() {
        stop30SecondTermuxLoading();
        isTermux30sActive = true;
        loadingStartTime = System.currentTimeMillis();
        loadingDotCycle = 0;

        loadingLayout.setVisibility(View.VISIBLE);
        HackerAnimationHelper.animateCardEntrance(loadingLayout);
        submitButton.setEnabled(false);
        phoneInput.setEnabled(false);
        statusText.setText("⚡ EXECUTING 30s QUANTUM TUNNEL DECRYPTION...");

        termuxAnimationRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isTermux30sActive || isDestroyed() || isFinishing()) return;

                long elapsedMs = System.currentTimeMillis() - loadingStartTime;
                int progressPercent = (int) Math.min(100, (elapsedMs * 100) / REQUIRED_30S_LOADING_MS);
                long secondsRemaining = Math.max(0, (REQUIRED_30S_LOADING_MS - elapsedMs) / 1000);

                // 1. Line 1: Real-time dynamic clock
                String currentTimeStr = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                loadingTimeText.setText("[TIME] " + currentTimeStr);
                loadingTimerCountdownText.setText("[" + secondsRemaining + "s]");

                // 2. Line 2: Animated Loading with 2, 3, 4, 5 dots (Loading.. -> Loading... -> Loading.... -> Loading.....)
                loadingDotCycle = (loadingDotCycle + 1) % 4; // 0, 1, 2, 3
                int numDots = loadingDotCycle + 2; // 2, 3, 4, 5 dots
                StringBuilder dots = new StringBuilder("Loading");
                for (int d = 0; d < numDots; d++) {
                    dots.append(".");
                }
                loadingDotsText.setText(dots.toString());

                // 3. Progress Bar & Hash Progression (0% to 100% ##########)
                loadingProgressBar.setProgress(progressPercent);
                int totalHashes = 20;
                int filledHashes = (progressPercent * totalHashes) / 100;
                StringBuilder hashBar = new StringBuilder();
                for (int h = 0; h < totalHashes; h++) {
                    hashBar.append(h < filledHashes ? "#" : ".");
                }
                loadingProgressBarText.setText("[PROGRESS] " + progressPercent + "% [" + hashBar.toString() + "]");

                // 4. Termux Console Hacker Logs Progression
                updateTermuxConsoleLog(elapsedMs, progressPercent);

                // Check if 30 seconds have completed
                if (elapsedMs >= REQUIRED_30S_LOADING_MS) {
                    on30SecondLoadingComplete();
                    return;
                }

                // Tick every 250ms for smooth 30s animation
                mainHandler.postDelayed(this, 250);
            }
        };

        mainHandler.post(termuxAnimationRunnable);
    }

    private void updateTermuxConsoleLog(long elapsedMs, int progressPercent) {
        StringBuilder termux = new StringBuilder();
        termux.append("> !!!!!!!\n");
        termux.append("> ¥NawabKingMods! Loading...\n");
        termux.append("> !!!Otp Generating!!! Loading...\n");
        termux.append("> Server is alive#####\n");
        termux.append("> •••••••\n");

        if (elapsedMs >= 5000) {
            termux.append("> [INJECT] Bypassing secure gateway...\n");
        }
        if (elapsedMs >= 10000) {
            termux.append("> [TUNNEL] Intercepting encrypted SMS/OTP stream...\n");
        }
        if (elapsedMs >= 16000) {
            termux.append("> [CIPHER] Quantum decrypting payload handshake...\n");
        }
        if (elapsedMs >= 22000) {
            termux.append("> [STATUS] 0% to 100% ####################\n");
        }
        if (elapsedMs >= 26000) {
            termux.append("> [SYS] Decryption pipeline engaged [READY]...\n");
        }

        if (pendingReplyContent != null) {
            termux.append("> [BUFFER] Intercepted OTP feed captured. Holding cipher sync until 30s lock release...");
        } else {
            termux.append("> [SCAN] Intercepting decoded packets...");
        }

        termuxConsoleText.setText(termux.toString());
    }

    private void on30SecondLoadingComplete() {
        isTermux30sActive = false;
        
        // 30 seconds are officially finished!
        if (pendingReplyContent != null) {
            // Reply arrived during the 30s - display it now in hacker style!
            displayHackerOtpReply(pendingReplyContent);
        } else {
            // 30s finished but message hasn't arrived yet, keep showing terminal waiting
            loadingProgressBar.setProgress(100);
            loadingProgressBarText.setText("[PROGRESS] 100% [####################]");
            loadingDotsText.setText("Awaiting OTP release...");
            termuxConsoleText.setText("> !!!!!!!\n> ¥NawabKingMods! Loading...\n> !!!Otp Generating!!! Loading...\n> Server is alive#####\n> •••••••\n> [STATUS] 100% [####################]\n> [AWAIT] Finalizing OTP packet arrival from @NawabKingMods...");
            statusText.setText("Waiting for Telegram payload arrival...");
        }
    }

    private void stop30SecondTermuxLoading() {
        isTermux30sActive = false;
        if (termuxAnimationRunnable != null) {
            mainHandler.removeCallbacks(termuxAnimationRunnable);
            termuxAnimationRunnable = null;
        }
        loadingLayout.setVisibility(View.GONE);
        submitButton.setEnabled(connectedChatId != null);
        phoneInput.setEnabled(true);
    }

    // -------------------------------------------------------------
    // Polling for Reply (matching message_id)
    // -------------------------------------------------------------
    private void startReplyPolling(int targetMessageId) {
        stopReplyPolling();
        isPollingForReply = true;
        pollingAttempts = 0;

        pollReplyRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isPollingForReply || isDestroyed() || isFinishing()) return;

                pollingAttempts++;

                if (pollingAttempts > MAX_POLLING_ATTEMPTS) {
                    stopReplyPolling();
                    handlePollingTimeout();
                    return;
                }

                pollGetUpdatesForReply(targetMessageId);
            }
        };

        mainHandler.postDelayed(pollReplyRunnable, 1000);
    }

    private void pollGetUpdatesForReply(int targetMessageId) {
        long offset = lastUpdateId + 1;
        String url = getApiBaseUrl() + "/getUpdates?offset=" + offset + "&limit=10&timeout=2";

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> scheduleNextPoll(targetMessageId));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                final String respStr = response.body() != null ? response.body().string() : "";

                mainHandler.post(() -> {
                    if (!isPollingForReply) return;

                    boolean foundReply = false;
                    String replyTextContent = "";

                    try {
                        if (!TextUtils.isEmpty(respStr)) {
                            JSONObject json = new JSONObject(respStr);
                            if (json.optBoolean("ok", false)) {
                                JSONArray result = json.optJSONArray("result");
                                if (result != null && result.length() > 0) {
                                    for (int i = 0; i < result.length(); i++) {
                                        JSONObject update = result.getJSONObject(i);
                                        long updateId = update.optLong("update_id", 0);
                                        if (updateId > lastUpdateId) {
                                            lastUpdateId = updateId;
                                        }

                                        if (update.has("message")) {
                                            JSONObject msg = update.getJSONObject("message");
                                            if (msg.has("reply_to_message")) {
                                                JSONObject replyTo = msg.getJSONObject("reply_to_message");
                                                int replyToMsgId = replyTo.optInt("message_id", -1);

                                                if (replyToMsgId == targetMessageId) {
                                                    // Targeted reply found!
                                                    foundReply = true;
                                                    replyTextContent = msg.optString("text", "[Encrypted Payload / OTP]");
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (JSONException ignored) {
                    }

                    if (foundReply) {
                        onReplyIntercepted(replyTextContent);
                    } else {
                        scheduleNextPoll(targetMessageId);
                    }
                });
            }
        });
    }

    private void scheduleNextPoll(int targetMessageId) {
        if (isPollingForReply && !isDestroyed() && !isFinishing()) {
            mainHandler.postDelayed(pollReplyRunnable, 2000);
        }
    }

    private void onReplyIntercepted(String content) {
        stopReplyPolling();
        pendingReplyContent = content;

        // Strict 30-Second Rule: Check if 30 seconds have already passed
        long elapsedMs = System.currentTimeMillis() - loadingStartTime;
        if (!isTermux30sActive || elapsedMs >= REQUIRED_30S_LOADING_MS) {
            displayHackerOtpReply(content);
        }
    }

    private void displayHackerOtpReply(String content) {
        stop30SecondTermuxLoading();

        replyLayout.setVisibility(View.VISIBLE);
        HackerAnimationHelper.animateCardEntrance(replyLayout);

        replySenderText.setText(R.string.reply_hacker_header);

        // Extract 4-8 digit OTP Code for highlighted badge display
        String extractedOtp = extractOtpCode(content);
        if (!TextUtils.isEmpty(extractedOtp)) {
            otpHighlightBox.setVisibility(View.VISIBLE);
            otpCodeText.setText(extractedOtp);
            HackerAnimationHelper.animateDecryptionText(otpCodeText, extractedOtp, null);
        } else {
            otpHighlightBox.setVisibility(View.GONE);
        }

        // Decrypt text scramble animation
        HackerAnimationHelper.animateDecryptionText(replyContentText, content, null);

        String currentTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        replyTimeText.setText(currentTime);

        phoneInput.setText("+");
        submitButton.setEnabled(connectedChatId != null);
        phoneInput.setEnabled(true);

        statusText.setText("⚡ INTERCEPT COMPLETE // Credit by @NawabKingMods");
    }

    private String extractOtpCode(String text) {
        if (TextUtils.isEmpty(text)) return null;
        Pattern pattern = Pattern.compile("\\b\\d{4,8}\\b");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private void handlePollingTimeout() {
        stop30SecondTermuxLoading();
        statusText.setText("⏰ Timeout: No reply received.");
    }

    private void stopReplyPolling() {
        isPollingForReply = false;
        if (pollReplyRunnable != null) {
            mainHandler.removeCallbacks(pollReplyRunnable);
            pollReplyRunnable = null;
        }
    }

    // -------------------------------------------------------------
    // History Model & Clipboard Operations
    // -------------------------------------------------------------
    public static class HistoryItem {
        public final String phoneNumber;
        public final int messageId;
        public final String timestamp;

        public HistoryItem(String phoneNumber, int messageId, String timestamp) {
            this.phoneNumber = phoneNumber;
            this.messageId = messageId;
            this.timestamp = timestamp;
        }
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("Copied Text", text);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "📋 Copied to clipboard!", Toast.LENGTH_SHORT).show();
        }
    }

    private void showClearHistoryConfirmationDialog() {
        if (sentHistoryList.isEmpty()) return;

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_clear_title)
                .setMessage(R.string.dialog_clear_msg)
                .setIcon(R.drawable.ic_delete)
                .setPositiveButton(R.string.dialog_clear_confirm, (dialog, which) -> {
                    sentHistoryList.clear();
                    updateHistoryUi();
                    View historyCard = findViewById(R.id.historyCard);
                    if (historyCard != null) {
                        HackerAnimationHelper.animateCyberGlitch(historyCard);
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void updateHistoryUi() {
        if (sentHistoryList.isEmpty()) {
            historyEmptyText.setVisibility(View.VISIBLE);
            historyContainer.setVisibility(View.GONE);
            historyContainer.removeAllViews();
            historyCountText.setText("0 sent");
            if (btnClearHistory != null) {
                btnClearHistory.setVisibility(View.GONE);
            }
        } else {
            historyEmptyText.setVisibility(View.GONE);
            historyContainer.setVisibility(View.VISIBLE);
            historyContainer.removeAllViews();
            historyCountText.setText(sentHistoryList.size() + " sent");
            if (btnClearHistory != null) {
                btnClearHistory.setVisibility(View.VISIBLE);
            }

            LayoutInflater inflater = LayoutInflater.from(this);
            for (HistoryItem item : sentHistoryList) {
                View itemView = inflater.inflate(R.layout.item_history, historyContainer, false);
                TextView tvPhone = itemView.findViewById(R.id.historyItemPhone);
                TextView tvDetail = itemView.findViewById(R.id.historyItemDetail);
                MaterialButton btnCopy = itemView.findViewById(R.id.btnCopyHistoryItem);

                tvPhone.setText(item.phoneNumber);
                String detail = item.timestamp + " • Msg #" + item.messageId + " • @NawabKingMods";
                tvDetail.setText(detail);

                btnCopy.setOnClickListener(v -> copyToClipboard(item.phoneNumber));

                itemView.setOnClickListener(v -> {
                    phoneInput.setText(item.phoneNumber);
                    phoneInput.setSelection(item.phoneNumber.length());
                });

                historyContainer.addView(itemView);
                HackerAnimationHelper.animateCardEntrance(itemView);
            }
        }
    }

    // -------------------------------------------------------------
    // Token Settings Dialog (Persisted in SharedPreferences)
    // -------------------------------------------------------------
    private void showTokenSettingsDialog() {
        final EditText input = new EditText(this);
        input.setText(currentBotToken);
        input.setSingleLine(true);
        input.setPadding(40, 30, 40, 30);
        input.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        input.setBackgroundColor(ContextCompat.getColor(this, R.color.cyber_code_bg));

        new AlertDialog.Builder(this)
                .setTitle("⚙️ Telegram Bot Token")
                .setMessage("Enter custom BotFather token (Saved permanently):")
                .setView(input)
                .setPositiveButton("Save & Connect", (dialog, which) -> {
                    String newToken = input.getText().toString().trim();
                    if (!TextUtils.isEmpty(newToken)) {
                        currentBotToken = newToken;
                        sharedPreferences.edit().putString(KEY_SAVED_TOKEN, currentBotToken).apply();
                        connectedChatId = null;
                        lastUpdateId = 0;
                        checkBotConnection();
                    }
                })
                .setNeutralButton("Reset Default", (dialog, which) -> {
                    currentBotToken = DEFAULT_BOT_TOKEN;
                    sharedPreferences.edit().remove(KEY_SAVED_TOKEN).apply();
                    connectedChatId = null;
                    lastUpdateId = 0;
                    checkBotConnection();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // -------------------------------------------------------------
    // Lifecycle Management
    // -------------------------------------------------------------
    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelPendingAutoRetry();
        stop30SecondTermuxLoading();
        stopReplyPolling();

        if (httpClient != null) {
            httpClient.dispatcher().cancelAll();
        }
        mainHandler.removeCallbacksAndMessages(null);
    }
}
