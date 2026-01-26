package com.hylir.receipt.service.autocapture;

import com.hylir.receipt.service.A4PaperDetectorHighCamera;
import com.hylir.receipt.service.BarcodeRecognitionService;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.javacv.Frame;
import org.bytedeco.opencv.opencv_core.Mat;
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

            // 2. A4矫正
            BufferedImage correctedImage = performA4Correction(originalImage);
            if (correctedImage == null) {
                correctedImage = originalImage; // 如果矫正失败，使用原图
            }

            // 3. 条码识别
            String barcode = barcodeService.recognize(correctedImage, 3);
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
     */
    private BufferedImage performA4Correction(BufferedImage original) {
        try {
            // 转换为 Mat
            Frame frame = JAVA2D_CONVERTER.convert(original);
            Mat srcMat = MAT_CONVERTER.convert(frame);

            // A4 检测与矫正
            Mat correctedMat = A4PaperDetectorHighCamera.detectAndWarpA4(srcMat);

            if (correctedMat != null && !correctedMat.empty()) {
                // 转换回 BufferedImage
                Frame outFrame = MAT_CONVERTER.convert(correctedMat);
                BufferedImage corrected = JAVA2D_CONVERTER.getBufferedImage(outFrame, 1);
                
                // 释放 Mat 资源
                srcMat.close();
                correctedMat.close();
                
                return corrected;
            } else {
                srcMat.close();
                return null;
            }
        } catch (Exception e) {
            logger.warn("A4 矫正失败: {}", e.getMessage());
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