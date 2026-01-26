package com.hylir.receipt.service.camera;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hylir.receipt.config.AppConfig;

import java.awt.image.BufferedImage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 摄像头流服务
 * 负责实时流和预览的执行器管理
 */
public class CameraStreamService {
    private static final Logger logger = LoggerFactory.getLogger(CameraStreamService.class);
    private ScheduledExecutorService streamExecutor = null;
    private volatile boolean streamRunning = false;

    // 实时流回调接口
    public interface StreamCallback {
        void onFrame(int[] pixels, int width, int height, long captureTimeNanos);

        void onError(String error);
    }

    private StreamCallback streamCallback = null;

    /**
     * 开始实时视频流
     *
     * @param callback       流回调
     * @param grabberManager 抓取器管理器
     * @param deviceIndex    设备索引
     * @return true 如果启动成功
     */
    public boolean startLiveStream(StreamCallback callback, CameraGrabberManager grabberManager, int deviceIndex) {
        if (streamRunning) {
            logger.warn("实时流已在运行中");
            return false;
        }

        if (deviceIndex < 0) {
            logger.error("未选择摄像头设备");
            return false;
        }

        streamCallback = callback;
        streamRunning = true;

        int width = AppConfig.getCameraWidth();
        int height = AppConfig.getCameraHeight();
        int fps = AppConfig.getCameraFps();
        logger.info("开始实时流，设备索引: {}, 分辨率: {}x{} @{}fps", deviceIndex, width, height, fps);

        streamExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Camera-Stream");
            t.setDaemon(true);
            return t;
        });

        try {
            // 创建并启动抓取器
            var grabber = grabberManager.createAndStartGrabber(deviceIndex, width, height);
            // 预热摄像头
            grabberManager.warmUpCamera(grabber);
        } catch (Exception e) {
            logger.error("启动摄像头失败", e);
            streamRunning = false;
            if (streamCallback != null) {
                streamCallback.onError(e.getMessage());
            }
            return false;
        }

        // 准备转换器一次性复用
        final Java2DFrameConverter converter = new Java2DFrameConverter();
        streamExecutor.scheduleWithFixedDelay(() -> {
            if (!streamRunning) return;
            try {
                Frame frame = grabberManager.getGrabber().grab();
                if (frame != null) {
                    BufferedImage bufferedImage = converter.convert(frame);
                    if (bufferedImage != null && streamCallback != null) {
                        // 转换为像素数组并回调
                        int w = bufferedImage.getWidth();
                        int h = bufferedImage.getHeight();
                        int[] pixels = new int[w * h];
                        bufferedImage.getRGB(0, 0, w, h, pixels, 0, w);
                        long captureTime = System.nanoTime();
                        if (logger.isTraceEnabled()) {
                            logger.debug("捕获帧时间(ns): {}", captureTime);
                        }
                        streamCallback.onFrame(pixels, w, h, captureTime);
                    }
                }
            } catch (Exception e) {
                logger.error(
                        "Live stream frame grab failed (deviceIndex={}, running={})",
                        deviceIndex,
                        streamRunning,
                        e
                );
                if (streamCallback != null) {
                    streamCallback.onError(e.getMessage());
                }
            }
        }, 0, Math.max(10, 1000 / AppConfig.getCameraFps()), TimeUnit.MILLISECONDS);

        return true;
    }

    /**
     * 停止实时视频流
     *
     * @param grabberManager 抓取器管理器
     */
    public void stopLiveStream(CameraGrabberManager grabberManager) {
        logger.info("停止实时流");

        streamRunning = false;

        if (streamExecutor != null) {
            streamExecutor.shutdown();
            try {
                if (!streamExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    streamExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                streamExecutor.shutdownNow();
            }
            streamExecutor = null;
        }

        streamCallback = null;

        // 关闭抓取器
        if (grabberManager != null) {
            grabberManager.closeGrabber();
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Shutting down stream executor");
        }
    }

    /**
     * 检查流是否正在运行
     *
     * @return true 如果流正在运行
     */
    public boolean isStreamRunning() {
        return streamRunning;
    }
}
