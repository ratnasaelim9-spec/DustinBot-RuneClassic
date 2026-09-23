package com.dustinbot.runeclassic;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Path;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class BotAccessibilityService extends AccessibilityService {

    private static BotAccessibilityService instance;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private boolean botRunning = false;
    private boolean screenshotBusy = false;

    private long lastTapTime = 0L;

    private static final long SCAN_INTERVAL = 500L;
    private static final long TAP_COOLDOWN = 1400L;

    private static final int MIN_COMPONENT_SIZE = 18;
    private static final int MAX_COMPONENT_SIZE = 20000;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onDestroy() {
        stopBot();
        instance = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(
            AccessibilityEvent event) {
        // Bot ใช้ภาพหน้าจอโดยตรง
    }

    @Override
    public void onInterrupt() {
        stopBot();
    }

    public static BotAccessibilityService getInstance() {
        return instance;
    }

    public boolean isBotRunning() {
        return botRunning;
    }

    public void startBot() {
        if (botRunning) {
            return;
        }

        botRunning = true;
        scheduleScan(0L);
    }

    public void stopBot() {
        botRunning = false;
        handler.removeCallbacksAndMessages(null);
    }

    private void scheduleScan(long delay) {
        handler.postDelayed(() -> {
            if (!botRunning) {
                return;
            }

            scanScreen();
        }, delay);
    }

    private void scanScreen() {
        if (!botRunning) {
            return;
        }

        if (screenshotBusy) {
            scheduleScan(SCAN_INTERVAL);
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            scheduleScan(SCAN_INTERVAL);
            return;
        }

        screenshotBusy = true;

        takeScreenshot(
                Display.DEFAULT_DISPLAY,
                getMainExecutor(),
                new ScreenshotCallback()
        );
    }

    private class ScreenshotCallback
            implements AccessibilityService.TakeScreenshotCallback {

        @Override
        public void onSuccess(
                AccessibilityService.ScreenshotResult result) {

            HardwareBuffer hardwareBuffer = null;
            Bitmap hardwareBitmap = null;
            Bitmap bitmap = null;

            try {
                hardwareBuffer =
                        result.getHardwareBuffer();

                if (hardwareBuffer == null) {
                    return;
                }

                hardwareBitmap =
                        Bitmap.wrapHardwareBuffer(
                                hardwareBuffer,
                                result.getColorSpace()
                        );

                if (hardwareBitmap == null) {
                    return;
                }

                bitmap =
                        hardwareBitmap.copy(
                                Bitmap.Config.ARGB_8888,
                                false
                        );

                if (bitmap == null) {
                    return;
                }

                processScreenshot(bitmap);

            } catch (Exception ignored) {

                // ป้องกัน Screenshot error
                // ไม่ให้ Bot หยุดทำงาน

            } finally {

                if (bitmap != null) {
                    bitmap.recycle();
                }

                if (hardwareBitmap != null
                        && hardwareBitmap != bitmap) {

                    try {
                        hardwareBitmap.recycle();
                    } catch (Exception ignored) {
                    }
                }

                if (hardwareBuffer != null) {
                    try {
                        hardwareBuffer.close();
                    } catch (Exception ignored) {
                    }
                }

                finishScreenshot();
            }
        }

        @Override
        public void onFailure(int errorCode) {
            finishScreenshot();
        }
    }

    private void finishScreenshot() {
        screenshotBusy = false;

        if (botRunning) {
            scheduleScan(SCAN_INTERVAL);
        }
    }

    private void processScreenshot(Bitmap bitmap) {

        if (bitmap.getWidth() <= 0
                || bitmap.getHeight() <= 0) {
            return;
        }

        List<Component> components =
                findPinkComponents(bitmap);

        if (components.isEmpty()) {
            return;
        }

        Component target =
                chooseTarget(components);

        if (target == null) {
            return;
        }

        long now =
                System.currentTimeMillis();

        if (now - lastTapTime < TAP_COOLDOWN) {
            return;
        }

        lastTapTime = now;

        tapScreen(
                target.centerX,
                target.centerY
        );
    }

    private List<Component> findPinkComponents(
            Bitmap bitmap) {

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        int sampleStep = 3;

        int gridWidth =
                Math.max(
                        1,
                        width / sampleStep
                );

        int gridHeight =
                Math.max(
                        1,
                        height / sampleStep
                );

        boolean[][] visited =
                new boolean[
                        gridHeight
                ][
                        gridWidth
                ];

        List<Component> result =
                new ArrayList<>();

        for (int gy = 0;
             gy < gridHeight;
             gy++) {

            int y =
                    gy * sampleStep;

            if (y >= height) {
                continue;
            }

            for (int gx = 0;
                 gx < gridWidth;
                 gx++) {

                if (visited[gy][gx]) {
                    continue;
                }

                int x =
                        gx * sampleStep;

                if (x >= width) {
                    continue;
                }

                int color =
                        bitmap.getPixel(
                                x,
                                y
                        );

                if (!isPink(color)) {

                    visited[gy][gx] = true;

                    continue;
                }

                Component component =
                        floodFill(
                                bitmap,
                                gx,
                                gy,
                                sampleStep,
                                visited
                        );

                if (component != null
                        && component.pixelCount
                        >= MIN_COMPONENT_SIZE
                        && component.pixelCount
                        <= MAX_COMPONENT_SIZE) {

                    result.add(component);
                }
            }
        }

        return result;
    }

    private Component floodFill(
            Bitmap bitmap,
            int startX,
            int startY,
            int step,
            boolean[][] visited) {

        int gridHeight =
                visited.length;

        int gridWidth =
                visited[0].length;

        ArrayDeque<Point> queue =
                new ArrayDeque<>();

        queue.add(
                new Point(
                        startX,
                        startY
                )
        );

        visited[startY][startX] = true;

        int count = 0;

        long sumX = 0;
        long sumY = 0;

        int minX = startX;
        int maxX = startX;

        int minY = startY;
        int maxY = startY;

        while (!queue.isEmpty()) {

            Point point =
                    queue.removeFirst();

            int gx = point.x;
            int gy = point.y;

            int px =
                    gx * step;

            int py =
                    gy * step;

            if (px >= bitmap.getWidth()
                    || py >= bitmap.getHeight()) {
                continue;
            }

            int color =
                    bitmap.getPixel(
                            px,
                            py
                    );

            if (!isPink(color)) {
                continue;
            }

            count++;

            sumX += px;
            sumY += py;

            minX =
                    Math.min(
                            minX,
                            gx
                    );

            maxX =
                    Math.max(
                            maxX,
                            gx
                    );

            minY =
                    Math.min(
                            minY,
                            gy
                    );

            maxY =
                    Math.max(
                            maxY,
                            gy
                    );

            addPoint(
                    queue,
                    gx + 1,
                    gy,
                    bitmap,
                    visited,
                    step
            );

            addPoint(
                    queue,
                    gx - 1,
                    gy,
                    bitmap,
                    visited,
                    step
            );

            addPoint(
                    queue,
                    gx,
                    gy + 1,
                    bitmap,
                    visited,
                    step
            );

            addPoint(
                    queue,
                    gx,
                    gy - 1,
                    bitmap,
                    visited,
                    step
            );
        }

        if (count == 0) {
            return null;
        }

        Component component =
                new Component();

        component.pixelCount =
                count;

        component.centerX =
                (int) (sumX / count);

        component.centerY =
                (int) (sumY / count);

        component.width =
                (maxX - minX + 1) * step;

        component.height =
                (maxY - minY + 1) * step;

        return component;
    }

    private void addPoint(
            ArrayDeque<Point> queue,
            int x,
            int y,
            Bitmap bitmap,
            boolean[][] visited,
            int step) {

        if (y < 0
                || y >= visited.length
                || x < 0
                || x >= visited[0].length) {
            return;
        }

        if (visited[y][x]) {
            return;
        }

        int px =
                x * step;

        int py =
                y * step;

        if (px >= bitmap.getWidth()
                || py >= bitmap.getHeight()) {
            return;
        }

        visited[y][x] = true;

        if (isPink(
                bitmap.getPixel(
                        px,
                        py
                ))) {

            queue.addLast(
                    new Point(
                            x,
                            y
                    )
            );
        }
    }

    private boolean isPink(int color) {

        int red =
                Color.red(color);

        int green =
                Color.green(color);

        int blue =
                Color.blue(color);

        float[] hsv =
                new float[3];

        Color.RGBToHSV(
                red,
                green,
                blue,
                hsv
        );

        float hue = hsv[0];
        float saturation = hsv[1];
        float value = hsv[2];

        boolean pinkHue =
                hue >= 300f
                        || hue <= 15f;

        boolean enoughSaturation =
                saturation >= 0.35f;

        boolean enoughBrightness =
                value >= 0.35f;

        return pinkHue
                && enoughSaturation
                && enoughBrightness;
    }

    private Component chooseTarget(
            List<Component> components) {

        Component best = null;

        double bestScore =
                Double.MIN_VALUE;

        int screenCenterX =
                getResources()
                        .getDisplayMetrics()
                        .widthPixels / 2;

        int screenCenterY =
                getResources()
                        .getDisplayMetrics()
                        .heightPixels / 2;

        for (Component component :
                components) {

            if (component.centerY < 80) {
                continue;
            }

            if (component.width < 5
                    || component.height < 5) {
                continue;
            }

            double distance =
                    Math.hypot(
                            component.centerX
                                    - screenCenterX,
                            component.centerY
                                    - screenCenterY
                    );

            double sizeScore =
                    Math.min(
                            component.pixelCount,
                            5000
                    );

            double distanceScore =
                    Math.max(
                            0,
                            1000 - distance
                    );

            double score =
                    sizeScore
                            + distanceScore;

            if (score > bestScore) {

                bestScore = score;

                best = component;
            }
        }

        return best;
    }

    private void tapScreen(
            int x,
            int y) {

        if (Build.VERSION.SDK_INT
                < Build.VERSION_CODES.N) {
            return;
        }

        Path path =
                new Path();

        path.moveTo(
                x,
                y
        );

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(
                        path,
                        0,
                        80
                );

        GestureDescription gesture =
                new GestureDescription.Builder()
                        .addStroke(stroke)
                        .build();

        dispatchGesture(
                gesture,
                null,
                null
        );
    }

    private static class Point {

        final int x;
        final int y;

        Point(
                int x,
                int y) {

            this.x = x;
            this.y = y;
        }
    }

    private static class Component {

        int centerX;
        int centerY;

        int width;
        int height;

        int pixelCount;
    }
}
