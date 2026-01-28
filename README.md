# Receipt Capture Agent (回单采集终端)

## 项目概述

Receipt Capture Agent 是一款专为仓库/工厂送货回单拍照和条码识别设计的 Windows 桌面应用程序。使用高拍仪实时预览采集图片，自动检测 A4 纸张区域并识别条码，支持手动输入单号后上传到后端系统。

## 核心功能

- **实时预览**：支持高拍仪实时预览画面
- **自动检测**：自动检测 A4 纸张区域并进行透视矫正
- **条码识别**：条码识别采用 `Aspose.Barcode for Java`，业务上固定使用一维码 **CODE128** 格式
- **手动输入**：识别失败时支持人工输入单号
- **自动上传**：识别成功后自动上传图片和结果到后端
- **调试功能**：预览时自动保存原始帧和矫正后的图片到临时目录

## 技术栈

| 技术 | 用途 |
|-----|------|
| Java 17 | 开发语言 |
| JavaFX 17 | UI 框架 |
| OpenCV / JavaCV | 摄像头采集 |
| Aspose.Barcode for Java | 条码识别（CODE128 一维码） |
| OkHttp | HTTP 客户端 |
| Jackson | JSON 处理 |
| Maven | 构建工具 |
| SLF4J + Logback | 日志 |

⚠️ 重要，一定要用有 FX 的 JDK，例如：`bellsoft-jdk17.0.18+10-windows-amd64-full`（自带 JavaFX），下载地址可参考：  
`https://download.bell-sw.com/java/17.0.18+10/bellsoft-jdk17.0.18+10-windows-amd64-full.zip`。

## 项目结构

```
src/main/java/com/hylir/receipt/
├── ReceiptCaptureApplication.java      # 主应用入口
├── config/
│   ├── AppConfig.java                  # 应用配置管理
│   └── ConfigManager.java              # 配置管理器
├── controller/
│   ├── MainController.java             # 主界面控制器
│   ├── ImageDetailController.java      # 图片详情控制器
│   ├── SettingsController.java         # 设置控制器
│   ├── AutoCaptureController.java      # 自动采集控制器（管理自动识别/上传）
│   ├── PreviewManager.java             # 预览管理器（摄像头预览与帧回调）
│   ├── HistoryManager.java             # 成功记录历史列表
│   ├── StatusUpdateManager.java        # 状态栏与日志节流更新
│   └── PreviewState.java               # 预览状态枚举
├── model/
│   └── CaptureResult.java              # 采集结果模型
└── service/
    ├── CameraService.java              # 摄像头服务（门面类）
    ├── BarcodeRecognitionService.java  # 条码识别服务（统一封装 ZXing / Aspose）
    ├── UploadService.java              # 上传服务
    ├── A4PaperDetectorHighCamera.java  # A4 纸张检测（高拍仪视角）
    ├── autocapture/                    # 自动采集相关
    │   ├── AutoCaptureService.java     # 自动采集服务
    │   ├── CapturePipeline.java        # 采集管线（检测 + 识别 + 上传）
    │   ├── FrameChangeDetector.java    # 帧变化检测（判断是否有新纸张放入）
    │   └── BarcodeDeduplicator.java    # 条码去重（防止重复上传）
    ├── barcode/                        # 条码识别实现
    │   ├── ZXingRecognitionEngine.java # ZXing 识别引擎
    │   ├── AsposeRecognitionEngine.java # Aspose 识别引擎
    │   └── OpenCVImagePreprocessor.java # 图像预处理
    └── camera/                         # 摄像头底层实现
        ├── CameraDeviceManager.java    # 设备管理
        ├── CameraGrabberManager.java   # 抓取器管理
        ├── CameraStreamService.java    # 视频流服务
        ├── CameraCaptureService.java   # 拍照服务
        ├── CameraConstants.java        # 摄像头常量配置
        └── CameraMockRenderer.java     # 调试用假数据/虚拟摄像头

src/main/resources/
├── config/
│   └── application.properties          # 配置文件
├── fxml/
│   ├── MainView.fxml                   # 主界面布局
│   ├── ImageDetailView.fxml            # 图片详情布局
│   └── SettingsView.fxml               # 设置界面布局
├── css/
│   └── application.css                 # 样式文件
└── assets/
    ├── windows.png                     # Windows 窗口图标
    └── success.mp3                     # 上传成功提示音
```

