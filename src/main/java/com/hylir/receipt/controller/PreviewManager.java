package com.hylir.receipt.controller;

import com.hylir.receipt.service.CameraService;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.IntBuffer;

/**
 * 实时预览管理器
 * 负责管理摄像头实时预览流的启动、停止和帧处理
 *
 * @author shanghai pubing
 * @date 2025/01/26
 */
public class PreviewManager {

    private static final Logger logger = LoggerFactory.getLogger(PreviewManager.class);

    private final CameraService cameraService;
    private final ImageView imageView;
    private final Pane imagePane;
    private final Button previewButton;
    private final Button resetButton;
    private final Label statusLabel;

    // 实时流使用的可写图像缓存（缓存复用，减少 GC）
    private WritableImage streamImage = null;
    private PixelBuffer<IntBuffer> pixelBuffer = null;
    private IntBuffer intBuffer = null;
    
    // 当前缓存的尺寸（用于判断是否需要重建 PixelBuffer）
    private int cachedWidth = -1;
    private int cachedHeight = -1;
    
    // 直接使用数组包装的 IntBuffer（零拷贝，减少内存分配）
    private int[] pixelArray = null;

    // 预览状态标志
    private volatile boolean isPreviewActive = false;
    
    // UI 更新标志（避免重复更新）
    private volatile boolean uiInitialized = false;

    // 回调接口
    private FrameCallback frameCallback;

    /**
     * 帧回调接口，用于通知外部组件处理帧数据
     */
    public interface FrameCallback {
        /**
         * 帧数据回调
         *
         * @param pixels 像素数组
         * @param width  宽度
         * @param height 高度
         */
        void onFrame(int[] pixels, int width, int height);
    }

    public PreviewManager(CameraService cameraService, ImageView imageView, Pane imagePane,
                          Button previewButton, Button resetButton, Label statusLabel) {
        this.cameraService = cameraService;
        this.imageView = imageView;
        this.imagePane = imagePane;
        this.previewButton = previewButton;
        this.resetButton = resetButton;
        this.statusLabel = statusLabel;
    }

    /**
     * 设置帧回调
     */
    public void setFrameCallback(FrameCallback callback) {
        this.frameCallback = callback;
    }

    /**
     * 开始实时预览
     * 优化：分离 UI 更新、状态管理和摄像头调用
     *
     * @param deviceIndex 设备索引
     * @param statusCallback 状态回调（用于输出日志）
     */
    public void startPreview(int deviceIndex, StatusCallback statusCallback) {
        // 1. UI 状态更新
        updateUIForStart(statusCallback);

        // 2. 状态管理
        isPreviewActive = true;
        uiInitialized = false;
        // 注意：不清空 PixelBuffer，保留缓存以便复用

        // 3. 摄像头调用
        boolean success = startCameraStream(deviceIndex, statusCallback);

        if (!success) {
            // 启动失败，重置状态
            isPreviewActive = false;
            if (statusCallback != null) {
                statusCallback.onStatus("✗ 无法启动实时预览，请检查设备选择");
            }
            resetPreviewButton();
        }
    }

    /**
     * 更新 UI 状态（启动预览时）
     */
    private void updateUIForStart(StatusCallback statusCallback) {
        Platform.runLater(() -> {
            previewButton.setDisable(true);
            // 清空当前图片，为新预览做准备
            imageView.setImage(null);
        });
        
        if (statusCallback != null) {
            statusCallback.onStatus("正在启动实时预览...");
        }
    }

    /**
     * 启动摄像头流
     */
    private boolean startCameraStream(int deviceIndex, StatusCallback statusCallback) {
        return cameraService.startLiveStream(new CameraService.StreamCallback() {
            @Override
            public void onFrame(int[] pixels, int w, int h, long captureTimeNanos) {
                if (pixels == null || w <= 0 || h <= 0) return;
                if (!isPreviewActive) return; // 如果预览已停止，忽略后续帧

                // 通知外部组件处理帧（如自动采集服务）- 在后台线程执行
                if (frameCallback != null) {
                    frameCallback.onFrame(pixels, w, h);
                }

                // UI 更新在 JavaFX 线程执行
                Platform.runLater(() -> {
                    try {
                        updatePreviewFrame(pixels, w, h);
                    } catch (Exception e) {
                        logger.error("加载实时预览帧失败", e);
                        if (statusCallback != null) {
                            statusCallback.onStatus("✗ 实时预览帧加载失败: " + e.getMessage());
                        }
                    }
                });
            }

            @Override
            public void onError(String error) {
                Platform.runLater(() -> {
                    logger.error("实时流错误: {}", error);
                    if (statusCallback != null) {
                        statusCallback.onStatus("✗ 实时流错误: " + error);
                    }
                    stopPreview(statusCallback);
                    resetPreviewButton();
                });
            }
        }, deviceIndex);
    }

    /**
     * 更新预览帧
     * 优化：缓存 PixelBuffer，只在尺寸变化时重建；使用零拷贝更新
     */
    private void updatePreviewFrame(int[] pixels, int w, int h) {
        // 1. 检查是否需要重建 PixelBuffer（只在尺寸变化时重建）
        boolean needReinit = pixelBuffer == null || cachedWidth != w || cachedHeight != h;
        if (needReinit) {
            initializePixelBuffer(w, h);
        } else {
            // 即使尺寸匹配，也要确保 imageView 已设置图像（防止停止后重新启动时图像丢失）
            if (imageView.getImage() == null && streamImage != null) {
                imageView.setImage(streamImage);
                logger.debug("重新设置 imageView 图像（尺寸匹配，使用缓存）");
            }
        }

        // 2. 零拷贝更新：直接使用数组，避免不必要的复制
        // 如果数组大小匹配，直接使用；否则需要复制
        if (pixelArray == null || pixelArray.length != pixels.length) {
            pixelArray = new int[pixels.length];
        }
        System.arraycopy(pixels, 0, pixelArray, 0, pixels.length);

        // 3. 更新 IntBuffer（使用缓存的数组，零拷贝）
        intBuffer.rewind();
        intBuffer.put(pixelArray);
        intBuffer.rewind();

        // 4. 通知 PixelBuffer 已更新（零拷贝，不创建新对象）
        pixelBuffer.updateBuffer(buf -> null);

        // 5. UI 更新（分离逻辑，减少重复操作）
        updateUIForFrame();
    }

