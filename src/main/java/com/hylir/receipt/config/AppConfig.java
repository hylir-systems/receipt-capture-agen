package com.hylir.receipt.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 应用配置类
 * 管理后端 API URL 和其他可配置参数
 *
 * @author shanghai pubing
 * @date 2025/01/19
 * @version 1.0.0
 */
public class AppConfig {

    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);

    private static final String CONFIG_FILE = "/config/application.properties";
    private static final Properties properties = new Properties();

    // 默认配置
    private static final String DEFAULT_BACKEND_URL = "http://117.143.214.90:3680/api";
    private static final String DEFAULT_UPLOAD_ENDPOINT = "/receipt/upload";
    private static final int DEFAULT_CONNECTION_TIMEOUT = 30000; // 30秒
    private static final int DEFAULT_READ_TIMEOUT = 60000; // 60秒

    static {
        loadConfig();
    }

    /**
     * 加载配置文件
     */
    private static void loadConfig() {
        try (InputStream inputStream = AppConfig.class.getResourceAsStream(CONFIG_FILE)) {
            if (inputStream != null) {
                properties.load(inputStream);
                logger.info("配置文件加载成功: {}", CONFIG_FILE);
            } else {
                logger.warn("未找到配置文件 {}, 使用默认配置", CONFIG_FILE);
            }
        } catch (IOException e) {
            logger.error("加载配置文件失败, 使用默认配置", e);
        }
    }

    /**
     * 获取后端基础 URL
     */
    public static String getBackendUrl() {
        return properties.getProperty("backend.url", DEFAULT_BACKEND_URL);
    }

    /**
     * 获取上传端点
     */
    public static String getUploadEndpoint() {
        return properties.getProperty("backend.upload.endpoint", DEFAULT_UPLOAD_ENDPOINT);
    }

    /**
     * 获取完整的上传 URL
     */
    public static String getUploadUrl() {
        String backendUrl = getBackendUrl();
        String endpoint = getUploadEndpoint();

        // 确保 URL 格式正确
        if (backendUrl.endsWith("/") && endpoint.startsWith("/")) {
            return backendUrl + endpoint.substring(1);
        } else if (!backendUrl.endsWith("/") && !endpoint.startsWith("/")) {
            return backendUrl + "/" + endpoint;
        } else {
            return backendUrl + endpoint;
        }
    }

    /**
     * 获取连接超时时间（毫秒）
     */
    public static int getConnectionTimeout() {
        String timeout = properties.getProperty("http.connection.timeout");
        try {
            return timeout != null ? Integer.parseInt(timeout) : DEFAULT_CONNECTION_TIMEOUT;
        } catch (NumberFormatException e) {
            logger.warn("连接超时配置无效，使用默认值: {}", DEFAULT_CONNECTION_TIMEOUT);
            return DEFAULT_CONNECTION_TIMEOUT;
        }
    }

    /**
     * 获取读取超时时间（毫秒）
     */
    public static int getReadTimeout() {
        String timeout = properties.getProperty("http.read.timeout");
        try {
            return timeout != null ? Integer.parseInt(timeout) : DEFAULT_READ_TIMEOUT;
        } catch (NumberFormatException e) {
            logger.warn("读取超时配置无效，使用默认值: {}", DEFAULT_READ_TIMEOUT);
            return DEFAULT_READ_TIMEOUT;
        }
    }

    /**
     * 获取摄像头设备名称（可选）
     */
    public static String getScannerDeviceName() {
        return properties.getProperty("scanner.device.name");
    }

    /**
     * 获取摄像头分辨率 DPI
     */
    public static int getScannerResolution() {
        String resolution = properties.getProperty("scanner.resolution.dpi");
        try {
            return resolution != null ? Integer.parseInt(resolution) : 300;
        } catch (NumberFormatException e) {
            return 300; // 默认 300 DPI
        }
    }

    /**
     * 获取摄像头采集宽度（像素），默认 1280
     */
    public static int getCameraWidth() {
        String w = properties.getProperty("camera.width");
        try {
            return w != null ? Integer.parseInt(w) : 1600;
        } catch (NumberFormatException e) {
            return 1600;
        }
    }

    /**
     * 获取摄像头采集高度（像素），默认 720
     */
    public static int getCameraHeight() {
        String h = properties.getProperty("camera.height");
        try {
            return h != null ? Integer.parseInt(h) : 1200;
        } catch (NumberFormatException e) {
            return 1200;
        }
    }

    /**
     * 是否启用自动 A4 矫正（默认 false）
     */
    public static boolean isAutoA4CorrectionEnabled() {
        String v = properties.getProperty("enable.autoA4Correction");
        return v != null ? Boolean.parseBoolean(v) : false;
    }

    /**
     * 获取摄像头帧率（fps），默认 30
     */
    public static int getCameraFps() {
        String f = properties.getProperty("camera.fps");
        try {
            return f != null ? Integer.parseInt(f) : 30;
        } catch (NumberFormatException e) {
            return 30;
        }
    }

    /**
     * 重新加载配置
     */
    public static void reloadConfig() {
        properties.clear();
        loadConfig();
        logger.info("配置已重新加载");
    }
}
