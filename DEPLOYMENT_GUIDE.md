# Receipt Capture Agent 部署指南

## 环境要求

### 系统要求
- **操作系统**: Windows 10/11 (64位)
- **Java版本**: Java 17 或更高版本
- **内存**: 至少 2GB RAM
- **磁盘空间**: 至少 500MB 可用空间

### 硬件要求
- **高拍仪**: 得力 GK122 或其他支持 TWAIN 的摄像头
- **USB端口**: 可用 USB 2.0/3.0 端口
- **显示器**: 分辨率至少 1366x768

## 依赖项安装

### 1. Java 17 安装

从 Oracle 官网下载并安装 Java 17：
```
https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html
```

安装完成后，验证版本：
```cmd
java -version
javac -version
```

### 2. 高拍仪驱动安装

#### 得力 GK122 高拍仪
1. 从得力官网或驱动盘安装 USB 驱动
2. 确保 TWAIN 兼容性

#### 通用 TWAIN 驱动
1. 安装摄像头制造商提供的 TWAIN 驱动
2. 验证设备在 Windows 设备管理器中正常显示

### 3. Asprise TWAIN SDK 安装

**重要**: Asprise TWAIN SDK 需要单独获取和安装

1. 访问 [Asprise 官网](https://www.asprise.com/)
2. 下载 TWAIN SDK (Java 版本)
3. 将 JAR 文件添加到项目的 lib 目录，或安装到本地 Maven 仓库

```cmd
# 如果有 JAR 文件，可以安装到本地仓库
mvn install:install-file -Dfile=asprise-scanner.jar -DgroupId=com.asprise -DartifactId=asprise-scanner -Dversion=21.3 -Dpackaging=jar
```

## 项目构建

### 1. 下载项目

```cmd
git clone <repository-url>
cd receipt-capture-agent
```

### 2. 编译项目

```cmd
mvn clean compile
```

### 3. 打包应用

```cmd
mvn clean package
```

打包后的 JAR 文件位于 `target/receipt-capture-agent-1.0.0.jar`

## 配置说明

### 1. 后端 API 配置

编辑 `src/main/resources/config/application.properties`:

```properties
# 修改为实际的后端地址
backend.url=http://your-server:8080/api
backend.upload.endpoint=/receipt/upload

# 根据网络情况调整超时时间
http.connection.timeout=30000
http.read.timeout=60000
```

### 2. 摄像头配置

```properties
# 设置实际的摄像头设备名称
scanner.device.name=GK122
scanner.resolution.dpi=300
```

## 部署步骤

### 1. 创建部署目录

```cmd
mkdir C:\ReceiptCapture
copy target\receipt-capture-agent-1.0.0.jar C:\ReceiptCapture\
copy src\main\resources\config\application.properties C:\ReceiptCapture\
```

### 2. 创建启动脚本

创建 `start.bat`:

```batch
@echo off
echo Starting Receipt Capture Agent...
cd /d C:\ReceiptCapture
java -jar receipt-capture-agent-1.0.0.jar
pause
```

### 3. 创建桌面快捷方式

1. 右键桌面 → 新建 → 快捷方式
2. 目标: `C:\ReceiptCapture\start.bat`
3. 图标: 可以从 JAR 文件中提取或使用自定义图标
4. 名称: "回单采集终端"

## 运行验证

### 1. 启动应用

双击桌面快捷方式或运行启动脚本。

### 2. 验证检查

应用启动后应显示：
- 主界面正常加载
- 状态栏显示"✓ 摄像头连接正常"
- 状态栏显示"✓ 后端服务连接正常"

### 3. 功能测试

1. **拍照测试**:
   - 确保高拍仪已连接并开机
   - 点击"拍照"按钮
   - 验证图片是否正确显示

2. **识别测试**:
   - 使用带有条码的测试单据
   - 验证条码是否正确识别
   - 检查识别结果是否显示在单号字段

3. **上传测试**:
   - 点击"上传"按钮
   - 验证上传状态和结果

## 故障排除

### 摄像头连接问题

**现象**: 应用启动时显示"✗ 摄像头连接失败"

**解决方案**:
1. 检查高拍仪电源和 USB 连接
2. 在设备管理器中确认设备状态
3. 重新安装 TWAIN 驱动
4. 尝试不同的 USB 端口

### 条码识别失败

**现象**: 拍照后显示"✗ 识别失败"

**解决方案**:
1. 确保图片清晰，条码完整显示
2. 调整高拍仪角度和距离
3. 检查条码格式是否支持
4. 尝试重新拍照

### 网络连接问题

**现象**: 上传时显示连接错误

**解决方案**:
1. 检查网络连接
2. 验证后端服务是否运行
3. 确认防火墙设置
4. 检查配置文件中的 URL

### 应用启动失败

**现象**: 双击 JAR 文件无反应或报错

**解决方案**:
1. 确认 Java 17 已正确安装
2. 检查系统 PATH 环境变量
3. 查看命令行错误信息
4. 检查 JAR 文件完整性

## 日志查看

应用运行时会生成日志文件，帮助诊断问题：

- 控制台输出: 启动应用时的命令行窗口
- 日志文件: `logs/receipt-capture.log` (如果配置了文件输出)

## 性能优化

### JVM 参数调优

创建带 JVM 参数的启动脚本:

```batch
@echo off
echo Starting Receipt Capture Agent...
cd /d C:\ReceiptCapture
java -Xms512m -Xmx1024m -jar receipt-capture-agent-1.0.0.jar
pause
```

### 临时文件清理

应用会自动清理临时文件，也可以通过以下方式手动清理：

```cmd
rd /s /q %TEMP%\receipt-capture
```

## 更新部署

### 版本更新

1. 下载新版本 JAR 文件
2. 备份配置文件
3. 替换 JAR 文件
4. 恢复配置文件
5. 重启应用

### 配置更新

直接编辑部署目录下的 `application.properties` 文件，重启应用即可生效。

## 安全注意事项

1. **网络安全**: 确保后端 API 使用 HTTPS
2. **访问控制**: 限制应用部署在授权设备上
3. **数据安全**: 定期清理临时图片文件
4. **权限控制**: 以普通用户权限运行，避免管理员权限

## 技术支持

如遇到部署或使用问题，请：

1. 查看应用日志获取详细错误信息
2. 检查系统环境是否符合要求
3. 参考故障排除章节
4. 联系技术支持团队提供日志和配置信息
