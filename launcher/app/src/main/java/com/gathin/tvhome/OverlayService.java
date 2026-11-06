package com.gathin.tvhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.Random;

/**
 * Draws the daily message in a non-interactive overlay, so it stays visible over other apps.
 * It is a foreground service because a plain started service is stopped by Android when the
 * launcher is no longer in front, which would take the overlay away with it.
 */
public class OverlayService extends Service {

    private static final String TAG = "MessageOverlay";
    private static final String CHANNEL_ID = "message_overlay";
    private static final int NOTIFICATION_ID = 41;

    private WindowManager windowManager;
    private TextView view;
    private WindowManager.LayoutParams params;
    private int baseX;
    private int baseY;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private final Runnable shift = new Runnable() {
        @Override
        public void run() {
            if (view == null || params == null) return;
            if (!Settings.canDrawOverlays(OverlayService.this)) {
                stopSelf();
                return;
            }
            int spread = MessageConfig.OVERLAY_SHIFT_MAX_PX * 2 + 1;
            params.x = baseX + random.nextInt(spread) - MessageConfig.OVERLAY_SHIFT_MAX_PX;
            params.y = baseY + random.nextInt(spread) - MessageConfig.OVERLAY_SHIFT_MAX_PX;
            try {
                windowManager.updateViewLayout(view, params);
            } catch (RuntimeException ignored) {
                // The window went away between the check and the update; onDestroy handles cleanup.
            }
            handler.postDelayed(this, MessageConfig.OVERLAY_SHIFT_INTERVAL_MS);
        }
    };

    /** Starts the overlay; returns false when the platform refuses to start the service. */
    static boolean start(Context context) {
        try {
            Intent intent = new Intent(context, OverlayService.class);
            context.startForegroundService(intent);
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "could not start overlay service", e);
            return false;
        }
    }

    static void stop(Context context) {
        context.stopService(new Intent(context, OverlayService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        startForeground();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground();
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW is not granted; stopping overlay");
            stopSelf();
            return START_NOT_STICKY;
        }
        if (view == null) addOverlay();
        if (view != null) {
            handler.removeCallbacks(shift);
            handler.postDelayed(shift, MessageConfig.OVERLAY_SHIFT_INTERVAL_MS);
        }
        return START_STICKY;
    }

    private void addOverlay() {
        TextView overlay = MessageView.create(this);
        overlay.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int bottomMargin = MessageView.bottomMarginPx(screenHeight);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP
                | (MessageConfig.MESSAGE_POSITION == MessageConfig.Position.BOTTOM_CENTER
                ? Gravity.CENTER_HORIZONTAL : Gravity.END);
        baseX = MessageConfig.MESSAGE_POSITION == MessageConfig.Position.BOTTOM_CENTER
                ? 0 : MessageView.sideMarginPx(this);
        baseY = screenHeight - bottomMargin - overlay.getMeasuredHeight();
        lp.x = baseX;
        lp.y = baseY;
        lp.setTitle("daily message");

        try {
            windowManager.addView(overlay, lp);
        } catch (RuntimeException e) {
            Log.w(TAG, "could not add overlay window", e);
            stopSelf();
            return;
        }
        view = overlay;
        params = lp;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (view == null || params == null) return;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        baseY = screenHeight - MessageView.bottomMarginPx(screenHeight) - view.getMeasuredHeight();
        params.y = baseY;
        try {
            windowManager.updateViewLayout(view, params);
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(shift);
        if (view != null && windowManager != null) {
            try {
                windowManager.removeView(view);
            } catch (RuntimeException ignored) {
            }
        }
        view = null;
        params = null;
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startForeground() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.overlay_channel_name),
                    NotificationManager.IMPORTANCE_MIN);
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_clean_settings)
                .setContentTitle(getString(R.string.overlay_notification_title))
                .setContentText(getString(R.string.overlay_notification_text))
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setShowWhen(false)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }
}
