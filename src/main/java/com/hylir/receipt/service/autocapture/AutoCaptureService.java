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

    enum State { IDLE, UNSTABLE, READY, PROCESSING, PROCESSED }

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
            case PROCESSING -> {
                // 处理中状态：等待异步任务完成，不进行任何处理
                // 如果画面变化，重置状态（可能是新纸张放入）
                if (changing) {
                    state = State.UNSTABLE;
                    stableCount.set(0);
                }
            }
            case PROCESSED -> {
                // 已处理状态：只有检测到画面变化（新纸张放入）才重新开始识别流程
                if (changing) {
                    state = State.UNSTABLE;
                    stableCount.set(0);
                }
                // 如果画面没有变化，保持 PROCESSED 状态，不进行任何处理
            }
        }
    }

    private void capture() {
        // 再次检查是否已禁用，避免处理已停止的任务
        if (!enabled.get()) {
            reset();
            return;
        }
        
        long now = System.currentTimeMillis();
        if (now - lastCaptureTime < COOLDOWN_MS) {
            reset();
            return;
        }
        lastCaptureTime = now;

        final int[] snap = frame.clone();
        final int sw = w, sh = h;

        // 进入处理中状态，避免在异步任务完成前重复触发
        state = State.PROCESSING;
        stableCount.set(0);

        executor.submit(() -> {
            // 在执行前再次检查是否已禁用
            if (!enabled.get()) {
                // 如果已禁用，重置状态
                state = State.IDLE;
                return;
            }
            
            CapturePipeline.CaptureResult r =
                    pipeline.processFrame(snap, sw, sh);
            
            // 在执行回调前最后一次检查是否已禁用
            if (enabled.get() && callback != null) {
                callback.accept(r);
            }
            
            // 根据识别结果决定状态
            if (r.isSuccess() || r.isDuplicate()) {
                // 识别成功或检测到重复条码，进入 PROCESSED 状态
                // 只有检测到画面变化（新纸张放入）才重新开始识别
                state = State.PROCESSED;
                stableCount.set(0);
            } else {
                // 识别失败，重置到 IDLE，可以继续尝试
                state = State.IDLE;
                stableCount.set(0);
            }
        });
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