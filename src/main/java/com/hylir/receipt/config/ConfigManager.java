package com.hylir.receipt.config;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * 配置管理类
 * 负责读取和保存用户配置到文件
 */
public class ConfigManager {

    private static final String CONFIG_FILE_NAME = "receipt-capture.properties";
    
    // 配置键名
    public static final String KEY_BACKEND_URL = "backend.url";
    public static final String KEY_UPLOAD_ENDPOINT = "backend.upload.endpoint";
    public static final String KEY_A4_SAVE_FOLDER = "a4.save.folder";
    
    // 默认值
    public static final String DEFAULT_BACKEND_URL = "http://117.143.214.90:3680/api";
    public static final String DEFAULT_UPLOAD_ENDPOINT = "/receipt/upload";
    public static final String DEFAULT_A4_SAVE_FOLDER = "temp-images";

    private Properties properties;
    private Path configFilePath;

    /**
     * 单例模式
     */
    private static ConfigManager instance;

    private ConfigManager() {
        properties = new Properties();
        // 配置文件放在用户主目录或程序运行目录
        String userHome = System.getProperty("user.home");
        Path userConfigDir = Paths.get(userHome, ".receipt-capture");
        
        // 也支持程序运行目录下的配置文件
        Path appConfigPath = Paths.get(CONFIG_FILE_NAME);
        if (Files.exists(appConfigPath)) {
            configFilePath = appConfigPath;
        } else {
            // 使用用户目录
            try {
                Files.createDirectories(userConfigDir);
                configFilePath = userConfigDir.resolve(CONFIG_FILE_NAME);
            } catch (IOException e) {
                configFilePath = appConfigPath;
            }
        }
        
        loadConfig();
    }

    /**
     * 获取单例实例
     */
    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    /**
     * 加载配置文件
     */
    private void loadConfig() {
        if (Files.exists(configFilePath)) {
            try (InputStream input = new FileInputStream(configFilePath.toFile())) {
                properties.load(input);
                System.out.println("配置已从 " + configFilePath + " 加载");
            } catch (IOException e) {
                System.err.println("加载配置文件失败: " + e.getMessage());
            }
        } else {
            System.out.println("配置文件不存在，将使用默认值: " + configFilePath);
        }
    }

    /**
     * 保存配置到文件
     */
    public synchronized void saveConfig() {
        try (OutputStream output = new FileOutputStream(configFilePath.toFile())) {
            properties.store(output, "Receipt Capture Agent Configuration");
            System.out.println("配置已保存到 " + configFilePath);
        } catch (IOException e) {
            System.err.println("保存配置文件失败: " + e.getMessage());
        }
    }

    /**
     * 获取后端服务地址
     */
    public String getBackendUrl() {
        return properties.getProperty(KEY_BACKEND_URL, DEFAULT_BACKEND_URL);
    }

    /**
     * 设置后端服务地址
     */
    public void setBackendUrl(String url) {
        properties.setProperty(KEY_BACKEND_URL, url);
    }

    /**
     * 获取上传接口路径
     */
    public String getUploadEndpoint() {
        return properties.getProperty(KEY_UPLOAD_ENDPOINT, DEFAULT_UPLOAD_ENDPOINT);
    }

    /**
     * 设置上传接口路径
     */
    public void setUploadEndpoint(String endpoint) {
        properties.setProperty(KEY_UPLOAD_ENDPOINT, endpoint);
    }

    /**
     * 获取A4保存文件夹
     */
    public String getA4SaveFolder() {
        return properties.getProperty(KEY_A4_SAVE_FOLDER, DEFAULT_A4_SAVE_FOLDER);
    }

    /**
     * 设置A4保存文件夹
     */
    public void setA4SaveFolder(String folder) {
        properties.setProperty(KEY_A4_SAVE_FOLDER, folder);
    }

    /**
     * 恢复默认配置
     */
    public void resetToDefaults() {
        properties.setProperty(KEY_BACKEND_URL, DEFAULT_BACKEND_URL);
        properties.setProperty(KEY_UPLOAD_ENDPOINT, DEFAULT_UPLOAD_ENDPOINT);
        properties.setProperty(KEY_A4_SAVE_FOLDER, DEFAULT_A4_SAVE_FOLDER);
        saveConfig();
    }

    /**
     * 获取配置文件路径
     */
    public Path getConfigFilePath() {
        return configFilePath;
    }

    /**
     * 获取完整的上传URL
     */
    public String getFullUploadUrl() {
        String baseUrl = getBackendUrl();
        String endpoint = getUploadEndpoint();
        
        // 移除URL末尾的斜杠
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        
        // 确保endpoint以斜杠开头
        if (!endpoint.startsWith("/")) {
            endpoint = "/" + endpoint;
        }
        
        return baseUrl + endpoint;
    }
}

