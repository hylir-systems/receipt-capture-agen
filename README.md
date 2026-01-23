# Receipt Capture Agent (回单采集终端)

## 项目概述

Receipt Capture Agent 是一款专为仓库/工厂送货回单拍照和条码识别设计的 Windows 桌面应用程序。使用高拍仪实时预览采集图片，自动检测 A4 纸张区域并识别条码，支持手动输入单号后上传到后端系统。

## 核心功能

- **实时预览**：支持高拍仪实时预览画面
- **自动检测**：自动检测 A4 纸张区域并进行透视矫正
- **条码识别**：使用 ZXing 识别一维码（CODE128、CODE39、EAN13 等）
- **手动输入**：识别失败时支持人工输入单号
- **自动上传**：识别成功后自动上传图片和结果到后端
- **调试功能**：预览时自动保存原始帧和矫正后的图片到临时目录

## 技术栈

| 技术 | 用途 |
|-----|------|
| Java 17 | 开发语言 |
| JavaFX 17 | UI 框架 |
| OpenCV / JavaCV | 摄像头采集 |
| ZXing | 条码识别 |
| OkHttp | HTTP 客户端 |
| Jackson | JSON 处理 |
| Maven | 构建工具 |
| SLF4J + Logback | 日志 |

## 项目结构

```
src/main/java/com/hylir/receipt/
├── ReceiptCaptureApplication.java      # 主应用入口
├── config/
│   └── AppConfig.java                  # 应用配置管理
├── controller/
│   └── MainController.java             # 主界面控制器
├── model/
│   └── CaptureResult.java              # 采集结果模型
├── service/
│   ├── CameraService.java              # 摄像头服务（门面类）
│   ├── BarcodeRecognitionService.java  # 条码识别服务
│   ├── UploadService.java              # 上传服务
│   ├── DocumentScanner.java            # A4 纸张检测与矫正
│   └── camera/                         # 摄像头底层实现
│       ├── CameraDeviceManager.java    # 设备管理
│       ├── CameraGrabberManager.java   # 抓取器管理
│       ├── CameraStreamService.java    # 视频流服务
│       └── CameraCaptureService.java   # 拍照服务

src/main/resources/
├── config/
│   └── application.properties          # 配置文件
├── fxml/
│   └── MainView.fxml                   # 主界面布局
└── css/
    └── application.css                 # 样式文件
```

## 快速开始

### 环境要求

- Windows 10/11
- Java 17+
- Maven 3.6+
- 高拍仪设备（如得力 GK122）

### 构建与运行

```bash
# 编译项目
mvn clean compile

# 开发模式运行（支持热重载）
mvn clean javafx:run

# 打包为可执行 JAR
mvn clean package

# 运行打包后的 JAR
java -jar target/receipt-capture-agent-1.0.0.jar
```

### IDEA 调试运行

1. 打开 Maven 面板（右侧）
2. 找到项目 → Plugins → javafx → javafx:run
3. 右键 → Debug

## 使用说明

1. **选择设备**：从下拉框选择高拍仪设备
2. **点击预览**：启动实时预览画面
3. **自动检测**：将 A4 送货单放在摄像头前，系统自动检测并识别条码
4. **手动输入**：如识别失败，可点击"手动输入"按钮
5. **上传**：确认单号后点击"上传"按钮

### 快捷键

- **F6** - 自动检测并识别条码

### 调试模式

预览模式下会自动保存图片到临时目录：
- `C:\Users\<用户名>\AppData\Local\Temp\receipt-capture\`
- 包含原始帧图片 (`preview_raw_*.png`)
- 包含 A4 矫正后图片 (`preview_corrected_*.png`)

## 配置说明

### application.properties

```properties
# 后端 API 配置
backend.url=http://localhost:8080
backend.upload.endpoint=/api/receipt/upload

# HTTP 超时配置（毫秒）
http.connection.timeout=30000
http.read.timeout=60000

# 摄像头配置
camera.width=1920
camera.height=1080
```

## 后端接口规范

### 上传接口

**端点**: `POST /api/receipt/upload`

**类型**: `multipart/form-data`

**参数**:
| 参数 | 类型 | 说明 |
|-----|------|-----|
| image | File | 图片文件 (PNG) |
| receiptNumber | String | 送货单号 |
| captureTime | String | 采集时间 (ISO 8601) |
| recognitionSuccess | Boolean | 识别是否成功 |

**响应示例**:
```json
{
    "success": true,
    "message": "上传成功",
    "receiptId": "RC202401190001"
}
```

## 常见问题

### 摄像头连接失败
- 检查高拍仪是否正确连接
- 确认设备驱动已安装
- 在设备管理器中检查摄像头状态

### 条码识别失败
- 确保条码完整可见
- 调整光线避免反光
- 检查条码类型是否支持

### 网络连接失败
- 检查后端服务是否运行
- 确认防火墙允许访问
- 验证后端 URL 配置正确

## 部署

1. 安装 Java 17+
2. 安装高拍仪驱动
3. 复制 JAR 文件
4. 修改 `application.properties` 中的后端地址
5. 运行 `java -jar receipt-capture-agent-1.0.0.jar`

## 许可证

MIT License
