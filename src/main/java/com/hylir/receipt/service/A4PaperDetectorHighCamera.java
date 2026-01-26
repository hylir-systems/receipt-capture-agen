package com.hylir.receipt.service;

import org.bytedeco.javacpp.indexer.IntIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.*;

import org.bytedeco.javacpp.indexer.FloatIndexer;
/**
 * 高拍仪专用 A4 检测与透视矫正（JavaCV 最终稳定版）
 *
 * 设计目标：
 * - A4 基本在画面中央
 * - 背景为深色软垫
 * - 稳定优先，不追求炫技
 */
public class A4PaperDetectorHighCamera {

    /**
     * 主入口：检测 + 矫正 A4
     */
    public static Mat detectAndWarpA4(Mat src) {
        if (src == null || src.empty()) {
            return null;
        }

        Mat gray = new Mat();
        Mat blur = new Mat();
        Mat edge = new Mat();

        // 1. 灰度
        opencv_imgproc.cvtColor(src, gray, opencv_imgproc.COLOR_BGR2GRAY);

        // 2. 高斯模糊（高拍仪很有用）
        opencv_imgproc.GaussianBlur(gray, blur, new Size(5, 5), 0);

        // 3. Canny
        opencv_imgproc.Canny(blur, edge, 75, 200);

        // 4. 找轮廓（只用 Mat）

        MatVector contours = new MatVector();
        Mat hierarchy = new Mat();

        opencv_imgproc.findContours(
                edge,
                contours,
                hierarchy,
                opencv_imgproc.RETR_EXTERNAL,
                opencv_imgproc.CHAIN_APPROX_SIMPLE
        );


        // 5. 找最大四边形
        Mat bestQuad = findBestA4Contour(contours);
        if (bestQuad == null) {
            return null;
        }

        // 6. 排序四个点
        Mat ordered  = orderPoints(bestQuad);

        // 7. 透视变换
        return warp(src, ordered);
    }

    /**
     * 找最像 A4 的四边形
     */
    private static Mat findBestA4Contour(MatVector contours) {
        double maxArea = 0;
        Mat best = null;
        long total = contours.size();
        for (long i = 0; i < total; i++) {
            Mat contour = contours.get(i);
            double area = Math.abs(opencv_imgproc.contourArea(contour));
            if (area < 50_000) continue; // 过滤小噪声

            Mat approx = new Mat();
            double peri = opencv_imgproc.arcLength(contour, true);
            opencv_imgproc.approxPolyDP(contour, approx, 0.02 * peri, true);

            if (approx.rows() == 4 && area > maxArea) {
                maxArea = area;
                best = approx;
            }
        }
        return best;
    }

    /**
     * 对四个点排序：TL, TR, BR, BL
     */
    private static Mat orderPoints(Mat approx) {

        // approx: CV_32SC2
        IntIndexer idx = approx.createIndexer();

        Point2f[] pts = new Point2f[4];
        for (int i = 0; i < 4; i++) {
            int x = idx.get(i, 0, 0);
            int y = idx.get(i, 0, 1);
            pts[i] = new Point2f(x, y);
        }
        idx.release();

        // 按中心点排序
        float cx = 0, cy = 0;
        for (Point2f p : pts) {
            cx += p.x();
            cy += p.y();
        }
        cx /= 4;
        cy /= 4;

        Point2f tl = null, tr = null, br = null, bl = null;
        for (Point2f p : pts) {
            if (p.x() < cx && p.y() < cy) tl = p;
            else if (p.x() > cx && p.y() < cy) tr = p;
            else if (p.x() > cx && p.y() > cy) br = p;
            else bl = p;
        }

        // 👉 关键：新建一个 float Mat
        Mat ordered = new Mat(4, 1, opencv_core.CV_32FC2);
        FloatIndexer out = ordered.createIndexer();

        out.put(0, 0, tl.x(), tl.y());
        out.put(1, 0, tr.x(), tr.y());
        out.put(2, 0, br.x(), br.y());
        out.put(3, 0, bl.x(), bl.y());

        out.release();
        return ordered;
    }

    /**
     * 透视矫正
     */
    /**
     * 透视矫正（JavaCV 正确版）
     * pts: CV_32FC2, 4x1, 顺序：TL, TR, BR, BL
     */
    private static Mat warp(Mat src, Mat pts) {

        // 读取 pts 中的 4 个点
        FloatIndexer p = pts.createIndexer();

        float x0 = p.get(0, 0, 0);
        float y0 = p.get(0, 0, 1);
        float x1 = p.get(1, 0, 0);
        float y1 = p.get(1, 0, 1);
        float x2 = p.get(2, 0, 0);
        float y2 = p.get(2, 0, 1);
        float x3 = p.get(3, 0, 0);
        float y3 = p.get(3, 0, 1);

        p.release();

        // 计算目标宽高
        double widthA = Math.hypot(x2 - x3, y2 - y3);
        double widthB = Math.hypot(x1 - x0, y1 - y0);
        int maxW = (int) Math.max(widthA, widthB);

        double heightA = Math.hypot(x1 - x2, y1 - y2);
        double heightB = Math.hypot(x0 - x3, y0 - y3);
        int maxH = (int) Math.max(heightA, heightB);

        // 目标点
        Mat dst = new Mat(4, 1, opencv_core.CV_32FC2);
        FloatIndexer d = dst.createIndexer();

        d.put(0, 0, 0f, 0f);
        d.put(1, 0, maxW - 1f, 0f);
        d.put(2, 0, maxW - 1f, maxH - 1f);
        d.put(3, 0, 0f, maxH - 1f);

        d.release();

        // 透视矩阵
        Mat M = opencv_imgproc.getPerspectiveTransform(pts, dst);

        Mat warped = new Mat();
        opencv_imgproc.warpPerspective(
                src,
                warped,
                M,
                new Size(maxW, maxH)
        );

        return warped;
    }
    /**
     * 简单 main 测试
     */
    public static void main(String[] args) {
        Mat src = opencv_imgcodecs.imread("input.jpg");
        Mat result = detectAndWarpA4(src);

        if (result != null) {
            opencv_imgcodecs.imwrite("output_a4.jpg", result);
            System.out.println("A4 检测完成");
        } else {
            System.out.println("未检测到 A4");
        }
    }
}
