package com.hylir.receipt.service;

import org.bytedeco.javacpp.indexer.IntIndexer;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.*;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 简单的文档检测与透视变换工具（基于 OpenCV）
 * - 查找最大的四边形轮廓作为文档边界
 * - 进行透视变换到目标宽高
 * - 专门支持 A4 纸张的自动识别
 */
public class DocumentScanner {

    private static final Java2DFrameConverter JAVA2D_CONVERTER = new Java2DFrameConverter();
    private static final OpenCVFrameConverter.ToMat MAT_CONVERTER = new OpenCVFrameConverter.ToMat();

    // A4 纸张标准比例 (210:297 ≈ 1:1.4142)
    private static final double A4_ASPECT_RATIO = 1.4142;
    private static final double ASPECT_RATIO_TOLERANCE = 0.15; // 15% 容差
    private static final double ANGLE_TOLERANCE = 15.0; // 角度容差（度）

    /**
     * 尝试检测文档并透视变换到目标大小。如果未找到合适四边形，则返回原图。
     */
    public static BufferedImage detectAndWarp(BufferedImage input, int targetWidth, int targetHeight) {
        try {
            Frame frame = JAVA2D_CONVERTER.convert(input);
            Mat srcMat = MAT_CONVERTER.convert(frame);

            Mat gray = new Mat();
            opencv_imgproc.cvtColor(srcMat, gray, opencv_imgproc.COLOR_BGR2GRAY);
            opencv_imgproc.GaussianBlur(gray, gray, new Size(5,5), 0);
            Mat edged = new Mat();
            opencv_imgproc.Canny(gray, edged, 75, 200);

            MatVector contours = new MatVector();
            opencv_imgproc.findContours(edged.clone(), contours, opencv_imgproc.RETR_LIST, opencv_imgproc.CHAIN_APPROX_SIMPLE);

            double maxArea = 0;
            Mat bestContour = null;

            for (int i = 0; i < contours.size(); i++) {
                Mat c = contours.get(i);
                double peri = opencv_imgproc.arcLength(c, true);
                Mat approx = new Mat();
                opencv_imgproc.approxPolyDP(c, approx, 0.02 * peri, true);
                if (approx.total() == 4) {
                    double area = Math.abs(opencv_imgproc.contourArea(approx));
                    if (area > maxArea) {
                        maxArea = area;
                        bestContour = approx;
                    }
                }
            }

            if (bestContour == null) {
                return input;
            }

            // 提取4个点（使用Indexer访问轮廓数据）
            List<Point> points = new ArrayList<>();
            try (IntIndexer indexer = bestContour.createIndexer()) {
                for (int i = 0; i < bestContour.rows(); i++) {
                    int x = indexer.get(i, 0, 0);
                    int y = indexer.get(i, 0, 1);
                    points.add(new Point(x, y));
                }
            } catch (Exception e) {

            }

            if (points.size() != 4) return input;

            // 对点排序：TL, TR, BR, BL
            Collections.sort(points, Comparator.comparingInt(p -> p.y() + p.x()));
            Point tl = points.get(0);
            Point br = points.get(3);
            Point other1 = points.get(1);
            Point other2 = points.get(2);
            Point tr = other1.x() > other2.x() ? other1 : other2;
            Point bl = other1.x() > other2.x() ? other2 : other1;

            Point2f srcPts = new Point2f(4);
            srcPts.position(0).x(tl.x()).y(tl.y());
            srcPts.position(1).x(tr.x()).y(tr.y());
            srcPts.position(2).x(br.x()).y(br.y());
            srcPts.position(3).x(bl.x()).y(bl.y());

            Point2f dstPts = new Point2f(4);
            dstPts.position(0).x(0).y(0);
            dstPts.position(1).x(targetWidth - 1).y(0);
            dstPts.position(2).x(targetWidth - 1).y(targetHeight - 1);
            dstPts.position(3).x(0).y(targetHeight - 1);

            Mat M = opencv_imgproc.getPerspectiveTransform(srcPts, dstPts);
            Mat warped = new Mat();
            opencv_imgproc.warpPerspective(srcMat, warped, M, new Size(targetWidth, targetHeight));

            Frame outFrame = MAT_CONVERTER.convert(warped);
            BufferedImage out = JAVA2D_CONVERTER.getBufferedImage(outFrame, 1);
            return out != null ? out : input;
        } catch (Exception e) {
            // 出错则返回原图
            return input;
        }
    }

