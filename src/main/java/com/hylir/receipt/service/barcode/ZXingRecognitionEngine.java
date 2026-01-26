package com.hylir.receipt.service.barcode;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.util.*;

/**
 * ZXing 识别引擎（严格参照 Code128Reader.java）
 *
 * @author shanghai pubing
 * @date 2025/01/20
 */
public class ZXingRecognitionEngine {

    private static final Logger logger = LoggerFactory.getLogger(ZXingRecognitionEngine.class);

    public ZXingRecognitionEngine() {
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

        String result = tryMultipleMethods(image);
        if (result != null) {
            logger.info("ZXing 识别成功: {}", result);
            return Optional.of(result);
        }

        return Optional.empty();
    }

    /**
     * 解码条码（带重试）
     *
     * @param image 输入图像
     * @param maxRetries 最大重试次数
     * @return 识别结果，如果失败返回 Optional.empty()
     */
    public Optional<String> decodeWithRetry(BufferedImage image, int maxRetries) {
        if (image == null) {
            logger.warn("输入图片为空");
            return Optional.empty();
        }

        // 先尝试标准方法
        String result = tryMultipleMethods(image);
        if (result != null) {
            return Optional.of(result);
        }

        // 如果标准方法失败，尝试增强处理
        result = tryEnhancedProcessing(image);
        if (result != null) {
            return Optional.of(result);
        }

        logger.warn("ZXing 所有识别策略均失败");
        return Optional.empty();
    }

    /**
     * 尝试多种识别方法（严格参照 Code128Reader.java 的 tryMultipleMethods）
     */
    private String tryMultipleMethods(BufferedImage image) {
        if (image == null) {
            return null;
        }

        // 方法列表（仅Code128优先，因为这是最可能成功的策略）
        List<DecodeStrategy> strategies = Arrays.asList(
                new DecodeStrategy("仅Code128", createCode128OnlyHints())
        );

//        ,
//        new DecodeStrategy("标准方法", createStandardHints()),
//                new DecodeStrategy("纯条码模式", createPureBarcodeHints()),
//                new DecodeStrategy("宽松模式", createLooseHints())
        // 尝试每个方法
        for (DecodeStrategy strategy : strategies) {
            try {
                logger.debug("尝试方法: {}", strategy.name);
                String result = decodeWithStrategy(image, strategy.hints);
                if (result != null && !result.trim().isEmpty()) {
                    logger.info("方法成功: {} -> {}", strategy.name, result);
                    return result;
                }
            } catch (Exception e) {
                logger.debug("方法失败: {} - {}", strategy.name, e.getMessage());
            }
        }

        return null;
    }

    /**
     * 使用特定策略解码（严格参照 Code128Reader.java 的 decodeWithStrategy）
     */
    private String decodeWithStrategy(BufferedImage image, Map<DecodeHintType, Object> hints) {
        // 尝试多种二值化器
        Binarizer[] binarizers = {
                new HybridBinarizer(new BufferedImageLuminanceSource(image)),
                new GlobalHistogramBinarizer(new BufferedImageLuminanceSource(image))
        };

        for (Binarizer binarizer : binarizers) {
            try {
                BinaryBitmap bitmap = new BinaryBitmap(binarizer);
                Result result = new MultiFormatReader().decode(bitmap, hints);

                // 验证结果
                String text = result.getText();
                if (isValidCode128(text)) {
                    return text;
                }
            } catch (NotFoundException e) {
                // 继续尝试下一个二值化器
            }
        }

        throw new RuntimeException("未找到条码");
    }

    /**
     * 验证 Code128 条码内容（严格参照 Code128Reader.java）
     */
    private boolean isValidCode128(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        // 根据你的条码模式验证
        // 你的条码类似: X202601200000093601

        // 简单验证：长度通常在 10-30 之间
        return text.length() >= 10 && text.length() <= 30;
    }

    /**
     * 尝试增强处理（严格参照 Code128Reader.java 的 tryEnhancedProcessing）
     */
    private String tryEnhancedProcessing(BufferedImage original) {
        try {
            // 1. 转换为灰度
            BufferedImage gray = convertToGrayscale(original);

            // 2. 尝试不同预处理方法
            BufferedImage[] processedImages = {
                    gray,
                    enhanceContrast(gray),
                    binarizeOtsu(gray),
                    scaleImage(gray, 1.5),
                    scaleImage(gray, 2.0)
            };

            // 3. 对每个处理后的图像尝试解码
            Map<DecodeHintType, Object> hints = createCode128OnlyHints();

            for (int i = 0; i < processedImages.length; i++) {
                try {
                    logger.debug("尝试增强处理 #{}", i + 1);
                    String result = decodeWithStrategy(processedImages[i], hints);
                    if (result != null && isValidCode128(result)) {
                        logger.info("增强处理成功: {}", result);
                        return result;
                    }
                } catch (Exception e) {
                    logger.debug("增强处理 #{} 失败: {}", i + 1, e.getMessage());
                }
            }

        } catch (Exception e) {
            logger.debug("增强处理过程出错: {}", e.getMessage());
        }

        return null;
    }

