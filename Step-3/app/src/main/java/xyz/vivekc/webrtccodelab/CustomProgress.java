package xyz.vivekc.webrtccodelab;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("ALL")
public class CustomProgress implements TimerTaskThread.ResultCallable {
  public static CustomProgress customProgress = null;
  private final static long TIME_SECONDS_TO_WAIT_CANCEL = 5L;
  private final static long TIME_SECONDS_TO_TIME_OUT = 50L;
  private Dialog mDialog;
  private ProgressBar mProgressBar;
  private Button btnCancel;
  private TimerTaskThread timerTaskThreadToShowBtnCancel, timerTaskThreadToTimeOut;
  private Activity activity;
  private ActionProgress actionProgress;
  private boolean notShowButtonCancel;

  private CustomProgress(Activity activity, ActionProgress actionProgress) {
    this.activity = activity;
    this.actionProgress = actionProgress;
  }

  public static CustomProgress getInstance(Activity activity, ActionProgress actionProgress) {
    if (customProgress == null) {
      customProgress = new CustomProgress(activity, actionProgress);
    }
    return customProgress;
  }

  @SuppressLint("SetTextI18n")
  public void showProgress(Context context, String message, boolean cancelable) {
    showProgress(context, message, cancelable, false);
  }

  public void showProgress(Context context, String message, boolean cancelable, boolean notShowButtonCancel) {
    this.notShowButtonCancel = notShowButtonCancel;
    mDialog = new Dialog(context);
    // no tile for the dialog
    mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    mDialog.setContentView(R.layout.prograss_bar_dialog);
    btnCancel = (Button) mDialog.findViewById(R.id.btn_cancel);
    btnCancel.setVisibility(View.GONE);
    btnCancel.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        actionProgress.actionCancel();
        mDialog.dismiss();
      }
    });
    mProgressBar = (ProgressBar) mDialog.findViewById(R.id.progress_bar);
    //  mProgressBar.getIndeterminateDrawable().setColorFilter(context.getResources()
    // .getColor(R.color.material_blue_gray_500), PorterDuff.Mode.SRC_IN);
    TextView progressText = (TextView) mDialog.findViewById(R.id.progress_text);
    progressText.setText("" + message);
    progressText.setVisibility(View.VISIBLE);
    mProgressBar.setVisibility(View.VISIBLE);
    // you can change or add this line according to your need
    mProgressBar.setIndeterminate(true);
    mDialog.setCancelable(cancelable);
    mDialog.setCanceledOnTouchOutside(cancelable);
    
    if (!notShowButtonCancel) {
      timerTaskThreadToShowBtnCancel = new TimerTaskThread(activity, new Callable<Object>() {
        @Override
        public Object call() throws Exception {
          showButtonCancel();
          setTimerToWaitTimeOut();
          return null;
        }
      },this, TimeUnit.SECONDS.toMillis(TIME_SECONDS_TO_WAIT_CANCEL));
      mDialog.setOnShowListener(new DialogInterface.OnShowListener() {
        @Override
        public void onShow(DialogInterface dialogInterface) {
          try {
            if (!notShowButtonCancel && timerTaskThreadToShowBtnCancel != null) {
              timerTaskThreadToShowBtnCancel.run();
            }
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      });
    } else {
      mDialog.setOnShowListener(null);
      setTimerToWaitTimeOut();
    }
    mDialog.show();
  }

  public boolean isShown() {
    if (mDialog != null) {
      return mDialog.isShowing();
    }
    return false;
  }

  public void hideProgress() {
    if (mDialog != null) {
      mDialog.dismiss();
      mDialog = null;
      mProgressBar = null;
      timerTaskThreadToShowBtnCancel = null;
      timerTaskThreadToTimeOut = null;
      btnCancel = null;
      notShowButtonCancel = false;
    }
  }

  private void showButtonCancel() {
    if (btnCancel != null && !notShowButtonCancel) {
      btnCancel.setVisibility(View.VISIBLE);
    }
  }

  private void setTimerToWaitTimeOut() {
    timerTaskThreadToTimeOut = new TimerTaskThread(activity, new Callable<Object>() {
      @Override
      public Object call() throws Exception {
        hideProgress();
        return null;
      }
    }, this, TimeUnit.SECONDS.toMillis(TIME_SECONDS_TO_TIME_OUT));
    timerTaskThreadToTimeOut.run();
  }

  @Override
  public void onResultCallable(Object result) {
    //
  }

  interface ActionProgress {
    public void actionCancel();
  }
}
