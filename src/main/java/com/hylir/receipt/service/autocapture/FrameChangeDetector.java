package com.hylir.receipt.service.autocapture;

/**
 * 基于像素差的帧变化检测器（高拍仪场景）
 * 只判断“画面是否在变化”，不做任何OpenCV操作
 */
public class FrameChangeDetector {

    private static final int SAMPLE_STEP = 20;   // 每隔多少像素采样
    private static final int DIFF_THRESHOLD = 25;
    private static final int CHANGE_COUNT_THRESHOLD = 300;

    private int[] lastFrame;
    private int width;
    private int height;

    public boolean isFrameChanging(int[] pixels, int w, int h) {
        if (pixels == null) return false;

        if (lastFrame == null || w != width || h != height) {
            lastFrame = pixels.clone();
            width = w;
            height = h;
            return true;
        }

        int diffCount = 0;

        for (int y = 0; y < h; y += SAMPLE_STEP) {
            for (int x = 0; x < w; x += SAMPLE_STEP) {
                int idx = y * w + x;
                int p1 = pixels[idx];
                int p2 = lastFrame[idx];

                int r1 = (p1 >> 16) & 0xff;
                int g1 = (p1 >> 8) & 0xff;
                int b1 = p1 & 0xff;

                int r2 = (p2 >> 16) & 0xff;
                int g2 = (p2 >> 8) & 0xff;
                int b2 = p2 & 0xff;

                int diff = Math.abs(r1 - r2) +
                           Math.abs(g1 - g2) +
                           Math.abs(b1 - b2);

                if (diff > DIFF_THRESHOLD) {
                    diffCount++;
                    if (diffCount > CHANGE_COUNT_THRESHOLD) {
                        lastFrame = pixels.clone();
                        return true;
                    }
                }
            }
        }

        lastFrame = pixels.clone();
        return false;
    }

    public void reset() {
        lastFrame = null;
    }
}