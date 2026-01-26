package com.hylir.receipt.service;


import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.Mat;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class DocumentScannerTest {
    @Test
    public static void main(String[] args) {
        // 输入图像路径（你的回单照片）
        String imagePath = "D:\\hylir\\front-end\\receipt-capture-agent\\temp-images\\preview_raw_1769143672030.png";
        // 输出图像路径
        String outputPath = "D:\\hylir\\front-end\\receipt-capture-agent\\temp-images\\output_a4_corrected.png";

        // A4标准尺寸（300DPI）
        int a4Width = 2480;
        int a4Height = 3508;

        try {
            BufferedImage input = ImageIO.read(new File(imagePath));
            if (input == null) {
                System.err.println("无法加载图像: " + imagePath);
                return;
            }

            System.out.println("原始图像尺寸: " + input.getWidth() + " x " + input.getHeight());
            System.out.println("开始检测A4纸张...");

            org.bytedeco.opencv.opencv_core.Mat src = opencv_imgcodecs.imread(imagePath);

            Mat mat = A4PaperDetectorHighCamera.detectAndWarpA4(src);

            if (mat != null) {
                // 保存结果
                opencv_imgcodecs.imwrite(outputPath, mat);

                System.out.println("✅ A4 纸检测完成！");
                System.out.println("输出文件: " + outputPath);
                System.out.println("结果已保存至: " + outputPath);
            } else {
                System.out.println("未检测到有效的A4纸张");
            }

        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }


}
