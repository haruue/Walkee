package moe.haruue.walkee.ui.floatalert;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import com.base.basepedo.config.Constant;
import com.base.basepedo.service.StepService;

import moe.haruue.walkee.App;
import moe.haruue.walkee.BuildConfig;
import moe.haruue.walkee.R;
import moe.haruue.walkee.config.Const;
import moe.haruue.walkee.util.KVUtils;
import moe.haruue.walkee.util.LogTimestampUtils;

import static moe.haruue.walkee.util.ApplicationUtils.isServiceWork;

/**
 * @author Haruue Icymoon haruue@caoyue.com.cn
 */

public class FloatAlertService extends Service implements Handler.Callback {

    public static final String TAG = "FloatAlertService";
    public static final boolean DEBUG = BuildConfig.DEBUG;

    private FloatAlertBarView alertBarView;
    private WindowManager.LayoutParams alertBarParams;
    private FloatFullscreenLockView fullscreenLockView;
    private WindowManager.LayoutParams fullscreenLockParams;
    private FloatFullscreenUnlockView fullscreenUnlockView;

    private WindowManager manager;

    private Handler handler;
    private long lastStep = 0;
    private long start = 0;
    private int preferType;
    boolean isStanded;

    private View currentShow;

    public static final int MSG_CHECK_STOP = 93190719;

