package com.hylir.receipt.controller;

import com.hylir.receipt.config.AppConfig;
import com.hylir.receipt.service.BarcodeRecognitionService;
import com.hylir.receipt.service.UploadService;
import com.hylir.receipt.service.autocapture.AutoCaptureService;
import com.hylir.receipt.service.autocapture.CapturePipeline;
import com.hylir.receipt.service.autocapture.FrameChangeDetector;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * 自动采集控制器
 * 负责管理自动采集服务的初始化、启用/禁用和结果回调处理
 *
 * @author shanghai pubing
 * @date 2025/01/26
 */
public class AutoCaptureController {

    private static final Logger logger = LoggerFactory.getLogger(AutoCaptureController.class);

    private final BarcodeRecognitionService barcodeService;
    private UploadService uploadService;
    private AutoCaptureService autoCaptureService;
    private FrameChangeDetector changeDetector;
    private CapturePipeline capturePipeline;

    // 日志去重和频率限制
    private String lastLogMessage = "";
    private long lastLogTime = 0L;
    private int duplicateLogCount = 0;
    private static final long LOG_THROTTLE_MS = 2000; // 相同日志至少间隔2秒
    private static final int MAX_DUPLICATE_COUNT = 5; // 最多连续显示5条相同日志

    // 回调接口
    private CaptureResultCallback resultCallback;
    private UploadSuccessCallback uploadSuccessCallback;
    private StatusCallback statusCallback;

    /**
     * 采集结果回调接口
     */
    public interface CaptureResultCallback {
        void onResult(CapturePipeline.CaptureResult result);
    }

    /**
     * 上传成功回调接口
     */
    public interface UploadSuccessCallback {
        void onUploadSuccess(String barcode, String imagePath, String uploadUrl);
    }

    /**
     * 状态回调接口
     */
    public interface StatusCallback {
        void onStatus(String message);
    }

    public AutoCaptureController(BarcodeRecognitionService barcodeService, UploadService uploadService) {
        this.barcodeService = barcodeService;
        this.uploadService = uploadService;
    }

    /**
     * 设置采集结果回调
     */
    public void setResultCallback(CaptureResultCallback callback) {
        this.resultCallback = callback;
    }

    /**
     * 设置上传成功回调
     */
    public void setUploadSuccessCallback(UploadSuccessCallback callback) {
        this.uploadSuccessCallback = callback;
    }

    /**
     * 设置状态回调
     */
    public void setStatusCallback(StatusCallback callback) {
        this.statusCallback = callback;
    }

    /**
     * 初始化自动采集服务
     */
    public void initialize() {
        // 创建帧变化检测器（只负责判断是否出现新 A4 纸）
        changeDetector = new FrameChangeDetector();

        // 创建或更新处理管道
        createOrUpdatePipeline();

        // 组装自动采集服务
        autoCaptureService = new AutoCaptureService(changeDetector, capturePipeline);

        // 设置结果回调（通过 Platform.runLater 通知 UI）
        autoCaptureService.setCallback(result -> {
            Platform.runLater(() -> {
                handleCaptureResult(result);
            });
        });

        // 默认启用自动采集
        autoCaptureService.enable();
        if (statusCallback != null) {
            statusCallback.onStatus("自动采集服务已初始化并启用");
        }
    }

    /**
     * 创建或更新处理管道（使用当前的 uploadService）
     */
    private void createOrUpdatePipeline() {
        // 获取输出目录（转换为绝对路径，避免相对路径问题）
        String outputDirPath = AppConfig.getA4SaveFolder();
        File outputDir = new File(outputDirPath).getAbsoluteFile();
        // 确保目录存在
        outputDir.mkdirs();

        // 创建处理管道（内部包含：A4矫正 → 条码识别 → 去重 → 文件保存 → 自动上传）
        capturePipeline = new CapturePipeline(barcodeService, outputDir, uploadService);

        // 设置上传成功回调
        capturePipeline.setUploadSuccessCallback((barcode, imagePath, uploadUrl) -> {
            if (uploadSuccessCallback != null) {
                Platform.runLater(() -> {
                    uploadSuccessCallback.onUploadSuccess(barcode, imagePath, uploadUrl);
                });
            }
        });
    }

    /**
     * 更新上传服务（用于配置更新后重新初始化）
     *
     * @param newUploadService 新的上传服务实例
     */
    public void updateUploadService(UploadService newUploadService) {
        this.uploadService = newUploadService;
        // 如果服务已初始化，需要重新创建管道以使用新的上传服务
        if (autoCaptureService != null) {
            // 先禁用，等待任务完成
            boolean wasEnabled = autoCaptureService.isEnabled();
            if (wasEnabled) {
                autoCaptureService.disable();
            }

            // 重新创建管道
            createOrUpdatePipeline();

            // 重新创建自动采集服务（使用新的管道）
            autoCaptureService = new AutoCaptureService(changeDetector, capturePipeline);
            autoCaptureService.setCallback(result -> {
                Platform.runLater(() -> {
                    handleCaptureResult(result);
                });
            });

            // 如果之前是启用的，重新启用
            if (wasEnabled) {
                autoCaptureService.enable();
            }
        }
    }

