# Receipt Capture Agent 核心流程时序说明

## 完整业务流程

```mermaid
sequenceDiagram
    participant U as 用户
    participant UI as 主界面 (MainController)
    participant SS as 摄像头服务 (ScannerService)
    participant BS as 条码识别服务 (BarcodeRecognitionService)
    participant US as 上传服务 (UploadService)
    participant TWAIN as TWAIN驱动
    participant BE as 后端Spring Boot API

    Note over U,BE: 应用启动和初始化
    UI->>SS: initialize()
    SS->>TWAIN: 初始化TWAIN库
    UI->>BS: new BarcodeRecognitionService()
    UI->>US: new UploadService()
    UI->>UI: 初始化UI组件和事件监听

    Note over U,BE: 用户点击拍照按钮
    U->>UI: 点击"拍照"按钮
    UI->>UI: captureButton.setDisable(true)
    UI->>UI: progressBar.setVisible(true)
    UI->>UI: appendStatus("正在拍照中...")

    Note over SS,TWAIN: 拍照流程
    UI->>SS: captureImage() [异步任务]
    SS->>TWAIN: Scanning.scan(request)
    TWAIN-->>SS: 返回图片文件路径
    SS->>SS: ImageIO.read() 读取图片
    SS-->>UI: 返回图片路径和BufferedImage

    Note over BS,UI: 条码识别流程
    UI->>BS: recognizeBarcodeWithRetry(image, 3)
    loop 重试最多3次
        BS->>BS: MultiFormatReader.decode()
        alt 识别成功
            BS-->>UI: 返回条码文本
        else 识别失败
            BS->>BS: rotateImage() 旋转图片
        end
    end

    Note over UI,BE: 结果处理和上传
    alt 识别成功
        UI->>UI: 显示识别到的单号
        UI->>UI: receiptNumberField.setText(barcode)
        UI->>UI: uploadButton.setDisable(false)
        UI->>US: uploadCaptureResult(result)
        US->>US: 构建MultipartBody
        US->>BE: POST /api/receipt/upload
        BE-->>US: 返回上传结果
        US-->>UI: 上传成功/失败
    else 识别失败
        UI->>U: 显示失败提示对话框
        UI->>UI: manualInputButton.setDisable(false)
        alt 用户选择手动输入
            U->>UI: 点击"手动输入"按钮
            UI->>U: 显示输入对话框
            U->>UI: 输入单号
            UI->>UI: receiptNumberField.setText(manualInput)
            UI->>US: uploadCaptureResult(result)
        else 用户选择重新拍照
            U->>UI: 点击"拍照"按钮 (重新开始)
        end
    end

    Note over UI,UI: 流程结束
    UI->>UI: captureButton.setDisable(false)
    UI->>UI: progressBar.setVisible(false)
    UI->>UI: appendStatus("操作完成")
```

## 关键状态转换

### UI状态机

```mermaid
stateDiagram-v2
    [*] --> 就绪: 应用启动
    就绪 --> 拍照中: 点击拍照按钮
    拍照中 --> 识别中: 拍照完成
    识别中 --> 识别成功: 条码识别成功
    识别中 --> 识别失败: 条码识别失败
    识别成功 --> 上传中: 点击上传按钮
    识别失败 --> 手动输入: 点击手动输入
    手动输入 --> 上传中: 输入单号后点击上传
    上传中 --> 上传成功: 上传完成
    上传中 --> 上传失败: 上传出错
    上传成功 --> 就绪: 清理状态
    上传失败 --> 就绪: 可重试
    识别失败 --> 拍照中: 重新拍照
```

## 异常处理流程

### 摄像头异常
1. TWAIN 初始化失败 → 显示错误对话框，退出应用
2. 设备未连接 → 显示警告，允许重试
3. 拍照超时 → 取消操作，允许重试

### 识别异常
1. 图片质量差 → 提示重新拍照
2. 不支持的条码格式 → 提示手动输入
3. 识别超时 → 自动重试或提示手动输入

### 网络异常
1. 连接超时 → 显示错误，可重试
2. 服务器错误 → 显示错误，可重试
3. 认证失败 → 提示检查配置

## 数据流说明

### 输入数据流
```
用户操作 → JavaFX事件 → Controller方法 → Service调用 → 外部组件
```

### 输出数据流
```
外部组件 → Service返回 → Controller处理 → UI更新 → 用户反馈
```

## 性能考虑

### 异步处理
- 拍照、识别、上传都使用 `Task` 在后台线程执行
- UI线程只负责界面更新
- 使用 `Platform.runLater()` 确保UI操作在正确线程

### 资源管理
- 临时文件定期清理
- HTTP连接池复用
- 图片对象及时释放

### 错误恢复
- 网络失败自动重试
- 识别失败提供备选方案
- 操作失败保持应用稳定
