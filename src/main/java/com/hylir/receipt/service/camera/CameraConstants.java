package com.hylir.receipt.service.camera;

/**
 * 摄像头相关常量
 */
public class CameraConstants {
    public static final int MAX_CAMERA_INDEX = 3;   // ★ 只扫 0~2
    public static final int WARM_UP_FRAMES = 3;     // ★ 自动对焦 / 曝光
    public static final String TEMP_DIR = System.getProperty("java.io.tmpdir") + "\\receipt-capture";
}
