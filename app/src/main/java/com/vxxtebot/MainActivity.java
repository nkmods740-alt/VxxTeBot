package com.vxxtebot;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
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

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    // -------------------------------------------------------------
    // Telegram Bot Configuration
    // -------------------------------------------------------------
    // Set your Telegram Bot Token below from BotFather
    public static final String BOT_TOKEN = "7731278146:AAGf7zO_R2F4j_vW3Z1q4G_jK7y9L0M1n2O";
    
    private String currentBotToken = BOT_TOKEN;
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

    private MaterialCardView loadingLayout;
    private ProgressBar loadingProgressBar;
    private TextView loadingStatusText;
    private TextView loadingDetailText;

    private MaterialCardView replyLayout;
    private TextView replySenderText;
    private TextView replyContentText;
    private TextView replyTimeText;

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
    private static final int MAX_POLLING_ATTEMPTS = 150; // ~5 minutes (2 sec intervals)

    private int activeSentMessageId = -1;
    private final List<HistoryItem> sentHistoryList = new ArrayList<>();

    // Runnables for delayed tasks
    private Runnable retryConnectionRunnable;
    private Runnable pollReplyRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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

        loadingLayout = findViewById(R.id.loadingLayout);
        loadingProgressBar = findViewById(R.id.loadingProgressBar);
        loadingStatusText = findViewById(R.id.loadingStatusText);
        loadingDetailText = findViewById(R.id.loadingDetailText);

        replyLayout = findViewById(R.id.replyLayout);
        replySenderText = findViewById(R.id.replySenderText);
        replyContentText = findViewById(R.id.replyContentText);
        replyTimeText = findViewById(R.id.replyTimeText);

        statusText = findViewById(R.id.statusText);
        historyEmptyText = findViewById(R.id.historyEmptyText);
        historyContainer = findViewById(R.id.historyContainer);
        historyCountText = findViewById(R.id.historyCountText);
        btnClearHistory = findViewById(R.id.btnClearHistory);

        // Header click to optionally customize bot token if needed
        findViewById(R.id.headerCard).setOnLongClickListener(v -> {
            showTokenSettingsDialog();
            return true;
        });
    }

    private void setupListeners() {
        btnRefreshConnection.setOnClickListener(v -> {
            if (isCheckingConnection) return;
            cancelPendingAutoRetry();
            checkBotConnection();
        });

        submitButton.setOnClickListener(v -> handleSendPhoneNumber());

        btnClearHistory.setOnClickListener(v -> showClearHistoryConfirmationDialog());
    }

    private String getApiBaseUrl() {
        return "https://api.telegram.org/bot" + currentBotToken.trim();
    }

    // -------------------------------------------------------------
    // Step 2: App Start / Connect to Bot (getUpdates)
    // -------------------------------------------------------------
    private void checkBotConnection() {
        if (isCheckingConnection) return;
        isCheckingConnection = true;

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
                    updateConnectionUiError("❌ Connection failed: " + e.getLocalizedMessage(), true);
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
                updateConnectionUiError("❌ API Error (Code: " + statusCode + "). Check Bot Token.", false);
                return;
            }

            JSONObject jsonObject = new JSONObject(jsonString);
            boolean ok = jsonObject.optBoolean("ok", false);

            if (!ok) {
                String description = jsonObject.optString("description", "Unknown Telegram API error");
                updateConnectionUiError("❌ Telegram: " + description, false);
                return;
            }

            JSONArray result = jsonObject.optJSONArray("result");
            if (result != null && result.length() > 0) {
                // Find latest update with a valid chat_id
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
                    updateConnectionUiSuccess(connectedChatTitle, connectedChatId);
                } else {
                    updateConnectionUiNeedStart();
                }
            } else {
                // No updates available yet. Tell user to /start bot in Telegram
                updateConnectionUiNeedStart();
            }

        } catch (JSONException e) {
            updateConnectionUiError("❌ JSON Parse error: " + e.getLocalizedMessage(), true);
        }
    }

    private void updateConnectionUiChecking() {
        statusCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.status_blue_bg));
        statusCard.setStrokeColor(ContextCompat.getColor(this, R.color.status_blue_stroke));
        connectionStatusText.setTextColor(ContextCompat.getColor(this, R.color.status_blue_text));
        connectionStatusText.setText(R.string.connection_checking);
        connectionDetailText.setText("Connecting to Telegram Bot API...");
        submitButton.setEnabled(false);
        statusText.setText("Connecting to Telegram...");
    }

    private void updateConnectionUiSuccess(String chatTitle, String chatId) {
        statusCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.status_green_bg));
        statusCard.setStrokeColor(ContextCompat.getColor(this, R.color.status_green_stroke));
        connectionStatusText.setTextColor(ContextCompat.getColor(this, R.color.status_green_text));
        
        String connectedMsg = "✅ Connected! (" + chatTitle + ")";
        connectionStatusText.setText(connectedMsg);
        connectionDetailText.setText("Target Chat ID: " + chatId + " • Ready to send numbers");
        
        submitButton.setEnabled(true);
        statusText.setText(R.string.status_ready);
        Toast.makeText(this, "✅ Telegram Bot se connect ho gaya!", Toast.LENGTH_SHORT).show();
    }

    private void updateConnectionUiNeedStart() {
        statusCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.status_yellow_bg));
        statusCard.setStrokeColor(ContextCompat.getColor(this, R.color.status_yellow_stroke));
        connectionStatusText.setTextColor(ContextCompat.getColor(this, R.color.status_yellow_text));
        connectionStatusText.setText(R.string.connection_need_start);
        connectionDetailText.setText("Telegram open karke bot ko /start karo. Retrying in 5 seconds...");
        submitButton.setEnabled(false);
        statusText.setText("⚠️ Bot ko /start karo!");

        // Schedule auto retry after 5 seconds
        scheduleAutoRetry(5000);
    }

    private void updateConnectionUiError(String errorMsg, boolean scheduleRetry) {
        statusCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.status_red_bg));
        statusCard.setStrokeColor(ContextCompat.getColor(this, R.color.status_red_stroke));
        connectionStatusText.setTextColor(ContextCompat.getColor(this, R.color.status_red_text));
        connectionStatusText.setText(errorMsg);
        connectionDetailText.setText("Tap refresh button to retry connection.");
        submitButton.setEnabled(false);
        statusText.setText("❌ Connection failed");

        if (scheduleRetry) {
            scheduleAutoRetry(6000);
        }
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
    // Step 3 & 4: Phone Number Input, Validation & Send
    // -------------------------------------------------------------
    private void handleSendPhoneNumber() {
        phoneInputLayout.setError(null);

        String rawPhone = phoneInput.getText() != null ? phoneInput.getText().toString().trim() : "";

        // 1. Check empty or only "+"
        if (TextUtils.isEmpty(rawPhone) || rawPhone.equals("+")) {
            phoneInputLayout.setError(getString(R.string.error_empty_phone));
            Toast.makeText(this, getString(R.string.error_empty_phone), Toast.LENGTH_SHORT).show();
            return;
        }

        // 2. Check starts with "+"
        if (!rawPhone.startsWith("+")) {
            phoneInputLayout.setError(getString(R.string.error_start_with_plus));
            Toast.makeText(this, getString(R.string.error_start_with_plus), Toast.LENGTH_SHORT).show();
            return;
        }

        // 3. Minimum length validation (e.g. +923001234567 has at least 6-15 digits)
        if (rawPhone.length() < 6) {
            phoneInputLayout.setError("Enter complete phone number with country code");
            return;
        }

        // 4. Check connected chat id
        if (TextUtils.isEmpty(connectedChatId)) {
            Toast.makeText(this, getString(R.string.error_connect_first), Toast.LENGTH_LONG).show();
            checkBotConnection();
            return;
        }

        // Hide old reply layout
        replyLayout.setVisibility(View.GONE);

        // Show loading state
        setSendingState(true);
        loadingStatusText.setText(R.string.status_sending);
        loadingDetailText.setText("Sending " + rawPhone + " to Telegram Chat (" + connectedChatId + ")...");
        statusText.setText(R.string.status_sending);

        // Construct message payload
        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String messageText = "📞 New Number Received: " + rawPhone + "\n⏰ Time: " + currentTime;

        sendTelegramMessage(connectedChatId, messageText, rawPhone);
    }

    private void sendTelegramMessage(String chatId, String textContent, String originalPhone) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("chat_id", chatId);
            payload.put("text", textContent);
        } catch (JSONException e) {
            handleSendFailure("JSON error: " + e.getLocalizedMessage());
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
                mainHandler.post(() -> handleSendFailure("Network Error: " + e.getLocalizedMessage()));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                final String respStr = response.body() != null ? response.body().string() : "";
                final int respCode = response.code();

                mainHandler.post(() -> {
                    try {
                        if (respCode != 200 || TextUtils.isEmpty(respStr)) {
                            handleSendFailure("Telegram API returned HTTP " + respCode);
                            return;
                        }

                        JSONObject json = new JSONObject(respStr);
                        if (!json.optBoolean("ok", false)) {
                            String desc = json.optString("description", "Failed to send message");
                            handleSendFailure("Error: " + desc);
                            return;
                        }

                        JSONObject result = json.getJSONObject("result");
                        int messageId = result.getInt("message_id");

                        // Successfully sent!
                        handleSendSuccess(originalPhone, messageId);

                    } catch (JSONException e) {
                        handleSendFailure("Parse error: " + e.getLocalizedMessage());
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

        Toast.makeText(this, "✅ Number sent! Waiting for reply...", Toast.LENGTH_SHORT).show();

        // Start Polling for Reply
        startReplyPolling(messageId);
    }

    private void handleSendFailure(String error) {
        setSendingState(false);
        statusText.setText("❌ Failed to send: " + error);
        Toast.makeText(this, "❌ Send failed: " + error, Toast.LENGTH_LONG).show();
    }

    private void setSendingState(boolean isSending) {
        loadingLayout.setVisibility(isSending ? View.VISIBLE : View.GONE);
        submitButton.setEnabled(!isSending && connectedChatId != null);
        phoneInput.setEnabled(!isSending);
    }

    // -------------------------------------------------------------
    // Step 5: Polling for Reply (matching message_id)
    // -------------------------------------------------------------
    private void startReplyPolling(int targetMessageId) {
        stopReplyPolling();
        isPollingForReply = true;
        pollingAttempts = 0;

        loadingLayout.setVisibility(View.VISIBLE);
        loadingStatusText.setText("⏳ Waiting for reply on Telegram...");
        loadingDetailText.setText("Tracking Message ID: #" + targetMessageId + " (Attempt 1/" + MAX_POLLING_ATTEMPTS + ")");
        statusText.setText("Polling Telegram for replies...");

        pollReplyRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isPollingForReply || isDestroyed() || isFinishing()) return;

                pollingAttempts++;
                loadingDetailText.setText("Tracking Message ID: #" + targetMessageId + " (Attempt " + pollingAttempts + "/" + MAX_POLLING_ATTEMPTS + ")");

                if (pollingAttempts > MAX_POLLING_ATTEMPTS) {
                    // Timeout!
                    stopReplyPolling();
                    handlePollingTimeout();
                    return;
                }

                // Poll Telegram getUpdates
                pollGetUpdatesForReply(targetMessageId);
            }
        };

        // Trigger first poll after 1 second
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
                    String replySender = "";

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
                                                    // Matched!
                                                    foundReply = true;
                                                    replyTextContent = msg.optString("text", "[Non-text reply / media]");

                                                    if (msg.has("from")) {
                                                        JSONObject from = msg.getJSONObject("from");
                                                        String fn = from.optString("first_name", "");
                                                        String ln = from.optString("last_name", "");
                                                        String un = from.optString("username", "");
                                                        replySender = !TextUtils.isEmpty(un) ? "@" + un : (fn + " " + ln).trim();
                                                    }
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
                        handleReplyReceived(replySender, replyTextContent);
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

    private void handleReplyReceived(String sender, String content) {
        stopReplyPolling();

        // Update Reply UI
        replyLayout.setVisibility(View.VISIBLE);
        replySenderText.setText("From: " + (TextUtils.isEmpty(sender) ? "Telegram User" : sender));
        replyContentText.setText(content);
        String currentTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        replyTimeText.setText(currentTime);

        // Reset Input Field
        phoneInput.setText("+");
        setSendingState(false);

        statusText.setText("✅ Reply received from Telegram!");
        Toast.makeText(this, "📨 Reply aaya: " + content, Toast.LENGTH_LONG).show();
    }

    private void handlePollingTimeout() {
        setSendingState(false);
        statusText.setText("⏰ Timeout! No reply received within 5 minutes.");
        Toast.makeText(this, "⏰ Timeout! No reply received.", Toast.LENGTH_LONG).show();
    }

    private void stopReplyPolling() {
        isPollingForReply = false;
        if (pollReplyRunnable != null) {
            mainHandler.removeCallbacks(pollReplyRunnable);
            pollReplyRunnable = null;
        }
        loadingLayout.setVisibility(View.GONE);
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
            ClipData clip = ClipData.newPlainText("Phone Number", text);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "📋 Copied " + text + " to clipboard!", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(MainActivity.this, R.string.history_cleared, Toast.LENGTH_SHORT).show();
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
                String detail = item.timestamp + " • Msg #" + item.messageId;
                tvDetail.setText(detail);

                btnCopy.setOnClickListener(v -> copyToClipboard(item.phoneNumber));

                itemView.setOnClickListener(v -> {
                    phoneInput.setText(item.phoneNumber);
                    phoneInput.setSelection(item.phoneNumber.length());
                    Toast.makeText(this, "Selected " + item.phoneNumber, Toast.LENGTH_SHORT).show();
                });

                historyContainer.addView(itemView);
            }
        }
    }

    // -------------------------------------------------------------
    // Token Settings Dialog (Optional Customization)
    // -------------------------------------------------------------
    private void showTokenSettingsDialog() {
        final EditText input = new EditText(this);
        input.setText(currentBotToken);
        input.setSingleLine(true);
        input.setPadding(40, 30, 40, 30);

        new AlertDialog.Builder(this)
                .setTitle("Telegram Bot Token")
                .setMessage("BotFather token set karo:")
                .setView(input)
                .setPositiveButton("Save & Connect", (dialog, which) -> {
                    String newToken = input.getText().toString().trim();
                    if (!TextUtils.isEmpty(newToken)) {
                        currentBotToken = newToken;
                        connectedChatId = null;
                        lastUpdateId = 0;
                        checkBotConnection();
                        Toast.makeText(MainActivity.this, "Bot Token updated!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // -------------------------------------------------------------
    // Step 8: Lifecycle Management
    // -------------------------------------------------------------
    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelPendingAutoRetry();
        stopReplyPolling();

        if (httpClient != null) {
            httpClient.dispatcher().cancelAll();
        }
        mainHandler.removeCallbacksAndMessages(null);
    }
}
