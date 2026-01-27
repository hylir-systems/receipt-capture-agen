package com.hylir.receipt.service;

import com.hylir.receipt.service.barcode.AsposeRecognitionEngine;
import com.hylir.receipt.service.barcode.ZXingRecognitionEngine;
import com.hylir.receipt.config.AppConfig;
import com.hylir.receipt.service.camera.CameraConstants;
import com.hylir.receipt.util.TempFileCleaner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 条码识别服务
 * <p>
 * 使用 ZXing 进行条码识别
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
        logger.info("条码识别服务已初始化（Aspose）");
        logger.info("条码识别服务已初始化（ZXing）");
    }

    private BufferedImage cropTopRightQuarter(BufferedImage src) {
        int width = src.getWidth();
        int height = src.getHeight();

        int x = width * 2 / 3;   // 右侧 1/3 起点
        int y = 0;

        int w = width / 3;
        int h = height / 3;

        // 防御式保护（避免极端分辨率）
        w = Math.min(w, width - x);
        h = Math.min(h, height - y);

        return src.getSubimage(x, y, w, h);
    }


    /**
     * 识别条码
     *
     * @param image      图片
     * @param maxRetries 重试次数
     * @return 识别结果，失败返回 null
     */
    public String recognize(BufferedImage image, int maxRetries) {
        if (image == null) {
            logger.warn("输入图片为空");
            return null;
        }

        // 生成裁剪图（右上角 1/3*1/3），并按需落盘调试
        BufferedImage croppedImage = cropTopRightQuarter(image);
        if (AppConfig.isBarcodeDebugSaveImagesEnabled()) {
            try {
                Path debugDir = Paths.get(CameraConstants.TEMP_DIR, "barcode-debug");
                Files.createDirectories(debugDir);
                TempFileCleaner.cleanupExpiredFiles(debugDir, AppConfig.getBarcodeDebugKeepMinutes());

                String suffix = System.currentTimeMillis() + "_" + Thread.currentThread().getId();
                File origFile = debugDir.resolve("barcode_orig_" + suffix + ".png").toFile();
                File cropFile = debugDir.resolve("barcode_crop_tr_" + suffix + ".png").toFile();

                ImageIO.write(image, "PNG", origFile);
                ImageIO.write(croppedImage, "PNG", cropFile);
                logger.info("条码调试图片已保存: orig={}, crop={}", origFile.getAbsolutePath(), cropFile.getAbsolutePath());
            } catch (Exception e) {
                // 调试能力不应影响主流程
                logger.warn("保存条码调试图片失败（将继续识别）: {}", e.getMessage());
            }
        }
        
        // 策略1: 先识别裁剪区域（更快，约0.8秒）
        long t0 = System.nanoTime();
        List<String> croppedResults = asposeEngine.recognize(croppedImage);
        long t1 = System.nanoTime();
        double croppedTime = (t1 - t0) / 1_000_000.0;
        logger.info("Aspose 识别耗时(右上角1/3*1/3): {} ms, results={}", croppedTime, croppedResults.size());
        if (croppedResults.size() > 0) {
            String result = croppedResults.get(0);
            if (result != null && isValidReceiptNumber(result)) {
                logger.info("Aspose 识别成功(裁剪区域): {}, 总耗时: {} ms", result, croppedTime);
                return result;
            }
        }
        // 策略2: 裁剪区域失败，识别全图（较慢，约4秒）
        long t2 = System.nanoTime();
        List<String> fullResults = asposeEngine.recognize(image);
        long t3 = System.nanoTime();
        double fullTime = (t3 - t2) / 1_000_000.0;
        logger.info("Aspose 识别耗时(全图): {} ms, results={}", fullTime, fullResults.size());
        
        if (fullResults.size() > 0) {
            String result = fullResults.get(0);
            if (result != null && isValidReceiptNumber(result)) {
                logger.info("Aspose 识别成功(全图): {}, 总耗时: {} ms", result, croppedTime + fullTime);
                return result;
            }
        }
        
        // 策略3: Aspose 都失败，使用 ZXing
        String result = zxingEngine.decodeWithRetry(image, maxRetries).orElse(null);
        if (result != null && isValidReceiptNumber(result)) {
            logger.info("ZXing 识别成功: {}, 总耗时: {} ms", result, croppedTime + fullTime);
            return result;
        }
        
        logger.warn("所有识别策略均失败，总耗时: {} ms", croppedTime + fullTime);
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
