package com.hylir.receipt.service.camera;

import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 摄像头抓取器管理器
 * 负责 OpenCVFrameGrabber 的生命周期管理
 */
public class CameraGrabberManager {
    private static final Logger logger = LoggerFactory.getLogger(CameraGrabberManager.class);
    private OpenCVFrameGrabber grabber = null;

    /**
     * 创建并启动摄像头抓取器
     *
     * @param deviceIndex 设备索引
     * @param width 图像宽度
     * @param height 图像高度
     * @return 启动后的抓取器
     * @throws Exception 如果启动失败
     */
    public OpenCVFrameGrabber createAndStartGrabber(int deviceIndex, int width, int height) throws Exception {
        logger.info("创建并启动摄像头抓取器，设备索引: {}, 分辨率: {}x{}", deviceIndex, width, height);
        
        // 关闭之前的抓取器
        closeGrabber();
        
        // 创建新的抓取器
        grabber = new OpenCVFrameGrabber(deviceIndex);
        grabber.setImageWidth(width);
        grabber.setImageHeight(height);
        grabber.start();
        
        logger.info("摄像头抓取器已启动");
        return grabber;
    }

    /**
     * 预热摄像头
     *
     * @param grabber 摄像头抓取器
     * @throws Exception 如果预热失败
     */
    public void warmUpCamera(OpenCVFrameGrabber grabber) throws Exception {
        logger.info("开始预热摄像头，共 {} 帧", CameraConstants.WARM_UP_FRAMES);
        
        for (int i = 0; i < CameraConstants.WARM_UP_FRAMES; i++) {
            Frame frame = grabber.grab();
            if (frame == null) {
                logger.warn("预热帧 {} 获取失败", i + 1);
            }
            Thread.sleep(30);
        }
        
        logger.info("摄像头预热完成");
    }

    /**
     * 停止并关闭抓取器
     */
    public void closeGrabber() {
        if (grabber != null) {
            try {
                logger.info("停止并关闭摄像头抓取器");
                grabber.stop();
                grabber.close();
                logger.info("摄像头抓取器已关闭");
            } catch (Exception e) {
                logger.warn("关闭摄像头抓取器失败", e);
            } finally {
                grabber = null;
            }
        }
    }

    /**
     * 获取当前抓取器
     *
     * @return 当前抓取器
     */
    public OpenCVFrameGrabber getGrabber() {
        return grabber;
    }

    /**
     * 检查摄像头是否可用（带重试机制）
     *
     * @param deviceIndex 设备索引
     * @return true 如果摄像头可用
     */
    public boolean isCameraAvailable(int deviceIndex) {
        int maxRetries = 5;
        long retryDelayMs = 2000; // 重试间隔2秒

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            if (tryCameraAvailable(deviceIndex, attempt)) {
                return true;
            }

            if (attempt < maxRetries) {
                logger.info("摄像头检测失败，{}秒后进行第{}次重试...", retryDelayMs / 1000, attempt + 1);
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        logger.warn("摄像头在{}次尝试后仍不可用，将使用模拟预览", maxRetries);
        return false;
    }

    /**
     * 尝试检测摄像头可用性
     *
     * @param deviceIndex 设备索引
     * @param attempt 当前尝试次数
     * @return true 如果摄像头可用
     */
    private boolean tryCameraAvailable(int deviceIndex, int attempt) {
        OpenCVFrameGrabber testGrabber = null;
        try {
            logger.info("尝试检测摄像头可用性，设备索引: {}, 第{}次尝试", deviceIndex, attempt);
            testGrabber = new OpenCVFrameGrabber(deviceIndex);
            testGrabber.setImageWidth(320);
            testGrabber.setImageHeight(240);
            testGrabber.start();
            testGrabber.stop();
            testGrabber.close();
            logger.info("摄像头检测成功");
            return true;
        } catch (Exception e) {
            logger.warn("第{}次摄像头检测失败: {}", attempt, e.getMessage());
            return false;
        } finally {
            if (testGrabber != null) {
                try {
                    testGrabber.close();
                } catch (Exception ignore) {
                }
            }
        }
    }
}
