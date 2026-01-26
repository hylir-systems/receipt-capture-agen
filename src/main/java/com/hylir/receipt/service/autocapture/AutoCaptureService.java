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
    
    // 标记是否有正在进行的异步任务
    private volatile boolean taskInProgress = false;
    
    // 标记是否已经初始化基准帧（用于首次启动或重新开始预览时）
    private volatile boolean detectorInitialized = false;

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
        // 重置检测器初始化标志，等待第一次帧来初始化基准帧
        detectorInitialized = false;
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

        // 如果还未初始化基准帧，先初始化（用于首次启动或重新开始预览时）
        // 这样即使回单已经在高拍仪下，也不会误判为"画面在变化"
        if (!detectorInitialized) {
            detector.initialize(pixels, width, height);
            detectorInitialized = true;
            // 初始化后，基准帧就是当前帧，所以画面必然是稳定的
            // 如果当前状态是 IDLE，应该直接进入 READY 状态，开始计数，准备识别
            // 这样即使回单已经稳定地放在高拍仪下，也能正常识别
            if (state == State.IDLE) {
                state = State.READY;
                stableCount.set(1);
            }
            // 首次初始化后，本次帧处理完成，等待下一帧继续计数
            return;
        }

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
                // 如果画面变化，说明可能是新纸张放入，应该重新开始流程
                if (changing) {
                    // 画面变化，重置状态，让状态机重新开始流程
                    // 异步任务完成后会检查状态，如果已经不是PROCESSING，就不会覆盖状态
                    state = State.UNSTABLE;
                    stableCount.set(0);
                    // 不调用detector.reset()，让detector自然检测到变化
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
        taskInProgress = true;

        executor.submit(() -> {
            try {
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
                
                // 检查当前状态是否仍然是PROCESSING（可能已被画面变化改变）
                // 如果状态已经不是PROCESSING，说明画面已经变化，应该重新开始流程
                if (state == State.PROCESSING) {
                    // 根据识别结果决定状态
                    if (r.isSuccess() || r.isDuplicate()) {
                        // 识别成功或检测到重复条码，进入 PROCESSED 状态
                        // 只有检测到画面变化（新纸张放入）才重新开始识别
                        state = State.PROCESSED;
                        stableCount.set(0);
                        // 不调用detector.reset()，让detector保持当前帧状态
                        // 这样如果画面没有变化，detector会正确返回false
                        // 如果画面变化（新纸张放入），detector会正确返回true
                    } else {
                        // 识别失败，重置到 IDLE，可以继续尝试
                        // 但需要等待冷却时间，避免立即重复尝试
                        state = State.IDLE;
                        stableCount.set(0);
                        // 不调用detector.reset()，保持detector状态，避免误判
                    }
                } else {
                    // 状态已经被改变（可能是画面变化），保持当前状态
                    // 不进行任何操作，让状态机自然流转
                }
            } finally {
                taskInProgress = false;
            }
        });
    }

    private void reset() {
        state = State.IDLE;
        stableCount.set(0);
        // 注意：不在这里调用detector.reset()，因为会导致下次检测误判为画面变化
        // detector.reset()只在需要真正重置检测器时调用（如识别完成后）
    }

    public void shutdown() {
        executor.shutdown();
    }
}