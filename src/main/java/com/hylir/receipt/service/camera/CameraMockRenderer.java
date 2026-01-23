package com.hylir.receipt.service.camera;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.Font;

/**
 * 摄像头模拟渲染器
 * 负责绘制模拟回单图像
 */
public class CameraMockRenderer {
    private static final Logger logger = LoggerFactory.getLogger(CameraMockRenderer.class);

    /**
     * 绘制模拟回单图像
     *
     * @param deviceIndex 设备索引
     * @param resolution 分辨率
     * @return 绘制好的模拟图像
     */
    public BufferedImage renderMockReceipt(int deviceIndex, int resolution) {
        logger.info("开始绘制模拟回单图像，设备索引: {}, 分辨率: {} DPI", deviceIndex, resolution);
        
        // 创建一个模拟的扫描图片
        BufferedImage testImage = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = testImage.createGraphics();

        try {
            // 填充白色背景
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, 800, 600);

            // 添加边框
            g2d.setColor(Color.BLACK);
            g2d.drawRect(20, 20, 760, 560);

            // 添加标题 - 适合摄像头照片
            g2d.setFont(getChineseFont(Font.BOLD, 24));
            g2d.setColor(Color.BLACK);
            g2d.drawString("回单照片", 50, 80);

            // 添加一些模拟信息
            g2d.setFont(getChineseFont(Font.PLAIN, 16));
            g2d.setColor(Color.DARK_GRAY);
            g2d.drawString("拍摄时间: " + new java.util.Date().toString(), 50, 120);
            g2d.drawString("设备索引: " + deviceIndex, 50, 150);
            g2d.drawString("分辨率: 800×600", 50, 180);

            // 添加一个简单的二维码模拟（用于识别）
            drawMockQRCode(g2d);

            logger.info("模拟回单图像绘制完成");
            return testImage;
        } finally {
            g2d.dispose();
        }
    }

    /**
     * 绘制模拟二维码
     *
     * @param g2d 图形上下文
     */
    private void drawMockQRCode(Graphics2D g2d) {
        g2d.setColor(Color.BLACK);
        int qrSize = 80;
        int qrX = 500;
        int qrY = 100;

        // 绘制QR码的外框
        g2d.fillRect(qrX, qrY, qrSize, qrSize);

        // 绘制QR码的内部图案（简化版）
        g2d.setColor(Color.WHITE);
        g2d.fillRect(qrX + 10, qrY + 10, qrSize - 20, qrSize - 20);

        // 添加定位角
        g2d.setColor(Color.BLACK);
        g2d.fillRect(qrX + 5, qrY + 5, 10, 10);
        g2d.fillRect(qrX + qrSize - 15, qrY + 5, 10, 10);
        g2d.fillRect(qrX + 5, qrY + qrSize - 15, 10, 10);

        // 添加说明文字
        g2d.setFont(getChineseFont(Font.PLAIN, 12));
        g2d.setColor(Color.GRAY);
        g2d.drawString("二维码用于自动识别", qrX - 10, qrY + qrSize + 20);
    }

    /**
     * 获取支持中文的字体
     *
     * @param style 字体样式
     * @param size 字体大小
     * @return 支持中文的字体
     */
    public Font getChineseFont(int style, int size) {
        // 尝试使用各种中文字体
        String[] chineseFonts = {"微软雅黑", "宋体", "黑体", "楷体", "仿宋", "Arial Unicode MS", "SimSun", "SimHei"};

        for (String fontName : chineseFonts) {
            try {
                Font font = new Font(fontName, style, size);
                // 检查字体是否真的加载成功（不是fallback字体）
                if (font.getFamily().toLowerCase().contains(fontName.toLowerCase().substring(0, 2)) ||
                    fontName.equals("Arial Unicode MS")) {
                    return font;
                }
            } catch (Exception e) {
                // 忽略字体加载错误，继续尝试下一个
            }
        }

        // 如果所有中文字体都不可用，使用默认字体
        logger.warn("未找到合适的中文字体，使用默认字体");
        return new Font("Arial", style, size);
    }
}
