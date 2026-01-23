package com.hylir.receipt.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 条码识别服务测试类
 *
 * @author shanghai pubing
 * @date 2025/01/19
 * @version 1.0.0
 */
class BarcodeRecognitionServiceTest {

    private BarcodeRecognitionService barcodeService;

    @BeforeEach
    void setUp() {
        barcodeService = new BarcodeRecognitionService();
    }

    @Test
    void testRecognizeBarcode_NullImage() {
        // 测试空图片输入
        String result = barcodeService.recognizeBarcode(null);
        assertNull(result, "空图片应返回null");
    }

    @Test
    void testIsValidReceiptNumber() {
        // 测试有效的送货单号
        assertTrue(barcodeService.isValidReceiptNumber("RC202401190001"), "有效单号应返回true");
        assertTrue(barcodeService.isValidReceiptNumber("123456789"), "纯数字应有效");

        // 测试无效的送货单号
        assertFalse(barcodeService.isValidReceiptNumber(null), "null应无效");
        assertFalse(barcodeService.isValidReceiptNumber(""), "空字符串应无效");
        assertFalse(barcodeService.isValidReceiptNumber("12"), "过短应无效");
        assertFalse(barcodeService.isValidReceiptNumber("ABC!@#"), "特殊字符应无效");
    }

    @Test
    void testRecognizeBarcodeWithRetry() {
        // 创建一个空的测试图片
        BufferedImage testImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);

        // 测试重试机制（由于没有实际条码，应返回null）
        String result = barcodeService.recognizeBarcodeWithRetry(testImage, 2);
        assertNull(result, "无条码的图片应返回null");
    }

    @Test
    void testServiceInitialization() {
        assertNotNull(barcodeService, "服务应正确初始化");
    }
}
