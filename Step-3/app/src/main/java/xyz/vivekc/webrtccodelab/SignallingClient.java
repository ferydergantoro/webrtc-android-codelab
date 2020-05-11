package xyz.vivekc.webrtccodelab;

import android.annotation.SuppressLint;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
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

    private ArrayList<String> jsonArrayToArrayList(JSONArray jsonArray) {
        ArrayList<String> dataArrayList = new ArrayList<String>();
        if (jsonArray != null) {
            for (int i=0;i<jsonArray.length();i++){
                try {
                    dataArrayList.add(jsonArray.getString(i));
                } catch (JSONException e) {
                    Log.e("SignallingClient", "jsonArrayToArrayList: ", e);
                }
            }
        }
        return dataArrayList;
    }

    public void init(String roomName, SignalingInterface signalingInterface) {
        this.roomName = roomName;
        this.callback = signalingInterface;
        try {
            SSLContext sslcontext = SSLContext.getInstance("TLS");
            sslcontext.init(null, trustAllCerts, null);
            IO.setDefaultHostnameVerifier((hostname, session) -> true);
            IO.setDefaultSSLContext(sslcontext);
            //set the socket.io url here
            //socket = IO.socket("http://127.0.0.1:1794");
            //socket = IO.socket("http://192.168.43.105:1794");
            socket = IO.socket("https://202.51.110.214:8092");
            socket.connect();
            Log.d("SignallingClient", "init() called");

            if (!this.roomName.isEmpty()) {
                emitInitStatement(this.roomName);
            }

            //room created event.
            socket.on("created", args -> {
                Log.d("SignallingClient", "created call() called with: args = [" + Arrays.toString(args) + "]");
                clientID = String.valueOf(args[1]);
                int numClients = (int) args[2];
                ArrayList<String> clients = args[3] != null ? jsonArrayToArrayList((JSONArray) args[3]) : new ArrayList<String>();
                isInitiator = true;
                callback.onCreatedRoom(clientID, numClients, clients);
            });

            //room is full event
            socket.on("full", args -> Log.d("SignallingClient", "full call() called with: args = [" + Arrays.toString(args) + "]"));

            //peer joined event
            socket.on("join", args -> {
                Log.d("SignallingClient", "join call() called with: args = [" + Arrays.toString(args) + "]");
                isChannelReady = true;
                String client_id = String.valueOf(args[1]);
                int numClients = (int) args[2];
                ArrayList<String> clients = args[3] != null ? jsonArrayToArrayList((JSONArray) args[3]) : new ArrayList<String>();
                callback.onNewPeerJoined(client_id, numClients, clients);
            });

            //when you joined a chat room successfully
            socket.on("joined", args -> {
                Log.d("SignallingClient", "joined call() called with: args = [" + Arrays.toString(args) + "]");
                isChannelReady = true;
                String client_id = String.valueOf(args[1]);
                int numClients = (int) args[2];
                ArrayList<String> clients = args[3] != null ? jsonArrayToArrayList((JSONArray) args[3]) : new ArrayList<String>();
                if (clientID == null) {
                    clientID = client_id;
                }
                /*if (clientID != null && clientID.equals(client_id)) {
                    callback.onTryToStart(client_id);
                }*/
                callback.onJoinedRoom(client_id, numClients, clients);
            });

            socket.on("connected", args -> {
                Log.d("SignallingClient", "connected call() called with: args = [" + Arrays.toString(args) + "]");
                String room = String.valueOf(args[0]);
                String client_id = String.valueOf(args[1]);
                String from_client_id = String.valueOf(args[2]);
                ArrayList<String> clients = args[3] != null ? jsonArrayToArrayList((JSONArray) args[3]) : new ArrayList<String>();
                if (!client_id.equals(clientID) && clients.size() > 2) {
                    int idxFromClient = clients.indexOf(from_client_id);
                    if ((idxFromClient >= 0) && (idxFromClient < (clients.size() - 1))) {
                        String nextClientId = clients.get(idxFromClient + 1);
                        if (nextClientId != null && nextClientId.equals(clientID) && !nextClientId.equals(client_id)) {
                            Log.d("SignallingClient", "its my turn for my clientID " + clientID + " to connect client " + client_id);
                            //Log.d("SignallingClient", "TryToStart: " + client_id);
                            //callback.onTryToStart(client_id, true);
                        }
                    }
                }
            });

            socket.on("offer", args -> {
                Log.d("SignallingClient", "offer call() called with: args = [" + Arrays.toString(args) + "]");
                Log.d("SignallingClient", "offer String received :: " + args[0]);
                if (args[0] instanceof JSONObject) {
                    String room = String.valueOf(args[1]);
                    String client_id = String.valueOf(args[2]);
                    if (room.equals(roomName) && client_id.equals(clientID)) {
                        JSONObject data = (JSONObject) args[0];
                        Log.d("SignallingClient", "offer Json Received :: " + data.toString());
                        try {
                            String type = data.getString("type");
                            if (type.equalsIgnoreCase("offer") && isStarted) {
                                String from_client_id = String.valueOf(args[3]);
                                String peerkey = String.valueOf(args[4]);
                                //ArrayList<String> clients = args[5] != null ? jsonArrayToArrayList((JSONArray) args[5]) : new ArrayList<String>();
                                callback.onOfferReceived(data, client_id, from_client_id, peerkey);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });

            socket.on("answer", args -> {
                Log.d("SignallingClient", "answer call() called with: args = [" + Arrays.toString(args) + "]");
                Log.d("SignallingClient", "answer String received :: " + args[0]);
                if (args[0] instanceof JSONObject) {
                    String room = String.valueOf(args[1]);
                    String client_id = String.valueOf(args[2]);
                    if (room.equals(roomName) && client_id.equals(clientID)) {
                        JSONObject data = (JSONObject) args[0];
                        Log.d("SignallingClient", "answer Json Received :: " + data.toString());
                        try {
                            String type = data.getString("type");
                            if (type.equalsIgnoreCase("answer") && isStarted) {
                                String from_client_id = String.valueOf(args[3]);
                                String peerkey = String.valueOf(args[4]);
                                //ArrayList<String> clients = args[5] != null ? jsonArrayToArrayList((JSONArray) args[5]) : new ArrayList<String>();
                                callback.onAnswerReceived(data, client_id, from_client_id, peerkey);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });

            socket.on("icecandidate", args -> {
                Log.d("SignallingClient", "icecandidate call() called with: args = [" + Arrays.toString(args) + "]");
                Log.d("SignallingClient", "icecandidate String received :: " + args[0]);
                if (args[0] instanceof JSONObject) {
                    String room = String.valueOf(args[1]);
                    String client_id = String.valueOf(args[2]);
                    if (room.equals(roomName) && client_id.equals(clientID)) {
                        JSONObject data = (JSONObject) args[0];
                        Log.d("SignallingClient", "icecandidate Json Received :: " + data.toString());
                        try {
                            String type = data.getString("type");
                            if (type.equalsIgnoreCase("candidate") && isStarted) {
                                String from_client_id = String.valueOf(args[3]);
                                String peerkey = String.valueOf(args[4]);
                                //ArrayList<String> clients = args[5] != null ? jsonArrayToArrayList((JSONArray) args[5]) : new ArrayList<String>();
                                callback.onIceCandidateReceived(data, client_id, from_client_id, peerkey);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });

            //log event
            socket.on("log", args -> Log.d("SignallingClient", "log call() called with: args = [" + Arrays.toString(args) + "]"));

            //bye event
            socket.on("bye", args -> {
                Log.d("SignallingClient", "bye call() called with: args = [" + Arrays.toString(args) + "]");
                if (args[0] instanceof String) {
                    String data = (String) args[0];
                    String the_room = (String) args[1];
                    String client_id = (String) args[2];
                    int numClients = (int) args[3];
                    ArrayList<String> clients = args[4] != null ? jsonArrayToArrayList((JSONArray) args[4]) : new ArrayList<String>();
                    isInitiator = (clientID != null && clients.size() > 0) ? clientID.equals(clients.get(0)) : false;
                    Log.d("SignallingClient", "isInitiator: " + isInitiator);
                    callback.onRemoteHangUp(data, client_id, numClients, clients);
                }
            });

            //messages - SDP and ICE candidates are transferred through this
            socket.on("message", args -> {
                Log.d("SignallingClient", "message call() called with: args = [" + Arrays.toString(args) + "]");
                if (args[0] instanceof String) {
                    Log.d("SignallingClient", "String received :: " + args[0]);
                    String data = (String) args[0];
                    String the_room = (String) args[1];
                    String client_Id = (String) args[2];
                    if (data.equalsIgnoreCase("got user media")) {
                        /*if (isChannelReady && !client_Id.equals(clientID)) {
                            callback.onTryToStart(client_Id, false);
                        } else*/
                        if (!isChannelReady) {
                            if (clientID != null && !clientID.isEmpty()){
                                emitJoin(roomName, clientID);
                            }
                        }
                    }
                    if (data.equalsIgnoreCase("bye")) {
                        int numClients = (int) args[3];
                        ArrayList<String> clients = args[4] != null ? jsonArrayToArrayList((JSONArray) args[4]) : new ArrayList<String>();
                        isInitiator = (clientID != null && clients.size() > 0) ? clientID.equals(clients.get(0)) : false;
                        Log.d("SignallingClient", "isInitiator: " + isInitiator);
                        callback.onRemoteHangUp(data, client_Id, numClients, clients);
                    }
                } else if (args[0] instanceof JSONObject) {
                    try {
                        String the_room = (String) args[1];
                        String client_Id = (String) args[2];
                        JSONObject data = (JSONObject) args[0];
                        Log.d("SignallingClient", "Json Received :: " + data.toString());
                        String type = data.getString("type");
                        if (type.equalsIgnoreCase("offer")) {
                            String from_client_Id = (String) args[3];
                            String peerkey = (String) args[4];
                            callback.onOfferReceived(data, client_Id, from_client_Id, peerkey);
                        } else if (type.equalsIgnoreCase("answer") && isStarted) {
                            String from_client_Id = (String) args[3];
                            String peerkey = (String) args[4];
                            callback.onAnswerReceived(data, client_Id, from_client_Id, peerkey);
                        } else if (type.equalsIgnoreCase("candidate") && isStarted) {
                            String from_client_Id = (String) args[3];
                            String peerkey = (String) args[4];
                            callback.onIceCandidateReceived(data, client_Id, from_client_Id, peerkey);
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

    public void emitMessage(String message, String client_id) {
        Log.d("SignallingClient", "emitMessage() called with: message = [" + message + "]");
        socket.emit("message", message, roomName, client_id);
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

    public void emitConnected(String client_id) {
        Log.d("SignallingClient", "emitConnected() called with: event = [" + "connected" + "], room = [" + roomName + "], from clientID = [" + clientID + "], to client_id = [" + client_id + "]");
        socket.emit("connected", roomName, client_id, clientID);
    }

    public void emitMessage(SessionDescription message, String client_id, String peerkey) {
        try {
            Log.d("SignallingClient", "emitMessage() called with: message = [" + message + "]");
            JSONObject obj = new JSONObject();
            String type = message.type.canonicalForm();
            obj.put("type", type);
            obj.put("sdp", message.description);
            Log.d("emitMessage", obj.toString());
            /*if (type.equalsIgnoreCase("offer")) {
                socket.emit("offer", obj, roomName, client_id, clientID, peerkey);
            } else if (type.equalsIgnoreCase("answer") && isStarted) {
                socket.emit("answer", obj, roomName, client_id, clientID, peerkey);
            } else if (type.equalsIgnoreCase("candidate") && isStarted) {
                socket.emit("icecandidate", obj, roomName, client_id, clientID, peerkey);
            } else { */
                socket.emit("message", obj, roomName, client_id, clientID, peerkey);
            //}
            Log.d("vivek1794", obj.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void emitIceCandidate(IceCandidate iceCandidate, String client_id, String peerkey) {
        try {
            Log.d("SignallingClient", "emitIceCandidate() called with: iceCandidate = [" + iceCandidate + "]");
            JSONObject object = new JSONObject();
            object.put("type", "candidate");
            object.put("label", iceCandidate.sdpMLineIndex);
            object.put("id", iceCandidate.sdpMid);
            object.put("candidate", iceCandidate.sdp);
            socket.emit("message", object, roomName, client_id, clientID, peerkey);
            //socket.emit("icecandidate", object, roomName, client_id, clientID, peerkey);
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

    interface SignalingInterface
    {
        void onRemoteHangUp(String msg, String client_id, int numClients, ArrayList clients);

        void onOfferReceived(JSONObject data, String client_id, String from_client_id, String peerkey);

        void onAnswerReceived(JSONObject data, String client_id, String from_client_id, String peerkey);

        void onIceCandidateReceived(JSONObject data, String client_id, String from_client_id, String peerkey);

        void onTryToStart(String client_id, boolean isNeedCall);

        void onCreatedRoom(String client_id, int numClients, ArrayList clients);

        void onJoinedRoom(String client_id, int numClients, ArrayList clients);

        void onNewPeerJoined(String client_id, int numClients, ArrayList clients);
    }
}
