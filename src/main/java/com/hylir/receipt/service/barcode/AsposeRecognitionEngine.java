package com.hylir.receipt.service.barcode;


import com.aspose.barcode.barcoderecognition.BarCodeReader;
import com.aspose.barcode.barcoderecognition.BarCodeResult;
import com.aspose.barcode.barcoderecognition.DecodeType;
import com.aspose.barcode.barcoderecognition.QualitySettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Aspose 条码识别引擎（兜底方案）
 *
 * 设计目标：
 * - 只在 ZXing 失败后使用
 * - 稳定优先，不追求速度
 * - 支持一维码为主（Code128 / Code39 / EAN 等）
 */
public class AsposeRecognitionEngine {

    private static final Logger log =
            LoggerFactory.getLogger(AsposeRecognitionEngine.class);

    /**
     * 识别条码（BufferedImage 输入）
     */
    public List<String> recognize(BufferedImage image) {

        List<String> results = new ArrayList<>();

        try {
            BarCodeReader reader = new BarCodeReader(
                    image,
                    DecodeType.CODE_128
            );

            // ⚠ 高拍仪 + 纸质回单，必须开这些
            QualitySettings qs = reader.getQualitySettings();
            qs.setAllowMedianSmoothing(true);
            qs.setAllowComplexBackground(true);
            qs.setAllowIncorrectBarcodes(true);
            qs.setReadTinyBarcodes(true);
            BarCodeResult[] barCodeResults = reader.readBarCodes();
            List<String> collect = Arrays.stream(barCodeResults).map(el -> el.getCodeText()).collect(Collectors.toList());
            return collect;


        } catch (Exception e) {
            log.error("Aspose 条码识别异常", e);
        }

        return results;
    }

    /**
     * 是否成功识别到条码
     */
    public boolean hasBarcode(BufferedImage image) {
        return !recognize(image).isEmpty();
    }
}
