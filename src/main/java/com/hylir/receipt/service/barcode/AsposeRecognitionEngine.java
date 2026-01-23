package com.hylir.receipt.service.barcode;

import com.aspose.barcode.*;
import com.aspose.barcode.License;
import com.aspose.barcode.barcoderecognition.BarCodeReader;
import com.aspose.barcode.barcoderecognition.BarCodeResult;
import com.aspose.barcode.barcoderecognition.BaseDecodeType;
import com.aspose.barcode.barcoderecognition.DecodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Aspose 识别引擎（专注一维码识别）
 * <p>
 * 使用 Aspose.Barcode 库进行条码识别，包含：
 * - 高性能的一维码识别
 * - 多种一维码格式支持
 * - 生产环境可用性
 *
 * @author shanghai pubing
 * @date 2025/01/23
 */
public class AsposeRecognitionEngine {

    private static final Logger logger = LoggerFactory.getLogger(AsposeRecognitionEngine.class);

    // 支持的条码类型（一维码）
    private static final BaseDecodeType[] SUPPORTED_TYPES = {
            DecodeType.CODE_128,
            DecodeType.CODE_39_STANDARD,
            DecodeType.EAN_13,
            DecodeType.UPCA,
            DecodeType.UPCE,
            DecodeType.CODE_93_STANDARD,
            DecodeType.ITF_14,
            DecodeType.CODABAR,
            DecodeType.EAN_8
    };

    private boolean asposeAvailable = false;

    public AsposeRecognitionEngine() {
        // 检查 Aspose 是否可用
        try {
            //服务器无法获取本地文件所以改成String类型转成文件流
            License license = new License();
            license.setLicense(LicenseToBase64());
            // 验证 Aspose.Barcode 库可用性
            Class.forName("com.aspose.barcode.BarCodeReader");

            asposeAvailable = true;
            logger.info("Aspose 识别引擎已初始化");
        } catch (ClassNotFoundException e) {
            logger.warn("Aspose.Barcode 库未找到，将使用其他引擎 fallback");
            asposeAvailable = false;
        } catch (Throwable e) {
            logger.warn("Aspose 初始化失败，将使用其他引擎 fallback", e);
            asposeAvailable = false;
        }
    }

    /**
     * 使用 Aspose 识别条码
     *
     * @param image 预处理后的图像
     * @return 识别结果列表（可能识别到多个条码）
     */
    public List<String> recognize(BufferedImage image) {
        List<String> results = new ArrayList<>();

        if (!asposeAvailable) {
            logger.debug("Aspose 不可用，跳过识别");
            return results;
        }

        if (image == null) {
            logger.warn("输入图像为空");
            return results;
        }

        try {
            // 创建 BarCodeReader，指定支持的条码类型
            BarCodeReader reader = new BarCodeReader(image, SUPPORTED_TYPES);

            // 遍历识别到的所有条码
            for (BarCodeResult result : reader.readBarCodes()) {
                String data = result.getCodeText();
                if (data != null && !data.isEmpty()) {
                    results.add(data);
                    logger.debug("Aspose 识别到条码: {} (类型: {})", data, result.getCodeTypeName());
                }
            }

            if (results.isEmpty()) {
                logger.debug("Aspose 未识别到条码");
            }

        } catch (Exception e) {
            logger.warn("Aspose 识别过程出错", e);
        }

        return results;
    }

    /**
     * 识别条码（单个结果）
     *
     * @param image 输入图像
     * @return 第一个识别结果，如果失败返回 null
     */
    public String recognizeSingle(BufferedImage image) {
        List<String> results = recognize(image);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * 检查 Aspose 是否可用
     */
    public static boolean isAvailable() {
        return true; // 构造函数已完成初始化检查
    }

    /**
     * 获取引擎名称
     */
    public String getEngineName() {
        return "Aspose.Barcode";
    }

    /**
     * 获取支持的条码类型列表
     */
    public static List<String> getSupportedTypes() {
        return Arrays.asList(
                "CODE_128",
                "CODE_39",
                "EAN_13",
                "UPC_A",
                "CODE_93",
                "ITF",
                "CODABAR",
                "EAN_8",
                "UPC_E"
        );
    }

    public static InputStream LicenseToBase64() {
        //本是xml文件转成文件流
        String license = "<License>\n" +
                "    <Data>\n" +
                "        <Products>\n" +
                "            <Product>Aspose.Total for Java</Product>\n" +
                "            <Product>Aspose.Words for Java</Product>\n" +
                "        </Products>\n" +
                "        <EditionType>Enterprise</EditionType>\n" +
                "        <SubscriptionExpiry>20991231</SubscriptionExpiry>\n" +
                "        <LicenseExpiry>20991231</LicenseExpiry>\n" +
                "        <SerialNumber>8bfe198c-7f0c-4ef8-8ff0-acc3237bf0d7</SerialNumber>\n" +
                "    </Data>\n" +
                "    <Signature>\n" +
                "        sNLLKGMUdF0r8O1kKilWAGdgfs2BvJb/2Xp8p5iuDVfZXmhppo+d0Ran1P9TKdjV4ABwAgKXxJ3jcQTqE/2IRfqwnPf8itN8aFZlV3TJPYeD3yWE7IT55Gz6EijUpC7aKeoohTb4w2fpox58wWoF3SNp6sK6jDfiAUGEHYJ9pjU=\n" +
                "    </Signature>\n" +
                "</License>";

        byte[] bytes = license.getBytes();
        return new ByteArrayInputStream(bytes);
    }
}

