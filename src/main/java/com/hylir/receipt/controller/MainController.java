package com.hylir.receipt.controller;

import com.hylir.receipt.config.AppConfig;
import com.hylir.receipt.service.BarcodeRecognitionService;
import com.hylir.receipt.service.CameraService;
import com.hylir.receipt.service.UploadService;
import com.hylir.receipt.service.autocapture.CapturePipeline;
import com.hylir.receipt.util.IconFactory;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.Node;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * 主界面控制器
 * 只负责 UI 事件处理和界面更新，符合单一职责原则
 *
 * @author shanghai pubing
 * @date 2025/01/26
 */
public class MainController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    // UI 组件
    @FXML
    private ImageView imageView;
    @FXML
    private Pane imagePane;
    @FXML
    private TextArea statusArea;
    @FXML
    private Button previewButton;
    @FXML
    private Button resetButton;
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
    @FXML
    private FlowPane successListContainer;
    @FXML
    private HBox titleIconBox;
    @FXML
    private HBox deviceIconBox;
    @FXML
    private HBox videoIconBox;
    @FXML
    private HBox historyIconBox;
    @FXML
    private HBox logIconBox;
    @FXML
    private HBox previewIconBox;
    @FXML
    private HBox resetIconBox;
    @FXML
    private HBox settingsIconBox;

    // 服务组件
    private CameraService cameraService;
    private BarcodeRecognitionService barcodeService;
    private UploadService uploadService;

    // 管理器组件（职责分离）
    private PreviewManager previewManager;
    private HistoryManager historyManager;
    private AutoCaptureController autoCaptureController;
    private StatusUpdateManager statusUpdateManager;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("=== MainController.initialize 开始执行 ===");
        try {
            // 初始化服务
            initializeServices();

            // 初始化管理器
            initializeManagers();

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
    }

    /**
     * 初始化管理器组件
     */
    private void initializeManagers() {
        // 初始化状态更新管理器（节流高频更新）
        statusUpdateManager = new StatusUpdateManager(statusArea, statusLabel);

        // 初始化预览管理器
        previewManager = new PreviewManager(cameraService, imageView, imagePane,
                previewButton, resetButton, statusLabel);

        // 设置预览管理器的帧回调，转发给自动采集控制器
        previewManager.setFrameCallback((pixels, width, height) -> {
            if (autoCaptureController != null) {
                autoCaptureController.onFrame(pixels, width, height);
            }
        });

        // 初始化历史记录管理器
        historyManager = new HistoryManager(successListContainer);

        // 初始化自动采集控制器
        autoCaptureController = new AutoCaptureController(barcodeService, uploadService);

        // 设置自动采集控制器的回调
        autoCaptureController.setResultCallback(result -> {
            // 如果预览已停止，不再处理自动采集结果
            if (!previewManager.isPreviewActive()) {
                return;
            }
        });

        autoCaptureController.setUploadSuccessCallback((barcode, imagePath, uploadUrl) -> {
            Window mainWindow = successListContainer.getScene().getWindow();
            historyManager.addHistoryItem(barcode, imagePath, uploadUrl, mainWindow);
        });

        // 使用状态更新管理器处理状态回调（节流更新）
        autoCaptureController.setStatusCallback(statusUpdateManager::appendStatus);

        // 初始化自动采集服务
        autoCaptureController.initialize();
    }

    /**
     * 初始化UI组件
     */
    private void initializeUI() {
        progressBar.setVisible(false);

        // 初始化成功列表容器
        if (successListContainer != null) {
            successListContainer.getChildren().clear();
        }

        // 初始化设备选择下拉框
        initializeDeviceSelection();

        // 设置图像视图属性
        try {
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
        } catch (Exception ignored) {
        }

        // 设置事件处理器
        previewButton.setOnAction(e -> handlePreview());
        resetButton.setOnAction(e -> handleReset());
        settingsButton.setOnAction(e -> handleSettings());

        // 初始化按钮状态
        previewButton.setText("预览");
        previewButton.setDisable(false);
        resetButton.setText("重置");
        resetButton.setDisable(true); // 初始状态：预览未启动，重置按钮禁用
        deviceComboBox.setDisable(false); // 初始状态：设备选择可用
        settingsButton.setText("设置");
        settingsButton.setDisable(false); // 设置按钮始终可用

        // 初始化彩色图标
        initializeIcons();

        // 初始化状态标签样式
        if (statusLabel != null) {
            statusLabel.setText("就绪");
            statusLabel.getStyleClass().removeAll("status-ready", "status-success", "status-error", 
                                                  "status-processing", "status-preview");
            statusLabel.getStyleClass().add("status-ready");
        }

        appendStatus("应用已就绪");
    }

    /**
     * 初始化彩色图标
     */
    private void initializeIcons() {
        Platform.runLater(() -> {
            try {
                // 标题图标
                if (titleIconBox != null) {
                    titleIconBox.getChildren().clear();
                    Node docIcon = IconFactory.createDocumentIcon(16);
                    titleIconBox.getChildren().add(docIcon);
                }

                // 设备图标
                if (deviceIconBox != null) {
                    deviceIconBox.getChildren().clear();
                    Node cameraIcon = IconFactory.createCameraIcon(18);
                    deviceIconBox.getChildren().add(cameraIcon);
                }

                // 视频图标
                if (videoIconBox != null) {
                    videoIconBox.getChildren().clear();
                    Node videoIcon = IconFactory.createCameraIcon(18);
                    videoIconBox.getChildren().add(videoIcon);
                }

                // 历史图标
                if (historyIconBox != null) {
                    historyIconBox.getChildren().clear();
                    Node listIcon = IconFactory.createListIcon(18);
                    historyIconBox.getChildren().add(listIcon);
                }

                // 日志图标
                if (logIconBox != null) {
                    logIconBox.getChildren().clear();
                    Node logIcon = IconFactory.createLogIcon(18);
                    logIconBox.getChildren().add(logIcon);
                }

                // 预览按钮图标
                if (previewIconBox != null) {
                    previewIconBox.getChildren().clear();
                    Node playIcon = IconFactory.createPlayIcon(18);
                    previewIconBox.getChildren().add(playIcon);
                }

                // 重置按钮图标
                if (resetIconBox != null) {
                    resetIconBox.getChildren().clear();
                    Node resetIcon = IconFactory.createResetIcon(14);
                    resetIconBox.getChildren().add(resetIcon);
                }

                // 设置按钮图标
                if (settingsIconBox != null) {
                    settingsIconBox.getChildren().clear();
                    Node settingsIcon = IconFactory.createSettingsIcon(14);
                    settingsIconBox.getChildren().add(settingsIcon);
                }
            } catch (Exception e) {
                logger.error("初始化图标失败", e);
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
                boolean deviceSelected = cameraService.selectDevice(0);
                if (deviceSelected) {
                    logger.debug("已选择默认设备索引: 0");
                }
            }

            // 添加选择变化监听器
            deviceComboBox.setOnAction(e -> {
                String selected = deviceComboBox.getValue();
                if (selected != null) {
                    int selectedIndex = deviceComboBox.getSelectionModel().getSelectedIndex();
                    
                    // 尝试选择设备（内部会检查是否已是当前设备）
                    boolean deviceSelected = cameraService.selectDevice(selectedIndex);
                    
                    if (deviceSelected) {
                        // 设备已切换，更新 UI
                        updateDeviceStatus(selected);
                        appendStatus("已选择设备: " + selected + " (索引: " + selectedIndex + ")");
                    } else {
                        // 设备未切换（已是当前设备），只更新 UI 显示，不输出日志
                        updateDeviceStatus(selected);
                        logger.debug("设备索引 {} 已是当前设备，仅更新 UI 显示", selectedIndex);
                    }
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

    /**
     * 更新设备状态显示
     */
    private void updateDeviceStatus(String deviceName) {
        Platform.runLater(() -> {
            if (currentDeviceLabel != null) {
                currentDeviceLabel.setText(deviceName);
            }
            if (statusLabel != null) {
                statusLabel.setText("就绪");
                // 清除所有状态样式类，应用就绪样式
                statusLabel.getStyleClass().removeAll("status-ready", "status-success", "status-error", 
                                                      "status-processing", "status-preview");
                statusLabel.getStyleClass().add("status-ready");
            }
        });
    }

    /**
     * 处理预览按钮点击
     * 根据预览状态切换按钮文本和行为
     */
    @FXML
    private void handlePreview() {
        if (previewManager.getPreviewState() == PreviewState.STOPPED) {
            // 开始实时预览
            startPreview();
        } else {
            // 停止实时预览
            stopPreview();
        }
    }

    /**
     * 开始实时预览
     */
    private void startPreview() {
        // 更新按钮状态：禁用设备选择，切换预览按钮文本
        Platform.runLater(() -> {
            previewButton.setText("停止预览");
            previewButton.setDisable(false);
            deviceComboBox.setDisable(true); // 启动预览时禁用设备选择
            resetButton.setDisable(false); // 重置按钮仅在预览运行时可用
        });

        // 启用自动采集服务
        if (autoCaptureController != null) {
            autoCaptureController.enable();
        }

        // 获取当前选择的设备索引
        int selectedDeviceIndex = cameraService.getSelectedDeviceIndex();
        if (selectedDeviceIndex < 0 && !deviceComboBox.getItems().isEmpty()) {
            selectedDeviceIndex = 0; // 默认使用第一个设备
        }

        // 使用预览管理器启动预览（使用状态更新管理器节流）
        previewManager.startPreview(selectedDeviceIndex, statusUpdateManager::appendStatus);
    }

    /**
     * 停止实时预览
     */
    private void stopPreview() {
        // 禁用自动采集
        if (autoCaptureController != null) {
            autoCaptureController.disable();
        }

        // 更新按钮状态：启用设备选择，切换预览按钮文本
        Platform.runLater(() -> {
            previewButton.setText("预览");
            previewButton.setDisable(false);
            deviceComboBox.setDisable(false); // 停止预览时启用设备选择
            resetButton.setDisable(true); // 停止预览时禁用重置按钮
        });

        // 使用预览管理器停止预览（使用状态更新管理器节流）
        previewManager.stopPreview(statusUpdateManager::appendStatus);
    }

    /**
     * 处理重置按钮点击
     * 安全重置流程：
     * 1. 检查预览是否激活
     * 2. 清空历史记录
     * 3. 重新创建上传服务（读取最新配置）
     * 4. 更新自动采集控制器中的上传服务
     * 5. 安全重置自动采集服务（停止接收新帧，等待任务完成，重置状态）
     */
    @FXML
    private void handleReset() {
        if (!previewManager.isPreviewActive()) {
            appendStatus("⚠ 预览未启动，无法重置");
            return;
        }

        appendStatus("🔄 正在重置自动预览...");

        // 清空历史记录
        if (historyManager != null) {
            historyManager.clearHistory();
            appendStatus("✓ 历史记录已清空");
        }

        // 重新创建上传服务，读取最新的配置
        uploadService = new UploadService();

        // 更新自动采集控制器中的上传服务（内部会安全停止并重新初始化）
        if (autoCaptureController != null) {
            // 先更新上传服务（会安全停止并重新创建管道）
            autoCaptureController.updateUploadService(uploadService);
            
            // 然后重置自动采集服务状态
            autoCaptureController.reset();
        } else {
            appendStatus("✗ 自动采集控制器未初始化");
        }
    }

    /**
     * 测试连接
     */
    private void testConnections() {
        Task<Void> testTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("正在测试后端连接...");
                boolean backendOk = uploadService.testBackendConnection();

                updateMessage("正在检查摄像头...");
                boolean cameraOk = cameraService.isUsingRealCamera();

                Platform.runLater(() -> {
                    if (backendOk) {
                        appendStatus("✓ 后端服务连接正常");
                    } else {
                        appendStatus("✗ 后端服务连接失败");
                        showWarningAlert("后端连接失败", "请检查网络连接和后端服务状态");
                    }

                    if (cameraOk) {
                        appendStatus("✓ 摄像头已就绪");
                    } else {
                        appendStatus("⚠ 摄像头未检测到（点击预览按钮时将重新检测）");
                    }
                });

                return null;
            }
        };

        progressBar.progressProperty().bind(testTask.progressProperty());
        new Thread(testTask).start();
    }

    /**
     * 添加状态信息（使用状态更新管理器节流）
     */
    private void appendStatus(String message) {
        if (statusUpdateManager != null) {
            statusUpdateManager.appendStatus(message);
        }
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

        // 重新加载配置
        AppConfig.reloadConfig();

        // 重新初始化上传服务以使用新配置
        uploadService = new UploadService();
        appendStatus("配置已更新，上传服务已重新初始化");
    }
}
