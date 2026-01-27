package com.hylir.receipt.util;

import javafx.application.Platform;
import javafx.scene.media.AudioClip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

/**
 * 简单音效播放器（用于提示音）
 *
 * 注意：
 * - 使用 AudioClip 适合短音效（无需手动管理 MediaPlayer 生命周期）
 * - 播放需要 JavaFX runtime 已初始化；本项目 UI 启动后调用安全
 */
public final class SoundPlayer {
    private static final Logger logger = LoggerFactory.getLogger(SoundPlayer.class);
    private static volatile AudioClip successClip;

    private SoundPlayer() {}

    private static synchronized void initIfNeeded() {
        if (successClip != null) return;

        try {
            // 优先使用 ClassLoader（更适合 fat-jar / jpackage 等场景）
            ClassLoader cl = SoundPlayer.class.getClassLoader();
            URL url = cl != null ? cl.getResource("assets/success.mp3") : null;

            // 兜底：使用 Class.getResource
            if (url == null) {
                url = SoundPlayer.class.getResource("/assets/success.mp3");
            }

            if (url == null) {
                logger.warn("未找到 success.mp3（classpath 资源缺失）：assets/success.mp3");
                return; // 保持可重试
            }

            logger.info("开始加载成功提示音: {}", url.toExternalForm());
            successClip = new AudioClip(url.toExternalForm());
            successClip.setVolume(1.0);
            logger.info("成功提示音已加载: {}", url.toExternalForm());
        } catch (Throwable e) {
            // 保持可重试
            logger.warn("初始化音效失败（将允许重试）: {}", e.toString());
        }
    }

    /**
     * 播放上传成功提示音（异步切到 JavaFX 线程）
     */
    public static void playSuccess() {
        Runnable r = () -> {
            try {
                // AudioClip/Media 初始化强制放到 FX 线程，避免在后台线程卡死
                initIfNeeded();
                if (successClip == null) return;
                successClip.play();
            } catch (Throwable t) {
                logger.warn("播放成功提示音失败: {}", t.toString());
            }
        };

        if (Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);
    }
}


