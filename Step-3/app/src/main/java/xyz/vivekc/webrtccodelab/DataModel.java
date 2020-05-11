package xyz.vivekc.webrtccodelab;

import org.webrtc.MediaStream;
import org.webrtc.VideoTrack;

/**
 * Created by anupamchugh on 11/02/17.
 */

@SuppressWarnings("ALL")
public class DataModel {

    public int index;
    public String peerKey;
    public String remoteClientId;
    public boolean isInitiator;
    public MediaStream mediaStream;
    public VideoTrack videoTrack;
    public String text;
    public int drawable;
    public String color;

    private DataModel(String t, int d, String c) {
        text=t;
        drawable=d;
        color=c;
    }

    public DataModel(int drawable, String color, String peerKey, String remoteClientId, int index, boolean isInitiator) {
        this("ID " + remoteClientId, drawable, color);
        this.peerKey = peerKey;
        this.remoteClientId = remoteClientId;
        this.index = index;
        this.isInitiator = isInitiator;
    }

    public void setRemoteClientId(String remoteClientId) {
        this.remoteClientId = remoteClientId;
        text = "ID " + this.remoteClientId;
    }
}
