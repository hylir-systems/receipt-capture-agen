package com.hylir.receipt.service.autocapture;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 自动采集核心状态机
 */
public class AutoCaptureService {

    private static final int STABLE_FRAME_COUNT = 8;
    private static final long COOLDOWN_MS = 1500;

    enum State { IDLE, UNSTABLE, READY }

    private final FrameChangeDetector detector;
    private final CapturePipeline pipeline;

    private volatile State state = State.IDLE;
    private final AtomicInteger stableCount = new AtomicInteger(0);
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private long lastCaptureTime = 0;

    private volatile int[] frame;
    private volatile int w, h;

    private Consumer<CapturePipeline.CaptureResult> callback;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "AutoCapture"));

    public AutoCaptureService(FrameChangeDetector detector,
                              CapturePipeline pipeline) {
        this.detector = detector;
        this.pipeline = pipeline;
    }

    public void enable() {
        enabled.set(true);
        reset();
    }

    public void disable() {
        enabled.set(false);
        reset();
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setCallback(Consumer<CapturePipeline.CaptureResult> cb) {
        this.callback = cb;
    }

    public void onFrame(int[] pixels, int width, int height) {
        if (!enabled.get()) return;

        frame = pixels.clone();
        w = width;
        h = height;

        boolean changing = detector.isFrameChanging(pixels, width, height);

        switch (state) {
            case IDLE -> {
                if (changing) state = State.UNSTABLE;
            }
            case UNSTABLE -> {
                if (!changing) {
                    state = State.READY;
                    stableCount.set(1);
                }
            }
            case READY -> {
                if (changing) {
                    state = State.UNSTABLE;
                    stableCount.set(0);
                } else if (stableCount.incrementAndGet() >= STABLE_FRAME_COUNT) {
                    capture();
                }
            }
        }
    }

    private void capture() {
        long now = System.currentTimeMillis();
        if (now - lastCaptureTime < COOLDOWN_MS) {
            reset();
            return;
        }
        lastCaptureTime = now;

        final int[] snap = frame.clone();
        final int sw = w, sh = h;

        executor.submit(() -> {
            CapturePipeline.CaptureResult r =
                    pipeline.processFrame(snap, sw, sh);
            if (callback != null) callback.accept(r);
        });

        reset();
    }

    private void reset() {
        state = State.IDLE;
        stableCount.set(0);
        detector.reset();
    }

    public void shutdown() {
        executor.shutdown();
    }
}