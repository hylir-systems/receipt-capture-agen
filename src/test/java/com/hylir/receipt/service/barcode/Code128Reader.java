package com.hylir.receipt.service.barcode;

import com.google.zxing.*;
import com.google.zxing.client.j2se.*;
import com.google.zxing.common.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

/**
 * 专门针对 Code128 条码的优化读取器
 */
public class Code128Reader {

    /**
     * 专门读取 Code128 条码
     */
    public static String readCode128(String imagePath) throws Exception {
        return readCode128(new File(imagePath));
    }

    public static String readCode128(File imageFile) throws Exception {
        // 1. 加载图像
        BufferedImage image = ImageIO.read(imageFile);
        if (image == null) {
            throw new RuntimeException("无法读取图像: " + imageFile.getPath());
        }

        System.out.println("图像尺寸: " + image.getWidth() + "x" + image.getHeight());
        System.out.println("图像类型: " + image.getType());

        // 2. 尝试多种方法
        String result = tryMultipleMethods(image);

        if (result == null) {
            // 3. 如果标准方法失败，尝试增强处理
            result = tryEnhancedProcessing(image);
        }

        return result;
    }

    /**
     * 尝试多种识别方法
     */
    private static String tryMultipleMethods(BufferedImage image) {
        // 方法列表
        List<DecodeStrategy> strategies = Arrays.asList(
                new DecodeStrategy("标准方法", createStandardHints()),
                new DecodeStrategy("纯条码模式", createPureBarcodeHints()),
                new DecodeStrategy("仅Code128", createCode128OnlyHints()),
                new DecodeStrategy("宽松模式", createLooseHints())
        );

        // 尝试每个方法
        for (DecodeStrategy strategy : strategies) {
            try {
                System.out.println("尝试方法: " + strategy.name);
                String result = decodeWithStrategy(image, strategy.hints);
                if (result != null && !result.trim().isEmpty()) {
                    System.out.println("✓ 方法成功: " + strategy.name);
                    return result;
                }
            } catch (Exception e) {
                System.out.println("✗ 方法失败: " + strategy.name + " - " + e.getMessage());
            }
        }

        return null;
    }

    /**
     * 使用特定策略解码
     */
    private static String decodeWithStrategy(BufferedImage image, Map<DecodeHintType, Object> hints)
            throws Exception {

        // 尝试多种二值化器
        Binarizer[] binarizers = {
                new HybridBinarizer(new BufferedImageLuminanceSource(image)),
                new GlobalHistogramBinarizer(new BufferedImageLuminanceSource(image))
        };

        for (Binarizer binarizer : binarizers) {
            try {
                BinaryBitmap bitmap = new BinaryBitmap(binarizer);
                Result result = new MultiFormatReader().decode(bitmap, hints);

                // 验证结果（Code128 通常以特定字符开头）
                String text = result.getText();
                if (isValidCode128(text)) {
                    return text;
                }
            } catch (NotFoundException e) {
                // 继续尝试下一个二值化器
            }
        }

        throw new RuntimeException ("未找到条码");
    }

    /**
     * 验证 Code128 条码内容
     */
    private static boolean isValidCode128(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        // 根据你的条码模式验证
        // 你的条码类似: X202601200000093601
        // 可以添加业务逻辑验证

        // 简单验证：长度通常在 10-30 之间
        return text.length() >= 10 && text.length() <= 30;
    }

