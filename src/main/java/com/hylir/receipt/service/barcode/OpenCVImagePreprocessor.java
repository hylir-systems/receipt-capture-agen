package com.hylir.receipt.service.barcode;

import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.opencv.core.CvType.CV_32F;

/**
 * OpenCV 图像预处理模块
 * 专门针对高拍仪拍摄的纸质回单进行优化预处理
 * 
 * @author shanghai pubing
 * @date 2025/01/20
 */
public class OpenCVImagePreprocessor {

    private static final Logger logger = LoggerFactory.getLogger(OpenCVImagePreprocessor.class);
    private static final Java2DFrameConverter JAVA2D_CONVERTER = new Java2DFrameConverter();
    private static final OpenCVFrameConverter.ToMat MAT_CONVERTER = new OpenCVFrameConverter.ToMat();

    /**
     * 预处理图像，优化条码识别效果
     * 
     * @param image 原始图像
     * @return 预处理后的图像列表（多种预处理结果，供识别引擎尝试）
     */
    public List<BufferedImage> preprocess(BufferedImage image) {
        List<BufferedImage> results = new ArrayList<>();
        
        if (image == null) {
            logger.warn("输入图像为空");
            return results;
        }

        try {
            // 转换为 OpenCV Mat
            Mat srcMat = bufferedImageToMat(image);
            
            // 1. 基础预处理：灰度化 + 降噪
            Mat gray = convertToGrayscale(srcMat);
            Mat denoised = denoise(gray);
            results.add(matToBufferedImage(denoised));
            
            // 2. 对比度增强（CLAHE - 自适应直方图均衡化）
            Mat enhanced = enhanceContrast(denoised);
            results.add(matToBufferedImage(enhanced));
            
            // 3. 二值化（多种方法）
            Mat binary1 = thresholdBinary(enhanced, opencv_imgproc.THRESH_BINARY);
            results.add(matToBufferedImage(binary1));
            
            Mat binary2 = thresholdBinary(enhanced, opencv_imgproc.THRESH_BINARY_INV);
            results.add(matToBufferedImage(binary2));
            
            Mat adaptive = adaptiveThreshold(enhanced);
            results.add(matToBufferedImage(adaptive));
            
            // 4. 形态学操作（去除噪点，连接断线）
            Mat morph = morphologicalOperation(adaptive);
            results.add(matToBufferedImage(morph));
            
            // 5. 锐化
            Mat sharpened = sharpen(enhanced);
            results.add(matToBufferedImage(sharpened));
            
            logger.debug("OpenCV 预处理完成，生成 {} 个预处理结果", results.size());
            
        } catch (Exception e) {
            logger.error("OpenCV 预处理失败", e);
            // 如果预处理失败，至少返回原图
            results.add(image);
        }
        
        return results;
    }

    /**
     * 转换为灰度图
     */
    private Mat convertToGrayscale(Mat src) {
        Mat gray = new Mat();
        if (src.channels() == 1) {
            src.copyTo(gray);
        } else {
            opencv_imgproc.cvtColor(src, gray, opencv_imgproc.COLOR_BGR2GRAY);
        }
        return gray;
    }

    /**
     * 降噪（高斯模糊）
     */
    private Mat denoise(Mat src) {
        Mat dst = new Mat();
        opencv_imgproc.GaussianBlur(src, dst, new Size(3, 3), 0);
        return dst;
    }

    /**
     * 对比度增强（CLAHE）
     */
    private Mat enhanceContrast(Mat src) {
        Mat dst = new Mat();
        // 使用 CLAHE (Contrast Limited Adaptive Histogram Equalization)
        // 注意：JavaCV 可能没有直接的 CLAHE，使用普通直方图均衡化
        opencv_imgproc.equalizeHist(src, dst);
        return dst;
    }

    /**
     * 二值化（固定阈值）
     */
    private Mat thresholdBinary(Mat src, int thresholdType) {
        Mat dst = new Mat();
        opencv_imgproc.threshold(src, dst, 127, 255, thresholdType);
        return dst;
    }

    /**
     * 自适应二值化
     */
    private Mat adaptiveThreshold(Mat src) {
        Mat dst = new Mat();
        opencv_imgproc.adaptiveThreshold(
            src, dst, 255,
            opencv_imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            opencv_imgproc.THRESH_BINARY, 11, 2
        );
        return dst;
    }

    /**
     * 形态学操作（开运算：先腐蚀后膨胀，去除小噪点）
     */
    private Mat morphologicalOperation(Mat src) {
        Mat dst = new Mat();
        Mat kernel = opencv_imgproc.getStructuringElement(
            opencv_imgproc.MORPH_RECT, new Size(3, 3)
        );
        opencv_imgproc.morphologyEx(src, dst, opencv_imgproc.MORPH_OPEN, kernel);
        kernel.close();
        return dst;
    }

    /**
     * 锐化
     */
    private Mat sharpen(Mat src) {
        Mat kernel = new Mat(3, 3, CV_32F);
        FloatIndexer indexer = kernel.createIndexer();
        // 锐化核
        indexer.put(0, 0, 0f);
        indexer.put(0, 1, -1f);
        indexer.put(0, 2, 0f);
        indexer.put(1, 0, -1f);
        indexer.put(1, 1, 5f);
        indexer.put(1, 2, -1f);
        indexer.put(2, 0, 0f);
        indexer.put(2, 1, -1f);
        indexer.put(2, 2, 0f);
        indexer.close();
        
        Mat dst = new Mat();
        opencv_imgproc.filter2D(src, dst, -1, kernel);
        kernel.close();
        return dst;
    }

    /**
     * BufferedImage 转 Mat
     */
    private Mat bufferedImageToMat(BufferedImage image) {
        org.bytedeco.javacv.Frame frame = JAVA2D_CONVERTER.convert(image);
        return MAT_CONVERTER.convert(frame);
    }

    /**
     * Mat 转 BufferedImage
     */
    private BufferedImage matToBufferedImage(Mat mat) {
        org.bytedeco.javacv.Frame frame = MAT_CONVERTER.convert(mat);
        return JAVA2D_CONVERTER.convert(frame);
    }
}

