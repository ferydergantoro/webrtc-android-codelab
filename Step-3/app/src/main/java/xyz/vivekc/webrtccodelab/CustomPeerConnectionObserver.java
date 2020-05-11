package xyz.vivekc.webrtccodelab;

import android.util.Log;

import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.RtpReceiver;

import java.util.Arrays;

import lombok.Data;

/**
 * Webrtc_Step2
 * Created by vivek-3102 on 11/03/17.
 */

@Data
@SuppressWarnings("ALL")
class CustomPeerConnectionObserver implements PeerConnection.Observer {

    private String logTag;
    private String peerKey;
    private String remoteClientId;
    private int index;

    CustomPeerConnectionObserver(String logTag, int index, String remoteClientId, String peerKey) {
        //this.logTag = this.getClass().getCanonicalName();
        this.logTag = this.getClass().getSimpleName();
        this.logTag = this.logTag + " " + logTag;
        this.index = index;
        this.remoteClientId = remoteClientId;
        this.peerKey = peerKey;
    }

    public String getRemoteClientId() {
        return remoteClientId;
    }

    public String getPeerKey() {
        return peerKey;
    }

    @Override
    public void onSignalingChange(PeerConnection.SignalingState signalingState) {
        Log.d(logTag, "onSignalingChange() called with: signalingState for client " + remoteClientId + " = [" + signalingState + "]");
    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
        Log.d(logTag, "onIceConnectionChange() called with: iceConnectionState for client " + remoteClientId + " = [" + iceConnectionState + "]");
    }

    @Override
    public void onIceConnectionReceivingChange(boolean b) {
        Log.d(logTag, "onIceConnectionReceivingChange() for client " + remoteClientId + " called with: b = [" + b + "]");
    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
        Log.d(logTag, "onIceGatheringChange() called with: iceGatheringState for client " + remoteClientId + " = [" + iceGatheringState + "]");
    }

    @Override
    public void onIceCandidate(IceCandidate iceCandidate) {
        Log.d(logTag, "onIceCandidate() called with: iceCandidate for client " + remoteClientId + " = [" + iceCandidate + "]");
    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
        Log.d(logTag, "onIceCandidatesRemoved() called with: iceCandidates for client " + remoteClientId + " = [" + Arrays
            .toString(iceCandidates) + "]");
    }

    @Override
    public void onAddStream(MediaStream mediaStream) {
        Log.d(logTag, "onAddStream() called with: mediaStream for client " + remoteClientId + " = [" + mediaStream + "]");
    }

    @Override
    public void onRemoveStream(MediaStream mediaStream) {
        Log.d(logTag, "onRemoveStream() called with: mediaStream for client " + remoteClientId + " = [" + mediaStream + "]");
    }

    @Override
    public void onDataChannel(DataChannel dataChannel) {
        Log.d(logTag, "onDataChannel() called with: dataChannel for client " + remoteClientId + " = [" + dataChannel + "]");
    }

    @Override
    public void onRenegotiationNeeded() {
        Log.d(logTag, "onRenegotiationNeeded() for client " + remoteClientId + " called");
    }

    @Override
    public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
        Log.d(logTag, "onAddTrack() for client " + remoteClientId + " called with: rtpReceiver = [" + rtpReceiver + "], mediaStreams = [" + Arrays
            .toString(mediaStreams) + "]");
    }
}
