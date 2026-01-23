package com.hylir.receipt.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.TimeUnit;

/**
 * 临时文件清理工具类
 * 用于清理超过指定时间的临时文件
 *
 * @author shanghai pubing
 * @date 2025/01/20
 */
public class TempFileCleaner {

    private static final Logger logger = LoggerFactory.getLogger(TempFileCleaner.class);

    /**
     * 清理过期文件（只保留最近指定分钟的文件）
     *
     * @param tempDir 临时目录
     * @param maxAgeMinutes 最大保留时间（分钟），默认2分钟
     */
    public static void cleanupExpiredFiles(File tempDir, int maxAgeMinutes) {
        if (tempDir == null || !tempDir.exists() || !tempDir.isDirectory()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        long maxAgeMillis = TimeUnit.MINUTES.toMillis(maxAgeMinutes);
        int deletedCount = 0;
        long totalSize = 0;

        try {
            File[] files = tempDir.listFiles();
            if (files == null) {
                return;
            }

            for (File file : files) {
                if (file.isFile()) {
                    try {
                        // 获取文件最后修改时间
                        long fileAge = currentTime - file.lastModified();

                        // 如果文件超过指定时间，删除它
                        if (fileAge > maxAgeMillis) {
                            long fileSize = file.length();
                            if (file.delete()) {
                                deletedCount++;
                                totalSize += fileSize;
                                logger.debug("删除过期临时文件: {} (年龄: {} 分钟)", 
                                    file.getName(), TimeUnit.MILLISECONDS.toMinutes(fileAge));
                            } else {
                                logger.warn("无法删除临时文件: {}", file.getAbsolutePath());
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("处理临时文件时出错: {}", file.getName(), e);
                    }
                }
            }

            if (deletedCount > 0) {
                logger.info("清理临时文件完成: 删除 {} 个文件，释放空间 {} KB", 
                    deletedCount, totalSize / 1024);
            }

        } catch (Exception e) {
            logger.error("清理临时文件失败", e);
        }
    }

    /**
     * 清理过期文件（默认保留最近2分钟）
     *
     * @param tempDir 临时目录
     */
    public static void cleanupExpiredFiles(File tempDir) {
        cleanupExpiredFiles(tempDir, 2);
    }

    /**
     * 清理过期文件（使用 Path）
     *
     * @param tempDirPath 临时目录路径
     * @param maxAgeMinutes 最大保留时间（分钟）
     */
    public static void cleanupExpiredFiles(Path tempDirPath, int maxAgeMinutes) {
        if (tempDirPath != null && Files.exists(tempDirPath)) {
            cleanupExpiredFiles(tempDirPath.toFile(), maxAgeMinutes);
        }
    }

    /**
     * 清理过期文件（使用 Path，默认保留最近2分钟）
     *
     * @param tempDirPath 临时目录路径
     */
    public static void cleanupExpiredFiles(Path tempDirPath) {
        cleanupExpiredFiles(tempDirPath, 2);
    }

    /**
     * 清理过期文件（使用字符串路径）
     *
     * @param tempDirPath 临时目录路径字符串
     * @param maxAgeMinutes 最大保留时间（分钟）
     */
    public static void cleanupExpiredFiles(String tempDirPath, int maxAgeMinutes) {
        if (tempDirPath != null && !tempDirPath.isEmpty()) {
            File tempDir = new File(tempDirPath);
            cleanupExpiredFiles(tempDir, maxAgeMinutes);
        }
    }

    /**
     * 清理过期文件（使用字符串路径，默认保留最近2分钟）
     *
     * @param tempDirPath 临时目录路径字符串
     */
    public static void cleanupExpiredFiles(String tempDirPath) {
        cleanupExpiredFiles(tempDirPath, 2);
    }
}

