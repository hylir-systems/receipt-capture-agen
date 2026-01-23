package com.hylir.receipt.service.camera;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hylir.receipt.config.AppConfig;
import com.hylir.receipt.util.TempFileCleaner;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 摄像头捕获服务
 * 负责单次拍照功能，包括真实拍照和模拟拍照
 */
public class CameraCaptureService {
    private static final Logger logger = LoggerFactory.getLogger(CameraCaptureService.class);
    private final CameraGrabberManager grabberManager;
    private final CameraMockRenderer mockRenderer;
    private boolean useRealCamera;

    /**
     * 构造函数
     *
     * @param grabberManager 抓取器管理器
     * @param mockRenderer 模拟渲染器
     */
    public CameraCaptureService(CameraGrabberManager grabberManager, CameraMockRenderer mockRenderer) {
        this.grabberManager = grabberManager;
        this.mockRenderer = mockRenderer;
        this.useRealCamera = false;
    }

    /**
     * 设置是否使用真实摄像头
     *
     * @param useRealCamera 是否使用真实摄像头
     */
    public void setUseRealCamera(boolean useRealCamera) {
        this.useRealCamera = useRealCamera;
    }

    /**
     * 拍照获取图片
     *
     * @param deviceIndex 设备索引
     * @return 保存的图片文件路径
     * @throws IOException 如果拍照或保存失败
     */
    public String captureImage(int deviceIndex) throws IOException {
        try {
            // 确保临时目录存在
            ensureTempDirExists();

            // 清理过期文件（只保留最近2分钟）
            TempFileCleaner.cleanupExpiredFiles(CameraConstants.TEMP_DIR);

            String imagePath = CameraConstants.TEMP_DIR + File.separator + "captured_" + System.currentTimeMillis() + ".png";

            if (useRealCamera) {
                // 使用真实摄像头拍照
                logger.info("使用真实摄像头拍照，设备索引: {}", deviceIndex);
                return captureFromRealCamera(deviceIndex, imagePath);
            } else {
                // 使用模拟拍照
                logger.info("使用模拟拍照，设备索引: {}", deviceIndex);
                return captureFromMock(deviceIndex, imagePath);
            }
        } catch (Exception e) {
            logger.error("拍照过程出错", e);
            throw new IOException("拍照失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从真实摄像头捕获图像
     *
     * @param deviceIndex 设备索引
     * @param outputPath 输出路径
     * @return 保存的图片文件路径
     * @throws Exception 如果捕获失败
     */
    private String captureFromRealCamera(int deviceIndex, String outputPath) throws Exception {
        logger.info("开始从真实摄像头捕获图像，输出路径: {}", outputPath);
        
        // 创建并启动抓取器
        var grabber = grabberManager.createAndStartGrabber(deviceIndex, 640, 480);
        
        try {
            // 预热摄像头
            grabberManager.warmUpCamera(grabber);
            
            // 捕获图像
            Frame frame = grabber.grab();
            if (frame == null) {
                throw new IOException("未获取到图像帧");
            }
            
            // 转换为 BufferedImage
            BufferedImage image = new Java2DFrameConverter().convert(frame);
            if (image == null) {
                throw new IOException("图像转换失败");
            }
            
            // 保存图像
            ImageIO.write(image, "PNG", new File(outputPath));
            logger.info("真实摄像头拍照完成，图片保存至: {}", outputPath);
            
            return outputPath;
        } finally {
            // 关闭抓取器
            grabberManager.closeGrabber();
        }
    }

    /**
     * 从模拟摄像头捕获图像
     *
     * @param deviceIndex 设备索引
     * @param outputPath 输出路径
     * @return 保存的图片文件路径
     * @throws IOException 如果保存失败
     */
    private String captureFromMock(int deviceIndex, String outputPath) throws IOException {
        logger.info("开始从模拟摄像头捕获图像，输出路径: {}", outputPath);
        
        // 模拟拍照延迟
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("模拟拍照延迟被中断");
        }
        
        // 获取分辨率
        int resolution = AppConfig.getScannerResolution();
        logger.info("模拟拍照，分辨率: {} DPI", resolution);
        
        // 渲染模拟回单
        BufferedImage mockImage = mockRenderer.renderMockReceipt(deviceIndex, resolution);
        
        // 保存图像
        ImageIO.write(mockImage, "PNG", new File(outputPath));
        logger.info("模拟拍照完成，图片保存至: {}", outputPath);
        
        return outputPath;
    }

    /**
     * 拍照并返回 BufferedImage
     *
     * @param deviceIndex 设备索引
     * @return 图片缓冲对象
     * @throws IOException 如果拍照或读取失败
     */
    public BufferedImage captureImageAsBufferedImage(int deviceIndex) throws IOException {
        String imagePath = captureImage(deviceIndex);
        return ImageIO.read(new File(imagePath));
    }

    /**
     * 确保临时目录存在
     *
     * @throws IOException 如果创建目录失败
     */
    private void ensureTempDirExists() throws IOException {
        Path tempPath = Paths.get(CameraConstants.TEMP_DIR);
        if (!Files.exists(tempPath)) {
            Files.createDirectories(tempPath);
            logger.info("创建临时目录: {}", CameraConstants.TEMP_DIR);
        }
    }
}