## 快速开始

### 环境要求

- Windows 10/11
- Maven 3.6+
- IntelliJ IDEA 2023+
- 高拍仪设备（如得力 GK122）

### 开发调试

**⚠️ 重要：必须使用 IntelliJ IDEA 的 Application 运行配置，不能使用 `mvn javafx:run`，否则无法进行断点调试。**

#### IDEA 运行配置步骤

1. **打开运行配置面板**：`Run → Edit Configurations...` 或 `Alt+Shift+F10` → `Edit Configurations`

2. **添加 Application 配置**：
   - 点击 `+` 按钮
   - 选择 `Application`

3. **配置运行参数**：
   ```
   Main class: com.hylir.receipt.ReceiptCaptureApplication

   VM options:
  --module-path "D:/workspace/crm/org/openjfx/javafx-controls/17.0.1/javafx-controls-17.0.1-win.jar;D:/workspace/crm/org/openjfx/javafx-fxml/17.0.1/javafx-fxml-17.0.1-win.jar;D:/workspace/crm/org/openjfx/javafx-swing/17.0.1/javafx-swing-17.0.1-win.jar;D:/workspace/crm/org/openjfx/javafx-graphics/17.0.1/javafx-graphics-17.0.1-win.jar;D:/workspace/crm/org/openjfx/javafx-base/17.0.1/javafx-base-17.0.1-win.jar;D:/workspace/crm/org/openjfx/javafx-media/17.0.1/javafx-media-17.0.1-win.jar"
  --add-modules javafx.controls,javafx.fxml,javafx.swing,javafx.media
  --add-opens java.base/java.lang=ALL-UNNAMED
  --add-opens javafx.graphics/com.sun.javafx.application=ALL-UNNAMED
  --add-opens javafx.graphics/com.sun.glass.utils=ALL-UNNAMED
   ```
   ![img.png](img.png)
   也可以这样运行
   ![img_1.png](img_1.png)
   > **注意**：请将 `--module-path` 中的路径替换为你本地 Maven 仓库中 JavaFX JAR 的实际路径。

4. **应用并运行**：
   - 点击 `Apply` → `OK`
   - 选择配置 → 点击 `Run` 或 `Debug` 按钮启动

#### 快速编译

```bash
# 编译项目（不运行）
mvn clean compile
```

### 构建安装包

#### 1. 打包前准备

- **操作系统**：必须在 Windows 10/11 上执行打包（jpackage 目标是 Windows EXE）。  
- **JDK**：使用带 JavaFX 的 JDK（推荐上面提到的 BellSoft 发行版）。  
- **Maven**：已安装并在 PATH 中。  
- **WiX Toolset 3.0+**：用于生成 `.exe` 安装包安装器（`jpackage` 在打包 EXE 时会调用 WiX 的工具链），安装后需要将 `candle.exe` / `light.exe` 所在目录加入 `PATH`。  
  建议直接下载官方发布的二进制包（例如 `wix314-binaries.zip`）
- **JavaFX SDK 路径**：仅用于 IDEA 调试时在 VM options 中配置 `--module-path`，**不再需要在 `pom.xml` 中单独配置** 固定路径。

#### 2. 打包步骤

```bash
# 1）清理并打包，生成 fat jar（包含所有依赖）
mvn clean package -DskipTests

# 2）基于 fat jar 生成 Windows 安装包 (exe)
mvn jpackage:jpackage
```

- 打包使用的主类为：`com.hylir.receipt.ReceiptCaptureApplication`。  
- 生成的安装包位置：`target/jpackage/ReceiptCaptureAgent-1.0.0.exe`。  
- 同时会在 `target/` 下生成可直接运行的 fat jar：`receipt-capture-agent-1.0.0.jar`。

## 使用说明

1. **安装运行**：双击 `ReceiptCaptureAgent-1.0.0.exe` 完成安装
2. **选择设备**：从下拉框选择高拍仪设备
3. **点击预览**：启动实时预览画面
4. **自动检测**：将 A4 送货单放在摄像头前，系统自动检测并识别条码
5. **手动输入**：如识别失败，可点击"手动输入"按钮
6. **上传**：确认单号后点击"上传"按钮

### 快捷键

- **F6** - 自动检测并识别条码

### 调试模式

