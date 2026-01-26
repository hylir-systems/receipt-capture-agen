package com.hylir.receipt.service.autocapture;

import com.hylir.receipt.service.A4PaperDetectorHighCamera;
import com.hylir.receipt.service.BarcodeRecognitionService;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.javacv.Frame;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 单帧处理管道：
 * 1. A4矫正
 * 2. 条码识别
 * 3. 去重
 * 4. 保存
 */
public class CapturePipeline {

    private static final Logger logger = LoggerFactory.getLogger(CapturePipeline.class);
    private static final Java2DFrameConverter JAVA2D_CONVERTER = new Java2DFrameConverter();
    private static final OpenCVFrameConverter.ToMat MAT_CONVERTER = new OpenCVFrameConverter.ToMat();

    private final BarcodeRecognitionService barcodeService;
    private final BarcodeDeduplicator deduplicator;
    private final File outputDir;

    public CapturePipeline(BarcodeRecognitionService barcodeService, File outputDir) {
        this.barcodeService = barcodeService;
        this.deduplicator = new BarcodeDeduplicator();
        this.outputDir = outputDir;
        outputDir.mkdirs();
    }

    public CaptureResult processFrame(int[] pixels, int width, int height) {
        try {
            // 1. 将 int[] pixels 转换为 BufferedImage
            BufferedImage originalImage = pixelsToBufferedImage(pixels, width, height);
            if (originalImage == null) {
                return CaptureResult.failure("无法转换像素数组为图像");
            }

            // 2. A4矫正（直接使用 pixels，避免不必要的转换）
            BufferedImage correctedImage = performA4Correction(pixels, width, height);
            if (correctedImage == null) {
                correctedImage = originalImage; // 如果矫正失败，使用原图
            }

            // 3. 条码识别
            String barcode = barcodeService.recognize(originalImage, 3);
            if (barcode == null || barcode.trim().isEmpty()) {
                return CaptureResult.failure("条码识别失败");
            }

            // 4. 去重
            if (deduplicator.isDuplicate(barcode)) {
                return CaptureResult.duplicate(barcode);
            }

            // 5. 保存文件
            File outputFile = new File(outputDir, barcode + ".png");
            ImageIO.write(correctedImage, "PNG", outputFile);

            return CaptureResult.success(barcode, outputFile.getAbsolutePath());

        } catch (Exception e) {
            logger.error("处理帧失败", e);
            return CaptureResult.failure(e.getMessage());
        }
    }

    /**
     * 将 int[] pixels 转换为 BufferedImage
     */
    private BufferedImage pixelsToBufferedImage(int[] pixels, int width, int height) {
        try {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            image.setRGB(0, 0, width, height, pixels, 0, width);
            return image;
        } catch (Exception e) {
            logger.error("转换像素数组失败", e);
            return null;
        }
    }

    /**
     * 执行 A4 矫正
     * 直接从 pixels 转换为 Mat，避免先转换为 BufferedImage
     */
    private BufferedImage performA4Correction(int[] pixels, int width, int height) {
        Mat srcMat = null;
        try {
            // 直接将 pixels 转换为 Mat (BGR 格式)
            srcMat = pixelsToMat(pixels, width, height);
            if (srcMat == null || srcMat.empty()) {
                return null;
            }

            // A4 检测与矫正
            Mat correctedMat = A4PaperDetectorHighCamera.detectAndWarpA4(srcMat);

            if (correctedMat != null && !correctedMat.empty()) {
                // 转换回 BufferedImage
                Frame outFrame = MAT_CONVERTER.convert(correctedMat);
                BufferedImage corrected = JAVA2D_CONVERTER.getBufferedImage(outFrame, 1);
                
                // 释放 Mat 资源
                correctedMat.close();
                
                return corrected;
            } else {
                return null;
            }
        } catch (Exception e) {
            logger.warn("A4 矫正失败: {}", e.getMessage());
            return null;
        } finally {
            // 确保释放 Mat 资源
            if (srcMat != null) {
                srcMat.close();
            }
        }
    }
    
    /**
     * 将 int[] pixels (ARGB格式) 直接转换为 OpenCV Mat (BGR格式)
     */
    private Mat pixelsToMat(int[] pixels, int width, int height) {
        try {
            // 创建 Mat，类型为 CV_8UC3 (BGR)
            Mat mat = new Mat(height, width, org.bytedeco.opencv.global.opencv_core.CV_8UC3);
            UByteIndexer indexer = mat.createIndexer();
            
            // 将 ARGB 格式的 pixels 转换为 BGR 格式并写入 Mat
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int idx = y * width + x;
                    int pixel = pixels[idx];
                    
                    // 提取 ARGB 分量
                    int a = (pixel >> 24) & 0xFF;
                    int r = (pixel >> 16) & 0xFF;
                    int g = (pixel >> 8) & 0xFF;
                    int b = pixel & 0xFF;
                    
                    // 如果 alpha < 255，进行 alpha 混合（假设背景为白色）
                    if (a < 255) {
                        float alpha = a / 255.0f;
                        r = (int) (r * alpha + 255 * (1 - alpha));
                        g = (int) (g * alpha + 255 * (1 - alpha));
                        b = (int) (b * alpha + 255 * (1 - alpha));
                    }
                    
                    // OpenCV Mat 使用 BGR 格式，写入顺序为 B, G, R
                    indexer.put(y, x, 0, b); // B
                    indexer.put(y, x, 1, g); // G
                    indexer.put(y, x, 2, r); // R
                }
            }
            
            indexer.close();
            return mat;
        } catch (Exception e) {
            logger.error("转换 pixels 到 Mat 失败", e);
            return null;
        }
    }

    public static class CaptureResult {
        private final boolean success;
        private final boolean duplicate;
        private final String barcode;
        private final String filePath;
        private final String errorMessage;

        private CaptureResult(boolean success, boolean duplicate,
                              String barcode, String filePath, String errorMessage) {
            this.success = success;
            this.duplicate = duplicate;
            this.barcode = barcode;
            this.filePath = filePath;
            this.errorMessage = errorMessage;
        }

        public static CaptureResult success(String barcode, String filePath) {
            return new CaptureResult(true, false, barcode, filePath, null);
        }

        public static CaptureResult duplicate(String barcode) {
            return new CaptureResult(false, true, barcode, null, null);
        }

        public static CaptureResult failure(String msg) {
            return new CaptureResult(false, false, null, null, msg);
        }

        public boolean isSuccess() { return success; }
        public boolean isDuplicate() { return duplicate; }
        public String getBarcode() { return barcode; }
        public String getFilePath() { return filePath; }
        public String getErrorMessage() { return errorMessage; }
    }
}