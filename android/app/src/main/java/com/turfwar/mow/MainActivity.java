package com.turfwar.mow;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(TrackPlugin.class);
        super.onCreate(savedInstanceState);

        // Android 15 draws behind the status and navigation bars, so inset the web view
        // by hand. Without this the header hides the clock and the tab bar sits under
        // the system buttons.
        View root = findViewById(android.R.id.content);
        root.setBackgroundColor(Color.parseColor("#F3F6EF"));
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.setAppearanceLightStatusBars(true);   // dark clock and icons on our light background
        c.setAppearanceLightNavigationBars(true);

        // The cut keeps recording with the screen off, but leaving it on is still handy while mowing.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
}
