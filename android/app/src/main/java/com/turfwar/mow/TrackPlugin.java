package com.turfwar.mow;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;

import java.util.List;

@CapacitorPlugin(name = "Tracking")
public class TrackPlugin extends Plugin {

    @PluginMethod
    public void start(PluginCall call) {
        if (Build.VERSION.SDK_INT >= 33
            && ContextCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(), new String[] { Manifest.permission.POST_NOTIFICATIONS }, 91);
        }
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            call.reject("Location permission is needed to record a cut.");
            return;
        }
        TrackService.drain(); // start from a clean buffer
        Intent i = new Intent(getContext(), TrackService.class);
        ContextCompat.startForegroundService(getContext(), i);
        call.resolve();
    }

    @PluginMethod
    public void stop(PluginCall call) {
        getContext().stopService(new Intent(getContext(), TrackService.class));
        call.resolve();
    }

    /** Everything recorded since the last call. */
    @PluginMethod
    public void drain(PluginCall call) {
        List<double[]> fixes = TrackService.drain();
        JSONArray arr = new JSONArray();
        for (double[] f : fixes) {
            JSObject o = new JSObject();
            o.put("lat", f[0]);
            o.put("lon", f[1]);
            if (f[2] >= 0) o.put("acc", f[2]);
            if (f[3] >= 0) o.put("speed", f[3]);
            o.put("time", (long) f[4]);
            arr.put(o);
        }
        JSONArray bumps = new JSONArray();
        for (double[] b : TrackService.drainBumps()) {
            JSObject o = new JSObject();
            o.put("time", (long) b[0]);
            o.put("sumSq", b[1]);
            o.put("n", (int) b[2]);
            bumps.put(o);
        }
        JSObject ret = new JSObject();
        ret.put("fixes", arr);
        ret.put("bumps", bumps);
        ret.put("running", TrackService.running);
        if (TrackService.lastError != null) ret.put("error", TrackService.lastError);
        call.resolve(ret);
    }
}