    private ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (name.getClassName().equals(StepService.class.getName())) {
                Messenger initMessenger = new Messenger(service);
                Messenger replyMessenger = new Messenger(handler);
                Message initMsg = Message.obtain();
                initMsg.what = Constant.MSG_INITIALIZE_PER_STEP_CALLBACK;
                initMsg.replyTo = replyMessenger;
                try {
                    initMessenger.send(initMsg);
                } catch (RemoteException e) {
                    Log.e(TAG, "onServiceConnected: can't send per step callback init msg, connect failed", e);
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        initializePreferType();
        LogTimestampUtils.refreshLastLogTimestamp(getApplicationContext());
        start = System.currentTimeMillis();
        lastStep = System.currentTimeMillis();
        startStepServiceForStrategy();
        handler = new Handler(Looper.getMainLooper(), this);
        handler.sendEmptyMessage(MSG_CHECK_STOP);
    }

    private void show(View view, WindowManager.LayoutParams params) {
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 && preferType == WindowManager.LayoutParams.TYPE_TOAST)
                || (currentShow != null && !currentShow.equals(view))) {
            // remove it before add it on Android 7.1.1, otherwise the view will be auto hidden by system
            // @see aosp/frameworks/base/services/core/java/com/android/server/wm/WindowManagerService.java:2102
            hide(currentShow);
        }
        try {
            getWindowManager().addView(view, params);
        } catch (Exception ignored) {}
        currentShow = view;
    }

    private void hide(View view) {
        try {
            getWindowManager().removeView(view);
        } catch (Exception ignored) {}
    }

    private void hideAllView() {
        for (View v : new View[]{alertBarView, fullscreenLockView, fullscreenUnlockView}) {
            hide(v);
        }
    }

    private void initializePreferType() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT_WATCH) {
            preferType = WindowManager.LayoutParams.TYPE_PHONE;
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
            preferType = WindowManager.LayoutParams.TYPE_TOAST;
        } else if (Settings.canDrawOverlays(getApplicationContext())) {
            preferType = WindowManager.LayoutParams.TYPE_PHONE;
        } else {
            preferType = WindowManager.LayoutParams.TYPE_TOAST;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //unregisterReceiver(receiver);
        unbindService(conn);
        hideAllView();
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_CHECK_STOP:
                if (System.currentTimeMillis() - lastStep > Const.TIMEOUT_BACK_STAND_LONG) {
                    isStanded = true;
                    hideAllView();
                } else {
                    isStanded = false;
                    refresh();
                    handler.sendEmptyMessageDelayed(MSG_CHECK_STOP, Const.INTERVAL);
                }
                return true;
            case Constant.MSG_PER_STEP:
                lastStep = System.currentTimeMillis();
                if (isStanded) {
                    handler.sendEmptyMessage(MSG_CHECK_STOP);
                }
                return true;
        }
        return false;
    }

    public WindowManager getWindowManager() {
        if (manager == null) {
            manager = (WindowManager) getSystemService(WINDOW_SERVICE);
        }
        return manager;
    }

    private void showAlertBarView() {
        if (alertBarView == null) {
            alertBarView = new FloatAlertBarView(getApplicationContext());
        }
        if (alertBarParams == null) {
            alertBarParams = new WindowManager.LayoutParams();
            alertBarParams.x = 0;
            alertBarParams.y = 0;
            alertBarParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
            alertBarParams.width = WindowManager.LayoutParams.MATCH_PARENT;
            alertBarParams.gravity = Gravity.TOP;
            alertBarParams.format = PixelFormat.TRANSPARENT;
            alertBarParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            alertBarParams.type = preferType;
        }
        show(alertBarView, alertBarParams);
    }

    private void showFullscreenHardLockView() {
        if (fullscreenLockView == null) {
            fullscreenLockView = new FloatFullscreenLockView(getApplicationContext());
        }
        if (fullscreenLockParams == null) {
            fullscreenLockParams = new WindowManager.LayoutParams();
            fullscreenLockParams.x = 0;
            fullscreenLockParams.y = 0;
            fullscreenLockParams.height = WindowManager.LayoutParams.MATCH_PARENT;
            fullscreenLockParams.width = WindowManager.LayoutParams.MATCH_PARENT;
            fullscreenLockParams.gravity = Gravity.CENTER;
            fullscreenLockParams.format = PixelFormat.TRANSPARENT;
            fullscreenLockParams.flags = WindowManager.LayoutParams.FLAG_FULLSCREEN;
            fullscreenLockParams.type = preferType;
        }
        show(fullscreenLockView, fullscreenLockParams);
    }

    private void showFullscreenHardUnlockView() {
        if (fullscreenUnlockView == null) {
            fullscreenUnlockView = new FloatFullscreenUnlockView(getApplicationContext());
            fullscreenUnlockView.findViewById(R.id.bt_fullscreen_unlock_unlock).setOnClickListener(v -> App.getInstance().unlock = System.currentTimeMillis());
            fullscreenUnlockView.findViewById(R.id.bt_fullscreen_unlock_walk).setOnClickListener(v -> start = System.currentTimeMillis());
        }
        if (fullscreenLockParams == null) {
            fullscreenLockParams = new WindowManager.LayoutParams();
            fullscreenLockParams.x = 0;
            fullscreenLockParams.y = 0;
            fullscreenLockParams.height = WindowManager.LayoutParams.MATCH_PARENT;
            fullscreenLockParams.width = WindowManager.LayoutParams.MATCH_PARENT;
            fullscreenLockParams.gravity = Gravity.CENTER;
            fullscreenLockParams.format = PixelFormat.TRANSPARENT;
            fullscreenLockParams.flags = WindowManager.LayoutParams.FLAG_FULLSCREEN;
            fullscreenLockParams.type = preferType;
        }
        show(fullscreenUnlockView, fullscreenLockParams);
    }

    public void refresh() {
        int mode = (int) KVUtils.get(getApplicationContext(), Const.KVKEY_MODE, Const.MODE_EASY);
        if (mode == Const.MODE_EASY) {
            showAlertBarView();
        } else if (mode == Const.MODE_HARD && System.currentTimeMillis() - App.getInstance().unlock > Const.TIMEOUT_RELOCK_HARD) {
            if (System.currentTimeMillis() - start < Const.TIMEOUT_UNLOCK_HARD) {
                showFullscreenHardLockView();
            } else {
                showFullscreenHardUnlockView();
            }
        } else {
            hideAllView();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void start(Context context) {
        Intent starter = new Intent(context, FloatAlertService.class);
        context.startService(starter);
    }

    public static void stop(Context context) {
        Intent starter = new Intent(context, FloatAlertService.class);
        context.stopService(starter);
    }

    private void startStepServiceForStrategy() {
        Intent intent = new Intent(this, StepService.class);
        if (!isServiceWork(this, StepService.class.getName())) {
            startService(intent);
        }
        bindService(intent, conn, Context.BIND_AUTO_CREATE);
    }

}
