# Android 构建与运行命令手册（本项目）

本文档面向刚接触 Android 构建环境的同学，按“可直接复制执行”的方式整理本项目常用命令。

适用项目：`watch-tower-andriod`
主要技术栈：Android 原生（Kotlin + Gradle + Compose）
应用包名：`com.watchtower.android`

## 1. 进入项目目录

```powershell
cd F:\test\watch-tower-andriod
```

## 2. 基础环境检查

### 2.1 检查 Java

本项目使用 Java 17 编译（见 `app/build.gradle.kts` 的 `sourceCompatibility = 17`）。

```powershell
java -version
```

### 2.2 检查 Android SDK 路径

本项目当前 `local.properties` 中配置的是：

```properties
sdk.dir=D:\\Andriod\\AndriodStudio
```

查看配置：

```powershell
Get-Content .\local.properties
```

如果你机器上的 SDK 路径不一样，请改成你自己的实际路径。

### 2.3（可选）设置当前终端环境变量

```powershell
$env:ANDROID_SDK_ROOT = "D:\Andriod\AndriodStudio"
$env:ANDROID_HOME = $env:ANDROID_SDK_ROOT
$env:Path += ";$env:ANDROID_SDK_ROOT\platform-tools"
$env:Path += ";$env:ANDROID_SDK_ROOT\emulator"
$env:Path += ";$env:ANDROID_SDK_ROOT\cmdline-tools\latest\bin"
```

验证：

```powershell
adb version
sdkmanager.bat --version
```

## 3. 模拟器（AVD）相关命令

### 3.1 查看已有模拟器

```powershell
& "D:\Andriod\AndriodStudio\emulator\emulator.exe" -list-avds
```

当前机器示例输出（你本机可能不同）：
- `Medium_Phone_API_36.1`
- `Medium_Tablet`

### 3.2 启动模拟器

```powershell
& "D:\Andriod\AndriodStudio\emulator\emulator.exe" -avd Medium_Phone_API_36.1
```

### 3.3 查看设备是否连上

```powershell
& "D:\Andriod\AndriodStudio\platform-tools\adb.exe" devices
```

## 4. 构建与安装命令（Gradle）

以下命令都在项目根目录执行。

### 4.1 构建 Debug APK

```powershell
.\gradlew.bat assembleDebug
```

产物路径：
`app\build\outputs\apk\debug\app-debug.apk`

### 4.2 安装到已连接设备/模拟器

```powershell
.\gradlew.bat installDebug
```

### 4.3 清理构建缓存

```powershell
.\gradlew.bat clean
```

### 4.4 查看可用任务

```powershell
.\gradlew.bat tasks
```

## 5. 启动 App 与查看日志

### 5.1 命令行启动主界面

```powershell
& "D:\Andriod\AndriodStudio\platform-tools\adb.exe" shell am start -n com.watchtower.android/.MainActivity
```

### 5.2 查看实时日志（全部）

```powershell
& "D:\Andriod\AndriodStudio\platform-tools\adb.exe" logcat
```

### 5.3 只看本应用日志（推荐）

```powershell
$pkg = "com.watchtower.android"
$pid = (& "D:\Andriod\AndriodStudio\platform-tools\adb.exe" shell pidof $pkg).Trim()
& "D:\Andriod\AndriodStudio\platform-tools\adb.exe" logcat --pid=$pid
```

## 6. 首次从零启动的推荐顺序

1. 打开一个 PowerShell，进入项目目录。
2. 启动模拟器（命令见 3.2），等待开机完成。
3. 另开一个 PowerShell，执行 `.\gradlew.bat installDebug`。
4. 若未自动打开 App，执行 5.1 的 `adb shell am start` 命令。
5. 需要排查问题时，用 5.3 看日志。

## 7. 常见问题与处理

### 7.1 `Timeout ... waiting for exclusive access to ... gradle-8.14.3-bin.zip`

含义：Gradle Wrapper 缓存被其他进程占用（常见于后台 Android Studio/Gradle Java 进程）。

处理步骤：

```powershell
# 1) 先关闭 Android Studio

# 2) 查看 Java/Gradle 进程
Get-Process | Where-Object { $_.ProcessName -like 'java' -or $_.ProcessName -like 'gradle*' }

# 3) 确认无关后结束占用进程（谨慎）
Get-Process java | Stop-Process -Force

# 4) 重新构建
.\gradlew.bat assembleDebug
```

### 7.2 `adb devices` 看不到设备

1. 确认模拟器已完全开机。
2. 重启 ADB：

```powershell
& "D:\Andriod\AndriodStudio\platform-tools\adb.exe" kill-server
& "D:\Andriod\AndriodStudio\platform-tools\adb.exe" start-server
& "D:\Andriod\AndriodStudio\platform-tools\adb.exe" devices
```

### 7.3 SDK 路径不对

修改 `local.properties` 的 `sdk.dir` 为你机器上的真实 SDK 根目录。

---

如果你愿意，我可以下一步再给你补一个 `scripts/dev-android.ps1` 一键脚本（自动启动模拟器、等待设备上线、安装并启动应用）。
