package xyz.vivekc.webrtccodelab;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.webrtc.MediaStream;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

import java.util.ArrayList;


@SuppressWarnings("ALL")
public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder> {

    static String TAG = "RecyclerViewAdapter";

    ArrayList<DataModel> mValues;
    Context mContext;
    Activity mActivity;
    protected ItemListener mListener;

    private RecyclerViewAdapter(Context context, Activity activity, ArrayList<DataModel> values, ItemListener itemListener) {
        mValues = values;
        mActivity = activity;
        mContext = context;
        mListener=itemListener;
    }

    public RecyclerViewAdapter(Activity activity, ArrayList<DataModel> values, ItemListener itemListener) {
        this(activity.getApplicationContext(), activity, values, itemListener);
        mActivity = activity;
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        public TextView textView;
        public ImageView imageView;
        public SurfaceViewRenderer remoteVideoView;
        public RelativeLayout relativeLayout;
        DataModel item;

        public ViewHolder(View v) {

            super(v);

            v.setOnClickListener(this);
            textView = (TextView) v.findViewById(R.id.textView);
            imageView = (ImageView) v.findViewById(R.id.imageView);
            remoteVideoView = (SurfaceViewRenderer) v.findViewById(R.id.remote_gl_surface_view);
            relativeLayout = (RelativeLayout) v.findViewById(R.id.relativeLayout);

            initVideos();
        }

        private void initVideos() {
            remoteVideoView.init(((MainActivity) mActivity).rootEglBase.getEglBaseContext(), null);
            remoteVideoView.setZOrderMediaOverlay(false);
            remoteVideoView.setEnableHardwareScaler(true);
            remoteVideoView.setMirror(true);
            //remoteVideoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        }

        public void setData(DataModel item) {
            this.item = item;

            textView.setText(this.item.text);
            imageView.setImageResource(this.item.drawable);
            relativeLayout.setBackgroundColor(Color.parseColor(this.item.color));
            gotRemoteStream(this.item.mediaStream);
            //gotRemoteStream(this.item.videoTrack);
        }

        private void gotRemoteStream(MediaStream mediaStream) {
            if (mediaStream.videoTracks.size() > 0) {
                //we have remote video stream. add to the renderer.
                final VideoTrack videoTrack = mediaStream.videoTracks.get(0);
                mActivity.runOnUiThread(() -> {
                    gotRemoteStream(videoTrack);
                });
            }
        }

        private void gotRemoteStream(final VideoTrack videoTrack) {
            mActivity.runOnUiThread(() -> {
                try {
                    remoteVideoView.setVisibility(View.VISIBLE);
                    videoTrack.setEnabled(true);
                    videoTrack.addSink(remoteVideoView);
                } catch (Exception e) {
                    Log.e(this.getClass().getSimpleName(), "gotRemoteStream: ", e);
                }
            });
        }


        @Override
        public void onClick(View view) {
            if (mListener != null) {
                mListener.onItemClick(item);
            }
        }
    }

    @Override
    public RecyclerViewAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(mContext).inflate(R.layout.recycler_view_item, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder Vholder, int position) {
        Vholder.setData(mValues.get(position));

    }

    @Override
    public int getItemCount() {
        return mValues.size();
    }

    public interface ItemListener {
        void onItemClick(DataModel item);
    }
}