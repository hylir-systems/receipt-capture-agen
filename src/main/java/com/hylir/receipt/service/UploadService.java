package com.hylir.receipt.service;

import com.hylir.receipt.config.AppConfig;
import com.hylir.receipt.model.CaptureResult;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 上传服务类
 * 负责将图片上传到common-center（通用中心），并根据单号类型调用MES中心（制造执行中心）的回单接口
 *
 * 工作流程：
 * 1. 上传图片到 common-center 的 /upload 接口
 * 2. 根据单号前缀（J开头或其他）调用对应的MES中心接口设置回单
 *
 * @author shanghai pubing
 * @date 2025/01/19
 */
public class UploadService {

    private static final Logger logger = LoggerFactory.getLogger(UploadService.class);
    private static final MediaType MEDIA_TYPE_PNG = MediaType.parse("image/png");

    private final OkHttpClient httpClient;
    // private final ObjectMapper objectMapper; // 如需要 JSON 处理可取消注释
    private final String baseUrl;

    public UploadService() {
        this.baseUrl = AppConfig.getBackendUrl();

        // 配置 HTTP 客户端
        // HttpLoggingInterceptor logging = new HttpLoggingInterceptor(logger::debug);
        // logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(AppConfig.getConnectionTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(AppConfig.getReadTimeout(), TimeUnit.MILLISECONDS)
                // .addInterceptor(logging) // 需要额外依赖时取消注释
                .build();

        // this.objectMapper = new ObjectMapper(); // 如需要 JSON 处理可取消注释

        logger.info("上传服务初始化完成，网关URL（路由到hylir-common-center和hylir-mes-center）: {}", baseUrl);
    }

    /**
     * 根据单号确定API端点（网关路由）
     * 网关会根据路径前缀自动路由到对应的服务：
     * - /common/** → common-center（通用中心）
     * - /v1/integration/chery/** → mes-center（MES制造执行中心）
     *
     * 单号规则：
     * - J开头：外排配送单 → mes-center 外排配送单管理
     * - 其他：看板配送单 → mes-center 看板配送单管理
     *
     * @param receiptNumber 送货单号
     * @return API端点路径
     */
    private String getApiEndpoint(String receiptNumber) {
        if (receiptNumber != null && receiptNumber.startsWith("J")) {
            // J开头调用MES中心的TmMmJisSheetController（外排配送单主表控制器）
            return "hylir-mes-center/v1/integration/chery/tmmmjissheet/upload-receipt";
        } else {
            // 其他调用MES中心的TmMmJitSheetController（看板配送单主表控制器）
            return "hylir-mes-center/v1/integration/chery/tmmmjitsheet/upload-receipt";
        }
    }

    /**
     * 上传采集结果到后端
     *
     * @param result 采集结果
     * @return 上传是否成功
     */
    public boolean uploadCaptureResult(CaptureResult result) {
        if (result == null || result.getImagePath() == null) {
            logger.error("采集结果或图片路径为空，无法上传");
            return false;
        }

        try {
            // 构建 Multipart 请求
            MultipartBody.Builder builder = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM);

            // 添加图片文件
            File imageFile = new File(result.getImagePath());
            if (!imageFile.exists()) {
                logger.error("图片文件不存在: {}", result.getImagePath());
                return false;
            }

            builder.addFormDataPart("image", imageFile.getName(),
                    RequestBody.create(imageFile, MEDIA_TYPE_PNG));

            // 添加送货单号
            if (result.getReceiptNumber() != null) {
                builder.addFormDataPart("receiptNumber", result.getReceiptNumber());
            }

            // 添加其他元数据
            builder.addFormDataPart("captureTime", result.getCaptureTime().toString());
            builder.addFormDataPart("recognitionSuccess",
                    String.valueOf(result.isRecognitionSuccess()));

            RequestBody requestBody = builder.build();

            // 构建请求 - 通过网关调用common-center（通用中心）的upload接口
            String uploadUrl = baseUrl + "/hylir-common-center/upload";
            Request request = new Request.Builder()
                    .url(uploadUrl)
                    .post(requestBody)
                    .build();

            // 执行请求
            logger.info("开始上传图片到common中心: {}", uploadUrl);
            try (Response response = httpClient.newCall(request).execute()) {
                boolean success = response.isSuccessful();
                String responseBody = response.body() != null ? response.body().string() : "";

                if (success) {
                    logger.info("图片上传成功，文件URL: {}", responseBody);
                    result.setUploaded(true);
                    result.setUploadResponse(responseBody);

                    // 根据单号类型调用对应的MES中心（制造执行中心）回单接口，传递文件URL
                    if (result.getReceiptNumber() != null) {
                        uploadReceiptToController(result.getReceiptNumber(), responseBody);
                    }

                    return true;
                } else {
                    logger.error("上传失败，HTTP状态码: {}, 响应: {}", response.code(), responseBody);
                    result.setErrorMessage("上传失败: HTTP " + response.code());
                    return false;
                }
            }

        } catch (IOException e) {
            logger.error("上传过程出错", e);
            result.setErrorMessage("上传失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 根据单号类型调用对应的MES中心（制造执行中心）Controller设置回单
     *
     * @param receiptNumber 单号
     * @param imagePath 图片路径（URL）
     * @return 设置是否成功
     */
    private boolean uploadReceiptToController(String receiptNumber, String imagePath) {
        try {
            String apiEndpoint = getApiEndpoint(receiptNumber);
            String fullUrl = baseUrl + apiEndpoint;

            // 构建表单数据
            MultipartBody.Builder builder = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("sheetNo", receiptNumber)
                    .addFormDataPart("receiptUrl", imagePath); // 这里用图片路径作为URL，实际应该用上传后的URL

            RequestBody requestBody = builder.build();

            // 构建请求
            Request request = new Request.Builder()
                    .url(fullUrl)
                    .post(requestBody)
                    .build();

            // 执行请求
            logger.info("开始设置回单到MES中心，单号: {}, 接口: {}", receiptNumber, fullUrl);
            try (Response response = httpClient.newCall(request).execute()) {
                boolean success = response.isSuccessful();
                String responseBody = response.body() != null ? response.body().string() : "";

                if (success) {
                    logger.info("回单设置到MES中心成功，单号: {}, 响应: {}", receiptNumber, responseBody);
                    return true;
                } else {
                    logger.error("回单设置到MES中心失败，单号: {}, HTTP状态码: {}, 响应: {}", receiptNumber, response.code(), responseBody);
                    return false;
                }
            }

        } catch (IOException e) {
            logger.error("设置回单过程出错，单号: {}", receiptNumber, e);
            return false;
        }
    }

    /**
     * 测试后端连接
     *
     * @return 连接是否正常
     */
    public boolean testBackendConnection() {
        try {
            // 测试 common-center（通用中心）的连接状态（通过网关）
            Request request = new Request.Builder()
                    .url(AppConfig.getBackendUrl() + "/hylir-common-center/actuator/health") // 通过网关调用 common-center（通用中心）
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                boolean success = response.isSuccessful();
                logger.info("hylir-common-center（通用中心）连接测试: {}", success ? "成功" : "失败");
                return success;
            }

        } catch (IOException e) {
            logger.error("后端连接测试失败", e);
            return false;
        }
    }

    /**
     * 获取后端配置信息
     */
    public String getBackendUrl() {
        return baseUrl;
    }

    /**
     * 关闭服务
     */
    public void close() {
        // OkHttpClient 会自动管理连接池，不需要手动关闭
        logger.debug("上传服务已关闭");
    }
}
