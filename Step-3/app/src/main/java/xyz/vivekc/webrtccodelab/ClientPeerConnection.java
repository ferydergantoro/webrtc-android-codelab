package xyz.vivekc.webrtccodelab;

import org.webrtc.PeerConnection;

import lombok.Data;

@Data
@SuppressWarnings("ALL")
public class ClientPeerConnection {

  private static final String TAG = "ClientPeerConnection";

  String peerKey;
  String remoteClientId;
  PeerConnection peerConnection;
  PeerConnection.IceConnectionState iceConnectionState;
  DataModel dataModel;
  boolean isPeerStarted;
  boolean hasBeenEverConnected;

  public ClientPeerConnection(String peerKey, String remoteClientId, PeerConnection peerConnection, DataModel dataModel) {
    this.peerKey = peerKey;
    this.remoteClientId = remoteClientId;
    this.peerConnection = peerConnection;
    this.dataModel = dataModel;
  }

  public void setIceConnectionState(PeerConnection.IceConnectionState iceConnectionState) {
    this.iceConnectionState = iceConnectionState;
    hasBeenEverConnected = (this.iceConnectionState != PeerConnection.IceConnectionState.CLOSED) &&
        (hasBeenEverConnected || this.iceConnectionState == PeerConnection.IceConnectionState.CONNECTED);
  }
}
