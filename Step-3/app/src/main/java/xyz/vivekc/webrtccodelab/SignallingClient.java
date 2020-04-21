package xyz.vivekc.webrtccodelab;

import android.annotation.SuppressLint;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.socket.client.IO;
import io.socket.client.Socket;

/**
 * Webrtc_Step3
 * Created by vivek-3102 on 11/03/17.
 */

@SuppressWarnings("ALL")
class SignallingClient {
    private static SignallingClient instance;
    String roomName = null;
    private Socket socket;
    boolean isChannelReady = false;
    boolean isInitiator = false;
    boolean isStarted = false;
    String clientID = null;
    private SignalingInterface callback;

    private void resetAll() {
        roomName = null;
        isChannelReady = false;
        isInitiator = false;
        isStarted = false;
        clientID = null;
        socket = null;
    }

    //This piece of code should not go into production!!
    //This will help in cases where the node server is running in non-https server and you want to ignore the warnings
    @SuppressLint("TrustAllX509TrustManager")
    private final TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return new java.security.cert.X509Certificate[]{};
        }

        public void checkClientTrusted(X509Certificate[] chain,
                                       String authType) {
        }

        public void checkServerTrusted(X509Certificate[] chain,
                                       String authType) {
        }
    }};

    public static SignallingClient getInstance() {
        if (instance == null) {
            instance = new SignallingClient();
        }
        if (instance.roomName == null) {
            //set the room name here
            instance.roomName = "vivek171";
        }
        return instance;
    }

    public void init(SignalingInterface signalingInterface) {
        this.callback = signalingInterface;
        try {
            SSLContext sslcontext = SSLContext.getInstance("TLS");
            sslcontext.init(null, trustAllCerts, null);
            IO.setDefaultHostnameVerifier((hostname, session) -> true);
            IO.setDefaultSSLContext(sslcontext);
            //set the socket.io url here
            //socket = IO.socket("http://127.0.0.1:1794");
            socket = IO.socket("http://192.168.43.105:1794");
            //socket = IO.socket("http://ns2.eluon.co.id:8282");
            socket.connect();
            Log.d("SignallingClient", "init() called");

            if (!roomName.isEmpty()) {
                emitInitStatement(roomName);
            }

            //room created event.
            socket.on("created", args -> {
                Log.d("SignallingClient", "created call() called with: args = [" + Arrays.toString(args) + "]");
                clientID = String.valueOf(args[1]);
                isInitiator = true;
                callback.onCreatedRoom();
            });

            //room is full event
            socket.on("full", args -> Log.d("SignallingClient", "full call() called with: args = [" + Arrays.toString(args) + "]"));

            //peer joined event
            socket.on("join", args -> {
                Log.d("SignallingClient", "join call() called with: args = [" + Arrays.toString(args) + "]");
                isChannelReady = true;
                String client_id = String.valueOf(args[1]);
                if (clientID != null && clientID.equals(client_id)) {
                    isInitiator = true;
                }
                callback.onNewPeerJoined(client_id);
            });

            //when you joined a chat room successfully
            socket.on("joined", args -> {
                Log.d("SignallingClient", "joined call() called with: args = [" + Arrays.toString(args) + "]");
                isChannelReady = true;
                String client_id = String.valueOf(args[1]);
                if (clientID == null) {
                    clientID = client_id;
                    isInitiator = true;
                }
                callback.onJoinedRoom(client_id);
            });

            //log event
            socket.on("log", args -> Log.d("SignallingClient", "log call() called with: args = [" + Arrays.toString(args) + "]"));

            //bye event
            socket.on("bye", args -> callback.onRemoteHangUp((String) args[0]));

            //messages - SDP and ICE candidates are transferred through this
            socket.on("message", args -> {
                Log.d("SignallingClient", "message call() called with: args = [" + Arrays.toString(args) + "]");
                if (args[0] instanceof String) {
                    Log.d("SignallingClient", "String received :: " + args[0]);
                    String data = (String) args[0];
                    if (data.equalsIgnoreCase("got user media")) {
                        if (isChannelReady) {
                            callback.onTryToStart();
                        } else if (clientID != null && !clientID.isEmpty()){
                            emitJoin(roomName, clientID);
                        }
                    }
                    if (data.equalsIgnoreCase("bye")) {
                        callback.onRemoteHangUp(data);
                    }
                } else if (args[0] instanceof JSONObject) {
                    try {

                        JSONObject data = (JSONObject) args[0];
                        Log.d("SignallingClient", "Json Received :: " + data.toString());
                        String type = data.getString("type");
                        if (type.equalsIgnoreCase("offer")) {
                            callback.onOfferReceived(data);
                        } else if (type.equalsIgnoreCase("answer") && isStarted) {
                            callback.onAnswerReceived(data);
                        } else if (type.equalsIgnoreCase("candidate") && isStarted) {
                            callback.onIceCandidateReceived(data);
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (URISyntaxException | NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
        }
    }

    private void emitInitStatement(String message) {
        Log.d("SignallingClient", "emitInitStatement() called with: event = [" + "create or join" + "], message = [" + message + "]");
        socket.emit("create or join", message);
    }

    public void emitMessage(String message) {
        Log.d("SignallingClient", "emitMessage() called with: message = [" + message + "]");
        socket.emit("message", message, roomName);
    }

    public void emitJoin() {
        if (!isChannelReady && clientID != null && !clientID.isEmpty()){
            emitJoin(roomName, clientID);
        }
    }

    private void emitJoin(String room, String clientID) {
        Log.d("SignallingClient", "emitJoin() called with: event = [" + "join" + "], room = [" + room + "], clientID = [" + clientID + "]");
        socket.emit("join", room, clientID);
    }

    public void emitMessage(SessionDescription message) {
        try {
            Log.d("SignallingClient", "emitMessage() called with: message = [" + message + "]");
            JSONObject obj = new JSONObject();
            obj.put("type", message.type.canonicalForm());
            obj.put("sdp", message.description);
            Log.d("emitMessage", obj.toString());
            socket.emit("message", obj, roomName);
            Log.d("vivek1794", obj.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void emitIceCandidate(IceCandidate iceCandidate) {
        try {
            JSONObject object = new JSONObject();
            object.put("type", "candidate");
            object.put("label", iceCandidate.sdpMLineIndex);
            object.put("id", iceCandidate.sdpMid);
            object.put("candidate", iceCandidate.sdp);
            socket.emit("message", object, roomName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void close() {
        if (socket != null) {
            socket.emit("bye", roomName, clientID);
            socket.disconnect();
            socket.close();
        }
        resetAll();
    }

    interface SignalingInterface {
        void onRemoteHangUp(String msg);

        void onOfferReceived(JSONObject data);

        void onAnswerReceived(JSONObject data);

        void onIceCandidateReceived(JSONObject data);

        void onTryToStart();

        void onCreatedRoom();

        void onJoinedRoom(String clientid);

        void onNewPeerJoined(String clientid);
    }
}
