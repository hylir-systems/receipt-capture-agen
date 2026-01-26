package com.hylir.receipt.service.camera;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hylir.receipt.config.AppConfig;
import com.hylir.receipt.service.A4PaperDetectorHighCamera;
import com.hylir.receipt.util.TempFileCleaner;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private volatile boolean firstFrameSaved = false;

    // JavaCV 转换器（用于 A4 检测）
    private static final Java2DFrameConverter JAVA2D_CONVERTER = new Java2DFrameConverter();
    private static final OpenCVFrameConverter.ToMat MAT_CONVERTER = new OpenCVFrameConverter.ToMat();

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

        // 重置首帧保存标志
        firstFrameSaved = false;

        // 准备转换器一次性复用
        final Java2DFrameConverter converter = new Java2DFrameConverter();
        streamExecutor.scheduleWithFixedDelay(() -> {
            if (!streamRunning) return;
            try {
                Frame frame = grabberManager.getGrabber().grab();
                if (frame != null) {
                    BufferedImage bufferedImage = converter.convert(frame);
                    if (bufferedImage != null && streamCallback != null) {
                        // 记录实际捕获的尺寸
                        int actualW = bufferedImage.getWidth();
                        int actualH = bufferedImage.getHeight();
                        if (logger.isDebugEnabled()) {
                            //logger.info("捕获尺寸: {}x{}", actualW, actualH);
                        }
                        // 保存首帧到临时目录
                        if (!firstFrameSaved) {
                            saveFirstFrame(bufferedImage);
                        }

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
     * 保存首帧图像
     *
     * @param bufferedImage 首帧图像
     */
    private void saveFirstFrame(BufferedImage bufferedImage) {
        try {
            // 使用配置的 A4 保存文件夹
            String saveFolder = com.hylir.receipt.config.AppConfig.getA4SaveFolder();
            Path tempPath = Paths.get(saveFolder);
            if (!Files.exists(tempPath)) {
                Files.createDirectories(tempPath);
            }

            // 清理过期文件（只保留最近2分钟）
            TempFileCleaner.cleanupExpiredFiles(tempPath);

            BufferedImage toSave = bufferedImage;
            boolean corrected = false;

            // 尝试 A4 矫正
            if (AppConfig.isAutoA4CorrectionEnabled()) {
                try {
                    // 转换 BufferedImage 为 Mat
                    Frame frame = JAVA2D_CONVERTER.convert(bufferedImage);
                    Mat srcMat = MAT_CONVERTER.convert(frame);

                    // 使用 A4PaperDetectorHighCamera 进行 A4 检测与透视变换
                    Mat correctedMat = A4PaperDetectorHighCamera.detectAndWarpA4(srcMat);

                    if (correctedMat != null && !correctedMat.empty()) {
                        // 转换回 BufferedImage
                        Frame outFrame = MAT_CONVERTER.convert(correctedMat);
                        BufferedImage correctedImg = JAVA2D_CONVERTER.getBufferedImage(outFrame, 1);
                        if (correctedImg != null) {
                            toSave = correctedImg;
                            corrected = true;
                        }
                    }
                } catch (Exception ex) {
                    logger.warn("A4 矫正失败: {}", ex.getMessage());
                }
            }

            // 保存首帧（A4 矫正后的图片保存到配置的文件夹）
            String firstPath = saveFolder + File.separator + (corrected ? "a4_corrected_" : "first_frame_") + System.currentTimeMillis() + ".png";
            ImageIO.write(toSave, "PNG", new File(firstPath));
            logger.info("首帧已保存: {} (corrected={})", firstPath, corrected);
        } catch (IOException ioe) {
            logger.warn("保存首帧失败: {}", ioe.getMessage());
        } finally {
            firstFrameSaved = true;
        }
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
