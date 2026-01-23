package com.hylir.receipt.service.barcode;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.common.GlobalHistogramBinarizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.util.*;

/**
 * ZXing 识别引擎
 * 
 * 独立的 ZXing 解码实现，包含：
 * - MultiFormatReader 配置
 * - 二值化方法（HybridBinarizer / GlobalHistogramBinarizer）
 * - ZXing 专属的图像预处理策略
 * - 旋转和重试逻辑
 * 
 * 该类不依赖 OpenCV、ZBar 或 pipeline 逻辑，可单独使用和测试。
 * 
 * @author shanghai pubing
 * @date 2025/01/20
 */
public class ZXingRecognitionEngine {

    private static final Logger logger = LoggerFactory.getLogger(ZXingRecognitionEngine.class);

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

    public ZXingRecognitionEngine() {
        this.reader = new MultiFormatReader();

        // 配置读取器 - 优化识别参数
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, SUPPORTED_FORMATS);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE); // 更努力地识别
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(DecodeHintType.ASSUME_GS1, Boolean.FALSE); // 不假设是 GS1 格式
        hints.put(DecodeHintType.PURE_BARCODE, Boolean.FALSE); // 不是纯条码图片，包含其他内容
        hints.put(DecodeHintType.ALSO_INVERTED, Boolean.TRUE); // 也尝试反转的图片

        reader.setHints(hints);
        
        logger.debug("ZXing 识别引擎已初始化");
    }

    /**
     * 解码条码（对外暴露的主要方法）
     * 
     * @param image 输入图像
     * @return 识别结果，如果失败返回 Optional.empty()
     */
    public Optional<String> decode(BufferedImage image) {
        if (image == null) {
            logger.warn("输入图片为空");
            return Optional.empty();
        }

        String result = recognizeBarcode(image);
        return result != null ? Optional.of(result) : Optional.empty();
    }

    /**
     * 解码条码（带重试和多种预处理策略）
     * 
     * @param image 输入图像
     * @param maxRetries 最大重试次数（旋转次数）
     * @return 识别结果，如果失败返回 Optional.empty()
     */
    public Optional<String> decodeWithRetry(BufferedImage image, int maxRetries) {
        if (image == null) {
            logger.warn("输入图片为空");
            return Optional.empty();
        }

        String result = recognizeBarcodeWithRetry(image, maxRetries);
        return result != null ? Optional.of(result) : Optional.empty();
    }

    /**
     * 从图片中识别条码（使用指定的二值化方法）
     *
     * @param image 图片缓冲对象
     * @param useHybridBinarizer 是否使用 HybridBinarizer（true）或 GlobalHistogramBinarizer（false）
     * @return 识别结果，如果失败返回 null
     */
    private String recognizeBarcode(BufferedImage image, boolean useHybridBinarizer) {
        if (image == null) {
            logger.warn("输入图片为空");
            return null;
        }

        try {
            // 转换为 ZXing 可处理的格式
            BufferedImageLuminanceSource source = new BufferedImageLuminanceSource(image);
            BinaryBitmap bitmap;
            
            if (useHybridBinarizer) {
                bitmap = new BinaryBitmap(new HybridBinarizer(source));
            } else {
                bitmap = new BinaryBitmap(new GlobalHistogramBinarizer(source));
            }

            // 尝试识别
            Result result = reader.decode(bitmap);

            String barcodeText = result.getText();
            BarcodeFormat format = result.getBarcodeFormat();

            logger.info("ZXing 条码识别成功: {} (格式: {}, 二值化方法: {})", 
                barcodeText, format, useHybridBinarizer ? "Hybrid" : "GlobalHistogram");

            return barcodeText;

        } catch (NotFoundException e) {
            return null; // 不记录日志，这是正常的失败情况
        } catch (Exception e) {
            logger.debug("ZXing 条码识别过程出错: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从图片中识别条码（尝试两种二值化方法）
     *
     * @param image 图片缓冲对象
     * @return 识别结果，如果失败返回 null
     */
    private String recognizeBarcode(BufferedImage image) {
        if (image == null) {
            logger.warn("输入图片为空");
            return null;
        }

        logger.debug("ZBar ZXing 开始识别条码，图片尺寸: {}x{}", image.getWidth(), image.getHeight());

        // 先尝试 HybridBinarizer
        String result = recognizeBarcode(image, true);
        if (result != null) {
            return result;
        }

        // 再尝试 GlobalHistogramBinarizer
        result = recognizeBarcode(image, false);
        if (result != null) {
            return result;
        }

        logger.debug("ZXing 使用标准方法未识别到条码");
        return null;
    }

    /**
     * 从图片中识别条码（带重试机制和多种预处理策略）
     *
     * @param image 图片缓冲对象
     * @param maxRetries 最大重试次数
     * @return 识别结果，如果失败返回 null
     */
    private String recognizeBarcodeWithRetry(BufferedImage image, int maxRetries) {
        if (image == null) {
            return null;
        }

        // 策略列表：每种策略都会尝试
        List<ImageProcessingStrategy> strategies = Arrays.asList(
            new ImageProcessingStrategy("原始图片", img -> img),
            new ImageProcessingStrategy("对比度增强", this::enhanceContrast),
            new ImageProcessingStrategy("锐化", this::sharpenImage),
            new ImageProcessingStrategy("灰度化+对比度", img -> enhanceContrast(convertToGrayscale(img))),
            new ImageProcessingStrategy("放大2倍", img -> scaleImage(img, 2.0)),
            new ImageProcessingStrategy("放大3倍", img -> scaleImage(img, 3.0)),
            new ImageProcessingStrategy("缩小0.5倍", img -> scaleImage(img, 0.5)),
            new ImageProcessingStrategy("反转颜色", this::invertImage),
            new ImageProcessingStrategy("反转+对比度", img -> enhanceContrast(invertImage(img)))
        );

        // 先尝试所有预处理策略
        for (ImageProcessingStrategy strategy : strategies) {
            try {
                BufferedImage processed = strategy.process(image);
                String result = recognizeBarcode(processed);
                if (result != null) {
                    logger.info("ZXing 使用策略 [{}] 识别成功: {}", strategy.name, result);
                    return result;
                }
            } catch (Exception e) {
                logger.debug("ZXing 策略 [{}] 处理失败: {}", strategy.name, e.getMessage());
            }
        }

        // 如果预处理都失败，尝试旋转
        for (int i = 0; i < maxRetries && i < 4; i++) {
            try {
                int angle = 90 * (i + 1);
                logger.debug("ZXing 尝试旋转图片 {} 度", angle);
                BufferedImage rotatedImage = rotateImage(image, angle);
                
                // 对旋转后的图片也尝试预处理
                for (ImageProcessingStrategy strategy : strategies) {
                    try {
                        BufferedImage processed = strategy.process(rotatedImage);
                        String result = recognizeBarcode(processed);
                        if (result != null) {
                            logger.info("ZXing 旋转 {} 度后使用策略 [{}] 识别成功: {}", angle, strategy.name, result);
                            return result;
                        }
                    } catch (Exception e) {
                        // 忽略单个策略失败
                    }
                }
            } catch (Exception e) {
                logger.debug("ZXing 旋转 {} 度失败: {}", 90 * (i + 1), e.getMessage());
            }
        }

        logger.debug("ZXing 所有识别策略均失败");
        return null;
    }

    /**
     * 图片处理策略接口
     */
    @FunctionalInterface
    private interface ImageProcessor {
        BufferedImage process(BufferedImage image);
    }

    /**
     * 图片处理策略
     */
    private static class ImageProcessingStrategy {
        final String name;
        final ImageProcessor processor;

        ImageProcessingStrategy(String name, ImageProcessor processor) {
            this.name = name;
            this.processor = processor;
        }

        BufferedImage process(BufferedImage image) {
            return processor.process(image);
        }
    }

    /**
     * 增强对比度
     */
    private BufferedImage enhanceContrast(BufferedImage image) {
        RescaleOp rescaleOp = new RescaleOp(1.2f, 10f, null);
        return rescaleOp.filter(image, null);
    }

    /**
     * 锐化图片
     */
    private BufferedImage sharpenImage(BufferedImage image) {
        BufferedImage sharpened = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());
        Graphics2D g2d = sharpened.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();
        return sharpened;
    }

    /**
     * 转换为灰度图
     */
    private BufferedImage convertToGrayscale(BufferedImage image) {
        BufferedImage gray = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2d = gray.createGraphics();
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();
        return gray;
    }

    /**
     * 缩放图片
     */
    private BufferedImage scaleImage(BufferedImage image, double scale) {
        int newWidth = (int) (image.getWidth() * scale);
        int newHeight = (int) (image.getHeight() * scale);
        BufferedImage scaled = new BufferedImage(newWidth, newHeight, image.getType());
        Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawImage(image, 0, 0, newWidth, newHeight, null);
        g2d.dispose();
        return scaled;
    }

    /**
     * 反转图片颜色（处理反色条码）
     */
    private BufferedImage invertImage(BufferedImage image) {
        BufferedImage inverted = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                int rgb = image.getRGB(x, y);
                int r = 255 - ((rgb >> 16) & 0xFF);
                int g = 255 - ((rgb >> 8) & 0xFF);
                int b = 255 - (rgb & 0xFF);
                inverted.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return inverted;
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
}

