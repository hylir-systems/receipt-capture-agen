package com.hylir.receipt.controller;

import com.hylir.receipt.config.AppConfig;
import com.hylir.receipt.model.CaptureResult;
import com.hylir.receipt.service.BarcodeRecognitionService;
import com.hylir.receipt.service.CameraService;
import com.hylir.receipt.service.UploadService;
import com.hylir.receipt.util.TempFileCleaner;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelBuffer;

import java.nio.IntBuffer;

import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import javax.imageio.ImageIO;
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
    private Button autoDetectButton;
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

    // 扫描区域选择相关（已移除，实时取景不再支持手动选区）

    // 拍照区域尺寸常量 (在640x480预览图像中的比例，适合摄像头取景)
    private static final double PHOTO_WIDTH_RATIO = 0.8;  // 拍照宽度占预览区域的80%
    private static final double PHOTO_HEIGHT_RATIO = 0.7; // 拍照高度占预览区域的70%

    // 服务组件
    private CameraService cameraService;
    private BarcodeRecognitionService barcodeService;
    private UploadService uploadService;

    // 当前采集结果
    private CaptureResult currentResult;
    // 实时流使用的可写图像缓存
    private WritableImage streamImage = null;
    private PixelBuffer<IntBuffer> pixelBuffer = null;
    private IntBuffer intBuffer = null;
    private long lastLatencyLogTime = 0L;
    // 自动预览检测相关
    private volatile boolean autoDetectInProgress = false;
    private long lastAutoDetectTime = 0L;
    private static final long AUTO_DETECT_INTERVAL_MS = 2000L; // 每2秒最多触发一次
    private static final String PREVIEW_TEMP_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "receipt-capture").toString();
    private static final int MAX_PREVIEW_IMAGES = 10; // 最多保存10张预览图片
    private static final long TEMP_FILE_EXPIRE_MS = 2 * 60 * 1000L; // 临时文件过期时间：2分钟
    private int previewImageCount = 0; // 预览图片计数器

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

            // 启动时清理过期临时文件
            TempFileCleaner.cleanupExpiredFiles(PREVIEW_TEMP_DIR);

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
        autoDetectButton.setOnAction(e -> handleAutoDetect());
        uploadButton.setOnAction(e -> handleUpload());
        manualInputButton.setOnAction(e -> handleManualInput());
        previewButton.setOnAction(e -> handlePreview());

        // 添加键盘快捷键支持
        setupKeyboardShortcuts();

        appendStatus("应用已就绪");
    }

    /**
     * 设置键盘快捷键
     */
    private void setupKeyboardShortcuts() {
        // 在 UI 完成渲染后再注册键盘事件，避免 getScene() 返回 null 导致 NPE
        Platform.runLater(() -> {
            if (imagePane.getScene() != null) {
                imagePane.getScene().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                    if (event.getCode() == KeyCode.F6) {
                        if (!autoDetectButton.isDisabled()) {
                            handleAutoDetect();
                            event.consume();
                        }
                    }
                });
            } else {
                // 如果仍为空，则监听 sceneProperty，当 scene 可用时注册并移除监听
                final javafx.beans.value.ChangeListener<javafx.scene.Scene> listener =
                        new javafx.beans.value.ChangeListener<javafx.scene.Scene>() {
                            @Override
                            public void changed(javafx.beans.value.ObservableValue<? extends javafx.scene.Scene> obs,
                                                javafx.scene.Scene oldScene, javafx.scene.Scene newScene) {
                                if (newScene != null) {
                                    newScene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                                        if (event.getCode() == KeyCode.F6) {
                                            if (!autoDetectButton.isDisabled()) {
                                                handleAutoDetect();
                                                event.consume();
                                            }
                                        }
                                    });
                                    imagePane.sceneProperty().removeListener(this);
                                }
                            }
                        };
                imagePane.sceneProperty().addListener(listener);
            }
        });
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

                        // 自动在预览中检测 A4 并识别条码（有节流，后台执行避免阻塞 UI）
                        long nowMs = System.currentTimeMillis();
                        if (AppConfig.isAutoA4CorrectionEnabled() && !autoDetectInProgress
                                && (nowMs - lastAutoDetectTime) >= AUTO_DETECT_INTERVAL_MS) {
                            lastAutoDetectTime = nowMs;
                            autoDetectInProgress = true;
                            // 复制像素数据，避免并发问题
                            int[] frameCopy = Arrays.copyOf(pixels, w * h);
                            final int fw = w;
                            final int fh = h;

                            Task<Void> detectTask = new Task<Void>() {
                                @Override
                                protected Void call() {
                                    try {
                                        BufferedImage frameImg = new BufferedImage(fw, fh, BufferedImage.TYPE_INT_ARGB);
                                        frameImg.setRGB(0, 0, fw, fh, frameCopy, 0, fw);

                                        // 保存原始帧图片到临时文件
                                        Path tmpDir = Paths.get(PREVIEW_TEMP_DIR);
                                        if (!Files.exists(tmpDir)) Files.createDirectories(tmpDir);
                                        
                                        // 清理过期文件（只保留最近2分钟）
                                        TempFileCleaner.cleanupExpiredFiles(tmpDir);
                                        
                                        String timestamp = String.valueOf(System.currentTimeMillis());
                                        String rawPath = PREVIEW_TEMP_DIR + File.separator + "preview_raw_" + timestamp + ".png";
                                        ImageIO.write(frameImg, "PNG", new File(rawPath));
                                        logger.info("预览原始图片保存到: {}", rawPath);

                                        // 优先尝试基于 A4 的矫正
                                        BufferedImage corrected = com.hylir.receipt.service.DocumentScanner.detectAndWarpA4(
                                                frameImg, AppConfig.getCameraWidth(), AppConfig.getCameraHeight());
                                        BufferedImage toRecognize = corrected != null ? corrected : frameImg;

                                        // 如果A4矫正成功，保存矫正后的图片
                                        if (corrected != null) {
                                            String correctedPath = PREVIEW_TEMP_DIR + File.separator + "preview_corrected_" + timestamp + ".png";
                                            ImageIO.write(corrected, "PNG", new File(correctedPath));
                                            logger.info("预览A4矫正图片保存到: {}", correctedPath);
                                        }

                                        // 更新计数器并检查是否超过最大数量
                                        previewImageCount++;
                                        if (previewImageCount >= MAX_PREVIEW_IMAGES) {
                                            previewImageCount = 0; // 循环使用，从1重新开始
                                        }

                                        // 识别条码（在后台执行）
                                        String code = barcodeService.recognize(toRecognize, 3);
                                        if (code != null && barcodeService.isValidReceiptNumber(code)) {
                                            Platform.runLater(() -> {
                                                receiptNumberField.setText(code);
                                                appendStatus("✓ 预览自动识别到条码: " + code);
                                                uploadButton.setDisable(false);
                                            });
                                        } else {
                                            logger.debug("预览未识别到有效条码");
                                        }
                                    } catch (Exception e) {
                                        logger.warn("预览自动检测或识别失败: {}", e.getMessage());
                                    } finally {
                                        autoDetectInProgress = false;
                                    }
                                    return null;
                                }
                            };

                            new Thread(detectTask, "Preview-AutoDetect").start();
                        }

                        // 记录并显示延迟（按1s频率显示以免过多日志）
                        long now = System.nanoTime();
                        long latencyMs = (now - captureTimeNanos) / 1_000_000;
                        if (lastLatencyLogTime == 0 || now - lastLatencyLogTime >= 1_000_000_000L) {
                            lastLatencyLogTime = now;
                           // logger.info("帧端到端延迟: {} ms", latencyMs);
                           // appendStatus("当前延迟: " + latencyMs + " ms");
                        }

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
        cameraService.stopLiveStream();


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
     * 处理自动检测按钮点击
     */
    @FXML
    private void handleAutoDetect() {
        autoDetectButton.setDisable(true);
        progressBar.setVisible(true);
        appendStatus("正在自动检测A4纸张...");

        Task<CaptureResult> autoDetectTask = new Task<CaptureResult>() {
            @Override
            protected CaptureResult call() throws Exception {
                try {
                    // 拍照
                    updateMessage("正在拍照...");
                    String imagePath = cameraService.captureImage();

                    // 读取图片
                    updateMessage("正在处理图片...");
                    BufferedImage bufferedImage = cameraService.captureImageAsBufferedImage();

                    // 自动检测并矫正A4纸张
                    updateMessage("正在检测A4纸张...");
                    BufferedImage correctedImage = com.hylir.receipt.service.DocumentScanner.detectAndWarpA4(
                            bufferedImage,
                            AppConfig.getCameraWidth(),
                            AppConfig.getCameraHeight()
                    );

                    if (correctedImage != null) {
                        updateMessage("A4纸张检测成功，正在矫正...");
                        // 使用矫正后的图像进行条码识别
                        bufferedImage = correctedImage;
                        logger.info("A4纸张自动检测和矫正成功");
                    } else {
                        updateMessage("未检测到A4纸张，使用原图处理...");
                        logger.warn("未检测到A4纸张，使用原图进行处理");
                    }

                    // 识别条码
                    updateMessage("正在识别条码...");
                    String barcode = barcodeService.recognize(bufferedImage, 3);

                    // 创建结果对象
                    CaptureResult result = new CaptureResult();
                    result.setImagePath(imagePath);

                    if (barcode != null && barcodeService.isValidReceiptNumber(barcode)) {
                        result.setReceiptNumber(barcode);
                        result.setRecognitionSuccess(true);
                        logger.info("条码识别成功: {}", barcode);
                    } else {
                        result.setRecognitionSuccess(false);
                        result.setErrorMessage("未能识别到有效的送货单号");
                        logger.warn("条码识别失败");
                    }

                    return result;

                } catch (Exception e) {
                    logger.error("自动检测过程出错", e);
                    throw e;
                }
            }

            @Override
            protected void succeeded() {
                currentResult = getValue();
                updateUIAfterAutoDetect();
            }

            @Override
            protected void failed() {
                Throwable exception = getException();
                logger.error("自动检测失败", exception);
                appendStatus("自动检测失败: " + exception.getMessage());
                showErrorAlert("自动检测失败", "自动检测过程中出现错误: " + exception.getMessage());
                resetUIAfterAutoDetect();
            }
        };

        progressBar.progressProperty().bind(autoDetectTask.progressProperty());
        new Thread(autoDetectTask).start();
    }

    /**
     * 自动检测成功后更新UI
     */
    private void updateUIAfterAutoDetect() {
        try {
            // 显示图片
            if (currentResult.getImagePath() != null) {
                File imageFile = new File(currentResult.getImagePath());
                if (imageFile.exists() && imageFile.length() > 0) {
                    logger.info("加载自动检测图片: {} (大小: {} bytes)", currentResult.getImagePath(), imageFile.length());
                    // 使用FileInputStream来确保图片加载
                    try (FileInputStream fis = new FileInputStream(imageFile)) {
                        Image image = new Image(fis);
                        imageView.setImage(image);
                        imageView.setVisible(true);
                        appendStatus("✓ A4自动检测完成，图片已加载，大小: " + imageFile.length() + " bytes");
                    }
                } else {
                    logger.warn("图片文件不存在或为空: {}", currentResult.getImagePath());
                    appendStatus("✗ 图片文件不存在或为空");
                }
            } else {
                logger.warn("自动检测结果中没有图片路径");
                appendStatus("✗ 未获取到图片路径");
            }

            // 显示识别结果
            if (currentResult.isRecognitionSuccess()) {
                receiptNumberField.setText(currentResult.getReceiptNumber());
                appendStatus("✓ 识别成功: " + currentResult.getReceiptNumber());
                uploadButton.setDisable(false);
            } else {
                receiptNumberField.setText("");
                appendStatus("✗ 识别失败: " + currentResult.getErrorMessage());
                showInfoAlert("识别失败", "未能自动识别送货单号，请手动输入或重新拍照");
                manualInputButton.setDisable(false);
            }

        } catch (Exception e) {
            logger.error("更新UI失败", e);
        } finally {
            resetUIAfterAutoDetect();
        }
    }

    /**
     * 重置自动检测后的UI状态
     */
    private void resetUIAfterAutoDetect() {
        autoDetectButton.setDisable(false);
        progressBar.setVisible(false);
        progressBar.progressProperty().unbind();
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
}
