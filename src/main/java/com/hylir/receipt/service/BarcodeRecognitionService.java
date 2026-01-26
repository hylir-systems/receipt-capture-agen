package com.hylir.receipt.service;

import com.hylir.receipt.service.barcode.ZXingRecognitionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;

/**
 * 条码识别服务
 *
 * 使用 ZXing 进行条码识别
 *
 * @author shanghai pubing
 * @date 2025/01/19
 */
public class BarcodeRecognitionService {

    private static final Logger logger = LoggerFactory.getLogger(BarcodeRecognitionService.class);

    private final ZXingRecognitionEngine zxingEngine;

    public BarcodeRecognitionService() {
        this.zxingEngine = new ZXingRecognitionEngine();
        logger.info("条码识别服务已初始化（ZXing）");
    }

    /**
     * 识别条码
     *
     * @param image 图片
     * @param maxRetries 重试次数
     * @return 识别结果，失败返回 null
     */
    public String recognize(BufferedImage image, int maxRetries) {
        if (image == null) {
            logger.warn("输入图片为空");
            return null;
        }

        String result = zxingEngine.decodeWithRetry(image, maxRetries).orElse(null);
        if (result != null && isValidReceiptNumber(result)) {
            logger.info("ZXing 识别成功: {}", result);
            return result;
        }

        logger.warn("识别失败");
        return null;
    }

    /**
     * 验证是否为有效的送货单号
     */
    public boolean isValidReceiptNumber(String barcodeText) {
        if (barcodeText == null || barcodeText.trim().isEmpty()) {
            return false;
        }
        if (barcodeText.length() < 6) {
            return false;
        }
        return barcodeText.matches("[A-Za-z0-9]+");
    }

    public void close() {
        logger.debug("条码识别服务已关闭");
    }
}
