package xyz.vivekc.webrtccodelab;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@SuppressWarnings("ALL")
public class MainActivity extends AppCompatActivity implements
    View.OnClickListener,
    SignallingClient.SignalingInterface,
    RecyclerViewAdapter.ItemListener
{
    private final static long TIME_MS_TO_DELAY_ON_EACH_STEP = 100L;
    PeerConnectionFactory peerConnectionFactory;
    MediaConstraints audioConstraints;
    MediaConstraints videoConstraints;
    MediaConstraints sdpConstraints;
    VideoSource videoSource;
    VideoTrack localVideoTrack;
    AudioSource audioSource;
    AudioTrack localAudioTrack;
    SurfaceTextureHelper surfaceTextureHelper;

    SurfaceViewRenderer localVideoView;
    SurfaceViewRenderer remoteVideoView;
    //SurfaceViewRenderer remoteVideoView2;

    Button start, call, hangup;
    PeerConnection localPeer;
    //PeerConnection localPeer2;
    List<IceServer> iceServers;
    HashMap<String, IceCandidate> remoteIceJSONCandidates;
    EglBase rootEglBase;

    boolean gotUserMedia;
    List<PeerConnection.IceServer> peerIceServers = new ArrayList<>();

    final int ALL_PERMISSIONS_CODE = 1;

    private static final String TAG = "MainActivity";

    private CustomProgress customProgress;

    RecyclerView recyclerView;
    ArrayList<DataModel> arrayList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        customProgress = CustomProgress.getInstance(this, new CustomProgress.ActionProgress() {
            @Override
            public void actionCancel() {
                hangup();
            }
        });

        doInit();

        //checkPermissionToStart();
    }

    private void doInit() {

        recyclerView = (RecyclerView) findViewById(R.id.recyclerView);
        arrayList = new ArrayList<>();
        arrayList.add(new DataModel("Item 1", R.drawable.battle, "#09A9FF"));
        arrayList.add(new DataModel("Item 2", R.drawable.beer, "#3E51B1"));
        arrayList.add(new DataModel("Item 3", R.drawable.ferrari, "#673BB7"));
        arrayList.add(new DataModel("Item 4", R.drawable.jetpack_joyride, "#4BAA50"));
        arrayList.add(new DataModel("Item 5", R.drawable.three_d, "#F94336"));
        arrayList.add(new DataModel("Item 6", R.drawable.terraria, "#0A9B88"));

        RecyclerViewAdapter adapter = new RecyclerViewAdapter(this, arrayList, this);
        recyclerView.setAdapter(adapter);

        /**
         AutoFitGridLayoutManager that auto fits the cells by the column width defined.
         **/

        AutoFitGridLayoutManager layoutManager = new AutoFitGridLayoutManager(this, 500);
        recyclerView.setLayoutManager(layoutManager);


        /**
         Simple GridLayoutManager that spans two columns
         **/
        /*GridLayoutManager manager = new GridLayoutManager(this, 2, GridLayoutManager.VERTICAL, false);
        recyclerView.setLayoutManager(manager);*/

        initViews();
        initVideos();
        //getIceServers();
    }

    private void checkPermissionToStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, ALL_PERMISSIONS_CODE);
        } else {
            // all permissions already granted
            start();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == ALL_PERMISSIONS_CODE
                && grantResults.length == 2
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            // all permissions granted
            start();
        } else {
            finish();
        }
    }

    private void initViews() {
        start = (Button) findViewById(R.id.start_call);
        call = (Button) findViewById(R.id.init_call);
        hangup = findViewById(R.id.end_call);
        localVideoView = findViewById(R.id.local_gl_surface_view);
        remoteVideoView = findViewById(R.id.remote_gl_surface_view);
        //remoteVideoView2 = findViewById(R.id.remote_gl_surface_view2);

        start.setOnClickListener(this);
        call.setOnClickListener(this);
        hangup.setOnClickListener(this);
    }

    private void initVideos() {
        rootEglBase = EglBase.create();
        localVideoView.init(rootEglBase.getEglBaseContext(), null);
        remoteVideoView.init(rootEglBase.getEglBaseContext(), null);
        //remoteVideoView2.init(rootEglBase.getEglBaseContext(), null);
        localVideoView.setZOrderMediaOverlay(true);
        remoteVideoView.setZOrderMediaOverlay(false);
        //remoteVideoView2.setZOrderMediaOverlay(false);
        localVideoView.setEnableHardwareScaler(true);
        localVideoView.setMirror(true);
        remoteVideoView.setEnableHardwareScaler(true);
        //remoteVideoView2.setEnableHardwareScaler(true);
        remoteVideoView.setMirror(true);
        //remoteVideoView2.setMirror(true);
    }

    private void getIceServers() {
        //get Ice servers using xirsys
        byte[] data = new byte[0];
        //data = (ident+":"+secret).getBytes(StandardCharsets.UTF_8);
        //String ident = "vivekchanddru";
        //String secret = "ad6ce53a-e6b5-11e6-9685-937ad99985b9";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            data = ("<xirsys_ident>:<xirsys_secret>").getBytes(StandardCharsets.UTF_8);
        } else {
            try {
                data = ("<xirsys_ident>:<xirsys_secret>").getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        //Basic dml2ZWtjaGFuZGRydTphZDZjZTUzYS1lNmI1LTExZTYtOTY4NS05MzdhZDk5OTg1Yjk=
        String authToken = "Basic " + Base64.encodeToString(data, Base64.NO_WRAP);
        Utils.getInstance().getRetrofitInstance().getIceCandidates(authToken).enqueue(new Callback<TurnServerPojo>() {
            @Override
            public void onResponse(@NonNull Call<TurnServerPojo> call, @NonNull Response<TurnServerPojo> response) {
                TurnServerPojo body = response.body();
                if (body != null) {
                    iceServers = body.iceServerList.iceServers;
                }
                for (IceServer iceServer : iceServers) {
                    if (iceServer.credential == null) {
                        PeerConnection.IceServer peerIceServer = PeerConnection.IceServer.builder(iceServer.url).createIceServer();
                        peerIceServers.add(peerIceServer);
                    } else {
                        PeerConnection.IceServer peerIceServer = PeerConnection.IceServer.builder(iceServer.url)
                                .setUsername(iceServer.username)
                                .setPassword(iceServer.credential)
                                .createIceServer();
                        peerIceServers.add(peerIceServer);
                    }
                }
                Log.d("onApiResponse", "IceServers\n" + iceServers.toString());
            }

            @Override
            public void onFailure(@NonNull Call<TurnServerPojo> call, @NonNull Throwable t) {
                t.printStackTrace();
            }
        });
    }

    private void onStartSuccess() {
        start.setEnabled(false);
        call.setEnabled(true);
    }

    public void start() {
        onStartProgress();

        // keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //doInit();

        SignallingClient.getInstance().init(this);

        //Initialize PeerConnectionFactory globals.
        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        //Create a new PeerConnectionFactory instance - using Hardware encoder and decoder.
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        DefaultVideoEncoderFactory defaultVideoEncoderFactory = new DefaultVideoEncoderFactory(
                rootEglBase.getEglBaseContext(),  /* enableIntelVp8Encoder */true,  /* enableH264HighProfile */true);
        DefaultVideoDecoderFactory defaultVideoDecoderFactory = new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());
        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(defaultVideoEncoderFactory)
                .setVideoDecoderFactory(defaultVideoDecoderFactory)
                .createPeerConnectionFactory();

        //Now create a VideoCapturer instance.
        VideoCapturer videoCapturerAndroid;
        //videoCapturerAndroid = createCameraCapturer(new Camera1Enumerator(false));
        videoCapturerAndroid = createVideoCapturer();

        //Create MediaConstraints - Will be useful for specifying video and audio constraints.
        audioConstraints = new MediaConstraints();
        videoConstraints = new MediaConstraints();

        //Create a VideoSource instance
        if (videoCapturerAndroid != null) {
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext());
            videoSource = peerConnectionFactory.createVideoSource(videoCapturerAndroid.isScreencast());
            videoCapturerAndroid.initialize(surfaceTextureHelper, this, videoSource.getCapturerObserver());
        }
        String localVideoTrackLabel = generateLabelStream("100");
        Log.d(TAG, "localVideoTrackLabel: " + localVideoTrackLabel);
        localVideoTrack = peerConnectionFactory.createVideoTrack(localVideoTrackLabel, videoSource);

        //create an AudioSource instance
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        String audioTrackLabel = generateLabelStream("101");
        Log.d(TAG, "audioTrackLabel: " + audioTrackLabel);
        localAudioTrack = peerConnectionFactory.createAudioTrack(audioTrackLabel, audioSource);

        if (videoCapturerAndroid != null) {
            videoCapturerAndroid.startCapture(1024, 720, 30);
        }

        localVideoView.setVisibility(View.VISIBLE);
        // And finally, with our VideoRenderer ready, we
        // can add our renderer to the VideoTrack.
        localVideoTrack.addSink(localVideoView);
        localVideoTrack.setEnabled(true);

        localVideoView.setMirror(true);
        remoteVideoView.setMirror(true);
        //remoteVideoView2.setMirror(true);

        gotUserMedia = true;

        onStartSuccess();

        /*
        if (SignallingClient.getInstance().isInitiator) {
            onTryToStart();
        }
        */
        call();
    }

    private void onStartProgress() {
        start.setEnabled(false);
        call.setEnabled(false);
        hangup.setEnabled(false);
    }

    private void onCallSuccess() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                start.setEnabled(false);
                call.setEnabled(false);
                hangup.setEnabled(true);
            }
        });
    }

    private void onCallFailed() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showToast("Ups sorry.. something wrong happened, Please try again..");
                hideProgress();
                hangup();
            }
        });
    }

    private void call() {
        onStartProgress();

        gotUserMedia = true;

        try {
            Thread.sleep(TIME_MS_TO_DELAY_ON_EACH_STEP);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // Try to call with start here
        if (SignallingClient.getInstance().isInitiator) {
            onTryToStart();
        }
    }

    /**
     * This method will be called directly by the app when it is the initiator and has got the local media
     * or when the remote peer sends a message through socket that it is ready to transmit AV data
     */
    @Override
    public void onTryToStart() {
        runOnUiThread(() -> {
            if (!SignallingClient.getInstance().isStarted && localVideoTrack != null) {
                if (SignallingClient.getInstance().isChannelReady) {
                    //we already have video and audio tracks. Now create peerconnections
                    try {
                        Thread.sleep(TIME_MS_TO_DELAY_ON_EACH_STEP);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    createPeerConnection();
                    try {
                        Thread.sleep(TIME_MS_TO_DELAY_ON_EACH_STEP);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    SignallingClient.getInstance().isStarted = true;
                    if (SignallingClient.getInstance().isInitiator) {
                        showProgress(false);
                        doCall();
                    }
                } else {
                    showProgress(false);
                    SignallingClient.getInstance().emitJoin();
                }
            }
        });
    }

    /**
     * Creating the local peerconnection instance
     */
    private void createPeerConnection() {
        PeerConnection.RTCConfiguration rtcConfig =
                new PeerConnection.RTCConfiguration(peerIceServers);
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;

        try {
            Thread.sleep(TIME_MS_TO_DELAY_ON_EACH_STEP);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //creating localPeer
        localPeer = peerConnectionFactory.createPeerConnection(rtcConfig, new CustomPeerConnectionObserver("localPeerCreation") {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                //addQueueRemoteIceCandidate(iceCandidate);
                onIceCandidateReceived(iceCandidate);
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                showToast("Received Remote stream");
                super.onAddStream(mediaStream);
                gotRemoteStream(mediaStream);
            }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                super.onIceGatheringChange(iceGatheringState);
            }
        });

        //creating localPeer2
        /*localPeer2 = peerConnectionFactory.createPeerConnection(rtcConfig, new CustomPeerConnectionObserver("localPeerCreation2") {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                //addQueueRemoteIceCandidate(iceCandidate);
                onIceCandidateReceived(iceCandidate);
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                showToast("Received Remote stream");
                super.onAddStream(mediaStream);
                gotRemoteStream2(mediaStream);
            }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                super.onIceGatheringChange(iceGatheringState);
            }
        });*/

        //creating local mediastream
        addStreamToLocalPeer();
    }

    private void addQueueRemoteIceCandidate(IceCandidate iceCandidate) {
      if (remoteIceJSONCandidates == null) {
        remoteIceJSONCandidates = new HashMap<>();
      }
      remoteIceJSONCandidates.put(String.valueOf(new Date().getTime()), iceCandidate);
    }

    private boolean setRemoteIceCandidate() {
      if (remoteIceJSONCandidates != null && remoteIceJSONCandidates.size() > 0) {
        TreeMap<String, IceCandidate> sorted = new TreeMap<>(remoteIceJSONCandidates);
        Set<Map.Entry<String, IceCandidate>> mappings = sorted.entrySet();

        Log.d(TAG, "Ice Candidates are : ");
        for(Map.Entry<String, IceCandidate> mapping : mappings){
          Log.d(TAG, mapping.getKey() + " ==> " + mapping.getValue());
          IceCandidate iceCandidate = (IceCandidate) mapping.getValue();
          onIceCandidateReceived(iceCandidate);
        }
        remoteIceJSONCandidates = new HashMap<>();
        return true;
      }
      return false;
    }

    @SuppressLint("HardwareIds")
    private String generateLabelStream(String reffCode) {
        return reffCode + "-" + Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    /**
     * Adding the stream to the localpeer
     */
    private void addStreamToLocalPeer() {
        //creating local mediastream
        String labelMediaStream = generateLabelStream("102");
        Log.d(TAG, "labelMediaStream: " + labelMediaStream);
        MediaStream stream = peerConnectionFactory.createLocalMediaStream(labelMediaStream);
        stream.addTrack(localAudioTrack);
        stream.addTrack(localVideoTrack);
        localPeer.addStream(stream);

        /*
        labelMediaStream = generateLabelStream("103");
        Log.d(TAG, "labelMediaStream2: " + labelMediaStream);
        MediaStream stream2 = peerConnectionFactory.createLocalMediaStream(labelMediaStream);
        stream2.addTrack(localAudioTrack);
        stream2.addTrack(localVideoTrack);
        localPeer2.addStream(stream2);
        */
    }

    /**
     * This method is called when the app is the initiator - We generate the offer and send it over through socket
     * to remote peer
     */
    private void doCall() {
        sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

        //creating Offer
        localPeer.createOffer(new CustomSdpObserver("localCreateOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                localPeer.setLocalDescription(new CustomSdpObserver("localSetLocalDesc"), sessionDescription);
                Log.d("onCreateSuccess", "SignallingClient emit - creating offer from local peer");
                SignallingClient.getInstance().emitMessage(sessionDescription);
                //if (setRemoteIceCandidate()) {
                    onCallSuccess();
                //} else {
                    //onCallFailed();
                //}
            }
        }, sdpConstraints);

        //creatiZng Offer 2
        /*
        localPeer2.createOffer(new CustomSdpObserver("localCreateOffer2") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                localPeer2.setLocalDescription(new CustomSdpObserver("localSetLocalDesc2"), sessionDescription);
                Log.d("onCreateSuccess2", "SignallingClient emit - creating offer from local peer");
                SignallingClient.getInstance().emitMessage(sessionDescription);
                //if (setRemoteIceCandidate()) {
                onCallSuccess();
                //} else {
                //onCallFailed();
                //}
            }
        }, sdpConstraints);
        */
    }

    /*private void gotRemoteStream2(MediaStream stream) {
        final VideoTrack videoTrack = stream.videoTracks.get(0);
        runOnUiThread(() -> {
            try {
                remoteVideoView2.setVisibility(View.VISIBLE);
                videoTrack.setEnabled(true);
                videoTrack.addSink(remoteVideoView2);
                hideProgress();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }*/

    /**
     * Received remote peer's media stream. we will get the first video track and render it
     */
    private void gotRemoteStream(MediaStream stream) {
        //we have remote video stream. add to the renderer.
        /*try {
            Thread.sleep(TIME_MS_TO_DELAY_ON_EACH_STEP);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }*/
        final VideoTrack videoTrack = stream.videoTracks.get(0);
        runOnUiThread(() -> {
            try {
                remoteVideoView.setVisibility(View.VISIBLE);
                videoTrack.setEnabled(true);
                videoTrack.addSink(remoteVideoView);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                );
                params.gravity = Gravity.TOP | Gravity.END;
                localVideoView.setLayoutParams(params);

                hideProgress();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Received local ice candidate. Send it to remote peer through signalling for negotiation
     */
    public void onIceCandidateReceived(IceCandidate iceCandidate) {
        //we have received ice candidate. We can set it to the other peer.
        SignallingClient.getInstance().emitIceCandidate(iceCandidate);
    }

    /**
     * SignallingCallback - called when the room is created - i.e. you are the initiator
     */
    @Override
    public void onCreatedRoom() {
        showToast("You created the room");
        Log.d(TAG, "onCreatedRoom: gotUserMedia = " + gotUserMedia);
        if (gotUserMedia) {
            SignallingClient.getInstance().emitMessage("got user media");
        }
    }

    /**
     * SignallingCallback - called when you join the room - you are a participant
     */
    @Override
    public void onJoinedRoom(String clientID) {
        showToast("You joined the room");
        Log.d(TAG, "onJoinedRoom: gotUserMedia = " + gotUserMedia);
        if (gotUserMedia) {
            SignallingClient.getInstance().emitMessage("got user media");
        }
        onCallSuccess();
        hideProgress();
    }

    @Override
    public void onNewPeerJoined(String clientID) {
        showToast("Remote Peer Joined");
    }

    @Override
    public void onRemoteHangUp(String msg) {
        showToast("Remote Peer hungup");
        SignallingClient.getInstance().clientID = null;
        runOnUiThread(this::hangup);
    }

    /**
     * SignallingCallback - Called when remote peer sends offer
     */
    @Override
    public void onOfferReceived(final JSONObject data) {
        showToast("Received Offer");
        runOnUiThread(() -> {
            if (!SignallingClient.getInstance().isInitiator && !SignallingClient.getInstance().isStarted) {
                onTryToStart();
            }

            try {
                localPeer.setRemoteDescription(new CustomSdpObserver("localSetRemote"), new SessionDescription(SessionDescription.Type.OFFER, data.getString("sdp")));
                doAnswer();
                updateVideoViews(true);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
    }

    private void doAnswer() {
        localPeer.createAnswer(new CustomSdpObserver("localCreateAns") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                localPeer.setLocalDescription(new CustomSdpObserver("localSetLocal"), sessionDescription);
                SignallingClient.getInstance().emitMessage(sessionDescription);
            }
        }, new MediaConstraints());
    }

    /**
     * SignallingCallback - Called when remote peer sends answer to your offer
     */

    @Override
    public void onAnswerReceived(JSONObject data) {
        showToast("Received Answer");
        try {
            localPeer.setRemoteDescription(new CustomSdpObserver("localSetRemote"), new SessionDescription(SessionDescription.Type.fromCanonicalForm(data.getString("type").toLowerCase()), data.getString("sdp")));
            updateVideoViews(true);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Remote IceCandidate received
     */
    @Override
    public void onIceCandidateReceived(JSONObject data) {
        try {
            if (localPeer != null) {
                localPeer.addIceCandidate(new IceCandidate(data.getString("id"), data.getInt("label"), data.getString("candidate")));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void updateVideoViews(final boolean remoteVisible) {
        runOnUiThread(() -> {
            ViewGroup.LayoutParams params = localVideoView.getLayoutParams();
            if (remoteVisible) {
                params.height = dpToPx(100);
                params.width = dpToPx(100);
            } else {
                params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            }
            localVideoView.setLayoutParams(params);
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        hideProgress();
    }

    /**
     * Closing up - normal hangup and app destroye
     */

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.start_call: {
                showProgress(false);
                checkPermissionToStart();
                break;
            }
            case R.id.init_call: {
                call();
                break;
            }
            case R.id.end_call: {
                hangup();
                break;
            }
        }
    }

    private void showProgress(String msg, boolean isNotShowButtonCancel) {
        if (customProgress != null) {
            if (customProgress.isShown()) {
                customProgress.hideProgress();
            }
            customProgress.showProgress(this, msg, false, isNotShowButtonCancel);
        }
    }

    private void showProgress(boolean isNotShowButtonCancel) {
        showProgress("Loading.. Please Wait..", isNotShowButtonCancel);
    }

    private void hideProgress() {
        if (customProgress != null) {
            customProgress.hideProgress();
        }
    }

    private void hangup() {
        //boolean isStarted = SignallingClient.getInstance().isStarted;
        boolean isStarted = false;
        try {
            if (isStarted) {
                showProgress(true);
            }
            if (localPeer != null) {
                localPeer.close();
            }
            localPeer = null;
            SignallingClient.getInstance().close();
            updateVideoViews(false);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            localPeer = null;
            call.setEnabled(false);
            hangup.setEnabled(false);
            remoteVideoView.setVisibility(View.GONE);
            if (isStarted) {
                new TimerTaskThread(this, new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                start.setEnabled(true);
                            }
                        });
                        return null;
                    }
                }, new TimerTaskThread.ResultCallable() {
                    @Override
                    public void onResultCallable(Object result) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                hideProgress();
                            }
                        });
                    }
                }, TimeUnit.SECONDS.toMillis(15L))
                .run();
            } else {
                start.setEnabled(true);
            }
        }
    }

    @Override
    protected void onDestroy() {
        SignallingClient.getInstance().close();
        super.onDestroy();

        if (surfaceTextureHelper != null) {
          surfaceTextureHelper.dispose();
          surfaceTextureHelper = null;
        }
    }

    /**
     * Util Methods
     */
    public int dpToPx(int dp) {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        return Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
    }

    public void showToast(final String msg) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
    }

    private VideoCapturer createVideoCapturer() {
        VideoCapturer videoCapturer;
        if (useCamera2()) {
            videoCapturer = createCameraCapturer(new Camera2Enumerator(this));
        } else {
            videoCapturer = createCameraCapturer(new Camera1Enumerator(true));
        }
        return videoCapturer;
    }

    private boolean useCamera2() {
        return Camera2Enumerator.isSupported(this);
    }

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        Logging.d(TAG, "Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating front facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        Logging.d(TAG, "Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating other camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    @Override
    public void onItemClick(DataModel item) {
        Toast.makeText(getApplicationContext(), item.text + " is clicked", Toast.LENGTH_SHORT).show();
    }
}
