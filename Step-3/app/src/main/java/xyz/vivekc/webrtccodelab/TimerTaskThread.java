package xyz.vivekc.webrtccodelab;

import android.app.Activity;
import android.os.Handler;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;

@SuppressWarnings("ALL")
public class TimerTaskThread implements Runnable {

  private long delay_ms;
  private Timer timer;
  private Activity activity;
  private Callable<Object> callable;
  private ResultCallable resultCallable;

  public TimerTaskThread(Callable<Object> callable, ResultCallable resultCallable, long delay_ms){
    this.callable = callable;
    this.resultCallable = resultCallable;
    this.delay_ms = delay_ms;
  }

  public TimerTaskThread(Activity activity, Callable<Object> callable, ResultCallable resultCallable, long delay_ms){
    this(callable, resultCallable, delay_ms);
    this.activity = activity;
  }

  private void doingCallable() {
    if(timer != null){
      timer.cancel();
    }

    if (callable != null && resultCallable != null) {
      try {
        //re-schedule timer here
        //otherwise, IllegalStateException of
        //"TimerTask is scheduled already"
        //will be thrown
        timer = new Timer();
        timer.schedule(new TimerTask() {
          @Override
          public void run() {
            if (activity != null) {
              activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                  letsdoit();
                }
              });
            } else {
              letsdoit();
            }
          }
        }, delay_ms);
      } catch (Exception e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      }
    }
  }

  private void letsdoit(){
    Object objectResult = null;
    try {
      objectResult = callable.call();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    resultCallable.onResultCallable(objectResult);
    if(timer != null){
      timer.cancel();
    }
  }

  @Override
  public void run() {
    if (activity != null) {
      activity.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          doingCallable();
        }
      });
    } else {
      new Handler().post(new Runnable() {
        @Override
        public void run() {
          doingCallable();
        }
      });
    }
  }

  interface ResultCallable {
    public void onResultCallable(Object result);
  }
}
