# IntelliJ IDEA Application 运行配置 - 调试模式

## 配置步骤

### 1. 创建 Application 运行配置

1. 在 IntelliJ 中，点击顶部菜单 **Run** → **Edit Configurations...**
2. 点击左上角的 **+** 按钮
3. 选择 **Application**
4. 填写以下配置：

#### 基本配置
- **Name**: `Debug Receipt App`
- **Main class**: `com.hylir.receipt.ReceiptCaptureApplication`
- **Use classpath of module**: `receipt-capture-agent`
- **Working directory**: `D:\hylir\front-end\receipt-capture-agent`

#### VM options（重要！）
将以下内容复制到 **VM options** 字段：

```
--module-path "D:/workspace/crm/org/openjfx/javafx-controls/17.0.10/javafx-controls-17.0.10-win.jar;D:/workspace/crm/org/openjfx/javafx-fxml/17.0.10/javafx-fxml-17.0.10-win.jar;D:/workspace/crm/org/openjfx/javafx-swing/17.0.10/javafx-swing-17.0.10-win.jar;D:/workspace/crm/org/openjfx/javafx-graphics/17.0.10/javafx-graphics-17.0.10-win.jar;D:/workspace/crm/org/openjfx/javafx-base/17.0.10/javafx-base-17.0.10-win.jar" --add-modules javafx.controls,javafx.fxml,javafx.swing
```

**注意**：如果 Maven 本地仓库路径不同，请根据实际情况修改路径。

### 2. 验证配置

1. 点击 **OK** 保存配置
2. 在代码中设置断点（例如：`ReceiptCaptureApplication.java` 第 27 行）
3. 选择 **Debug Receipt App** 配置
4. 点击 **Debug** 按钮（虫子图标）启动
5. 断点应该会正常停止

### 3. 如果路径不同

如果你的 Maven 本地仓库路径不是 `D:/workspace/crm`，请：

1. 找到你的 Maven 本地仓库路径（通常在 `~/.m2/repository` 或 `settings.xml` 中配置的路径）
2. 替换 VM options 中的所有路径前缀
3. 确保所有 JavaFX jar 文件都存在

### 4. 快速检查 JavaFX jar 是否存在

运行以下 PowerShell 命令检查：

```powershell
$mavenRepo = "D:/workspace/crm"  # 修改为你的 Maven 仓库路径
$javafxVersion = "17.0.10"
$modules = @("javafx-controls", "javafx-fxml", "javafx-swing", "javafx-graphics", "javafx-base")
foreach ($module in $modules) {
    $path = "$mavenRepo/org/openjfx/$module/$javafxVersion/$module-$javafxVersion-win.jar"
    if (Test-Path $path) {
        Write-Host "✓ $module" -ForegroundColor Green
    } else {
        Write-Host "✗ $module - 文件不存在: $path" -ForegroundColor Red
    }
}
```

## 常见问题

### 问题 1: "缺少 JavaFX 运行时组件"
- **原因**: VM options 中的模块路径不正确或 jar 文件不存在
- **解决**: 检查 Maven 仓库路径，确保所有 JavaFX jar 文件都存在

### 问题 2: 断点不停
- **原因**: 代码没有重新编译，或断点被禁用
- **解决**: 
  1. **Build** → **Rebuild Project**
  2. 检查顶部工具栏的"跳过所有断点"按钮是否被按下
  3. 在 **Run** → **View Breakpoints** 中检查断点状态

### 问题 3: 找不到主类
- **原因**: 类路径配置不正确
- **解决**: 确保 **Use classpath of module** 选择了正确的模块

## 为什么 Application 配置可以调试，而 Maven 配置不行？

### 主要原因分析

#### 1. **类加载器和类路径映射问题**

**Maven 配置 (`javafx:run`)**:
- Maven 通过 `javafx-maven-plugin` 启动程序
- 插件会动态构建类路径，可能使用不同的类加载器
- IntelliJ 的调试器可能无法正确映射源码行号到字节码
- 调试器连接的是 Maven 启动的 JVM，但源码映射可能不准确

**Application 配置**:
- IntelliJ 直接启动 Java 进程，完全控制类路径
- 使用标准的类加载器，调试器可以准确映射源码
- 源码和字节码的对应关系更清晰

#### 2. **JavaFX 模块系统**

**Maven 配置**:
- `javafx-maven-plugin` 内部处理 JavaFX 模块路径
- 可能使用特殊的启动方式，影响调试器对模块的识别

**Application 配置**:
- 明确指定了 `--module-path` 和 `--add-modules`
- 调试器可以清楚地知道哪些模块被加载

#### 3. **调试信息映射**

**Maven 配置**:
- 虽然编译时包含了调试信息（`debug=true`）
- 但 Maven 插件启动时可能使用了不同的工作目录或类路径
- 导致调试器无法正确找到对应的源码文件

**Application 配置**:
- IntelliJ 直接管理编译输出和源码映射
- 工作目录和类路径都是 IDE 控制的
- 调试器可以准确找到源码位置

#### 4. **JVM 参数传递**

**Maven 配置**:
- 调试参数通过 Maven 传递，可能被插件修改或过滤
- 某些 JVM 参数可能不完整

**Application 配置**:
- 所有 JVM 参数（包括调试参数）由 IntelliJ 直接控制
- 确保调试器正常工作所需的所有参数都正确设置

### 总结

**Application 配置的优势**:
- ✅ 直接控制 JVM 启动参数
- ✅ 清晰的类路径和模块路径
- ✅ 准确的源码映射
- ✅ IntelliJ 完全管理调试过程

**Maven 配置的限制**:
- ⚠️ 通过插件间接启动，可能影响调试器
- ⚠️ 类加载器可能不同
- ⚠️ 源码映射可能不准确

### 建议

**开发调试时**: 使用 **Application 配置**（推荐）
- 断点工作正常
- 调试体验更好
- 变量查看更准确

**构建和部署时**: 使用 **Maven 配置**
- 确保构建过程正确
- 验证打包配置
- 测试实际运行环境

## 替代方案：使用 Maven 配置

如果 Application 配置有问题，可以继续使用 Maven 配置：

1. 在 Maven 面板中找到 `javafx:run`
2. 右键 → **Debug**
3. 确保代码已重新编译（**Build** → **Rebuild Project**）

**注意**: Maven 配置的断点可能不稳定，如果遇到断点不停的问题，建议切换到 Application 配置。

