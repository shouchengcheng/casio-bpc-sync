# BPC 授时（安卓）

和网页同一套 BPC 编码。手机通过有线耳机播放 13.7 kHz，喇叭贴在表背，让电波表接收中国 BPC。已在卡西欧 5610 的网页版上验证过同一套信号。

应用名是「BPC 授时」，包名 `com.shouchengcheng.bpcsync`。界面可以在中文和 English 之间切换。优先把声音送到 3.5 mm 或 USB-C 有线耳机；蓝牙和外放会提示，因为手表收不到。

## 下载

当前正式包：[bpc-sync-1.0.0.apk](https://github.com/shouchengcheng/casio-bpc-sync/releases/download/v1.0.0/bpc-sync-1.0.0.apk)。拷到手机上打开安装。这是本机签名的 Release 包，不是应用商店上架包。

## 编译 APK

用 Android Studio 打开这个 `android` 目录，等待 Gradle 同步完成后：

- 调试包：Build → Build Bundle(s) / APK(s) → Build APK(s)。产物在 `app/build/outputs/apk/debug/app-debug.apk`。
- 装到手机：手机打开开发者选项里的 USB 调试，Android Studio 点 Run。

需要 JDK 17 和 Android SDK 35。这台开发机如果还没装 Android Studio，先安装它，第一次打开时会带上 SDK。

## 使用

1. 插上 3.5 mm 或 USB-C 有线耳机。蓝牙无效。
2. 打开应用，等状态出现时间来源，不要是红色。
3. 点「音量最大」，再点「开始」。
4. 耳机喇叭贴住表背，手表强制接收，保持大约 3 到 8 分钟。屏幕可以关掉，通知栏会保持发射。
5. 若失败：换载波或波形，勾选反相，或把延迟微调每次移动 20 毫秒。正值表示提前发射。

## 以后上架

当前是未签名的调试包结构，还没有上架。上架前需要：正式签名密钥、隐私政策（应用只访问公网时间，不收集账号）、商店截图，以及把 Casio 这类商标留在说明里的兼容性描述中，不放进应用名。
