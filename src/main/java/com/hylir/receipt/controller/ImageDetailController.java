package com.hylir.receipt.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * 图片详情对话框控制器
 * 
 * @author shanghai pubing
 * @date 2025/01/19
 */
public class ImageDetailController {

    private static final Logger logger = LoggerFactory.getLogger(ImageDetailController.class);

    @FXML
    private Label titleLabel;
    @FXML
    private Label barcodeValueLabel;
    @FXML
    private Label timeValueLabel;
    @FXML
    private TextField urlValueField;
    @FXML
    private ScrollPane imageScrollPane;
    @FXML
    private ImageView detailImageView;
    @FXML
    private Button closeButton;

    private Stage dialogStage;

    /**
     * 初始化
     */
    @FXML
    private void initialize() {
        // 设置关闭按钮事件
        closeButton.setOnAction(e -> {
            if (dialogStage != null) {
                dialogStage.close();
            }
        });
    }

    /**
     * 设置对话框数据
     * 
     * @param barcode 单号
     * @param imagePath 本地图片路径
     * @param uploadUrl 上传后的URL
     * @param timestamp 时间戳
     */
    public void setData(String barcode, String imagePath, String uploadUrl, String timestamp) {
        // 设置信息
        barcodeValueLabel.setText(barcode);
        timeValueLabel.setText(timestamp);
        urlValueField.setText(uploadUrl != null && !uploadUrl.isEmpty() ? uploadUrl : "未上传");

        // 加载图片：优先使用上传后的URL
        try {
            Image detailImage;
            if (uploadUrl != null && !uploadUrl.trim().isEmpty()) {
                logger.info("对话框中使用上传后的URL加载图片: {}", uploadUrl);
                detailImage = new Image(uploadUrl);
            } else {
                logger.info("对话框中使用本地文件加载图片: {}", imagePath);
                File imageFile = new File(imagePath);
                if (imageFile.exists()) {
                    detailImage = new Image(imageFile.toURI().toString());
                } else {
                    throw new Exception("本地文件不存在");
                }
            }

            detailImageView.setImage(detailImage);

            // 设置图片显示大小（最大宽度800，保持比例）
            double maxWidth = 800;
            double imageWidth = detailImage.getWidth();
            double imageHeight = detailImage.getHeight();

            if (imageWidth > maxWidth) {
                double scale = maxWidth / imageWidth;
                detailImageView.setFitWidth(maxWidth);
                detailImageView.setFitHeight(imageHeight * scale);
            } else {
                detailImageView.setFitWidth(imageWidth);
                detailImageView.setFitHeight(imageHeight);
            }

        } catch (Exception e) {
            logger.error("加载图片失败", e);
            Label errorLabel = new Label("图片加载失败: " + e.getMessage());
            errorLabel.setStyle("-fx-text-fill: #e74c3c; -fx-padding: 20px;");
            imageScrollPane.setContent(errorLabel);
        }
    }

    /**
     * 设置对话框窗口
     */
    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }

    /**
     * 静态方法：显示图片详情对话框
     * 
     * @param ownerWindow 主窗口
     * @param barcode 单号
     * @param imagePath 本地图片路径
     * @param uploadUrl 上传后的URL
     * @param timestamp 时间戳
     */
    public static void showImageDetailDialog(Window ownerWindow, String barcode, String imagePath, 
                                             String uploadUrl, String timestamp) {
        try {
            // 加载 FXML
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(ImageDetailController.class.getResource("/fxml/ImageDetailView.fxml"));
            
            javafx.scene.Parent root = loader.load();
            
            // 获取控制器
            ImageDetailController controller = loader.getController();
            
            // 创建对话框窗口
            Stage dialogStage = new Stage();
            dialogStage.setTitle("回单详情 - " + barcode);
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(ownerWindow);
            dialogStage.setResizable(true);
            
            // 设置控制器数据
            controller.setDialogStage(dialogStage);
            controller.setData(barcode, imagePath, uploadUrl, timestamp);
            
            // 创建场景
            Scene scene = new Scene(root, 900, 700);
            // 加载样式表
            scene.getStylesheets().add(ImageDetailController.class.getResource("/css/application.css").toExternalForm());
            dialogStage.setScene(scene);
            
            // 显示对话框
            dialogStage.show();
            
        } catch (Exception e) {
            LoggerFactory.getLogger(ImageDetailController.class).error("显示图片详情对话框失败", e);
            // 如果加载失败，显示错误提示
            javafx.application.Platform.runLater(() -> {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR);
                alert.setTitle("错误");
                alert.setHeaderText(null);
                alert.setContentText("无法显示图片详情: " + e.getMessage());
                alert.showAndWait();
            });
        }
    }
}

