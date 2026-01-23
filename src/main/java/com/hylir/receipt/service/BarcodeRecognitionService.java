package com.hylir.receipt.service;

import com.hylir.receipt.service.barcode.AsposeRecognitionEngine;
import com.hylir.receipt.service.barcode.ZXingRecognitionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * 条码识别服务
 *
 * Pipeline: Aspose 主识别 → ZXing Fallback
 *
 * @author shanghai pubing
 * @date 2025/01/19
 */
public class BarcodeRecognitionService {

    private static final Logger logger = LoggerFactory.getLogger(BarcodeRecognitionService.class);

    private final AsposeRecognitionEngine asposeEngine;
    private final ZXingRecognitionEngine zxingEngine;

    public BarcodeRecognitionService() {
        this.asposeEngine = new AsposeRecognitionEngine();
        this.zxingEngine = new ZXingRecognitionEngine();
        logger.info("条码识别服务已初始化（Aspose → ZXing Fallback）");
    }

    /**
     * 识别条码
     *
     * @param image 图片
     * @param maxRetries ZXing 重试次数
     * @return 识别结果，失败返回 null
     */
    public String recognize(BufferedImage image, int maxRetries) {
        if (image == null) {
            logger.warn("输入图片为空");
            return null;
        }

        // Stage 1: Aspose 主识别
        List<String> asposeResults = asposeEngine.recognize(image);
        for (String result : asposeResults) {
            if (isValidReceiptNumber(result)) {
                logger.info("Aspose 识别成功: {}", result);
                return result;
            }
        }
        logger.debug("Aspose 识别未找到有效条码");

        // Stage 2: ZXing Fallback
        String zxingResult = String.valueOf(zxingEngine.decodeWithRetry(image, maxRetries));
        if (zxingResult != null && isValidReceiptNumber(zxingResult)) {
            logger.info("ZXing Fallback 识别成功: {}", zxingResult);
            return zxingResult;
        }

        logger.warn("所有识别引擎均失败");
        return null;
    }

    /**
     * 识别条码（默认重试3次）
     */
    public String recognize(BufferedImage image) {
        return recognize(image, 3);
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

