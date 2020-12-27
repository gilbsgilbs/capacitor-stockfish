package org.lichess.mobileapp.stockfish;

import android.app.ActivityManager;
import android.content.Context;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.getcapacitor.JSObject;
import com.getcapacitor.NativePlugin;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;


@NativePlugin
public final class Stockfish extends Plugin {

  private static final String EVENT_OUTPUT = "output";

  private boolean isInit = false;

  private final ScheduledExecutorService scheduler =
    Executors.newScheduledThreadPool(1);
  private ScheduledFuture<?> stopOnPauseHandle;

  static {
    System.loadLibrary("stockfish");
  }

  // JNI
  public native void jniInit();
  public native void jniExit();
  public native void jniCmd(String cmd);
  public void onMessage(byte[] b) {
    JSObject output = new JSObject();
    output.put("line", new String(b));
    notifyListeners(EVENT_OUTPUT, output);
  }
  // end JNI

  @PluginMethod
  public void getMaxMemory(PluginCall call) {
    Context context = getContext();
    ActivityManager actManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
    actManager.getMemoryInfo(memInfo);
    // allow max 1/16th of total mem
    long maxMemInMB = (memInfo.totalMem / 16) / (1024L * 1024L);
    JSObject ret = new JSObject();
    ret.put("value", maxMemInMB);
    call.success(ret);
  }

  @PluginMethod
  public void start(PluginCall call) {
    if (!isInit) {
      jniInit();
      isInit = true;
    }
    call.success();
  }

  @PluginMethod
  public void cmd(PluginCall call) {
    if (isInit) {
      String cmd = call.getString("cmd");
      if (cmd == null) {
        call.error("Must provide a cmd");
        return;
      }
      jniCmd(cmd);
      call.success();
    } else {
      call.error("Please call init before doing anything.");
    }
  }

  @PluginMethod
  public void exit(PluginCall call) {
    if (isInit) {
      doExit();
    }
    call.success();
  }

  @Override
  protected void handleOnDestroy() {
    if (isInit) {
      doExit();
    }
  }

  @Override
  protected void handleOnPause() {
    if (isInit) {
      stopOnPauseHandle = scheduler.schedule(new Runnable() {
        public void run() {
          if (isInit) {
            jniCmd("stop");
          }
        }
      }, 60 * 10, SECONDS);
    }
  }

  @Override
  protected void handleOnResume() {
    if (isInit) {
      if (stopOnPauseHandle != null) {
        stopOnPauseHandle.cancel(false);
      }
    }
  }

  private void doExit() {
    if (isInit) {
      jniCmd("stop");
      jniExit();
      isInit = false;
    }
  }
}
