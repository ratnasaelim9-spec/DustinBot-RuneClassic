package com.dustinbot.runeclassic;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 50, 40, 40);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("DustinBot RuneClassic");
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 40);

        statusText = new TextView(this);
        statusText.setTextSize(18);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, 20, 0, 30);

        Button accessibilityButton = new Button(this);
        accessibilityButton.setText("เปิด Accessibility Service");

        Button startButton = new Button(this);
        startButton.setText("เริ่ม Bot");

        Button stopButton = new Button(this);
        stopButton.setText("หยุด Bot");

        root.addView(title);
        root.addView(statusText);
        root.addView(accessibilityButton);
        root.addView(startButton);
        root.addView(stopButton);

        setContentView(root);

        accessibilityButton.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        startButton.setOnClickListener(v -> {
            BotAccessibilityService service =
                    BotAccessibilityService.getInstance();

            if (service != null) {
                service.startBot();
                updateStatus();
            } else {
                statusText.setText(
                        "กรุณาเปิด Accessibility Service ก่อน"
                );
            }
        });

        stopButton.setOnClickListener(v -> {
            BotAccessibilityService service =
                    BotAccessibilityService.getInstance();

            if (service != null) {
                service.stopBot();
                updateStatus();
            }
        });

        updateStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        BotAccessibilityService service =
                BotAccessibilityService.getInstance();

        if (service == null) {
            statusText.setText("สถานะ: Accessibility ยังไม่ทำงาน");
        } else if (service.isBotRunning()) {
            statusText.setText("สถานะ: BOT กำลังทำงาน");
        } else {
            statusText.setText("สถานะ: พร้อมใช้งาน");
        }
    }

    public static boolean isAccessibilityEnabled(
            MainActivity activity) {

        String enabledServices =
                Settings.Secure.getString(
                        activity.getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                );

        if (TextUtils.isEmpty(enabledServices)) {
            return false;
        }

        ComponentName expectedComponent =
                new ComponentName(
                        activity,
                        BotAccessibilityService.class
                );

        String expected =
                expectedComponent.flattenToString();

        TextUtils.SimpleStringSplitter splitter =
                new TextUtils.SimpleStringSplitter(':');

        splitter.setString(enabledServices);

        while (splitter.hasNext()) {
            String service = splitter.next();

            if (service.equalsIgnoreCase(expected)) {
                return true;
            }
        }

        return false;
    }
}
