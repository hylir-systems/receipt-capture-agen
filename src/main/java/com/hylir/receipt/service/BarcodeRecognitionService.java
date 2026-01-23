package com.hylir.receipt.service;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.*;

/**
 * 条码识别服务类
 * 使用 ZXing 库识别一维码（送货单号）
 *
 * @author shanghai pubing
 * @date 2025/01/19
 * @version 1.0.0
 */
public class BarcodeRecognitionService {

    private static final Logger logger = LoggerFactory.getLogger(BarcodeRecognitionService.class);

    // 支持的条码格式（主要是一维码）
    private static final List<BarcodeFormat> SUPPORTED_FORMATS = Arrays.asList(
        BarcodeFormat.CODE_128,
        BarcodeFormat.CODE_39,
        BarcodeFormat.EAN_13,
        BarcodeFormat.UPC_A,
        BarcodeFormat.CODE_93,
        BarcodeFormat.ITF,
        BarcodeFormat.CODABAR
    );

    private final MultiFormatReader reader;

    public BarcodeRecognitionService() {
        this.reader = new MultiFormatReader();

        // 配置读取器
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, SUPPORTED_FORMATS);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE); // 更努力地识别
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");

        reader.setHints(hints);
    }

    /**
     * 从图片中识别条码
     *
     * @param image 图片缓冲对象
     * @return 识别结果，如果失败返回 null
     */
    public String recognizeBarcode(BufferedImage image) {
        if (image == null) {
            logger.warn("输入图片为空");
            return null;
        }

        try {
            logger.debug("开始识别条码，图片尺寸: {}x{}", image.getWidth(), image.getHeight());

            // 转换为 ZXing 可处理的格式
            BufferedImageLuminanceSource source = new BufferedImageLuminanceSource(image);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

            // 尝试识别
            Result result = reader.decode(bitmap);

            String barcodeText = result.getText();
            BarcodeFormat format = result.getBarcodeFormat();

            logger.info("条码识别成功: {} (格式: {})", barcodeText, format);

            return barcodeText;

        } catch (NotFoundException e) {
            logger.warn("未在图片中找到条码");
            return null;
        } catch (Exception e) {
            logger.error("条码识别过程出错", e);
            return null;
        }
    }

    /**
     * 从图片中识别条码（带重试机制）
     *
     * @param image 图片缓冲对象
     * @param maxRetries 最大重试次数
     * @return 识别结果，如果失败返回 null
     */
    public String recognizeBarcodeWithRetry(BufferedImage image, int maxRetries) {
        String result = recognizeBarcode(image);

        if (result != null || maxRetries <= 0) {
            return result;
        }

        // 尝试不同的预处理方式
        for (int i = 0; i < maxRetries; i++) {
            try {
                logger.debug("重试识别条码，次数: {}", i + 1);

                // 尝试旋转图片
                BufferedImage rotatedImage = rotateImage(image, 90 * (i + 1));
                result = recognizeBarcode(rotatedImage);

                if (result != null) {
                    logger.info("旋转后识别成功: {}", result);
                    return result;
                }

            } catch (Exception e) {
                logger.warn("重试识别失败", e);
            }
        }

        return null;
    }

    /**
     * 验证识别结果是否为有效的送货单号
     * 可以根据业务规则进行验证
     *
     * @param barcodeText 识别的条码文本
     * @return 是否有效
     */
    public boolean isValidReceiptNumber(String barcodeText) {
        if (barcodeText == null || barcodeText.trim().isEmpty()) {
            return false;
        }

        // 基本长度检查（假设送货单号至少6位）
        if (barcodeText.length() < 6) {
            return false;
        }

        // 检查是否包含字母数字（根据业务需求调整）
        return barcodeText.matches("[A-Za-z0-9]+");
    }

    /**
     * 旋转图片
     *
     * @param image 原始图片
     * @param angle 旋转角度（度）
     * @return 旋转后的图片
     */
    private BufferedImage rotateImage(BufferedImage image, double angle) {
        double radian = Math.toRadians(angle);
        double sin = Math.abs(Math.sin(radian));
        double cos = Math.abs(Math.cos(radian));

        int width = image.getWidth();
        int height = image.getHeight();
        int newWidth = (int) Math.floor(width * cos + height * sin);
        int newHeight = (int) Math.floor(height * cos + width * sin);

        BufferedImage rotated = new BufferedImage(newWidth, newHeight, image.getType());
        Graphics2D g2d = rotated.createGraphics();
        g2d.translate((newWidth - width) / 2, (newHeight - height) / 2);
        g2d.rotate(radian, width / 2, height / 2);
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();

        return rotated;
    }

    /**
     * 清理资源
     */
    public void close() {
        // ZXing reader 不需要特殊清理
        logger.debug("条码识别服务已关闭");
    }
}