条码识别调试（可选，需在配置中启用）：
- 如果启用了 `barcode.debug.saveImages=true`，条码识别时会保存调试图片到临时目录
- 临时目录：`C:\Users\<用户名>\AppData\Local\Temp\receipt-capture\barcode-debug\`
- 包含原始图片 (`barcode_orig_*.png`) 和裁剪后的图片 (`barcode_crop_tr_*.png`)
- 自动清理：超过配置时间（默认 5 分钟）的文件会被自动删除

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

### 运行时配置

安装目录下的 `receipt-capture-agent.cfg` 文件可配置 JVM 参数：

```properties
--add-opens java.base/java.lang=ALL-UNNAMED
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

### 用户部署（推荐）

1. 双击 `ReceiptCaptureAgent-1.0.0.exe`
2. 按安装向导完成安装
3. 运行桌面快捷方式启动程序

### 开发者打包

```bash
# 完整构建流程（开发者本地打包）
mvn clean package jpackage:jpackage -DskipTests

# 输出目录
# - JAR: target/receipt-capture-agent-1.0.0.jar
# - EXE: target/jpackage/ReceiptCaptureAgent-1.0.0.exe
```

## 技术交底说明

### 总体架构

- **前端壳**：Windows 桌面应用，基于 `JavaFX` + `jpackage` 打包成 `.exe` 安装包。  
- **视频采集与图像处理**：基于 `JavaCV / OpenCV` 进行摄像头采集、A4 纸张检测和图像预处理。  
- **条码识别**：通过 `BarcodeRecognitionService` 统一封装 `ZXing` 与 `Aspose Barcode` 两套识别引擎。  
- **业务编排**：`AutoCaptureController` + `CapturePipeline` 将“预览 → 检测 → 识别 → 上传 → UI 展示”串联起来。  
- **后端交互**：`UploadService` 负责 HTTP 调用，将图片和识别结果上传到后端接口。  

可以简单理解为：**JavaFX UI 壳 + 摄像头服务层 + 自动采集管线 + 上传服务** 四层结构。  

### 核心模块职责速览

- **入口与 UI**
  - `ReceiptCaptureApplication`：JavaFX 应用入口，加载 `MainView.fxml`，设置主窗口。  
  - `MainController`：主界面控制器，只处理 UI 事件和状态展示，不直接做图像/识别逻辑。  
  - `ImageDetailController` / `SettingsController`：图片详情界面 & 设置界面逻辑。  
  - `application.css`：整体 UI 皮肤样式；`IconFactory` / `SoundPlayer` 提供图标和声音效果。  

- **配置与模型**
  - `AppConfig` / `ConfigManager`：读取 `application.properties`，封装运行时配置。  
  - `CaptureResult`：一次采集/识别结果的数据模型。  

- **摄像头与图像处理**
  - `CameraService`：摄像头门面，供 UI 层/控制器调用。  
  - `camera` 包下：真正和 JavaCV / 设备打交道的实现（设备枚举、抓取器、视频流与拍照）。  
  - `A4PaperDetectorHighCamera`：从高拍仪视角检测 A4 纸张，输出裁剪/透视后的图像。  

- **自动采集与业务编排**
  - `AutoCaptureController`（controller 包）：负责和 UI 交互、管理自动采集开关、把帧送入 `AutoCaptureService`。  
  - `AutoCaptureService`：长跑后台任务，驱动 `CapturePipeline`，控制节奏、防抖等。  
  - `CapturePipeline`：一条流水线，内部依次做：帧变化检测 → A4 检测 → 图像预处理 → 条码识别 → 上传。  
  - `FrameChangeDetector`：用来识别“是否放入了新的纸张”，避免对静止画面反复处理。  
  - `BarcodeDeduplicator`：对近期识别到的条码做去重，避免多次上传同一张单据。  

- **条码识别与上传**
  - `BarcodeRecognitionService`：统一入口，内部根据配置/策略调用 `ZXingRecognitionEngine` 或 `AsposeRecognitionEngine`。  
  - `ZXingRecognitionEngine` / `AsposeRecognitionEngine`：具体的识别实现。  
  - `OpenCVImagePreprocessor`：对输入图像进行灰度化、二值化、缩放等预处理，提高识别率。  
  - `UploadService`：负责 HTTP 上传，调用后端 `/api/receipt/upload` 接口。  

