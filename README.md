# Hinata

Hinata 是一款面向 Android Qualcomm Snapdragon platform 的**电池信息查看**应用，采用 Material Design 3 界面。应用通过 **`dumpsys battery`** 与 **只读 sysfs**（经 Root 下的 `su` 执行 `cat` / 批量读取等）聚合数据，**不会对系统电源或电池配置进行任何写入操作**。

---

## 重要说明（使用前必读）

### 仅适用于已 Root 设备

- 本应用**依赖 Root 权限**读取 `/sys/class/power_supply`、`/sys/class/qcom-battery` 等路径；**未 Root 或拒绝授权时无法正常使用**（将无法完成数据读取）。
- 请在已 root 且可为应用授予 **超级用户 / su** 的设备上使用。

### 仅针对高通骁龙（Qualcomm Snapdragon）平台

- **充电协议**等能力依赖 **`/sys/class/qcom-battery/real_type`** 等**高通内核/驱动暴露的节点**；请在 **高通骁龙平台**设备上使用以获得完整体验。
- 使用 **联发科、麒麟、Exynos** 等其他 SoC 时，部分字段可能缺失、显示为「—」或与机型实际不符，**不属本应用支持范围**。

---

## 功能概览

| 模块 | 说明 |
|------|------|
| **数据** | 展示设计容量、当前满充容量、剩余寿命估算、循环次数、**充电协议（real_type）**、电量、电流/功率、温度、电压、电池型号等；支持下拉刷新与定时刷新 sysfs。 |
| **日志** | 展示应用运行过程中的内存环形日志；可清空；可将日志导出为 UTF-8 文本文件。 |
| **关于** | 从工具栏溢出菜单打开，显示版本号、源码仓库链接（可点击），并可在浏览器中打开仓库。 |

界面脚注会标明数据来源路径或算式说明；部分字段因机型节点差异可能不可用。

---

## 使用教程

### 安装与首次打开

1. 使用 Android Studio 打开本仓库，连接**已 Root**设备（**API 不低于 29**，见下文「系统要求」）。
2. 运行 **Run** 安装调试包，或自行执行 `./gradlew assembleRelease` 生成发布包后安装。
3. 启动应用后授予 **Root**，默认在 **「数据」** 标签页会自动加载电池信息。

### 数据页

- **下拉刷新**：在「数据」页面向下拖动即可重新执行全量读取（含 `dumpsys`）。
- **定时刷新**：在成功读取后，部分字段会按固定间隔批量读取 sysfs 更新（界面脚注以「`>`」标记动态卡片）。

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
- **Android SDK**：Compile SDK **36**，**minSdk 29**，**targetSdk 36**（见 `app/build.gradle.kts`）。
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
- **已 Root**（必须）。
- **高通骁龙平台**（推荐；完整功能以该机型的 sysfs / `qcom-battery` 节点为准）。
- 日志导出到公共下载目录依赖 **Android 10+** 的存储访问方式；更低版本会提示不支持。

---

## 注意事项与免责声明

- 界面中的**剩余寿命**等由可读数据推算，**仅供参考**，不构成维修或更换电池的专业依据。
- 本应用**仅读取**授权可访问的电池相关信息；在已 root 设备上授予 shell 权限的风险请自行评估。
- **非高通平台**或**未 Root**导致的显示缺失、异常，不视为应用缺陷。
- 仓库若未单独附带开源许可证文件，使用前请自行与作者确认授权方式。

---

## 技术栈（摘要）

- 语言：**Kotlin**
- UI：**Material 3**、ViewBinding、ViewPager2 + TabLayout、SwipeRefreshLayout
- 异步：**Kotlin Coroutines**、Lifecycle

欢迎在 Issue 或 Pull Request 中反馈问题与改进建议。
