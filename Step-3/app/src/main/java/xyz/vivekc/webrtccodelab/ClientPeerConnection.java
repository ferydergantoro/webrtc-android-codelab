package xyz.vivekc.webrtccodelab;

import org.webrtc.PeerConnection;

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

  public String getPeerKey() {
    return peerKey;
  }

  public void setPeerKey(String peerKey) {
    this.peerKey = peerKey;
  }

  public String getRemoteClientId() {
    return remoteClientId;
  }

  public void setRemoteClientId(String remoteClientId) {
    this.remoteClientId = remoteClientId;
  }

  public PeerConnection getPeerConnection() {
    return peerConnection;
  }

  public void setPeerConnection(PeerConnection peerConnection) {
    this.peerConnection = peerConnection;
  }

  public PeerConnection.IceConnectionState getIceConnectionState() {
    return iceConnectionState;
  }

  public DataModel getDataModel() {
    return dataModel;
  }

  public void setDataModel(DataModel dataModel) {
    this.dataModel = dataModel;
  }

  public boolean isPeerStarted() {
    return isPeerStarted;
  }

  public void setPeerStarted(boolean peerStarted) {
    isPeerStarted = peerStarted;
  }

  public boolean isHasBeenEverConnected() {
    return hasBeenEverConnected;
  }

  public void setHasBeenEverConnected(boolean hasBeenEverConnected) {
    this.hasBeenEverConnected = hasBeenEverConnected;
  }

  @Override
  public String toString() {
    return "ClientPeerConnection{" +
        "peerKey='" + peerKey + '\'' +
        ", remoteClientId='" + remoteClientId + '\'' +
        ", peerConnection=" + peerConnection +
        ", iceConnectionState=" + iceConnectionState +
        ", dataModel=" + dataModel +
        ", isPeerStarted=" + isPeerStarted +
        ", hasBeenEverConnected=" + hasBeenEverConnected +
        '}';
  }
}
