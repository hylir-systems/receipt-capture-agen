package com.hylir.receipt.controller;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * 扫描历史记录管理器
 * 负责管理扫描历史记录的显示、缩略图加载和点击事件
 *
 * @author shanghai pubing
 * @date 2025/01/26
 */
public class HistoryManager {

    private static final Logger logger = LoggerFactory.getLogger(HistoryManager.class);

    private final FlowPane successListContainer;
    private static final int MAX_HISTORY_ITEMS = 30;

    public HistoryManager(FlowPane successListContainer) {
        this.successListContainer = successListContainer;
    }

    /**
     * 显示上传成功的历史记录
     * 布局：缩略图在上，单据号在下（完整显示）
     * 使用上传后的URL来加载缩略图，确认文件已上传
     *
     * @param barcode    单号
     * @param imagePath  本地图片路径（备用）
     * @param uploadUrl  上传后的文件URL（优先使用）
     * @param mainWindow 主窗口（用于显示详情对话框）
     */
    public void addHistoryItem(String barcode, String imagePath, String uploadUrl, Window mainWindow) {
        if (successListContainer == null) {
            return;
        }

        Platform.runLater(() -> {
            try {
                // 获取当前时间戳（格式：HH:mm:ss）
                java.time.LocalTime now = java.time.LocalTime.now();
                String timestamp = String.format("%02d:%02d:%02d",
                        now.getHour(), now.getMinute(), now.getSecond());

                // 创建历史记录条目容器（垂直布局：缩略图在上，单号在下）
                VBox historyItem = createHistoryItem(barcode, timestamp, imagePath, uploadUrl, mainWindow);

                // 添加到列表顶部（最新的在最上面）
                successListContainer.getChildren().add(0, historyItem);

                // 限制最大显示数量（保留最近30条）
                if (successListContainer.getChildren().size() > MAX_HISTORY_ITEMS) {
                    successListContainer.getChildren().remove(MAX_HISTORY_ITEMS,
                            successListContainer.getChildren().size());
                }

            } catch (Exception e) {
                logger.error("显示上传成功提示失败", e);
            }
        });
    }

    /**
     * 创建历史记录条目
     */
    private VBox createHistoryItem(String barcode, String timestamp, String imagePath,
                                    String uploadUrl, Window mainWindow) {
        VBox historyItem = new VBox();
        historyItem.setSpacing(6.0);
        historyItem.setAlignment(Pos.TOP_CENTER);
        historyItem.getStyleClass().add("history-item");
        historyItem.setMinWidth(150);
        historyItem.setMaxWidth(150);

        // 缩略图（上方）
        ImageView thumbnail = createThumbnail(barcode, imagePath, uploadUrl, timestamp, mainWindow);

        // 信息区域（下方）
        VBox infoBox = createInfoBox(barcode, timestamp);

        // 组装条目：缩略图 - 信息（单号+时间）
        historyItem.getChildren().addAll(thumbnail, infoBox);
        historyItem.setPadding(new Insets(10, 8, 10, 8));

        return historyItem;
    }

    /**
     * 创建缩略图
     */
    private ImageView createThumbnail(String barcode, String imagePath, String uploadUrl,
                                       String timestamp, Window mainWindow) {
        ImageView thumbnail = new ImageView();
        thumbnail.getStyleClass().add("history-thumbnail");
        thumbnail.setFitWidth(130);
        thumbnail.setFitHeight(90);
        thumbnail.setPreserveRatio(true);
        thumbnail.setCursor(Cursor.HAND); // 鼠标悬停显示手型

        // 加载缩略图：优先使用上传后的URL，如果URL无效则使用本地文件
        loadThumbnailImage(thumbnail, uploadUrl, imagePath);

        // 添加点击事件：显示放大图片对话框
        thumbnail.setOnMouseClicked(e -> {
            logger.info("点击缩略图，准备显示详情对话框: barcode={}", barcode);
            try {
                ImageDetailController.showImageDetailDialog(mainWindow, barcode,
                        imagePath, uploadUrl, timestamp);
            } catch (Exception ex) {
                logger.error("显示图片详情对话框时发生异常", ex);
            }
        });

        return thumbnail;
    }

    /**
     * 加载缩略图图片
     */
    private void loadThumbnailImage(ImageView thumbnail, String uploadUrl, String imagePath) {
        try {
            if (uploadUrl != null && !uploadUrl.trim().isEmpty()) {
                // 使用上传后的URL加载缩略图，确认文件已上传
                logger.info("使用上传后的URL加载缩略图: {}", uploadUrl);
                Image image = new Image(uploadUrl, 130, 90, true, true, true);
                thumbnail.setImage(image);
            } else {
                // 备用方案：使用本地文件
                logger.warn("上传URL为空，使用本地文件作为缩略图");
                File imageFile = new File(imagePath);
                if (imageFile.exists()) {
                    Image image = new Image(imageFile.toURI().toString(), 130, 90, true, true, true);
                    thumbnail.setImage(image);
                }
            }
        } catch (Exception e) {
            logger.warn("加载缩略图失败: {}, 尝试使用本地文件", e.getMessage());
            // 如果URL加载失败，尝试使用本地文件
            try {
                File imageFile = new File(imagePath);
                if (imageFile.exists()) {
                    Image image = new Image(imageFile.toURI().toString(), 130, 90, true, true, true);
                    thumbnail.setImage(image);
                }
            } catch (Exception e2) {
                logger.error("加载本地缩略图也失败: {}", e2.getMessage());
            }
        }
    }

    /**
     * 创建信息区域
     */
    private VBox createInfoBox(String barcode, String timestamp) {
        VBox infoBox = new VBox();
        infoBox.setSpacing(3.0);
        infoBox.setAlignment(Pos.CENTER);
        infoBox.setMaxWidth(Double.MAX_VALUE);

        // 单号标签（完整显示，支持换行）
        Label barcodeLabel = new Label(barcode);
        barcodeLabel.getStyleClass().add("history-barcode");
        barcodeLabel.setWrapText(true);
        barcodeLabel.setAlignment(Pos.CENTER);
        barcodeLabel.setTextAlignment(TextAlignment.CENTER);
        barcodeLabel.setMaxWidth(140);
        barcodeLabel.setMinHeight(Label.USE_PREF_SIZE);

        // 时间戳标签
        Label timeLabel = new Label(timestamp);
        timeLabel.getStyleClass().add("history-time");

        infoBox.getChildren().addAll(barcodeLabel, timeLabel);
        return infoBox;
    }

    /**
     * 清空历史记录
     */
    public void clearHistory() {
        Platform.runLater(() -> {
            if (successListContainer != null) {
                successListContainer.getChildren().clear();
            }
        });
    }
}