    // ========== 图像处理方法（严格参照 Code128Reader.java）==========

    /**
     * 转换为灰度图
     */
    private BufferedImage convertToGrayscale(BufferedImage original) {
        BufferedImage gray = new BufferedImage(
                original.getWidth(), original.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        gray.getGraphics().drawImage(original, 0, 0, null);
        return gray;
    }

    /**
     * 增强对比度
     */
    private BufferedImage enhanceContrast(BufferedImage image) {
        // 简单的对比度增强
        BufferedImage enhanced = new BufferedImage(
                image.getWidth(), image.getHeight(), image.getType());

        int[] histogram = new int[256];
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y) & 0xFF;
                histogram[pixel]++;
            }
        }

        // 找到有效范围
        int min = 0, max = 255;
        while (min < 255 && histogram[min] == 0) min++;
        while (max > 0 && histogram[max] == 0) max--;

        if (min >= max) return image;

        // 拉伸对比度
        double scale = 255.0 / (max - min);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y) & 0xFF;
                int newPixel = (int) ((pixel - min) * scale);
                if (newPixel < 0) newPixel = 0;
                if (newPixel > 255) newPixel = 255;
                enhanced.setRGB(x, y, (newPixel << 16) | (newPixel << 8) | newPixel);
            }
        }

        return enhanced;
    }

    /**
     * Otsu 二值化
     */
    private BufferedImage binarizeOtsu(BufferedImage gray) {
        // Otsu 二值化
        int[] histogram = new int[256];
        for (int y = 0; y < gray.getHeight(); y++) {
            for (int x = 0; x < gray.getWidth(); x++) {
                int pixel = gray.getRGB(x, y) & 0xFF;
                histogram[pixel]++;
            }
        }

        // Otsu 算法求最佳阈值
        int total = gray.getWidth() * gray.getHeight();
        double sum = 0;
        for (int i = 0; i < 256; i++) sum += i * histogram[i];

        double sumB = 0;
        int wB = 0;
        int wF;

        double maxVariance = 0;
        int threshold = 0;

        for (int i = 0; i < 256; i++) {
            wB += histogram[i];
            if (wB == 0) continue;

            wF = total - wB;
            if (wF == 0) break;

            sumB += i * histogram[i];

            double mB = sumB / wB;
            double mF = (sum - sumB) / wF;

            double variance = wB * wF * (mB - mF) * (mB - mF);
            if (variance > maxVariance) {
                maxVariance = variance;
                threshold = i;
            }
        }

        // 应用阈值
        BufferedImage binary = new BufferedImage(
                gray.getWidth(), gray.getHeight(), BufferedImage.TYPE_BYTE_BINARY);

        for (int y = 0; y < gray.getHeight(); y++) {
            for (int x = 0; x < gray.getWidth(); x++) {
                int pixel = gray.getRGB(x, y) & 0xFF;
                int newPixel = pixel > threshold ? 255 : 0;
                binary.setRGB(x, y, (newPixel << 16) | (newPixel << 8) | newPixel);
            }
        }

        return binary;
    }

    /**
     * 缩放图片
     */
    private BufferedImage scaleImage(BufferedImage original, double scale) {
        int newWidth = (int) (original.getWidth() * scale);
        int newHeight = (int) (original.getHeight() * scale);

        BufferedImage scaled = new BufferedImage(newWidth, newHeight, original.getType());
        scaled.getGraphics().drawImage(
                original.getScaledInstance(newWidth, newHeight, java.awt.Image.SCALE_SMOOTH),
                0, 0, null
        );

        return scaled;
    }

    // ========== 提示配置方法（严格参照 Code128Reader.java）==========

    /**
     * 标准提示配置
     */
    private Map<DecodeHintType, Object> createStandardHints() {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        return hints;
    }

    /**
     * 纯条码模式提示
     */
    private Map<DecodeHintType, Object> createPureBarcodeHints() {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.PURE_BARCODE, Boolean.TRUE);
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        return hints;
    }

    /**
     * 仅识别 Code128
     */
    private Map<DecodeHintType, Object> createCode128OnlyHints() {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS,
                Collections.singletonList(BarcodeFormat.CODE_128));
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        return hints;
    }

    /**
     * 宽松模式提示
     */
    private Map<DecodeHintType, Object> createLooseHints() {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.PURE_BARCODE, Boolean.FALSE);
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        return hints;
    }

    // ========== 内部类 ==========

    /**
     * 解码策略类（严格参照 Code128Reader.java）
     */
    private static class DecodeStrategy {
        String name;
        Map<DecodeHintType, Object> hints;

        DecodeStrategy(String name, Map<DecodeHintType, Object> hints) {
            this.name = name;
            this.hints = hints;
        }
    }
}
