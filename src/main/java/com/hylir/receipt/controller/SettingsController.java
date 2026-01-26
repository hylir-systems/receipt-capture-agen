package com.hylir.receipt.controller;

import com.hylir.receipt.config.ConfigManager;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * 设置界面控制器
 * 处理用户配置交互
 */
public class SettingsController implements Initializable {

    @FXML
    private TextField backendUrlField;
    @FXML
    private TextField uploadEndpointField;
    @FXML
    private TextField a4SaveFolderField;
    @FXML
    private Button browseFolderButton;
    @FXML
    private Button resetButton;
    @FXML
    private Button cancelButton;
    @FXML
    private Button saveButton;
    @FXML
    private Label statusLabel;

    private ConfigManager configManager;
    private Stage dialogStage;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        configManager = ConfigManager.getInstance();
        loadCurrentConfig();
        setupEventHandlers();
    }

    /**
     * 设置对话框舞台
     */
    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }

    /**
     * 加载当前配置到界面
     */
    private void loadCurrentConfig() {
        backendUrlField.setText(configManager.getBackendUrl());
        uploadEndpointField.setText(configManager.getUploadEndpoint());
        a4SaveFolderField.setText(configManager.getA4SaveFolder());
    }

    /**
     * 设置事件处理器
     */
    private void setupEventHandlers() {
        saveButton.setOnAction(this::handleSave);
        cancelButton.setOnAction(this::handleCancel);
        resetButton.setOnAction(this::handleReset);
        browseFolderButton.setOnAction(this::handleBrowseFolder);
    }

    /**
     * 保存配置
     */
    @FXML
    private void handleSave(ActionEvent event) {
        String backendUrl = backendUrlField.getText().trim();
        String uploadEndpoint = uploadEndpointField.getText().trim();
        String a4SaveFolder = a4SaveFolderField.getText().trim();

        // 验证输入
        if (backendUrl.isEmpty()) {
            showStatus("错误: 后端服务地址不能为空", true);
            return;
        }

        if (uploadEndpoint.isEmpty()) {
            showStatus("错误: 上传接口路径不能为空", true);
            return;
        }

        if (a4SaveFolder.isEmpty()) {
            showStatus("错误: A4保存文件夹不能为空", true);
            return;
        }

        // 保存配置
        configManager.setBackendUrl(backendUrl);
        configManager.setUploadEndpoint(uploadEndpoint);
        configManager.setA4SaveFolder(a4SaveFolder);
        configManager.saveConfig();

        showStatus("配置已保存", false);

        // 延迟关闭对话框
        new Thread(() -> {
            try {
                Thread.sleep(500);
                Platform.runLater(() -> {
                    if (dialogStage != null) {
                        dialogStage.close();
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * 取消操作
     */
    @FXML
    private void handleCancel(ActionEvent event) {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    /**
     * 恢复默认配置
     */
    @FXML
    private void handleReset(ActionEvent event) {
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("确认恢复默认");
        confirmDialog.setHeaderText(null);
        confirmDialog.setContentText("确定要恢复默认配置吗？当前设置将被覆盖。");

        confirmDialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                configManager.resetToDefaults();
                loadCurrentConfig();
                showStatus("已恢复默认配置", false);
            }
        });
    }

    /**
     * 浏览文件夹
     */
    @FXML
    private void handleBrowseFolder(ActionEvent event) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("选择A4保存文件夹");

        // 设置初始目录
        String currentFolder = a4SaveFolderField.getText().trim();
        if (!currentFolder.isEmpty()) {
            File folder = new File(currentFolder);
            if (folder.exists()) {
                directoryChooser.setInitialDirectory(folder);
            }
        }

        Window window = dialogStage;
        File selectedFolder = directoryChooser.showDialog(window);

        if (selectedFolder != null) {
            a4SaveFolderField.setText(selectedFolder.getAbsolutePath());
        }
    }

    /**
     * 显示状态信息
     */
    private void showStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setStyle(isError ? "-fx-text-fill: #e74c3c;" : "-fx-text-fill: #27ae60;");
    }

    /**
     * 静态方法：显示设置对话框
     */
    public static void showSettingsDialog(Stage parentStage) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(SettingsController.class.getResource("/fxml/SettingsView.fxml"));

            Parent root = loader.load();

            // 获取控制器并设置对话框舞台
            SettingsController controller = loader.getController();
            Stage dialogStage = new Stage();
            dialogStage.setTitle("系统设置");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(parentStage);
            dialogStage.setResizable(false);

            Scene scene = new Scene(root);
            dialogStage.setScene(scene);

            controller.setDialogStage(dialogStage);

            dialogStage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("错误");
            alert.setHeaderText(null);
            alert.setContentText("无法打开设置窗口: " + e.getMessage());
            alert.showAndWait();
        }
    }
}