    /**
     * 处理自动采集结果
     */
    private void handleCaptureResult(CapturePipeline.CaptureResult result) {
        String message;
        if (result.isSuccess()) {
            message = "✓ 自动采集成功: 条码=" + result.getBarcode() +
                    ", 文件=" + result.getFilePath();
            appendStatus(message);
            // 注意：上传成功提示会通过 CapturePipeline 的上传成功回调显示
            // 成功日志重置去重计数
            lastLogMessage = "";
            duplicateLogCount = 0;

            // 通知外部结果回调
            if (resultCallback != null) {
                resultCallback.onResult(result);
            }
        } else if (result.isDuplicate()) {
            message = "⚠ 条码重复，已跳过: " + result.getBarcode();
            appendStatusThrottled(message);
        } else {
            message = "✗ 自动采集失败: " + result.getErrorMessage();
            appendStatusThrottled(message);
        }
    }

    /**
     * 带频率限制的状态日志输出
     * 避免相同日志频繁刷屏
     */
    private void appendStatusThrottled(String message) {
        long now = System.currentTimeMillis();

        // 如果是相同的消息
        if (message.equals(lastLogMessage)) {
            duplicateLogCount++;

            // 如果距离上次日志时间太短，且重复次数未超过阈值，则跳过
            if ((now - lastLogTime) < LOG_THROTTLE_MS && duplicateLogCount <= MAX_DUPLICATE_COUNT) {
                return; // 跳过这条日志
            }

            // 如果重复次数超过阈值，显示汇总信息
            if (duplicateLogCount > MAX_DUPLICATE_COUNT) {
                appendStatus(message + " (已重复 " + duplicateLogCount + " 次)");
                duplicateLogCount = 0; // 重置计数
                lastLogTime = now;
                return;
            }

            // 如果时间间隔足够，显示日志
            if ((now - lastLogTime) >= LOG_THROTTLE_MS) {
                appendStatus(message);
                lastLogTime = now;
                duplicateLogCount = 0; // 重置计数（因为已经显示了）
            }
        } else {
            // 新消息，直接显示并重置计数
            appendStatus(message);
            lastLogMessage = message;
            duplicateLogCount = 0;
            lastLogTime = now;
        }
    }

    /**
     * 添加状态信息
     */
    private void appendStatus(String message) {
        if (statusCallback != null) {
            statusCallback.onStatus(message);
        }
    }

    /**
     * 启用自动采集
     */
    public void enable() {
        if (autoCaptureService != null) {
            autoCaptureService.enable();
        }
    }

    /**
     * 禁用自动采集
     */
    public void disable() {
        if (autoCaptureService != null) {
            autoCaptureService.disable();
        }
    }

    /**
     * 安全重置自动采集服务
     * 1. 先禁用自动采集（停止接收新帧）
     * 2. 等待正在进行的任务完成（有超时保护）
     * 3. 重置服务状态
     * 4. 重新启用
     */
    public void reset() {
        if (autoCaptureService == null) {
            if (statusCallback != null) {
                statusCallback.onStatus("✗ 自动采集服务未初始化");
            }
            return;
        }

        // 1. 先禁用自动采集，停止接收新帧
        boolean wasEnabled = autoCaptureService.isEnabled();
        if (wasEnabled) {
            autoCaptureService.disable();
        }

        // 2. 等待正在进行的任务完成（最多等待 2 秒）
        waitForTasksCompletion(2000);

        // 3. 重置服务状态（重新启用会触发内部重置）
        if (wasEnabled) {
            autoCaptureService.enable();
        }

        // 4. 重置日志去重状态
        lastLogMessage = "";
        duplicateLogCount = 0;
        lastLogTime = 0L;

        if (statusCallback != null) {
            statusCallback.onStatus("✓ 自动预览已重置，重新开始识别流程");
        }
    }

    /**
     * 等待正在进行的任务完成
     * 注意：AutoCaptureService 内部使用 ExecutorService，任务完成后会设置 taskInProgress = false
     * 但由于没有公开的 API 来检查任务状态，这里采用简单的延迟等待
     *
     * @param timeoutMs 超时时间（毫秒）
     */
    private void waitForTasksCompletion(long timeoutMs) {
        long startTime = System.currentTimeMillis();
        long checkInterval = 50; // 每 50ms 检查一次

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                Thread.sleep(checkInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("等待任务完成被中断", e);
                break;
            }
        }
    }

    /**
     * 处理帧数据（由 PreviewManager 调用）
     */
    public void onFrame(int[] pixels, int width, int height) {
        if (autoCaptureService != null && autoCaptureService.isEnabled()) {
            autoCaptureService.onFrame(pixels, width, height);
        }
    }

    /**
     * 检查自动采集是否启用
     */
    public boolean isEnabled() {
        return autoCaptureService != null && autoCaptureService.isEnabled();
    }
}