    /**
     * 初始化 PixelBuffer（只在尺寸变化或首次启动时调用）
     */
    private void initializePixelBuffer(int w, int h) {
        logger.debug("初始化 PixelBuffer: {}x{} (之前: {}x{})", w, h, cachedWidth, cachedHeight);
        
        // 分配或重用 IntBuffer
        int bufferSize = w * h;
        if (intBuffer == null || intBuffer.capacity() < bufferSize) {
            // 需要重新分配（容量不足）
            intBuffer = IntBuffer.allocate(bufferSize);
            pixelArray = new int[bufferSize];
        } else {
            // 重用现有 buffer，只需调整 limit
            intBuffer.clear();
            intBuffer.limit(bufferSize);
        }

        // 创建或重建 PixelBuffer
        pixelBuffer = new PixelBuffer<>(w, h, intBuffer, PixelFormat.getIntArgbPreInstance());
        streamImage = new WritableImage(pixelBuffer);
        
        // 更新缓存尺寸
        cachedWidth = w;
        cachedHeight = h;

        // 设置到 ImageView
        imageView.setImage(streamImage);
        
        logger.debug("PixelBuffer 初始化完成: {}x{}", w, h);
    }

    /**
     * 更新 UI 显示（分离逻辑，减少重复操作）
     */
    private void updateUIForFrame() {
        // 只在首次或需要时更新 UI 属性（避免每帧都更新）
        if (!uiInitialized) {
            // 计算缩放以完整显示视频
            double paneW = imagePane.getWidth() - 20;
            double paneH = imagePane.getHeight() - 20;
            if (paneW <= 0) paneW = imageView.getFitWidth();
            if (paneH <= 0) paneH = imageView.getFitHeight();

            // 设置图像显示属性（只需设置一次）
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
            imageView.setCache(true);
            imageView.setFitWidth(paneW);
            imageView.setFitHeight(paneH);

            // 清除可能存在的 viewport/clip
            try {
                imageView.setViewport(null);
            } catch (Exception ignore) {
            }
            try {
                imageView.setClip(null);
            } catch (Exception ignore) {
            }

            // 更新按钮状态（只需更新一次）
            if (!"停止预览".equals(previewButton.getText())) {
                previewButton.setText("停止预览");
                previewButton.setDisable(false);
                resetButton.setDisable(false);

                if (statusLabel != null) {
                    statusLabel.setText("预览中");
                }
            }

            uiInitialized = true;
        }

        // 每帧都需要更新位置（容器大小可能变化）
        double paneW = imagePane.getWidth() - 20;
        double paneH = imagePane.getHeight() - 20;
        if (paneW <= 0) paneW = imageView.getFitWidth();
        if (paneH <= 0) paneH = imageView.getFitHeight();

        double actualW = Math.min(imageView.getBoundsInLocal().getWidth(), paneW);
        double actualH = Math.min(imageView.getBoundsInLocal().getHeight(), paneH);
        imageView.setLayoutX((imagePane.getWidth() - actualW) / 2);
        imageView.setLayoutY((imagePane.getHeight() - actualH) / 2);
        imageView.setVisible(true);
    }

    /**
     * 停止实时预览
     * 优化：分离状态管理和 UI 更新，保留缓存以便下次复用
     *
     * @param statusCallback 状态回调
     */
    public void stopPreview(StatusCallback statusCallback) {
        // 1. 状态管理：先停止接收新帧
        isPreviewActive = false;
        uiInitialized = false;

        // 2. 摄像头调用：停止流
        cameraService.stopLiveStream();

        // 3. UI 更新：清空显示，但保留缓存（下次启动可复用）
        Platform.runLater(() -> {
            imageView.setImage(null);
            // 注意：不清空 pixelBuffer 和 intBuffer，保留缓存以便下次复用
            // 只有在尺寸变化时才会重新创建
        });

        // 4. 状态回调
        if (statusCallback != null) {
            statusCallback.onStatus("✓ 实时预览已停止");
        }

        // 5. 重置按钮状态
        resetPreviewButton();
    }

    /**
     * 完全清理缓存（在需要时调用，如应用关闭）
     */
    public void clearCache() {
        Platform.runLater(() -> {
            imageView.setImage(null);
            pixelBuffer = null;
            intBuffer = null;
            streamImage = null;
            pixelArray = null;
            cachedWidth = -1;
            cachedHeight = -1;
        });
    }

    /**
     * 重置预览按钮状态
     */
    private void resetPreviewButton() {
        Platform.runLater(() -> {
            previewButton.setText("预览");
            previewButton.setDisable(false);
            resetButton.setDisable(true);
            if (statusLabel != null) {
                statusLabel.setText("就绪");
            }
        });
    }

    /**
     * 检查预览是否激活
     */
    public boolean isPreviewActive() {
        return isPreviewActive;
    }

    /**
     * 状态回调接口
     */
    public interface StatusCallback {
        void onStatus(String message);
    }
}