    /**
     * 尝试增强处理
     */
    private static String tryEnhancedProcessing(BufferedImage original) {
        // 预处理步骤
        try {
            // 1. 转换为灰度
            BufferedImage gray = convertToGrayscale(original);

            // 2. 尝试不同预处理方法
            BufferedImage[] processedImages = {
                    gray,
                    enhanceContrast(gray),
                    binarizeOtsu(gray),
                    scaleImage(gray, 1.5),  // 放大1.5倍
                    scaleImage(gray, 2.0)   // 放大2倍
            };

            // 3. 对每个处理后的图像尝试解码
            Map<DecodeHintType, Object> hints = createCode128OnlyHints();

            for (int i = 0; i < processedImages.length; i++) {
                try {
                    System.out.println("尝试增强处理 #" + (i + 1));
                    String result = decodeWithStrategy(processedImages[i], hints);
                    if (result != null && isValidCode128(result)) {
                        System.out.println("✓ 增强处理成功");
                        return result;
                    }
                } catch (Exception e) {
                    // 继续下一个
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    // ========== 图像处理方法 ==========

    private static BufferedImage convertToGrayscale(BufferedImage original) {
        BufferedImage gray = new BufferedImage(
                original.getWidth(), original.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        gray.getGraphics().drawImage(original, 0, 0, null);
        return gray;
    }

    private static BufferedImage enhanceContrast(BufferedImage image) {
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

    private static BufferedImage binarizeOtsu(BufferedImage gray) {
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

    private static BufferedImage scaleImage(BufferedImage original, double scale) {
        int newWidth = (int) (original.getWidth() * scale);
        int newHeight = (int) (original.getHeight() * scale);

        BufferedImage scaled = new BufferedImage(newWidth, newHeight, original.getType());
        scaled.getGraphics().drawImage(
                original.getScaledInstance(newWidth, newHeight, java.awt.Image.SCALE_SMOOTH),
                0, 0, null
        );

        return scaled;
    }

    // ========== 提示配置 ==========

    private static Map<DecodeHintType, Object> createStandardHints() {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        return hints;
    }

    private static Map<DecodeHintType, Object> createPureBarcodeHints() {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.PURE_BARCODE, Boolean.TRUE); // 纯条码模式
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        return hints;
    }

    private static Map<DecodeHintType, Object> createCode128OnlyHints() {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS,
                Collections.singletonList(BarcodeFormat.CODE_128)); // 只识别 Code128
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        return hints;
    }

    private static Map<DecodeHintType, Object> createLooseHints() {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.PURE_BARCODE, Boolean.FALSE); // 非纯条码模式
        hints.put(DecodeHintType.ALLOWED_LENGTHS,
                new int[]{15, 16, 17, 18, 19, 20}); // 你的条码长度范围
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        return hints;
    }

    // ========== 测试方法 ==========

    public static void main(String[] args) {
        try {
            // 你的条码文件路径
//            String imagePath =
//                    "D:\\hylir\\front-end\\receipt-capture-agent\\temp-images\\barcode.png";
//            // 输入图像路径（你的回单照片）
            String imagePath = "D:\\hylir\\front-end\\receipt-capture-agent\\temp-images\\preview_raw_1769143672030.png";

            System.out.println("开始识别 Code128 条码...");
            System.out.println("文件: " + imagePath);
            System.out.println("=" .repeat(50));

            String result = readCode128(imagePath);

            if (result != null) {
                System.out.println("\n✅ 识别成功!");
                System.out.println("条码内容: " + result);
                System.out.println("期望内容: X202601200000093601");

                // 验证准确性
                if (result.equals("X202601200000093601")) {
                    System.out.println("🎉 完全匹配!");
                } else if (result.contains("X2026012000000")) {
                    System.out.println("✓ 部分匹配，可能读取有误");
                    System.out.println("差异: " + findDifference("X202601200000093601", result));
                } else {
                    System.out.println("⚠️ 内容不匹配");
                }
            } else {
                System.out.println("\n❌ 识别失败");
                System.out.println("建议:");
                System.out.println("1. 检查图像质量");
                System.out.println("2. 尝试其他预处理方法");
                System.out.println("3. 考虑使用 OpenCV 预处理");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String findDifference(String expected, String actual) {
        int minLength = Math.min(expected.length(), actual.length());
        for (int i = 0; i < minLength; i++) {
            if (expected.charAt(i) != actual.charAt(i)) {
                return "位置 " + i + ": 期望 '" + expected.charAt(i) +
                        "', 实际 '" + actual.charAt(i) + "'";
            }
        }
        if (expected.length() != actual.length()) {
            return "长度不同: 期望 " + expected.length() + ", 实际 " + actual.length();
        }
        return "无差异";
    }

    /**
     * 解码策略类
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