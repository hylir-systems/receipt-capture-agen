package com.hylir.receipt.service.camera;

import org.bytedeco.javacv.VideoInputFrameGrabber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 摄像头设备管理器
 * 负责设备枚举和选择
 */
public class CameraDeviceManager {
    private static final Logger logger = LoggerFactory.getLogger(CameraDeviceManager.class);
    private static List<String> availableDevices = null;
    private int selectedDeviceIndex = -1;

    /**
     * 获取可用的摄像头设备列表
     *
     * @return 设备名称列表
     */
    public List<String> getAvailableScanners() {
        List<String> devices = new ArrayList<>();

        try {
            // VideoInputFrameGrabber 才能拿到 Windows 真实设备名
            String[] deviceNames = VideoInputFrameGrabber.getDeviceDescriptions();

            if (deviceNames != null) {
                for (int i = 0; i < deviceNames.length; i++) {
                    String name = deviceNames[i];
                    if (name != null && !name.isBlank()) {
                        devices.add(name);
                        logger.info("发现摄像头设备: {}", name);
                    }
                }
            }

        } catch (Exception e) {
            logger.error("枚举摄像头设备失败", e);
        }

        if (devices.isEmpty()) {
            logger.warn("未检测到任何摄像头设备");
        }

        availableDevices = devices;
        return devices;
    }

    /**
     * 选择摄像头设备
     *
     * @param deviceIndex 设备索引
     */
    public void selectDevice(int deviceIndex) {
        logger.info("选择摄像头设备索引: {}", deviceIndex);
        this.selectedDeviceIndex = deviceIndex;
        logger.info("已选择摄像头索引: {}", deviceIndex);
    }

    /**
     * 获取当前选中的设备索引
     *
     * @return 设备索引，如果未选择则返回 -1
     */
    public int getSelectedDeviceIndex() {
        return selectedDeviceIndex;
    }

    /**
     * 获取可用设备列表缓存
     *
     * @return 设备名称列表
     */
    public static List<String> getAvailableDevices() {
        return availableDevices;
    }
}