    /**
     * 专门检测和矫正 A4 纸张图像
     * @param input 输入图像
     * @param targetWidth 目标宽度
     * @param targetHeight 目标高度
     * @return 矫正后的 A4 纸张图像，如果未检测到 A4 纸张则返回 null
     */
    public static BufferedImage detectAndWarpA4(BufferedImage input, int targetWidth, int targetHeight) {
        try {
            Frame frame = JAVA2D_CONVERTER.convert(input);
            Mat srcMat = MAT_CONVERTER.convert(frame);

            Mat gray = new Mat();
            opencv_imgproc.cvtColor(srcMat, gray, opencv_imgproc.COLOR_BGR2GRAY);
            opencv_imgproc.GaussianBlur(gray, gray, new Size(5,5), 0);
            Mat edged = new Mat();
            opencv_imgproc.Canny(gray, edged, 75, 200);

            MatVector contours = new MatVector();
            opencv_imgproc.findContours(edged.clone(), contours, opencv_imgproc.RETR_LIST, opencv_imgproc.CHAIN_APPROX_SIMPLE);

            Mat bestContour = findBestA4Contour(contours);

            if (bestContour == null) {
                return null; // 未找到合适的 A4 纸张
            }

            // 提取4个点并排序
            List<Point> points = extractContourPoints(bestContour);
            if (points.size() != 4) return null;

            // 对点排序：TL, TR, BR, BL
            Collections.sort(points, Comparator.comparingInt(p -> p.y() + p.x()));
            Point tl = points.get(0);
            Point br = points.get(3);
            Point other1 = points.get(1);
            Point other2 = points.get(2);
            Point tr = other1.x() > other2.x() ? other1 : other2;
            Point bl = other1.x() > other2.x() ? other2 : other1;

            Point2f srcPts = new Point2f(4);
            srcPts.position(0).x(tl.x()).y(tl.y());
            srcPts.position(1).x(tr.x()).y(tr.y());
            srcPts.position(2).x(br.x()).y(br.y());
            srcPts.position(3).x(bl.x()).y(bl.y());

            Point2f dstPts = new Point2f(4);
            dstPts.position(0).x(0).y(0);
            dstPts.position(1).x(targetWidth - 1).y(0);
            dstPts.position(2).x(targetWidth - 1).y(targetHeight - 1);
            dstPts.position(3).x(0).y(targetHeight - 1);

            Mat M = opencv_imgproc.getPerspectiveTransform(srcPts, dstPts);
            Mat warped = new Mat();
            opencv_imgproc.warpPerspective(srcMat, warped, M, new Size(targetWidth, targetHeight));

            Frame outFrame = MAT_CONVERTER.convert(warped);
            return JAVA2D_CONVERTER.getBufferedImage(outFrame, 1);

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从轮廓中找到最适合的 A4 纸张轮廓
     */
    private static Mat findBestA4Contour(MatVector contours) {
        double maxScore = 0;
        Mat bestContour = null;

        for (int i = 0; i < contours.size(); i++) {
            Mat c = contours.get(i);
            double peri = opencv_imgproc.arcLength(c, true);
            Mat approx = new Mat();
            opencv_imgproc.approxPolyDP(c, approx, 0.02 * peri, true);

            if (approx.total() == 4) {
                List<Point> points = extractContourPoints(approx);
                if (points.size() == 4) {
                    double score = calculateA4Score(points);
                    if (score > maxScore) {
                        maxScore = score;
                        bestContour = approx;
                    }
                }
            }
        }

        return bestContour;
    }

    /**
     * 从轮廓中提取点
     */
    private static List<Point> extractContourPoints(Mat contour) {
        List<Point> points = new ArrayList<>();
        try (IntIndexer indexer = contour.createIndexer()) {
            for (int i = 0; i < contour.rows(); i++) {
                int x = indexer.get(i, 0, 0);
                int y = indexer.get(i, 0, 1);
                points.add(new Point(x, y));
            }
        } catch (Exception e) {
            // 忽略异常
        }
        return points;
    }

    /**
     * 计算四边形符合 A4 纸张的评分
     * 评分基于：尺寸比例匹配度和角度正交度
     */
    private static double calculateA4Score(List<Point> points) {
        if (points.size() != 4) return 0;

        // 计算边长
        double side1 = distance(points.get(0), points.get(1));
        double side2 = distance(points.get(1), points.get(2));
        double side3 = distance(points.get(2), points.get(3));
        double side4 = distance(points.get(3), points.get(0));

        // 计算宽高比
        double width = Math.max(side1, side3);
        double height = Math.max(side2, side4);
        double aspectRatio = width / height;

        // 计算与 A4 标准比例的匹配度
        double ratioDeviation = Math.abs(aspectRatio - A4_ASPECT_RATIO) / A4_ASPECT_RATIO;
        double ratioScore = ratioDeviation <= ASPECT_RATIO_TOLERANCE ?
                           (1.0 - ratioDeviation / ASPECT_RATIO_TOLERANCE) : 0;

        // 计算角度是否接近 90 度
        double angleScore = calculateAngleScore(points);

        // 综合评分（加权平均）
        double totalScore = (ratioScore * 0.6 + angleScore * 0.4);

        // 只有当评分超过阈值时才认为是有效的 A4 纸张
        return totalScore > 0.7 ? totalScore : 0;
    }

    /**
     * 计算两点间距离
     */
    private static double distance(Point p1, Point p2) {
        return Math.sqrt(Math.pow(p1.x() - p2.x(), 2) + Math.pow(p1.y() - p2.y(), 2));
    }

    /**
     * 计算四边形角度正交度评分
     */
    private static double calculateAngleScore(List<Point> points) {
        double totalAngleDeviation = 0;
        int validAngles = 0;

        // 计算每个角的角度
        for (int i = 0; i < 4; i++) {
            Point p1 = points.get(i);
            Point p2 = points.get((i + 1) % 4);
            Point p3 = points.get((i + 2) % 4);

            double angle = calculateAngle(p1, p2, p3);
            double deviation = Math.abs(angle - 90);
            if (deviation <= ANGLE_TOLERANCE) {
                totalAngleDeviation += deviation;
                validAngles++;
            } else if (deviation > 45) {
                // 如果角度偏差太大，直接返回低分
                return 0;
            }
        }

        if (validAngles < 3) return 0;

        // 计算平均角度偏差
        double avgDeviation = totalAngleDeviation / validAngles;
        return Math.max(0, 1.0 - avgDeviation / ANGLE_TOLERANCE);
    }

    /**
     * 计算三点形成的角的角度
     */
    private static double calculateAngle(Point p1, Point p2, Point p3) {
        // 向量 p1->p2 和 p2->p3
        double v1x = p2.x() - p1.x();
        double v1y = p2.y() - p1.y();
        double v2x = p3.x() - p2.x();
        double v2y = p3.y() - p2.y();

        // 计算点积
        double dot = v1x * v2x + v1y * v2y;
        // 计算向量长度
        double len1 = Math.sqrt(v1x * v1x + v1y * v1y);
        double len2 = Math.sqrt(v2x * v2x + v2y * v2y);

        if (len1 == 0 || len2 == 0) return 0;

        // 计算余弦值
        double cos = dot / (len1 * len2);
        // 限制在 [-1, 1] 范围内
        cos = Math.max(-1, Math.min(1, cos));

        // 返回角度（度）
        return Math.toDegrees(Math.acos(cos));
    }
}


