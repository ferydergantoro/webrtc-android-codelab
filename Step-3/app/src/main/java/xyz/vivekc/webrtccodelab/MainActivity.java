package xyz.vivekc.webrtccodelab;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@SuppressWarnings("ALL")
public class MainActivity extends AppCompatActivity implements
    View.OnClickListener,
    SignallingClient.SignalingInterface,
    RecyclerViewAdapter.ItemListener
{
    PeerConnectionFactory peerConnectionFactory;
    MediaConstraints audioConstraints;
    MediaConstraints videoConstraints;
    MediaConstraints sdpConstraints;
    VideoSource videoSource;
    VideoTrack localVideoTrack;
    AudioSource audioSource;
    AudioTrack localAudioTrack;
    SurfaceTextureHelper surfaceTextureHelper;

    ArrayList<SurfaceViewRenderer> remoteVideoViewers;
    LinearLayout remoteVideoViewLayout;
    SurfaceViewRenderer localVideoView;
//    SurfaceViewRenderer remoteVideoView;
//    SurfaceViewRenderer remoteVideoView2;
//    SurfaceViewRenderer remoteVideoView3;

    Button start, call, hangup;
    List<IceServer> iceServers;
    public EglBase rootEglBase;

    boolean gotUserMedia;
    boolean isNegotiatingConn1, isNegotiatingConn2;
    List<PeerConnection.IceServer> peerIceServers = new ArrayList<>();

    final int ALL_PERMISSIONS_CODE = 1;

    private static final String TAG = "MainActivity";

    private CustomProgress customProgress;

    RecyclerView recyclerView;
    ArrayList<DataModel> arrayList;
    RecyclerViewAdapter adapter;

    HashMap<String, ClientPeerConnection> peerConnectionList = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        try {
//            ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
//            //ScriptEngine scriptEngine = scriptEngineManager.getEngineByName("Nashorn");
//            ScriptEngine scriptEngine = scriptEngineManager.getEngineByName("rhino");
//            scriptEngine.eval(Reader.readRawString(getResources(), R.raw.rtc_multi_connection));
//            Invocable invocable = (Invocable)scriptEngine;
//            Object objConnection = (Object) invocable.invokeFunction("getRTCMultiConnection");
//            Log.d(TAG, "onCreate: object connection => " + objConnection);
//        } catch (ScriptException e) {
//            e.printStackTrace();
//        } catch (NoSuchMethodException e) {
//            e.printStackTrace();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

        //Log.d(TAG, "onCreate: " + Tools.runScript(this));

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
        /*
        arrayList.add(new DataModel("Item 1", R.drawable.battle, "#09A9FF"));
        arrayList.add(new DataModel("Item 2", R.drawable.beer, "#3E51B1"));
        arrayList.add(new DataModel("Item 3", R.drawable.ferrari, "#673BB7"));
        arrayList.add(new DataModel("Item 4", R.drawable.jetpack_joyride, "#4BAA50"));
        arrayList.add(new DataModel("Item 5", R.drawable.three_d, "#F94336"));
        arrayList.add(new DataModel("Item 6", R.drawable.terraria, "#0A9B88"));
        */

        adapter = new RecyclerViewAdapter(this, arrayList, this);
        recyclerView.setAdapter(adapter);

        /**
         AutoFitGridLayoutManager that auto fits the cells by the column width defined.
         **/

        AutoFitGridLayoutManager layoutManager = new AutoFitGridLayoutManager(this, getMaxWidth());
        recyclerView.setLayoutManager(layoutManager);

        /**
         Simple GridLayoutManager that spans two columns
         **/
        /*GridLayoutManager manager = new GridLayoutManager(this, 2, GridLayoutManager.VERTICAL, false);
        recyclerView.setLayoutManager(manager);*/

        initViews();
        initVideos();
        getMyIceServers();
        //getIceServers();
    }

    private DataModel getTheDataModel(String client_id, String peerKey, int index, boolean isInitiator) {
        if (index <= 0) {
            index = 1;
        }
        String colors[] = new String[] {
            "#09A9FF",
            "#3E51B1",
            "#673BB7",
            "#4BAA50",
            "#F94336",
            "#0A9B88",
        };
        int images[] = new int[] {
            R.drawable.battle,
            R.drawable.beer,
            R.drawable.ferrari,
            R.drawable.jetpack_joyride,
            R.drawable.three_d,
            R.drawable.terraria,
        } ;
        return new DataModel(images[new Random().nextInt(images.length)], colors[(index % colors.length) - 1], peerKey, client_id, index, isInitiator);
    }

    private int findIndexDataModeItemList(DataModel dataModel) {
        for (int idx = 0; idx < arrayList.size(); idx++) {
            DataModel dm = arrayList.get(idx);
            if (dataModel.remoteClientId.equals(dm.remoteClientId) && dataModel.peerKey.equals(dm.peerKey)) {
                return idx;
            }
        }
        return -1;
    }

    private void removeAllDataModelList() {
        if (arrayList == null) {
            arrayList = new ArrayList<>();
            return;
        }
        Log.d(TAG, "removeAllDataModelList: remove all datamodel");
        arrayList.clear();
        adapter.notifyDataSetChanged();
        Log.d(TAG, "removeAllDataModelList: count datamodel list now is " + arrayList.size());
    }

    private void removeDataModelList(DataModel dataModel) {
        if (arrayList == null) {
            arrayList = new ArrayList<>();
            return;
        }
        int idxDm = findIndexDataModeItemList(dataModel);
        if (idxDm > -1 && idxDm < arrayList.size()) {
            Log.d(TAG, "removeDataModelList: count datamodel list before remove " + arrayList.size());
            arrayList.remove(idxDm);
            adapter.notifyItemRemoved(idxDm);
            Log.d(TAG, "removeDataModelList: remove index " + idxDm);
        }
    }

    private void addOrUpdateDataModelList(DataModel dataModel) {
        if (arrayList == null) {
            arrayList = new ArrayList<>();
        }
        int idxDm = findIndexDataModeItemList(dataModel);
        Log.d(TAG, "addOrUpdateDataModelList: count datamodel list before add or update is " + arrayList.size());
        if (idxDm > -1 && idxDm < arrayList.size()) {
            arrayList.set(idxDm, dataModel);
            adapter.notifyItemChanged(idxDm);
            Log.d(TAG, "addOrUpdateDataModelList: updated on index " + idxDm);
        } else {
            arrayList.add(dataModel);
            idxDm = arrayList.size()-1;
            adapter.notifyItemInserted(idxDm);
            Log.d(TAG, "addOrUpdateDataModelList: add on index " + idxDm);
        }
        Log.d(TAG, "addOrUpdateDataModelList: count datamodel list now is " + arrayList.size());
    }

    private int getMaxWidth() {
        Display display = getWindowManager().getDefaultDisplay();
        Point m_size = new Point();
        display.getSize(m_size);
        int m_width = m_size.x;

        display = getWindowManager().getDefaultDisplay();
        m_width = Math.max(m_width, display.getWidth());

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        m_width = Math.max(m_width, metrics.widthPixels);

        return m_width;
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

//        remoteVideoView = findViewById(R.id.remote_gl_surface_view);
//        remoteVideoView2 = findViewById(R.id.remote_gl_surface_view2);
//        remoteVideoView3 = findViewById(R.id.remote_gl_surface_view3);

        remoteVideoViewLayout = findViewById(R.id.remote_video_view_layout);

        remoteVideoViewers = new ArrayList<>();

        start.setOnClickListener(this);
        call.setOnClickListener(this);
        hangup.setOnClickListener(this);
    }

    private void initVideos(SurfaceViewRenderer videoView, boolean makeOverlay) {
        videoView.init(rootEglBase.getEglBaseContext(), null);

        videoView.setZOrderMediaOverlay(makeOverlay);

        videoView.setEnableHardwareScaler(true);
        videoView.setMirror(true);
        //videoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
    }

    private void initVideos() {
        rootEglBase = EglBase.create();
        localVideoView.init(rootEglBase.getEglBaseContext(), null);
//        remoteVideoView.init(rootEglBase.getEglBaseContext(), null);
//        remoteVideoView2.init(rootEglBase.getEglBaseContext(), null);

        localVideoView.setZOrderMediaOverlay(true);
//        remoteVideoView.setZOrderMediaOverlay(false);
//        remoteVideoView2.setZOrderMediaOverlay(false);

        localVideoView.setEnableHardwareScaler(true);
        localVideoView.setMirror(true);
        //localVideoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);

//        remoteVideoView.setEnableHardwareScaler(true);
//        remoteVideoView2.setEnableHardwareScaler(true);

//        remoteVideoView.setMirror(true);
//        remoteVideoView2.setMirror(true);

        //remoteVideoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        //remoteVideoView2.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);

//        initVideos(remoteVideoView, false);
//        initVideos(remoteVideoView2, false);
//        initVideos(remoteVideoView3, false);
    }

    private void getMyIceServers() {
        iceServers = new ArrayList<IceServer>();
        /*IceServer myIceServer =  new IceServer();
        myIceServer.setUrl("stun:stun.l.google.com:19302");
        iceServers.add(myIceServer);*/
        IceServer myIceServer2 =  new IceServer();
        myIceServer2.setUrl("turn:202.51.110.214:8282");
        myIceServer2.setUsername("eluon");
        myIceServer2.setCredential("eluon123");
        iceServers.add(myIceServer2);
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
        Log.d("getMyIceServers", "IceServers\n" + iceServers.toString());
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
      showFormData();
    }

    public void start(String roomName) {
        onStartProgress();

        // keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //doInit();

        SignallingClient.getInstance().init(roomName,this);

        //Initialize PeerConnectionFactory globals.
        String fieldTrials = (PeerConnectionFactory.VIDEO_FRAME_EMIT_TRIAL + "/" + PeerConnectionFactory.TRIAL_ENABLED + "/");
        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .setFieldTrials(fieldTrials)
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
        //localVideoTrack = createVideoTrack(null);

        //create an AudioSource instance
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        String audioTrackLabel = generateLabelStream("101");
        Log.d(TAG, "audioTrackLabel: " + audioTrackLabel);
        localAudioTrack = peerConnectionFactory.createAudioTrack(audioTrackLabel, audioSource);
        //localAudioTrack = createAudioTrack(null);

        if (videoCapturerAndroid != null) {
            videoCapturerAndroid.startCapture(1024, 720, 30);
        }

        localVideoView.setVisibility(View.VISIBLE);
        // And finally, with our VideoRenderer ready, we
        // can add our renderer to the VideoTrack.
        localVideoTrack.addSink(localVideoView);
        localVideoTrack.setEnabled(true);

        localVideoView.setMirror(true);

//        remoteVideoView.setMirror(true);
//        remoteVideoView2.setMirror(true);
//        remoteVideoView3.setMirror(true);

        gotUserMedia = true;

        onStartSuccess();

        /*
        if (SignallingClient.getInstance().isInitiator) {
            onTryToStart();
        }
        */
        call();
    }

    private VideoTrack createVideoTrack(String client_id)
    {
        VideoSource videoSource = null;

        //Now create a VideoCapturer instance.
        VideoCapturer videoCapturerAndroid;
        //videoCapturerAndroid = createCameraCapturer(new Camera1Enumerator(false));
        videoCapturerAndroid = createVideoCapturer();

        //Create a VideoSource instance
        if (videoCapturerAndroid != null) {
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext());
            videoSource = peerConnectionFactory.createVideoSource(videoCapturerAndroid.isScreencast());
            videoCapturerAndroid.initialize(surfaceTextureHelper, this, videoSource.getCapturerObserver());
        }
        String localVideoTrackLabel = generateLabelStream("100"+((client_id!=null)?"-"+client_id:""));
        Log.d(TAG, "localVideoTrackLabel: " + localVideoTrackLabel);

        VideoTrack vt = null;
        if (videoSource != null) {
            vt = peerConnectionFactory.createVideoTrack(localVideoTrackLabel, videoSource);
        }

        if (videoCapturerAndroid != null) {
            videoCapturerAndroid.startCapture(1024, 720, 30);
        }

        // And finally, with our VideoRenderer ready, we
        // can add our renderer to the VideoTrack.
        vt.addSink(localVideoView);
        vt.setEnabled(true);

        localVideoView.setMirror(true);

        return vt;
    }

    private AudioTrack createAudioTrack(String client_id)
    {
        MediaConstraints audioConstraints = new MediaConstraints();

        //create an AudioSource instance
        AudioSource audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        String audioTrackLabel = generateLabelStream("101"+((client_id!=null)?"-"+client_id:""));
        Log.d(TAG, "audioTrackLabel: " + audioTrackLabel);
        return peerConnectionFactory.createAudioTrack(audioTrackLabel, audioSource);
    }

    private void showFormData(){
      // get prompts.xml view
      LayoutInflater li = LayoutInflater.from(this);
      View promptsView = li.inflate(R.layout.prompts, null);

      AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);

      // set prompts.xml to alertdialog builder
      alertDialogBuilder.setView(promptsView);

      final EditText userInput = (EditText) promptsView.findViewById(R.id.roomNameInput);
      //userInput.setText(Utils.getRandomSaltString());
        userInput.setText("eluon");

      // set dialog message
      alertDialogBuilder
          .setCancelable(false)
          .setPositiveButton("OK",
              new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog,int id) {
                  // get user input and set it to result
                  // edit text
                  String roomName = userInput.getText().toString();
                  if (roomName != null && !roomName.isEmpty()) {
                    start(roomName);
                  } else {
                    showToast("Please input your room name.");
                    hideProgress();
                  }
                }
              })
          .setNegativeButton("Cancel",
              new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog,int id) {
                    hideProgress();
                    dialog.cancel();
                }
              });

      // create alert dialog
      AlertDialog alertDialog = alertDialogBuilder.create();

      // show it
      alertDialog.show();
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

    private void onCallFailed(String client_id) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showToast("Ups sorry.. something wrong happened, Please try again..");
                hideProgress();
                if (client_id.equals(SignallingClient.getInstance().clientID)) {
                    hangup();
                }
            }
        });
    }

    private void call() {
        onStartProgress();

        gotUserMedia = true;

        // Try to call with start here
        if (SignallingClient.getInstance().isInitiator) {
            onTryToStart(SignallingClient.getInstance().clientID, true);
        }
    }

    /**
     * This method will be called directly by the app when it is the initiator and has got the local media
     * or when the remote peer sends a message through socket that it is ready to transmit AV data
     */
    @Override
    public void onTryToStart(String client_id, boolean isNeedCall) {
        runOnUiThread(() -> {
            if (client_id != null) {
                if (!client_id.equals(SignallingClient.getInstance().clientID)){
                    Log.d(TAG,"onTryToStart: other client_id " + client_id);
                } else{
                    Log.d(TAG,"onTryToStart: my client_id " + SignallingClient.getInstance().clientID);
                }
            } else {
                Log.d(TAG,"onTryToStart: null client_id");
            }
            int sizeConn = getRealSizePeerConnectionList();
            Log.d(TAG, "onTryToStart: " + SignallingClient.getInstance().isInitiator + " " +
                SignallingClient.getInstance().isStarted + " " + client_id + " " + SignallingClient.getInstance().clientID + " " +
                localVideoTrack
            );
            if ((SignallingClient.getInstance().isInitiator || !SignallingClient.getInstance().isStarted ||
                (client_id != null && SignallingClient.getInstance().clientID != null && !client_id.equals(SignallingClient.getInstance().clientID))
                ) && localVideoTrack != null
            ) {
                Log.d(TAG, "onTryToStart: isChannelReady: " + SignallingClient.getInstance().isChannelReady);
                if (SignallingClient.getInstance().isChannelReady &&
                    SignallingClient.getInstance().clientID != null &&
                    !SignallingClient.getInstance().clientID.isEmpty())
                {
                    ClientPeerConnection cpc = null;
                    String peerkey = null;
                    if (!SignallingClient.getInstance().isStarted || !SignallingClient.getInstance().clientID.equals(client_id)) {
                        if (!SignallingClient.getInstance().isStarted) {
                            //we already have video and audio tracks. Now create peerconnections
                            cpc = createPeerConnection(client_id);
                        } else if (!SignallingClient.getInstance().clientID.equals(client_id)) {
                            peerkey = client_id + "-" + SignallingClient.getInstance().clientID;
                            peerConnectionList.remove(null);
                            cpc = peerConnectionList.get(peerkey);
                            if (cpc == null) {
                                cpc = createPeerConnection(client_id);
                            }
                        }
                    }
                    if (cpc != null && client_id != null) {
                        SignallingClient.getInstance().isStarted = true;
                        cpc.setPeerStarted(true);
                        sizeConn = getRealSizePeerConnectionList();
                        Log.d(TAG, "onTryToStart: isInitiator: " + SignallingClient.getInstance().isInitiator);
                        if (isNeedCall /*&& ((peerkey != null && peerConnectionList.get(peerkey) != null && sizeConn > 2)*//* ||
                            SignallingClient.getInstance().isInitiator ||*/
                            && (SignallingClient.getInstance().clientID != null && !client_id.equals(SignallingClient.getInstance().clientID))
                        ) {
                            showProgress("Try Call client " + client_id, false);
                            Log.d(TAG, "onTryToStart: do call " + client_id);
                            doCall(cpc.getPeerConnection(), client_id, peerkey);
                        }
                    }
                } else {
                    Log.d(TAG, "onTryToStart: send join request");
                    showProgress(false);
                    SignallingClient.getInstance().emitJoin();
                }
            }
        });
    }

    public int getRealSizePeerConnectionList() {
        int sizeConn = 0;
        for (Map.Entry<String, ClientPeerConnection> mapEntryPc  : peerConnectionList.entrySet()) {
            if (mapEntryPc.getKey() != null && mapEntryPc.getValue() != null) {
                sizeConn++;
            }
        }
        return sizeConn;
    }

    /**
     * Creating the local peerconnection instance
     */
    private ClientPeerConnection createPeerConnection(String client_id)
    {
        ClientPeerConnection cpc = null;
        boolean isNew = false;

        int sizeConn = getRealSizePeerConnectionList();
        int index = 1; // init first

        String peerKey = null;
        if (!SignallingClient.getInstance().clientID.equals(client_id)) {
            peerKey = client_id + "-" + SignallingClient.getInstance().clientID;
        }

        if (sizeConn == 0) {
            isNew = true;

            PeerConnection localPeer = createPeerConnection(index, peerKey, client_id);

            if (peerKey != null) {
                Log.d(TAG, "createPeerConnection: fill peer-" + index + " conn in key " + peerKey);
                peerConnectionList.put(peerKey, new ClientPeerConnection(peerKey, client_id, localPeer, getTheDataModel(client_id, peerKey, index, true)));
                cpc = peerConnectionList.get(peerKey);
            }
        }

        if (cpc == null && peerKey != null) {
            Log.d(TAG, "createPeerConnection: size peer connection list = " + sizeConn);
            if (peerConnectionList.get(peerKey) != null) {
                cpc = peerConnectionList.get(peerKey);
            } else {
                isNew = true;
                index = sizeConn+1;
                PeerConnection localPeerIdxN = createPeerConnection(index, peerKey, client_id);
                Log.d(TAG, "createPeerConnection: fill peer-" + index + " conn in key " + peerKey);
                peerConnectionList.put(peerKey, new ClientPeerConnection(peerKey, client_id, localPeerIdxN, getTheDataModel(client_id, peerKey, index, false)));
                cpc = peerConnectionList.get(peerKey);
            }
        }

        if (cpc != null && isNew) {
            //creating local mediastream
            addStreamToLocalPeer(cpc.getPeerConnection(), client_id);
        }

        return cpc;
    }

    private PeerConnection createPeerConnection(int index, String peerkey, String client_id)
    {
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(peerIceServers);
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        //rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;

        if (index > 0 && client_id != null && peerkey != null) {
            //creating localPeer #n
            return peerConnectionFactory.createPeerConnection(rtcConfig, new CustomPeerConnectionObserver("localPeerCreation", index, client_id, peerkey) {
                    @Override
                    public void onIceCandidate(IceCandidate iceCandidate) {
                        super.onIceCandidate(iceCandidate);
                        //addQueueRemoteIceCandidate(iceCandidate);
                        onIceCandidateReceived(iceCandidate, getRemoteClientId(), getPeerKey());
                    }

                    @Override
                    public void onAddStream(MediaStream mediaStream) {
                        showToast("Received Remote stream peer-" + getIndex());
                        Log.d(TAG,"onAddStream: Received Remote stream peer-" + getIndex() + " for client " + getRemoteClientId());
                        super.onAddStream(mediaStream);
                        Log.d(TAG,"onAddStream: will get client peer connection with peerkey " + getPeerKey());
                        ClientPeerConnection cpc = peerConnectionList.get(getPeerKey());
                        Log.d(TAG, "onAddStream: get client peer connection " + cpc);
                        if (cpc != null) {
                            Log.d(TAG, "onAddStream: get datamodel " + cpc.getDataModel());
                            if (cpc.getDataModel() != null && cpc.getDataModel().remoteClientId != null && cpc.getDataModel().peerKey != null) {
                                Log.d(TAG,"onAddStream: got client peer connection for client " + cpc.getDataModel().remoteClientId + " with peerkey " + cpc.getDataModel().peerKey);
                                gotRemoteStream(mediaStream, cpc.getDataModel());
                            }
                        }
                    }

                    @Override
                    public void onIceConnectionChange(
                        PeerConnection.IceConnectionState iceConnectionState) {
                        showToast("ICE Peer-" + getIndex() + " State: " + iceConnectionState);
                        Log.d(TAG,"onIceConnectionChange: ICE Peer-" + getIndex() + " for client " + getRemoteClientId() + " with State: " + iceConnectionState);
                        super.onIceConnectionChange(iceConnectionState);
                        SignallingClient.getInstance().emitIceCandidateState(iceConnectionState, getRemoteClientId(), getPeerKey());
                        ClientPeerConnection cpc = peerConnectionList.get(getPeerKey());
                        if (cpc != null) {
                            if (!cpc.isHasBeenEverConnected() && iceConnectionState == PeerConnection.IceConnectionState.CONNECTED) {
                                SignallingClient.getInstance().emitConnected(getRemoteClientId());
                            }
                            cpc.setIceConnectionState(iceConnectionState);
                        }
                    }
                });
        } else {
            return null;
        }
    }

    private String generateLabelStream(String reffCode) {
        return reffCode + "-" + Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    /**
     * Adding the stream to the localpeer
     */
    private void addStreamToLocalPeer(PeerConnection currPc, String client_id) {
        //creating local mediastream
        String labelMediaStream = generateLabelStream("102");
        labelMediaStream += "-" + client_id;
        Log.d(TAG, "labelMediaStream: " + labelMediaStream);
        MediaStream stream = peerConnectionFactory.createLocalMediaStream(labelMediaStream);
        stream.addTrack(localAudioTrack);
        stream.addTrack(localVideoTrack);
        /*stream.addTrack(createAudioTrack(client_id));
        stream.addTrack(createVideoTrack(client_id));*/

        currPc.addStream(stream);
    }

    /**
     * This method is called when the app is the initiator - We generate the offer and send it over through socket
     * to remote peer
     */
    private void doCall(PeerConnection currPc, String client_id, String peerkey) {
        sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

        //creating Offer
        currPc.createOffer(new CustomSdpObserver("localCreateOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                Log.d(TAG, "onCreateSuccess: on create offer success and setLocalDescription " + client_id);
                currPc.setLocalDescription(new CustomSdpObserver("localSetLocalDesc"), sessionDescription);
                Log.d("onCreateSuccess", "SignallingClient emit - creating offer from local peer-"+client_id);
                SignallingClient.getInstance().emitMessage(sessionDescription, client_id, peerkey);
                hideProgress();
                onCallSuccess();
            }

            @Override
            public void onCreateFailure(String s) {
                super.onCreateFailure(s);
                Log.d(TAG, "onCreateFailure: s: " + s);
                hideProgress();
                onCallFailed(client_id);
            }
        }, sdpConstraints);
    }

    /*private void gotRemoteStream2(MediaStream stream) {
        final VideoTrack videoTrack = stream.videoTracks.get(0);
        runOnUiThread(() -> {
            try {
                remoteVideoView2.setVisibility(View.VISIBLE);
                videoTrack.setEnabled(true);
                videoTrack.addSink(remoteVideoView2);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                );
                params.height = dpToPx(100);
                params.width = dpToPx(100);
                params.gravity = Gravity.TOP | Gravity.END;
                localVideoView.setLayoutParams(params);

                hideProgress();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }*/

    /**
     * Received remote peer's media stream. we will get the first video track and render it
     */
    private void gotRemoteStream(MediaStream stream, DataModel dataModel) {
        //we have remote video stream. add to the renderer.
        final VideoTrack videoTrack = stream.videoTracks.get(0);
        runOnUiThread(() -> {
            try {
//                remoteVideoView.setVisibility(View.VISIBLE);
//                videoTrack.setEnabled(true);
                /*Log.d(TAG, "gotRemoteStream: set track for remote video with peer-" + dataModel.index);
                switch (dataModel.index) {
                    case 1: {
                        remoteVideoView.setVisibility(View.VISIBLE);
                        videoTrack.setEnabled(true);
                        videoTrack.addSink(remoteVideoView);
                        Log.d(TAG, "gotRemoteStream: add video track on index peer-1");
                        break;
                    }
                    case 3: {
                        remoteVideoView2.setVisibility(View.VISIBLE);
                        videoTrack.setEnabled(true);
                        videoTrack.addSink(remoteVideoView2);
                        Log.d(TAG, "gotRemoteStream: add video track on index peer-3");
                        break;
                    }
                    case 4: {
                        remoteVideoView3.setVisibility(View.VISIBLE);
                        videoTrack.setEnabled(true);
                        videoTrack.addSink(remoteVideoView3);
                        Log.d(TAG, "gotRemoteStream: add video track on index peer-4");
                        break;
                    }
                    default:break;
                }*/
//                videoTrack.addSink(remoteVideoView);

                Log.d(TAG, "gotRemoteStream: updating mediastream " + stream);
                dataModel.mediaStream = stream;
                Log.d(TAG, "gotRemoteStream: updating videotrack " + videoTrack);
                dataModel.videoTrack = videoTrack;
                Log.d(TAG, "gotRemoteStream: add or update datamodel");
                addOrUpdateDataModelList(dataModel);

                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                );
                params.height = dpToPx(100);
                params.width = dpToPx(100);
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
    public void onIceCandidateReceived(IceCandidate iceCandidate, String client_id, String peerkey) {
        //we have received ice candidate. We can set it to the other peer.
        SignallingClient.getInstance().emitIceCandidate(iceCandidate, client_id, peerkey);
    }

    /**
     * SignallingCallback - called when the room is created - i.e. you are the initiator
     */
    @Override
    public void onCreatedRoom(String client_id, int numClients, ArrayList clients) {
        showToast("You created the room");
        Log.d(TAG, "onCreatedRoom: clients => " + Arrays.toString(clients.toArray()));
        /*String peerkey = SignallingClient.getInstance().clientID;
        if (!peerConnectionList.containsKey(peerkey)) {
            Log.d(TAG, "onCreatedRoom: create new peer key " + peerkey);
            peerConnectionList.put(peerkey, null);
        }*/
        Log.d(TAG, "onCreatedRoom: gotUserMedia = " + gotUserMedia);
        if (gotUserMedia) {
            SignallingClient.getInstance().emitMessage("got user media", SignallingClient.getInstance().clientID);
        }
        onCallSuccess();
        hideProgress();
    }

    private void generateNewPossiblePeerKey(ArrayList clients) {
        if (SignallingClient.getInstance().clientID != null) {
            for (int i = 0; i < clients.toArray().length; i++) {
                String cid_str = (String) clients.toArray()[i];
                if (!cid_str.equals(SignallingClient.getInstance().clientID)) {
                    String peerkey = cid_str + "-" + SignallingClient.getInstance().clientID;
                    if (!peerConnectionList.containsKey(peerkey)) {
                        Log.d(TAG, "generateNewPossiblePeerKey: create new peer key " + peerkey);
                        peerConnectionList.put(peerkey, null);
                    }
                }
            }
        }
    }

    /**
     * SignallingCallback - called when you join the room - you are a participant
     */
    @Override
    public void onJoinedRoom(String client_id, int numClients, ArrayList clients) {
        showToast("You joined the room");
        Log.d(TAG, "onJoinedRoom: clients => " + Arrays.toString(clients.toArray()));
        Log.d(TAG, "onJoinedRoom: need generate new possibility peer key");
        generateNewPossiblePeerKey(clients);
        Log.d(TAG, "onJoinedRoom: gotUserMedia = " + gotUserMedia);
        if (gotUserMedia) {
            SignallingClient.getInstance().emitMessage("got user media", client_id);
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setTitle(getString(R.string.app_name));
                getSupportActionBar().setSubtitle("ID " + client_id);
            }
        });
        onCallSuccess();
        hideProgress();
    }

    @Override
    public void onNewPeerJoined(String client_id, int numClients, ArrayList clients) {
        Log.d(TAG, "onNewPeerJoined: Remote Peer Joined with client " + client_id);
        Log.d(TAG, "onNewPeerJoined: clients => " + Arrays.toString(clients.toArray()));
        Log.d(TAG, "onNewPeerJoined: need generate new possibility peer key");
        generateNewPossiblePeerKey(clients);
        int sizeConn = getRealSizePeerConnectionList();
        if (SignallingClient.getInstance().clientID != null && !SignallingClient.getInstance().clientID.equals(client_id)) {
            showToast("Remote Peer Joined");
            Log.d(TAG, "onNewPeerJoined: isInitiator " + SignallingClient.getInstance().isInitiator);
            Log.d(TAG, "onNewPeerJoined: it is the other new client joined " + client_id);
            String peerkey = client_id + "-" + SignallingClient.getInstance().clientID;
            if (!peerConnectionList.containsKey(peerkey)) {
                Log.d(TAG, "onNewPeerJoined: create new peer key " + peerkey);
                peerConnectionList.put(peerkey, null);
            }
            Log.d(TAG, "onNewPeerJoined: isInitiator " + SignallingClient.getInstance().isInitiator);
//            if (SignallingClient.getInstance().isInitiator) {
//                //Log.d(TAG, "onNewPeerJoined: onTryToStart client " + client_id);
//                //onTryToStart(client_id, true);
//            } else {
//                if (peerConnectionList.get(peerkey) == null) {
                    Log.d(TAG, "onNewPeerJoined: onTryToStart client " + client_id);
                    onTryToStart(client_id, true);
//                }
//            }
        }
    }

    @Override
    public void onRemoteHangUp(String msg, String client_id, int numClients, ArrayList clients) {
        showToast("Remote Peer hungup");
        Log.d(TAG, "onRemoteHangUp: client " + client_id);
        Log.d(TAG, "onRemoteHangUp: " + numClients + " client(s) still in room => " + clients);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String peerKey = client_id + "-" + SignallingClient.getInstance().clientID;
                Log.d(TAG, "onRemoteHangUp: run: will remove peer conn with peerkey " + peerKey);
                if (peerConnectionList.get(peerKey) != null) {
                    if (peerConnectionList.get(peerKey).getDataModel() != null) {
                        Log.d(TAG, "onRemoteHangUp: run: will hide remote video views with peerkey " + peerKey);
                        removeDataModelList(peerConnectionList.get(peerKey).getDataModel());
                    }
                    if (peerConnectionList.get(peerKey).getPeerConnection() != null) {
                        Log.d(TAG, "onRemoteHangUp: run: will close peer connection with peerkey " + peerKey);
                        peerConnectionList.get(peerKey).getPeerConnection().close();
                    }
                }
                Log.d(TAG, "onRemoteHangUp: run: recycle list view count now is " + arrayList.size());
                peerConnectionList.remove(peerKey);
                int sizeConn = getRealSizePeerConnectionList();
                Log.d(TAG, "onRemoteHangUp: run: peer connection list count now is " + sizeConn);
                if (sizeConn == 0 || (sizeConn == 1 && peerConnectionList.containsKey(SignallingClient.getInstance().clientID))) {
                    Log.d(TAG,"onRemoteHangUp: run: not any remote view client saved, and local video view will be set full screen");
                    removeAllDataModelList();
                    updateVideoViews(false);
                }
            }
        });
        //TODO
        //SignallingClient.getInstance().clientID = null;
        //runOnUiThread(this::hangup);
    }

    private PeerConnection getPeerConnection(String client_id, String from_client_id, String peerKey) {
        ClientPeerConnection cpc = getClientPeerConnection(client_id, from_client_id, peerKey);
        return (cpc != null) ? cpc.getPeerConnection() : null;
    }

    private ClientPeerConnection getClientPeerConnection(String client_id, String from_client_id, String reffPeerKey) {
        Log.d(TAG, "getClientPeerConnection: for client " + client_id);
        Log.d(TAG, "getClientPeerConnection: from client " + from_client_id);
        Log.d(TAG, "getClientPeerConnection: reffPeerKey " + reffPeerKey);
        Log.d(TAG, "getClientPeerConnection: existing key " + peerConnectionList.keySet());

        String peerKey = from_client_id + "-" + client_id;
        ClientPeerConnection cPc = peerConnectionList.get(peerKey);

        if (cPc == null) {
            peerKey = reffPeerKey;
            Log.d(TAG, "getClientPeerConnection: 1st try for key " + peerKey);
            cPc = peerConnectionList.get(reffPeerKey);
        }
        if (cPc == null) {
            peerKey = client_id + "-" + from_client_id;
            Log.d(TAG, "getClientPeerConnection: 2nd try for key " + peerKey);
            cPc = peerConnectionList.get(peerKey);
        }
        if (cPc == null && client_id.equals(from_client_id) && !client_id.equals(SignallingClient.getInstance().clientID)) {
            peerKey = client_id + "-" + SignallingClient.getInstance().clientID;
            Log.d(TAG, "getClientPeerConnection: 3rd try for key " + peerKey);
            cPc = peerConnectionList.get(peerKey);
//            if (peerConnectionList.containsKey(peerKey) && cPc == null) {
//                if (peerConnectionList.size() < 2) {
//                    cPc = peerConnectionList.get(SignallingClient.getInstance().clientID);
//                    cPc.getDataModel().remoteClientId = client_id;
//                    cPc.getDataModel().peerKey = peerKey;
//                    cPc.setRemoteClientId(client_id);
//                    cPc.setPeerKey(peerKey);
//                    peerConnectionList.put(SignallingClient.getInstance().clientID, cPc);
//                    Log.d(TAG, "getClientPeerConnection: fill peer-1 conn in key " + peerKey);
//                    peerConnectionList.put(peerKey, cPc);
//                }
//            }
        }
//        if (cPc == null && reffPeerKey != null) {
//            if (reffPeerKey.startsWith(SignallingClient.getInstance().clientID+"-") || reffPeerKey.endsWith("-"+SignallingClient.getInstance().clientID)) {
//                if (reffPeerKey.startsWith(SignallingClient.getInstance().clientID+"-")) {
//                    peerKey = SignallingClient.getInstance().clientID+"-"+reffPeerKey.replace(SignallingClient.getInstance().clientID+"-","");
//                }
//                Log.d(TAG, "getClientPeerConnection: 4th try for key " + peerKey);
//                cPc = peerConnectionList.get(peerKey);
//            }
//        }
//        if (cPc == null && client_id.equals(from_client_id) && client_id.equals(SignallingClient.getInstance().clientID)) {
//            Iterator<Map.Entry<String, ClientPeerConnection>> iteratorPcList = peerConnectionList.entrySet().iterator();
//            int count_found=0;
//            while (iteratorPcList.hasNext()){
//                Map.Entry<String, ClientPeerConnection> mapEntryPc = iteratorPcList.next();
//                String keyPc = mapEntryPc.getKey();
//                ClientPeerConnection valCpc = mapEntryPc.getValue();
//                if (keyPc != null && (keyPc.endsWith("-"+SignallingClient.getInstance().clientID) || (keyPc.equals(reffPeerKey) && reffPeerKey.endsWith("-"+SignallingClient.getInstance().clientID)))) {
//                    Log.d(TAG, "getClientPeerConnection: 5th try decision key " + keyPc);
//                    peerKey = keyPc;
//                    cPc = valCpc;
//                }
//                count_found++;
//                if (count_found == 2) {
//                    break;
//                }
//            }
//        }
//        int sizeConn = getRealSizePeerConnectionList();
//        if (cPc == null && !client_id.equals(from_client_id) && client_id.equals(SignallingClient.getInstance().clientID) && sizeConn == 1) {
//            cPc = peerConnectionList.get(SignallingClient.getInstance().clientID);
//            peerKey = from_client_id + "-" + SignallingClient.getInstance().clientID;
//            Log.d(TAG, "getClientPeerConnection: 6th try for key " + peerKey);
//            Log.d(TAG, "getClientPeerConnection: fill peer-1 conn in key " + peerKey);
//            /*cPc.getDataModel().setRemoteClientId(from_client_id);
//            cPc.getDataModel().peerKey = peerKey;
//            cPc.setRemoteClientId(from_client_id);
//            cPc.setPeerKey(peerKey);
//            peerConnectionList.put(SignallingClient.getInstance().clientID, cPc);
//            peerConnectionList.put(peerKey, cPc);*/
//        }

//        if (cPc == null && sizeConn == 1) {
//            cPc = peerConnectionList.get(SignallingClient.getInstance().clientID);
//            peerKey = from_client_id + "-" + SignallingClient.getInstance().clientID;
//            Log.d(TAG, "getClientPeerConnection: fill peer-1 conn in key " + peerKey);
//            peerConnectionList.put(peerKey, cPc);
//            Log.d(TAG, "getClientPeerConnection: 4th decision key with my client id " + SignallingClient.getInstance().clientID);
//            peerKey = SignallingClient.getInstance().clientID;
//        }
        Log.d(TAG, "getClientPeerConnection: get peer connection with key " + peerKey);
        Log.d(TAG, "getClientPeerConnection: client peer connection is " + cPc);
        return cPc;
    }

    /**
     * SignallingCallback - Called when remote peer sends offer
     */
    @Override
    public void onOfferReceived(final JSONObject data, final String client_id, final String from_client_id, final String peerKey) {
        if (from_client_id.equals(SignallingClient.getInstance().clientID) &&
            !client_id.equals(SignallingClient.getInstance().clientID)
        ) {
            return;
        }

        showToast("Received Offer");
        Log.d(TAG, "onOfferReceived: Received Offer (from " + from_client_id + ") for " + client_id);
        Log.d(TAG, "onOfferReceived: Received Offer peerKey " + peerKey);
        runOnUiThread(() -> {
            if (!SignallingClient.getInstance().isInitiator && !SignallingClient.getInstance().isStarted
            ) {
                Log.d(TAG, "onOfferReceived: TryToStart client " + from_client_id);
                onTryToStart(from_client_id, false);
            }

            try {
                ClientPeerConnection cpc = getClientPeerConnection(client_id, from_client_id, peerKey);
                if (cpc == null || cpc.getPeerConnection() == null) {
                    String pk = from_client_id + "-" + client_id;
                    if (peerConnectionList.containsKey(pk)) {
                        Log.d(TAG, "onOfferReceived: contain key with peerkey " + pk);
                        Log.d(TAG, "onOfferReceived: TryToStart client " + from_client_id + " with peerkey " + pk);
                        onTryToStart(from_client_id, false);
                        cpc = peerConnectionList.get(pk);
                    }
                }
                if (cpc != null && cpc.getPeerConnection() != null) {
                    Log.d(TAG, "onOfferReceived: setRemoteDescription client " + from_client_id);
                    cpc.getPeerConnection().setRemoteDescription(new CustomSdpObserver("localSetRemote"), new SessionDescription(SessionDescription.Type.OFFER, data.getString("sdp")));
                    doAnswer(cpc.getPeerConnection(), from_client_id, cpc.getPeerKey());
                    updateVideoViews(true);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
    }

    private void doAnswer(PeerConnection currPc, String from_client_id, String peerkey) {
        if (currPc != null) {
            Log.d(TAG, "doAnswer: client " + from_client_id);
            currPc.createAnswer(new CustomSdpObserver("localCreateAns") {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                    super.onCreateSuccess(sessionDescription);
                    Log.d(TAG, "onCreateSuccess: on create answer success and setLocalDescription " + from_client_id);
                    currPc.setLocalDescription(new CustomSdpObserver("localSetLocal"), sessionDescription);
                    Log.d("onCreateSuccess","SignallingClient emit - creating answer from local peer-" + from_client_id);
                    SignallingClient.getInstance().emitMessage(sessionDescription, from_client_id, peerkey);
                }
            }, new MediaConstraints());
        }
    }

    /**
     * SignallingCallback - Called when remote peer sends answer to your offer
     */

    @Override
    public void onAnswerReceived(JSONObject data, String client_id, String from_client_id, String peerKey) {
        if (from_client_id.equals(SignallingClient.getInstance().clientID) &&
            !client_id.equals(SignallingClient.getInstance().clientID)
        ) {
            return;
        }
        showToast("Received Answer");
        Log.d(TAG, "onAnswerReceived: client (from " + from_client_id + ") for " + client_id);
        Log.d(TAG, "onAnswerReceived: peerKey " + peerKey);
        try {
            ClientPeerConnection cpc = getClientPeerConnection(client_id, from_client_id, peerKey);
            if (cpc != null && cpc.getPeerConnection() != null) {
                Log.d(TAG, "onAnswerReceived: setRemoteDescription client " + cpc.getRemoteClientId());
                cpc.getPeerConnection().setRemoteDescription(new CustomSdpObserver("localSetRemote"), new SessionDescription(SessionDescription.Type.fromCanonicalForm(data.getString("type").toLowerCase()), data.getString("sdp")));
                updateVideoViews(true);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Remote IceCandidate received
     */
    @Override
    public void onIceCandidateReceived(JSONObject data, String client_id, String from_client_id, String peerKey) {
        /*if (from_client_id.equals(SignallingClient.getInstance().clientID) &&
            !client_id.equals(SignallingClient.getInstance().clientID)
        ) {
            return;
        }*/
        try {
            ClientPeerConnection cpc = getClientPeerConnection(client_id, from_client_id, peerKey);
            if (cpc != null && cpc.getPeerConnection() != null) {
                cpc.getPeerConnection().addIceCandidate(new IceCandidate(data.getString("id"), data.getInt("label"), data.getString("candidate")));
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

                FrameLayout.LayoutParams newparams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                );
                newparams.gravity = Gravity.TOP | Gravity.END;
                localVideoView.setLayoutParams(newparams);
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
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (customProgress != null) {
                    customProgress.hideProgress();
                }
            }
        });
    }

    private void hangup() {
        try {
            SignallingClient.getInstance().close();
            //remoteVideoView.setVisibility(View.GONE);
            //remoteVideoView2.setVisibility(View.GONE);
            removeAllDataModelList();
            updateVideoViews(false);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            peerConnectionList.clear();
            call.setEnabled(false);
            hangup.setEnabled(false);
            //remoteVideoView.setVisibility(View.GONE);
            //remoteVideoView2.setVisibility(View.GONE);
            removeAllDataModelList();
            setTitle(getString(R.string.app_name));
            //getSupportActionBar().setSubtitle("");
            start.setEnabled(true);
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
