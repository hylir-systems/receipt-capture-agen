package com.hylir.receipt.model;

import java.time.LocalDateTime;

/**
 * 采集结果模型类
 *
 * @author shanghai pubing
 * @date 2025/01/19
 * @version 1.0.0
 */
public class CaptureResult {

    private String receiptNumber;        // 送货单号
    private String imagePath;           // 本地图片路径
    private boolean recognitionSuccess; // 识别是否成功
    private String errorMessage;        // 错误信息
    private LocalDateTime captureTime;  // 采集时间
    private boolean uploaded;           // 是否已上传
    private String uploadResponse;      // 上传响应

    public CaptureResult() {
        this.captureTime = LocalDateTime.now();
        this.recognitionSuccess = false;
        this.uploaded = false;
    }

    // Getters and Setters
    public String getReceiptNumber() {
        return receiptNumber;
    }

    public void setReceiptNumber(String receiptNumber) {
        this.receiptNumber = receiptNumber;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public boolean isRecognitionSuccess() {
        return recognitionSuccess;
    }

    public void setRecognitionSuccess(boolean recognitionSuccess) {
        this.recognitionSuccess = recognitionSuccess;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCaptureTime() {
        return captureTime;
    }

    public void setCaptureTime(LocalDateTime captureTime) {
        this.captureTime = captureTime;
    }

    public boolean isUploaded() {
        return uploaded;
    }

    public void setUploaded(boolean uploaded) {
        this.uploaded = uploaded;
    }

    public String getUploadResponse() {
        return uploadResponse;
    }

    public void setUploadResponse(String uploadResponse) {
        this.uploadResponse = uploadResponse;
    }

    @Override
    public String toString() {
        return "CaptureResult{" +
                "receiptNumber='" + receiptNumber + '\'' +
                ", imagePath='" + imagePath + '\'' +
                ", recognitionSuccess=" + recognitionSuccess +
                ", errorMessage='" + errorMessage + '\'' +
                ", captureTime=" + captureTime +
                ", uploaded=" + uploaded +
                '}';
    }
}
