package com.hylir.receipt;

import com.hylir.receipt.service.CameraService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Receipt Capture Agent 主应用类
 * 回单采集终端 - 高拍仪拍照和条码识别桌面应用
 *
 * @author shanghai pubing
 * @date 2025/01/19
 */
public class ReceiptCaptureApplication extends Application {

    private static final Logger logger = LoggerFactory.getLogger(ReceiptCaptureApplication.class);

    @Override
    public void start(Stage primaryStage) {
        System.out.println("=== start 方法开始执行 ==="); // 在这里设置断点测试
        try {
            // 确保在这里打断点测试
            System.out.println("JavaFX Thread: " + Thread.currentThread().getName());
            // 加载主界面 FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
            Parent root = loader.load();

            // 设置场景
            Scene scene = new Scene(root, 800, 600);
            scene.getStylesheets().add(getClass().getResource("/css/application.css").toExternalForm());

            // 配置主舞台
            primaryStage.setTitle("回单采集终端 - Receipt Capture Agent");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(600);
            primaryStage.setMinHeight(400);
            // 启动时默认最大化窗口，便于操作与预览
            primaryStage.setMaximized(true);
            primaryStage.show();

            logger.info("Receipt Capture Agent 启动成功");

        } catch (IOException e) {
            logger.error("启动应用失败", e);
            throw new RuntimeException("无法加载主界面", e);
        }
    }

    @Override
    public void stop() {
        logger.info("Receipt Capture Agent 关闭");
        // 关闭摄像头服务，释放资源
        CameraService.shutdown();
    }

    public static void main(String[] args) {
        System.out.println("=== 程序开始执行 ==="); // 在这里设置断点测试
        launch(args);
    }
}
