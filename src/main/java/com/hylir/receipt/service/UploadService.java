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
 * 负责将回单图片上传到MES中心（制造执行中心）的回单接口
 *
 * 工作流程：
 * 1. 根据单号前缀（J开头或其他）确定对应的MES中心接口
 * 2. 直接上传图片文件到MES中心的回单上传接口（/receipt/upload）
 * 3. MES中心内部会调用common-center上传文件并更新单据回单信息
 *
 * @author shanghai pubing
 * @date 2025/01/19
 */
public class UploadService {

    private static final Logger logger = LoggerFactory.getLogger(UploadService.class);
    private static final MediaType MEDIA_TYPE_PNG = MediaType.parse("image/png");
    private static final MediaType MEDIA_TYPE_OCTET_STREAM = MediaType.parse("application/octet-stream");

    private final OkHttpClient httpClient;
    // private final ObjectMapper objectMapper; // 如需要 JSON 处理可取消注释
    private final String baseUrl;
    //通用中心
    private final String commonCenterUrl ="/hylir-common-center/";

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
     * - /api/v1/integration/chery/** → mes-center（MES制造执行中心）
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
            return "/hylir-mes-center/api/v1/integration/chery/tmmmjissheet/receipt/upload";
        } else {
            // 其他调用MES中心的TmMmJitSheetController（看板配送单主表控制器）
            return "/hylir-mes-center//api/v1/integration/chery/tmmmjitsheet/receipt/upload";
        }
    }


    /**
     * 上传采集结果到后端
     * 直接上传到MES中心的回单上传接口，MES中心内部会处理文件上传和单据更新
     *
     * @param result 采集结果
     * @return 上传是否成功
     */
    public boolean uploadCaptureResult(CaptureResult result) {
        if (result == null || result.getImagePath() == null) {
            logger.error("采集结果或图片路径为空，无法上传");
            return false;
        }

        if (result.getReceiptNumber() == null || result.getReceiptNumber().trim().isEmpty()) {
            logger.error("送货单号为空，无法上传");
            result.setErrorMessage("送货单号为空");
            return false;
        }

        try {
            File imageFile = new File(result.getImagePath());
            if (!imageFile.exists()) {
                logger.error("图片文件不存在: {}", result.getImagePath());
                result.setErrorMessage("图片文件不存在");
                return false;
            }

            // 直接调用MES中心的回单上传接口
            String fileUrl = uploadReceiptToController(result.getReceiptNumber(), imageFile);
            
            if (fileUrl != null) {
                result.setUploaded(true);
                result.setUploadUrl(fileUrl);
                logger.info("回单上传成功，单号: {}, URL: {}", result.getReceiptNumber(), fileUrl);
            } else {
                result.setErrorMessage("回单上传失败");
            }
            
            return fileUrl != null;

        } catch (Exception e) {
            logger.error("上传过程出错", e);
            result.setErrorMessage("上传失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 根据单号类型调用对应的MES中心（制造执行中心）Controller上传回单
     *
     * 后端接口要求：
     * - 第一步：调用通用中心上传文件，返回回单url
     * - 第二步：上传回单，参数单号和回单url
     *
     * @param receiptNumber 单号
     * @param imageFile 图片文件
     * @return 上传后的文件URL，失败返回null
     */
    public String uploadReceiptToController(String receiptNumber, File imageFile) {
        try {
            // ========== 第一步：调用通用中心上传文件，返回回单url ==========
            String fileUrl = uploadFileToCommonCenter(imageFile);
            if (fileUrl == null) {
                logger.error("第一步失败：文件上传到通用中心失败，单号: {}", receiptNumber);
                return null;
            }
            logger.info("第一步成功：文件上传到通用中心，url: {}", fileUrl);

            // ========== 第二步：上传回单，参数单号和回单url ==========
            boolean success = uploadReceiptInfo(receiptNumber, fileUrl);
            if (success) {
                logger.info("第二步成功：回单信息上传成功，单号: {}, URL: {}", receiptNumber, fileUrl);
                return fileUrl; // 返回上传后的URL
            } else {
                logger.error("第二步失败：回单信息上传失败，单号: {}", receiptNumber);
                return null;
            }

        } catch (Exception e) {
            logger.error("上传回单过程出错，单号: {}", receiptNumber, e);
            return null;
        }
    }

    /**
     * 第一步：上传文件到通用中心
     *
     * @param imageFile 图片文件
     * @return 文件URL，失败返回null
     */
    private String uploadFileToCommonCenter(File imageFile) {
        try {
            String uploadUrl = baseUrl + "/hylir-common-center/upload";

            // 根据文件扩展名确定媒体类型
            String fileName = imageFile.getName().toLowerCase();
            MediaType fileMediaType = MEDIA_TYPE_PNG;
            if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                fileMediaType = MediaType.parse("image/jpeg");
            } else if (fileName.endsWith(".png")) {
                fileMediaType = MEDIA_TYPE_PNG;
            } else {
                fileMediaType = MEDIA_TYPE_OCTET_STREAM;
            }

            RequestBody fileRequestBody = RequestBody.create(fileMediaType, imageFile);
            MultipartBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", imageFile.getName(), fileRequestBody)
                    .build();

            Request request = new Request.Builder()
                    .url(uploadUrl)
                    .post(requestBody)
                    .build();

            logger.info("开始上传文件到通用中心: {}", uploadUrl);
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    logger.info("文件上传到通用中心成功，响应: {}", responseBody);
                    // 假设返回的是文件的URL字符串
                    return responseBody;
                } else {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    logger.error("文件上传到通用中心失败，HTTP状态码: {}, 响应: {}", response.code(), responseBody);
                    return null;
                }
            }

        } catch (IOException e) {
            logger.error("上传文件到通用中心过程出错", e);
            return null;
        }
    }

    /**
     * 第二步：上传回单信息
     *
     * @param receiptNumber 单号
     * @param fileUrl 文件URL
     * @return 上传是否成功
     */
    private boolean uploadReceiptInfo(String receiptNumber, String fileUrl) {
        try {
            String apiEndpoint = getApiEndpoint(receiptNumber);
            String fullUrl = baseUrl + apiEndpoint;

            MultipartBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("sheetNo", receiptNumber)
                    .addFormDataPart("receiptUrl", fileUrl)
                    .build();

            Request request = new Request.Builder()
                    .url(fullUrl)
                    .post(requestBody)
                    .build();

            logger.info("开始上传回单信息，单号: {}, 接口: {}", receiptNumber, fullUrl);
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    logger.info("回单信息上传成功，响应: {}", responseBody);
                    return true;
                } else {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    logger.error("回单信息上传失败，HTTP状态码: {}, 响应: {}", response.code(), responseBody);
                    return false;
                }
            }

        } catch (IOException e) {
            logger.error("上传回单信息过程出错，单号: {}", receiptNumber, e);
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
                    .url(AppConfig.getBackendUrl() + commonCenterUrl + "actuator/health") // 通过网关调用 common-center（通用中心）
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
