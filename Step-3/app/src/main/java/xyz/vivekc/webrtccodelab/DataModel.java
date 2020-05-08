package xyz.vivekc.webrtccodelab;

import org.webrtc.MediaStream;
import org.webrtc.VideoTrack;

/**
 * Created by anupamchugh on 11/02/17.
 */

@SuppressWarnings("ALL")
public class DataModel {

    public String peerKey;
    public String remoteClientId;
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

    public DataModel(int drawable, String color, String peerKey, String remoteClientId) {
        this("ID " + remoteClientId, drawable, color);
        this.peerKey = peerKey;
        this.remoteClientId = remoteClientId;
    }

    public void setRemoteClientId(String remoteClientId) {
        this.remoteClientId = remoteClientId;
        text = "ID " + this.remoteClientId;
    }
}
