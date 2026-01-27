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
     * 如果设备索引与当前已选择的设备相同，则跳过选择，避免重复初始化
     *
     * @param deviceIndex 设备索引
     * @return true 如果实际执行了选择操作，false 如果跳过（已是当前设备）
     */
    public boolean selectDevice(int deviceIndex) {
        // 避免重复初始化：如果选择的设备已经是当前设备，则跳过
        if (deviceIndex == selectedDeviceIndex) {
            logger.debug("设备索引 {} 已是当前设备，跳过选择", deviceIndex);
            return false;
        }

        logger.info("选择摄像头设备索引: {} (之前: {})", deviceIndex, selectedDeviceIndex);
        this.selectedDeviceIndex = deviceIndex;
        logger.info("已选择摄像头索引: {}", deviceIndex);
        return true;
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