- **状态与历史记录**
  - `PreviewManager`：只负责预览的启动/停止、当前帧回调绑定等。  
  - `HistoryManager`：管理右侧/下方面板里的“成功记录列表”（图片缩略图 + 条码信息）。  
  - `StatusUpdateManager`：对状态栏/日志输出做“节流”，避免高频刷新 UI。  

### 关键业务流程（从用户操作视角）

1. **启动应用**
   - 入口 `ReceiptCaptureApplication.main()` → JavaFX `start()` → 加载 `MainView.fxml`。  
   - `MainController.initialize()` 依次做：初始化服务 → 初始化管理器 → 初始化 UI → 测试后端连接和摄像头。  

2. **选择设备**
   - `MainController.initializeDeviceSelection()` 调用 `CameraService.getAvailableScanners()` 获取设备列表。  
   - 用户从下拉框选择设备时，通过 `CameraService.selectDevice(index)` 切换当前摄像头。  

3. **点击“预览”**
   - `MainController.handlePreview()` → `startPreview()`：
     - 禁用设备选择、启用“重置”按钮，预览按钮改为“停止预览”+红色停止图标。  
     - 通过 `PreviewManager.startPreview(...)` 开始抓取帧，并把每帧回调交给 `AutoCaptureController.onFrame(...)`。  
     - 同时 `AutoCaptureController.enable()` 启用自动采集逻辑。  

4. **自动检测与上传**
   - `AutoCaptureController` 收到帧后，将帧推给 `AutoCaptureService / CapturePipeline`。  
   - `CapturePipeline`：
     - 用 `FrameChangeDetector` 判断是否出现“新纸张”。  
     - 调用 `A4PaperDetectorHighCamera` 提取 A4 区域并做透视矫正。  
     - 用 `OpenCVImagePreprocessor` 做图像清洗，然后用 `BarcodeRecognitionService` 做条码识别。  
     - 成功识别后构造 `CaptureResult`，通过 `UploadService` 上传到后端。  
   - 上传成功后，通过回调通知 `HistoryManager.addHistoryItem(...)`，在 UI 上新增一条成功记录。  

5. **停止预览 / 重置**
   - 停止预览：`MainController.stopPreview()` 关闭自动采集、恢复按钮状态、停止帧抓取。  
   - 重置：`handleReset()` 清空历史记录、重建 `UploadService`、调用 `AutoCaptureController.updateUploadService()` 与 `reset()`。  

### 本地开发与调试建议

- **优先使用 IDEA Application 配置调试**  
  不建议用 `mvn javafx:run` 断点调试，按 README 里的 “开发调试” 小节配置 Application 运行项即可。  

- **区分两种运行方式**
  - 开发调试：IDEA Application，依赖 Maven 管理的 JavaFX/Javacv 等 jar。  
  - 生产/测试安装包：先用 `mvn clean package` 生成 fat jar，再用 `mvn jpackage:jpackage` 生成 EXE。  

- **日志查看**
  - 项目使用 `SLF4J + Logback`，默认会在控制台输出日志；EXE 模式下由于 `winConsole=true`，也会弹出控制台窗口，方便看异常。  

### 常见坑与排查思路

- **JDK / JavaFX 相关**
  - 必须使用带 JavaFX 的 JDK（比如 README 推荐的 BellSoft 发行版），否则运行期会报找不到 JavaFX 模块。  
  - 如果 IDEA 里提示 JavaFX 类找不到，优先检查：项目 SDK / Language level、Maven 导入是否正常。  

- **WiX / 打包失败**
  - `jpackage:jpackage` 报找不到 `candle.exe` / `light.exe`：说明 WiX 没装好或没在 `PATH` 中。  
  - 优先检查 WiX 安装目录，与环境变量 PATH 配置。  

- **摄像头无法识别**
  - 检查系统设备管理器里能否看到高拍仪；  
  - 查看日志中 `CameraDeviceManager` / `CameraGrabberManager` 的输出；  
  - 确认没有被其他软件（如厂商自带预览工具）独占。  

- **条码识别率不佳**
  - 优先看保存到临时目录的调试图片（路径见 README“调试模式”章节）；  
  - 通过调整光线、纸张位置等方式先确保图片质量，再考虑调整 `OpenCVImagePreprocessor` 的参数或切换识别引擎。  

---

## 许可证

MIT License
