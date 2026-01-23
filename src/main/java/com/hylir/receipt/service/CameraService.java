package com.hylir.receipt.service;
import com.hylir.receipt.service.camera.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

/**
 * 摄像头服务
 * 门面类，对外唯一入口，负责协调所有摄像头相关服务
 */
public class CameraService {
    private static final Logger logger = LoggerFactory.getLogger(CameraService.class);
    private static CameraService instance;

    private final CameraDeviceManager deviceManager = new CameraDeviceManager();
    private final CameraStreamService streamService = new CameraStreamService();
    private final CameraGrabberManager grabberManager = new CameraGrabberManager();
    private final CameraCaptureService captureService = new CameraCaptureService(grabberManager, new CameraMockRenderer());
    private boolean useRealCamera = false;

    /**
     * 私有构造函数
     */
    public CameraService() {
        // 初始化服务
    }

    /**
     * 获取单例实例
     *
     * @return CameraService 实例
     */
    public static synchronized CameraService getInstance() {
        if (instance == null) {
            instance = new CameraService();
        }
        return instance;
    }

    /**
     * 初始化摄像头服务
     */
    public static void initialize() {
        logger.info("初始化摄像头服务");
        CameraService service = getInstance();
        service.doInitialize();
    }

    /**
     * 执行初始化
     */
    private void doInitialize() {
        try {
            // 检查摄像头可用性
            useRealCamera = grabberManager.isCameraAvailable(0);
            if (useRealCamera) {
                logger.info("检测到摄像头可用，将使用真实摄像头预览");
            } else {
                logger.info("未检测到摄像头，将使用模拟预览");
            }
            captureService.setUseRealCamera(useRealCamera);
            logger.info("摄像头服务初始化完成");
        } catch (Exception e) {
            logger.error("摄像头服务初始化失败", e);
            throw new RuntimeException("摄像头服务初始化失败", e);
        }
    }

    /**
     * 获取可用的摄像头设备列表
     *
     * @return 设备名称列表
     */
    public List<String> getAvailableScanners() {
        return deviceManager.getAvailableScanners();
    }

    /**
     * 选择摄像头设备
     *
     * @param index 设备索引
     */
    public void selectDevice(int index) {
        deviceManager.selectDevice(index);
    }

    /**
     * 获取当前选中的设备索引
     *
     * @return 设备索引，如果未选择则返回 -1
     */
    public int getSelectedDeviceIndex() {
        return deviceManager.getSelectedDeviceIndex();
    }

    /**
     * 检查是否正在使用真实摄像头
     *
     * @return true如果使用真实摄像头，false如果使用模拟
     */
    public boolean isUsingRealCamera() {
        return useRealCamera;
    }

    /**
     * 开始实时视频流
     */
    public boolean startLiveStream(StreamCallback callback, int deviceIndex) {
        return streamService.startLiveStream(new CameraStreamService.StreamCallback() {
            @Override
            public void onFrame(int[] pixels, int width, int height, long captureTimeNanos) {
                callback.onFrame(pixels, width, height, captureTimeNanos);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        }, grabberManager, deviceIndex);
    }

    /**
     * 停止实时视频流
     */
    public void stopLiveStream() {
        streamService.stopLiveStream(grabberManager);
    }

    /**
     * 拍照获取图片
     *
     * @return 保存的图片文件路径
     * @throws IOException 如果拍照或保存失败
     */
    public String captureImage() throws IOException {
        return captureService.captureImage(deviceManager.getSelectedDeviceIndex());
    }

    /**
     * 拍照并返回 BufferedImage
     *
     * @return 图片缓冲对象
     * @throws IOException 如果拍照或读取失败
     */
    public BufferedImage captureImageAsBufferedImage() throws IOException {
        return captureService.captureImageAsBufferedImage(deviceManager.getSelectedDeviceIndex());
    }


    /**
     * 静态方法：关闭摄像头服务，释放所有资源
     * 在应用关闭时调用
     */
    public static void shutdown() {
        if (instance != null) {
            instance.shutdown();
            instance = null;
        }
    }

    // 实时流回调接口
    public interface StreamCallback {
        void onFrame(int[] pixels, int width, int height, long captureTimeNanos);
        void onError(String error);
    }
}
