package com.turfwar.mow;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Foreground service that keeps GPS running while the screen is off. Fixes are buffered here;
 * the web layer drains the buffer, so nothing is lost even if Android freezes its JavaScript.
 */
public class TrackService extends Service implements LocationListener, SensorEventListener {
    public static final String CHANNEL = "mowing";
    public static boolean running = false;
    public static String lastError = null;
    private static final List<double[]> fixes = new ArrayList<>(); // lat, lon, accuracy, speed, time
    private static final List<double[]> bumps = new ArrayList<>(); // time, sum of squares, sample count

    private SensorManager sm;
    private float gx, gy, gz;          // gravity direction, slow low-pass
    private boolean haveG = false;
    private double bpPrev, bpHigh, bpLow;  // 0.5-5 Hz band-pass state
    private int warm = 0;
    private long lastNs = 0;
    private long bucketEnd = 0;
    private double bucketSum = 0;
    private int bucketN = 0;

    private LocationManager lm;
    private PowerManager.WakeLock wake;

    public static synchronized List<double[]> drain() {
        List<double[]> out = new ArrayList<>(fixes);
        fixes.clear();
        return out;
    }

    public static synchronized List<double[]> drainBumps() {
        List<double[]> out = new ArrayList<>(bumps);
        bumps.clear();
        return out;
    }

    private static synchronized void addBump(double[] b) {
        if (bumps.size() < 20000) bumps.add(b);
    }

    private static synchronized void add(double[] fix) {
        if (fixes.size() < 20000) fixes.add(fix);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @SuppressLint({ "MissingPermission", "WakelockTimeout" })
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (running) return START_NOT_STICKY;
        running = true;
        lastError = null;

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Mowing", NotificationManager.IMPORTANCE_LOW));
        }
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
            .setContentTitle("Turf War is recording your cut")
            .setContentText("Tap to open. Tracking continues with the screen off.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(pi)
            .build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        else startForeground(1, n);

        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper());
        } catch (Exception e) {
            lastError = "GPS couldn't start. Check that location permission is allowed.";
        }
        try {
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 0f, this, Looper.getMainLooper());
        } catch (Exception ignored) { }

        // Bumpiness needs the accelerometer running with the screen off too. The partial wake lock
        // below keeps the CPU alive so samples keep arriving.
        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (acc != null) sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_GAME);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TurfWar:track");
        wake.acquire(8 * 60 * 60 * 1000L);
        return START_NOT_STICKY;
    }

    @Override
    public void onLocationChanged(Location l) {
        add(new double[] {
            l.getLatitude(), l.getLongitude(),
            l.hasAccuracy() ? l.getAccuracy() : -1,
            l.hasSpeed() ? l.getSpeed() : -1,
            l.getTime(),
        });
    }

    /**
     * Same filter the web app uses: find "up" from gravity, keep only the vertical shaking,
     * band-pass 0.5-5 Hz so engine buzz and slow tilt drop out, then bank one bucket per second.
     */
    @Override
    public void onSensorChanged(SensorEvent e) {
        double dt = lastNs == 0 ? 0.016 : (e.timestamp - lastNs) / 1e9;
        lastNs = e.timestamp;
        if (dt < 0.001) dt = 0.001;
        if (dt > 0.1) dt = 0.1;

        float x = e.values[0], y = e.values[1], z = e.values[2];
        if (!haveG) { gx = x; gy = y; gz = z; haveG = true; }
        double k = dt / (0.8 + dt);
        gx += k * (x - gx); gy += k * (y - gy); gz += k * (z - gz);

        double lx = x - gx, ly = y - gy, lz = z - gz;
        double gn = Math.sqrt(gx * gx + gy * gy + gz * gz);
        if (gn < 1e-3) gn = 9.81;
        double vert = (lx * gx + ly * gy + lz * gz) / gn;

        double hRC = 0.318, lRC = 0.0318;
        double hp = (hRC / (hRC + dt)) * (bpHigh + vert - bpPrev);
        bpHigh = hp; bpPrev = vert;
        bpLow += (dt / (lRC + dt)) * (hp - bpLow);
        if (warm < 60) { warm++; return; } // let the filters settle

        bucketSum += bpLow * bpLow;
        bucketN++;
        long now = System.currentTimeMillis();
        if (bucketEnd == 0) bucketEnd = now + 1000;
        if (now >= bucketEnd) {
            addBump(new double[] { now, bucketSum, bucketN });
            bucketSum = 0; bucketN = 0; bucketEnd = now + 1000;
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    // Older Android versions crash without these
    @Override public void onProviderEnabled(String p) { lastError = null; }
    @Override public void onProviderDisabled(String p) { lastError = "Location is turned off. Turn it on from quick settings."; }
    @Override public void onStatusChanged(String p, int s, Bundle e) { }

    @Override
    public void onDestroy() {
        running = false;
        try { if (lm != null) lm.removeUpdates(this); } catch (Exception ignored) { }
        try { if (sm != null) sm.unregisterListener(this); } catch (Exception ignored) { }
        try { if (wake != null && wake.isHeld()) wake.release(); } catch (Exception ignored) { }
        super.onDestroy();
    }
}
