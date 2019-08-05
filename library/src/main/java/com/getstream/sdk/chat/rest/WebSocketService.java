package com.getstream.sdk.chat.rest;

import android.os.Handler;
import android.util.Log;

import com.getstream.sdk.chat.enums.EventType;
import com.getstream.sdk.chat.interfaces.WSResponseHandler;
import com.getstream.sdk.chat.model.Event;
import com.getstream.sdk.chat.utils.Global;
import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

//TODO: thread-safety
public class WebSocketService extends WebSocketListener {

    private final String TAG = WebSocketService.class.getSimpleName();

    private WSResponseHandler webSocketListener;
    private String wsURL;
    private String clientID;
    private String userID;
    private OkHttpClient httpClient;
    protected EchoWebSocketListener listener;
    private WebSocket webSocket;

    /** The connection is considered resolved after the WS connection returned a good message */
    private boolean connectionResolved;

    /** We only make 1 attempt to reconnect at the same time.. */
    private boolean isConnecting;

    /** Boolean that indicates if we have a working connection to the server */
    private boolean isHealthy;

    /** Store the last event time for health checks */
    private Date lastEvent;

    /** Send a health check message every 30 seconds */
    private int healthCheckInterval = 30 * 1000;

    /** consecutive failures influence the duration of the timeout */
    private int consecutiveFailures;

    private int wsId;

    private boolean isConnecting() {
        return isConnecting;
    }

    private void setConnecting(boolean connecting) {
        isConnecting = connecting;
    }

    private boolean isHealthy() {
        return isHealthy;
    }

    private void setHealthy(boolean healthy) {
        isHealthy = healthy;
    }

    private Date getLastEvent() {
        return lastEvent;
    }

    private void setLastEvent(Date lastEvent) {
        this.lastEvent = lastEvent;
    }

    private int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    private void resetConsecutiveFailures() {
        this.consecutiveFailures = 0;
    }

    public WebSocketService(String wsURL, String clientID, String userID, WSResponseHandler webSocketListener) {
        this.wsURL = wsURL;
        this.clientID = clientID;
        this.userID = userID;
        this.webSocketListener = webSocketListener;
    }

    // TODO: raise exceptions instead of silently continue
    public void connect() {
        Log.i(TAG, "connect...");

        if (isConnecting()) {
            Log.w(TAG, "already connecting");
            return;
        }

        wsId = 1;
        setConnecting(true);
        resetConsecutiveFailures();
        setupWS();
        startMonitor();
    }

    // TODO: check previous state and clean up if needed
    private void setupWS(){
        Log.i(TAG, "setupWS");

        httpClient = new OkHttpClient();
        Request request = new Request.Builder().url(wsURL).build();
        listener = new EchoWebSocketListener();
        webSocket = httpClient.newWebSocket(request, listener);
        httpClient.dispatcher().executorService().shutdown();
    }

    private void setHealth(boolean healthy) {
        Log.i(TAG, "setHealth " + healthy);
        if (healthy && !isHealthy()) {
            setHealthy(true);
            Event wentOnline = new Event(true);
            webSocketListener.handleWSEvent(wentOnline);
        }
        if (!healthy && isHealthy()) {
            setHealthy(false);
            Log.i(TAG, "spawn mOfflineNotifier");
            mHandler.postDelayed(mOfflineNotifier, 5000);
        }
    }

    private int getRetryInterval() {
		int max = Math.min(500 + getConsecutiveFailures() * 2000, 25000);
		int min = Math.min(Math.max(250, (getConsecutiveFailures() - 1) * 2000), 25000);
        return (int) Math.floor(Math.random() * (max - min) + min);
    }

    private void reconnect(){
        Log.i(TAG, "reconnecting...");
        if (isConnecting() || isHealthy()) {
            return;
        }
        mHandler.postDelayed(mReconnect, getRetryInterval());
    }

    private Handler mHandler = new Handler();

    private Runnable mOfflineNotifier = new Runnable() {
        @Override
        public void run() {
            if (!isHealthy()) {
                Event wentOffline = new Event(false);
                webSocketListener.handleWSEvent(wentOffline);
            }
        }
    };

    private Runnable mMonitor = new Runnable() {
        @Override
        public void run() {
            long millisNow = new Date().getTime();
            int monitorInterval = 1000;
            if (getLastEvent() != null) {
                if (millisNow - getLastEvent().getTime() > (healthCheckInterval + 10 * 1000)) {
                    consecutiveFailures += 1;
                    setHealth(false);
                    reconnect();
                }
            }
            mHandler.postDelayed(mHealthCheck, monitorInterval);
        }
    };

    private Runnable mHealthCheck = new Runnable() {
        @Override
        public void run() {
            try {
                Event event = new Event();
                event.setType(EventType.HEALTH_CHECK);
                event.setClientId(clientID);
                event.setUserId(userID);
                webSocket.send(new Gson().toJson(event));
            } finally {
                mHandler.postDelayed(mHealthCheck, healthCheckInterval);
            }
        }
    };

    private Runnable mReconnect = () -> {
        if (isConnecting() || isHealthy()) {
            return;
        }

        destroyCurrentWSConnection();
        setupWS();
    };

    private void startMonitor() {
        mHealthCheck.run();
        mMonitor.run();
    }

    private boolean isConnectionResolved() {
        return connectionResolved;
    }

    private void setConnectionResolved() {
        this.connectionResolved = true;
    }

    private class EchoWebSocketListener extends WebSocketListener {
        private static final int NORMAL_CLOSURE_STATUS = 1000;

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            setHealth(true);
            setConnecting(false);
            resetConsecutiveFailures();
            if (wsId > 1) {
                webSocketListener.handleWSRecover();
            }
            Log.d(TAG, "WebSocket Connected : " + response);
            Global.noConnection = false;
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            Log.d(TAG, "WebSocket Response : " + text);
            JSONObject json;

            try {
                json = new JSONObject(text);
            } catch (JSONException e) {
                e.printStackTrace();
                return;
            }

            Event event = Parser.parseEvent(json);
            setLastEvent(new Date());

            if (isConnectionResolved()) {
                webSocketListener.handleWSConnectReply(event);
            } else {
                setConnectionResolved();
                webSocketListener.handleWSEvent(event);
            }
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            Log.d(TAG, "Closing : " + code + " / " + reason);
            // this usually happens only when the connection fails for auth reasons
            if (code == NORMAL_CLOSURE_STATUS) {
                // TODO: propagate this upstream
                webSocket.close(code, reason);
            } else {
                consecutiveFailures += 1;
                setHealth(false);
                reconnect();
                webSocket.close(code, reason);
            }
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            try {
                Log.d(TAG, "Error: " + t.getMessage());
            } catch (Exception e) {
                e.printStackTrace();
            }

            consecutiveFailures++;
            setHealth(false);
            reconnect();
        }
    }

    private void destroyCurrentWSConnection() {
        wsId++;
        try {
            httpClient.dispatcher().cancelAll();
        } catch (Exception e) {
            e.printStackTrace();
        }
        Global.noConnection = true;
    }
}
