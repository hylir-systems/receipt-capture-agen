package com.hylir.receipt.controller;

import com.hylir.receipt.config.AppConfig;
import com.hylir.receipt.model.CaptureResult;
import com.hylir.receipt.service.BarcodeRecognitionService;
import com.hylir.receipt.service.CameraService;
import com.hylir.receipt.service.UploadService;
import com.hylir.receipt.service.autocapture.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelBuffer;

import java.nio.IntBuffer;

import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * 主界面控制器
 * 处理用户交互和业务逻辑
 *
 * @author shanghai pubing
 * @date 2025/01/19
 */
public class MainController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    // UI 组件
    @FXML
    private ImageView imageView;
    @FXML
    private Pane imagePane;
    @FXML
    private TextField receiptNumberField;
    @FXML
    private TextArea statusArea;
    @FXML
    private Button uploadButton;
    @FXML
    private Button manualInputButton;
    @FXML
    private Button previewButton;
    @FXML
    private ComboBox<String> deviceComboBox;
    @FXML
    private Label currentDeviceLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Button settingsButton;

    // 服务组件
    private CameraService cameraService;
    private BarcodeRecognitionService barcodeService;
    private UploadService uploadService;
    
    // 自动采集服务
    private AutoCaptureService autoCaptureService;

    // 当前采集结果
    private CaptureResult currentResult;
    // 实时流使用的可写图像缓存
    private WritableImage streamImage = null;
    private PixelBuffer<IntBuffer> pixelBuffer = null;
    private IntBuffer intBuffer = null;
    private long lastLatencyLogTime = 0L;
    
    // 预览状态标志
    private volatile boolean isPreviewActive = false;
    
    // 日志去重和频率限制
    private String lastLogMessage = "";
    private long lastLogTime = 0L;
    private int duplicateLogCount = 0;
    private static final long LOG_THROTTLE_MS = 2000; // 相同日志至少间隔2秒
    private static final int MAX_DUPLICATE_COUNT = 5; // 最多连续显示5条相同日志

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("=== MainController.initialize 开始执行 ==="); // 在这里设置断点测试
        try {
            // 初始化服务
            initializeServices();

            // 初始化UI
            initializeUI();

            // 测试连接
            testConnections();


            logger.info("主控制器初始化完成");

        } catch (Exception e) {
            logger.error("控制器初始化失败", e);
            showErrorAlert("初始化失败", "应用初始化过程中出现错误: " + e.getMessage());
        }
    }

    /**
     * 初始化服务组件
     */
    private void initializeServices() {
        cameraService = new CameraService();
        barcodeService = new BarcodeRecognitionService();
        uploadService = new UploadService();

        // 初始化摄像头
        cameraService.initialize();
        
        // 初始化自动采集服务
        initializeAutoCaptureService();
    }
    
    /**
     * 初始化自动采集服务
     */
    private void initializeAutoCaptureService() {
        // 创建帧变化检测器（只负责判断是否出现新 A4 纸）
        FrameChangeDetector changeDetector = new FrameChangeDetector();
        
        // 获取输出目录
        String outputDirPath = AppConfig.getA4SaveFolder();
        java.io.File outputDir = new java.io.File(outputDirPath);
        
        // 创建处理管道（内部包含：A4矫正 → 条码识别 → 去重 → 文件保存）
        CapturePipeline capturePipeline = new CapturePipeline(barcodeService, outputDir);
        
        // 组装自动采集服务
        autoCaptureService = new AutoCaptureService(changeDetector, capturePipeline);
        
        // 设置结果回调（通过 Platform.runLater 通知 UI）
        autoCaptureService.setCallback(result -> {
            Platform.runLater(() -> {
                handleAutoCaptureResult(result);
            });
        });
        
        // 默认启用自动采集
        autoCaptureService.enable();
        appendStatus("自动采集服务已初始化并启用");
    }
    
    /**
     * 处理自动采集结果
     */
    private void handleAutoCaptureResult(CapturePipeline.CaptureResult result) {
        // 如果预览已停止，不再处理自动采集结果
        if (!isPreviewActive) {
            return;
        }
        
        String message;
        if (result.isSuccess()) {
            message = "✓ 自动采集成功: 条码=" + result.getBarcode() + 
                     ", 文件=" + result.getFilePath();
            appendStatus(message);
            // 成功日志重置去重计数
            lastLogMessage = "";
            duplicateLogCount = 0;
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
     * 初始化UI组件
     */
    private void initializeUI() {
        progressBar.setVisible(false);
        uploadButton.setDisable(true);
        receiptNumberField.setEditable(false);

        // 初始化设备选择下拉框
        initializeDeviceSelection();

        // 初始化扫描区域选择功能已移除（实时预览使用中不需要手动选区）

        // Bind ImageView to pane size so the whole A4 can fit and scale
        try {
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
            // do not bind here; we'll compute and center per-frame to avoid cropping
        } catch (Exception ignored) {
        }

        // 设置快捷键
        uploadButton.setOnAction(e -> handleUpload());
        manualInputButton.setOnAction(e -> handleManualInput());
        previewButton.setOnAction(e -> handlePreview());
        settingsButton.setOnAction(e -> handleSettings());

        // 添加键盘快捷键支持
        setupKeyboardShortcuts();

        appendStatus("应用已就绪");
    }

    /**
     * 设置键盘快捷键
     */
    private void setupKeyboardShortcuts() {
        // 键盘快捷键功能已移除
    }

    /**
     * 初始化设备选择功能
     */
    private void initializeDeviceSelection() {
        try {
            // 获取可用设备列表
            List<String> devices = cameraService.getAvailableScanners();
            deviceComboBox.getItems().addAll(devices);

            // 设置默认选择
            if (!devices.isEmpty()) {
                deviceComboBox.setValue(devices.get(0));
                // 默认选择第一个设备（索引为0）
                cameraService.selectDevice(0);
            }

            // 添加选择变化监听器
            deviceComboBox.setOnAction(e -> {
                String selected = deviceComboBox.getValue();
                if (selected != null) {
                    // 获取选择设备的索引
                    int selectedIndex = deviceComboBox.getSelectionModel().getSelectedIndex();
                    cameraService.selectDevice(selectedIndex);
                    updateDeviceStatus(selected);
                    appendStatus("已选择设备: " + selected + " (索引: " + selectedIndex + ")");
                }
            });

            // 更新设备状态显示
            String initialDevice = deviceComboBox.getValue();
            if (initialDevice != null) {
                updateDeviceStatus(initialDevice);
            }

            appendStatus("设备选择初始化完成，可用设备: " + devices.size() + "个");

        } catch (Exception e) {
            logger.error("初始化设备选择失败", e);
            showErrorAlert("设备初始化失败", "无法获取摄像头设备列表: " + e.getMessage());
        }
    }

    // 扫描区域选择功能已移除

    /**
     * 更新设备状态显示
     *
     * @param deviceName 设备名称
     */
    private void updateDeviceStatus(String deviceName) {
        Platform.runLater(() -> {
            if (currentDeviceLabel != null) {
                currentDeviceLabel.setText(deviceName);
            }
            if (statusLabel != null) {
                statusLabel.setText("就绪");
            }
        });
    }

    /**
     * 处理预览按钮点击
     */
    @FXML
    private void handlePreview() {
        if ("预览".equals(previewButton.getText())) {
            // 开始实时预览
            startLivePreview();
        } else {
            // 停止实时预览
            stopLivePreview();
        }
    }

    /**
     * 开始实时预览
     */
    private void startLivePreview() {
        previewButton.setDisable(true);
        appendStatus("正在启动实时预览...");
        
        // 设置预览状态标志
        isPreviewActive = true;
        // 重置日志去重状态
        lastLogMessage = "";
        duplicateLogCount = 0;
        lastLogTime = 0L;
        
        // 重新启用自动采集服务
        if (autoCaptureService != null) {
            autoCaptureService.enable();
        }

        // 清空当前图片，为新预览做准备
        imageView.setImage(null);
        // 清理之前的 PixelBuffer / WritableImage，确保重新创建（避免尺寸/缓存问题）
        pixelBuffer = null;
        intBuffer = null;
        streamImage = null;

        // 获取当前选择的设备索引
        int selectedDeviceIndex = cameraService.getSelectedDeviceIndex();
        if (selectedDeviceIndex < 0 && !deviceComboBox.getItems().isEmpty()) {
            selectedDeviceIndex = 0; // 默认使用第一个设备
        }

        boolean success = cameraService.startLiveStream(new CameraService.StreamCallback() {
            @Override
            public void onFrame(int[] pixels, int w, int h, long captureTimeNanos) {
                // 非 UI 线程回调，尽量少做工作，提交到 UI 线程只做 PixelBuffer 更新
                if (pixels == null || w <= 0 || h <= 0) return;
                
                // 自动采集服务处理帧（在后台线程，不阻塞UI）
                if (autoCaptureService != null && autoCaptureService.isEnabled()) {
                    autoCaptureService.onFrame(pixels, w, h);
                }

                Platform.runLater(() -> {
                    try {
                        // 初始化或重建 PixelBuffer / WritableImage
                        if (pixelBuffer == null || intBuffer == null || pixelBuffer.getWidth() != w || pixelBuffer.getHeight() != h) {
                            intBuffer = IntBuffer.allocate(w * h);
                            pixelBuffer = new PixelBuffer<>(w, h, intBuffer, PixelFormat.getIntArgbPreInstance());
                            streamImage = new WritableImage(pixelBuffer);
                            imageView.setImage(streamImage);
                        }

                        // 将像素复制到 IntBuffer（使用同一块内存）
                        intBuffer.rewind();
                        intBuffer.put(pixels, 0, w * h);
                        intBuffer.rewind();

                        // 通知 PixelBuffer 已更新（零拷贝）
                        pixelBuffer.updateBuffer(buf -> null);

                        // 记录并显示延迟（按1s频率显示以免过多日志）
                        long now = System.nanoTime();
                        // 计算缩放以完整显示视频（等比缩放，确保完整显示）
                        double paneW = imagePane.getWidth() - 20;
                        double paneH = imagePane.getHeight() - 20;
                        if (paneW <= 0) paneW = imageView.getFitWidth();
                        if (paneH <= 0) paneH = imageView.getFitHeight();

                        // 使用容器大小作为 fit 大小，确保完整显示（等比缩放 letterbox，不裁剪）
                        imageView.setPreserveRatio(true);
                        imageView.setSmooth(true);
                        imageView.setCache(true);

                        imageView.setFitWidth(paneW);
                        imageView.setFitHeight(paneH);

                        // 清除可能存在的 viewport/clip，防止显示被裁剪
                        try {
                            imageView.setViewport(null);
                        } catch (Exception ignore) {
                        }
                        try {
                            imageView.setClip(null);
                        } catch (Exception ignore) {
                        }

                        // 居中显示（基于实际显示尺寸）
                        double actualW = Math.min(imageView.getBoundsInLocal().getWidth(), paneW);
                        double actualH = Math.min(imageView.getBoundsInLocal().getHeight(), paneH);
                        imageView.setLayoutX((imagePane.getWidth() - actualW) / 2);
                        imageView.setLayoutY((imagePane.getHeight() - actualH) / 2);
                        imageView.setVisible(true);

                        // 更新按钮状态
                        if (!"停止预览".equals(previewButton.getText())) {
                            previewButton.setText("停止预览");
                            previewButton.setDisable(false);

                            boolean usingRealCamera = isRealCameraAvailable();
                            String cameraType = usingRealCamera ? "真实摄像头" : "模拟预览";
                            appendStatus("✓ 摄像头实时预览已启动 (" + cameraType + ")，自动设置拍照区域");
                        }

                    } catch (Exception e) {
                        logger.error("加载实时预览帧失败", e);
                        appendStatus("✗ 实时预览帧加载失败: " + e.getMessage());
                    }
                });
            }

            @Override
            public void onError(String error) {
                Platform.runLater(() -> {
                    logger.error("实时流错误: {}", error);
                    appendStatus("✗ 实时流错误: " + error);
                    resetPreviewButton();
                });
            }
        }, selectedDeviceIndex);

        if (!success) {
            appendStatus("✗ 无法启动实时预览，请检查设备选择");
            resetPreviewButton();
        }
    }

    /**
     * 停止实时预览
     */
    private void stopLivePreview() {
        // 先设置预览状态标志为 false，防止后续回调输出日志
        isPreviewActive = false;
        
        cameraService.stopLiveStream();
        
        // 禁用自动采集
        if (autoCaptureService != null) {
            autoCaptureService.disable();
        }

        appendStatus("✓ 实时预览已停止");
        // 清空 UI 上的预览并释放本地缓存，确保下次重新创建
        Platform.runLater(() -> {
            imageView.setImage(null);
            pixelBuffer = null;
            intBuffer = null;
            streamImage = null;
        });
        resetPreviewButton();
    }

    // 默认拍照区域逻辑已移除

    /**
     * 检查是否使用真实摄像头
     */
    private boolean isRealCameraAvailable() {
        return cameraService.isUsingRealCamera();
    }

    // 扫描区域相关功能已移除

    /**
     * 重置预览按钮状态
     */
    private void resetPreviewButton() {
        Platform.runLater(() -> {
            previewButton.setText("预览");
            previewButton.setDisable(false);
        });
    }

    /**
     * 测试连接
     * 注意：摄像头检测失败时不显示警告，让用户在预览时再实际启动摄像头
     */
    private void testConnections() {
        Task<Void> testTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("正在测试后端连接...");
                boolean backendOk = uploadService.testBackendConnection();

                // 摄像头检测改为在预览时实际启动，这里只做初步检查
                updateMessage("正在检查摄像头...");
                boolean cameraOk = cameraService.isUsingRealCamera();

                Platform.runLater(() -> {
                    if (backendOk) {
                        appendStatus("✓ 后端服务连接正常");
                    } else {
                        appendStatus("✗ 后端服务连接失败");
                        showWarningAlert("后端连接失败", "请检查网络连接和后端服务状态");
                    }

                    // 摄像头状态：只在状态栏显示，不弹窗警告
                    if (cameraOk) {
                        appendStatus("✓ 摄像头已就绪");
                    } else {
                        appendStatus("⚠ 摄像头未检测到（点击预览按钮时将重新检测）");
                        // 不再这里弹窗警告，让用户在预览时看到实际结果
                    }
                });

                return null;
            }
        };

        progressBar.progressProperty().bind(testTask.progressProperty());
        new Thread(testTask).start();
    }


    /**
     * 处理上传按钮点击
     */
    @FXML
    private void handleUpload() {
        if (currentResult == null) {
            showWarningAlert("无数据", "请先拍照获取数据");
            return;
        }

        uploadButton.setDisable(true);
        progressBar.setVisible(true);
        appendStatus("正在上传中...");

        Task<Boolean> uploadTask = new Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
                return uploadService.uploadCaptureResult(currentResult);
            }

            @Override
            protected void succeeded() {
                Boolean success = getValue();
                if (success) {
                    appendStatus("✓ 上传成功");
                    showInfoAlert("上传成功", "数据已成功上传到后端服务器");
                    uploadButton.setDisable(true);
                } else {
                    appendStatus("✗ 上传失败");
                    showErrorAlert("上传失败", "上传过程中出现错误，请重试");
                    uploadButton.setDisable(false);
                }
                progressBar.setVisible(false);
            }

            @Override
            protected void failed() {
                Throwable exception = getException();
                logger.error("上传失败", exception);
                appendStatus("上传失败: " + exception.getMessage());
                showErrorAlert("上传失败", "上传过程中出现错误: " + exception.getMessage());
                uploadButton.setDisable(false);
                progressBar.setVisible(false);
            }
        };

        new Thread(uploadTask).start();
    }

    /**
     * 处理手动输入按钮点击
     */
    @FXML
    private void handleManualInput() {
        if (currentResult == null) {
            showWarningAlert("无数据", "请先拍照获取数据");
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("手动输入送货单号");
        dialog.setHeaderText("请输入送货单号");
        dialog.setContentText("单号:");

        dialog.showAndWait().ifPresent(receiptNumber -> {
            if (!receiptNumber.trim().isEmpty()) {
                currentResult.setReceiptNumber(receiptNumber.trim());
                currentResult.setRecognitionSuccess(true);
                receiptNumberField.setText(receiptNumber);
                appendStatus("✓ 手动输入单号: " + receiptNumber);
                uploadButton.setDisable(false);
                manualInputButton.setDisable(true);
            }
        });
    }

    /**
     * 添加状态信息
     */
    private void appendStatus(String message) {
        Platform.runLater(() -> {
            statusArea.appendText("[" + java.time.LocalTime.now().toString() + "] " + message + "\n");

            // 根据消息内容更新状态标签
            if (message.contains("✓") || message.contains("成功")) {
                if (statusLabel != null) {
                    statusLabel.setText("成功");
                }
            } else if (message.contains("✗") || message.contains("失败")) {
                if (statusLabel != null) {
                    statusLabel.setText("错误");
                }
            } else if (message.contains("正在") || message.contains("处理")) {
                if (statusLabel != null) {
                    statusLabel.setText("处理中");
                }
            }
        });
    }

    /**
     * 显示错误对话框
     */
    private void showErrorAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * 显示警告对话框
     */
    private void showWarningAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.WARNING);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * 显示信息对话框
     */
    private void showInfoAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * 处理设置按钮点击
     */
    @FXML
    private void handleSettings() {
        Stage primaryStage = (Stage) settingsButton.getScene().getWindow();
        SettingsController.showSettingsDialog(primaryStage);
        
        // 设置保存后，重新初始化上传服务以使用新配置
        uploadService = new UploadService();
        appendStatus("配置已更新，上传服务已重新初始化");
    }
}
