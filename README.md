# Hinata

Hinata 是一款面向 Android 的**电池信息查看**应用，采用 Material Design 3 界面。应用通过系统 API、`dumpsys battery` 以及（在可用时）经 **root 只读**访问 sysfs 等方式聚合数据，**不会对系统电源或电池配置进行任何写入操作**。

---

## 功能概览

| 模块 | 说明 |
|------|------|
| **数据** | 展示 Root 授权状态、设计容量、当前满充容量、剩余寿命估算、循环次数、电量、温度、电压、电池型号、技术类型等；支持下拉刷新。 |
| **日志** | 展示应用运行过程中的内存环形日志；可清空；可将日志导出为 UTF-8 文本文件。 |
| **关于** | 从工具栏溢出菜单打开，显示版本号、源码仓库链接（可点击），并可在浏览器中打开仓库。 |

部分字段（尤其是容量、循环次数等）在未授予 root 或设备 sysfs 不可读时可能显示为「—」，界面脚注会标明数据来源或不可用原因。

---

## 使用教程

### 安装与首次打开

1. 使用 Android Studio 打开本仓库，连接设备或启动模拟器（**API 不低于 29**，见下文「系统要求」）。
2. 运行 **Run** 安装调试包，或自行执行 `./gradlew assembleRelease` 生成发布包后安装。
3. 启动应用后默认在 **「数据」** 标签页，会自动加载一次电池信息。

### 数据页

- **下拉刷新**：在「数据」页面向下拖动即可重新读取；刷新结束时会提示「已刷新」。
- **Root**：若设备已 root 且本应用获得授权，可读取更多 sysfs 信息；未授予时尽量使用系统 API，部分容量相关数据可能缺失。

### 日志页

- 读取电池信息、异常等会追加到内存日志（有行数上限，旧行会被丢弃）。
- **清空**：清空当前内存中的日志展示，并提示「已清空」。
- **导出**：当日志**非空**时可用。导出文件写入系统 **「下载」** 目录下的 **`AHinata`** 子目录，文件名形如 `Hinata yyyy-MM-dd HH-mm-ss.txt`（时间中的冒号在文件名中用 `-` 代替，以兼容常见文件系统）。导出成功后可选择分享或复制完整路径。

### 关于

- 点击顶部工具栏右侧 **⋮** 菜单中的 **「关于」**。
- 对话框中可查看版本与仓库链接；可使用 **「在浏览器中打开」** 用外部浏览器访问仓库。

---

## 从源码构建

### 环境要求

- **JDK**：11 或以上（与 `app/build.gradle.kts` 中 `JavaVersion.VERSION_11` 一致）。
- **Android SDK**：Compile SDK **36**，**minSdk 29**，**targetSdk 36**。
- **Gradle / AGP**：使用项目自带的 Gradle Wrapper；Android Gradle Plugin 版本见 `gradle/libs.versions.toml`。

### 常用命令

在项目根目录执行：

```bash
# Windows（PowerShell / CMD）
gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```

构建期会尝试执行 `git remote get-url origin`，将规范化后的 HTTPS 地址写入 `BuildConfig.GIT_REPO_URL`，供「关于」对话框展示。若不在 git 仓库中或命令失败，将使用 `app/build.gradle.kts` 中配置的默认仓库地址。

---

## 系统要求

- **Android 10（API 29）及以上**。
- 日志导出到公共下载目录依赖 **Android 10+** 的存储访问方式；更低版本会提示不支持。

---

## 注意事项与免责声明

- 界面中的**剩余寿命**等由可读数据推算，**仅供参考**，不构成维修或更换电池的专业依据。
- 本应用**仅读取**公开或授权可访问的电池相关信息；请自行评估在已 root 设备上授予 shell 权限的风险。
- 仓库若未单独附带开源许可证文件，使用前请自行与作者确认授权方式。

---

## 技术栈（摘要）

- 语言：**Kotlin**
- UI：**Material 3**、ViewBinding、ViewPager2 + TabLayout、SwipeRefreshLayout
- 异步：**Kotlin Coroutines**、Lifecycle

欢迎在 Issue 或 Pull Request 中反馈问题与改进建议。
