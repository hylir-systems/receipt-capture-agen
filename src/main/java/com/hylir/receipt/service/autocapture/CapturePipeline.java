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

            // 4. 条码识别
            String barcode = barcodeService.recognize(originalImage, 3);
            if (barcode == null || barcode.trim().isEmpty()) {
                return CaptureResult.failure("条码识别失败");
            }

            // 5. 去重
            if (deduplicator.isDuplicate(barcode)) {
                return CaptureResult.duplicate(barcode);
            }

            // 6. 保存文件
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
     * 裁剪右上角区域（条码区域）
     * A4纸标准尺寸：210mm x 297mm
     * 条码区域：宽约8cm（80mm），高约4cm（40mm），位于右上角
     * 注意：条码在打印时间和标题下方，需要从顶部稍微下移
     *
     * @param image A4矫正后的图像
     * @return 裁剪后的右上角区域图像
     */
    private BufferedImage cropTopRightRegion(BufferedImage image) {
        if (image == null) {
            return null;
        }

        try {
            int imgWidth = image.getWidth();
            int imgHeight = image.getHeight();

            logger.debug("原图尺寸: {}x{}", imgWidth, imgHeight);

            // A4纸标准尺寸比例：宽210mm，高297mm
            // 条码区域：宽80mm（占宽度的 80/210 ≈ 38.1%），高40mm（占高度的 40/297 ≈ 13.5%）
            // 但条码在打印时间和标题下方，需要从顶部下移约5-8%（给标题留空间）

            // 计算裁剪区域（基于实际图像尺寸）
            // 宽度：图像宽度的约40%（条码宽度8cm）
            int cropWidth = (int) (imgWidth * 0.40);
            // 高度：图像高度的约15%（条码高度4cm，确保包含条码）
            int cropHeight = (int) (imgHeight * 0.2);

            // 确保最小尺寸（避免裁剪区域太小）
            cropWidth = Math.max(cropWidth, 300); // 最小宽度300像素
            cropHeight = Math.max(cropHeight, 150); // 最小高度150像素

            // 右上角位置：x = 图像宽度 - 裁剪宽度，y = 从顶部下移约6%（给打印时间和标题留空间）
            int x = Math.max(0, imgWidth - cropWidth);
            int y = Math.max(0, (int) (imgHeight * 0.06)); // 从顶部下移6%

            // 确保裁剪区域不超出图像边界
            if (x + cropWidth > imgWidth) {
                cropWidth = imgWidth - x;
            }
            if (y + cropHeight > imgHeight) {
                cropHeight = imgHeight - y;
            }

            // 最终边界检查：确保裁剪区域有效且足够大
            if (cropWidth <= 0 || cropHeight <= 0 || x < 0 || y < 0 ||
                    x + cropWidth > imgWidth || y + cropHeight > imgHeight) {
                logger.error("裁剪区域计算错误: 原图={}x{}, 裁剪区域=({},{}) 尺寸={}x{}",
                        imgWidth, imgHeight, x, y, cropWidth, cropHeight);
                // 使用安全的默认值
                cropWidth = Math.min(imgWidth / 3, 500);
                cropHeight = Math.min(imgHeight / 5, 200);
                x = Math.max(0, imgWidth - cropWidth);
                y = Math.max(0, Math.min((int) (imgHeight * 0.06), imgHeight - cropHeight));
            }

            logger.debug("裁剪区域: 原图={}x{}, 裁剪区域=({},{}) 尺寸={}x{}",
                    imgWidth, imgHeight, x, y, cropWidth, cropHeight);

            // 裁剪图像
            BufferedImage cropped = image.getSubimage(x, y, cropWidth, cropHeight);

            // 创建新的BufferedImage，确保类型正确
            BufferedImage result = new BufferedImage(cropWidth, cropHeight, BufferedImage.TYPE_INT_RGB);
            result.getGraphics().drawImage(cropped, 0, 0, null);

            logger.debug("裁剪右上角区域: 原图尺寸={}x{}, 裁剪区域=({},{}) 尺寸={}x{}",
                    imgWidth, imgHeight, x, y, cropWidth, cropHeight);

            return result;
        } catch (Exception e) {
            logger.warn("裁剪右上角区域失败: {}", e.getMessage());
            return null;
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

        public boolean isSuccess() {
            return success;
        }

        public boolean isDuplicate() {
            return duplicate;
        }

        public String getBarcode() {
            return barcode;
        }

        public String getFilePath() {
            return filePath;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}