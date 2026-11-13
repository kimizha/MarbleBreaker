# 小游戏 · MarbleBreaker

原创、完全离线的 Android 弹珠消砖游戏。Kotlin + 原生 Canvas，无网页、广告、账号或联网权限。

## 安装 APK

打开本仓库的 [Actions](https://github.com/kimizha/MarbleBreaker/actions/workflows/android.yml)，选择最新成功构建，下载 **小游戏-Android-APK** Artifact，解压后安装 **小游戏.apk**。SHA256SUMS.txt 可用于校验，COMMIT.txt 标记实际构建提交。需登录 GitHub 下载 Artifact；默认保留 90 天。当前为 Debug 签名，只供测试安装，正式发行应配置固定的 Release 密钥。

## 玩法

按住游戏区域拖动瞄准，松手依次发射；瞄准方向跟随手指，始终向上且限制水平角度。弹珠全部回收后，目标向下移动，第一颗回收位置成为下次发射位置。消灭全部彩色目标即可过关；目标到达回收线或回合耗尽则失败，可以重试。

- 初始 3 颗弹珠，45 ms 间隔，上限 150；新增数量下回合生效。
- 普通、强化、高生命值彩色方块，固定墙、可破坏墙、倾斜反弹板、移动障碍。
- 增长道具 +1、+3、+5、×2；特殊道具爆炸、穿透、分裂、横向激光、减速、双倍伤害。
- 30 个不同的程序化手工规则关卡，按 5 关分组逐步加入障碍；通关解锁。
- 无限模式持续生成新波次，每波重置弹珠、回合及本波分数，最高分记录最佳单波。
- 首页、选关、暂停、失败重试、通关、设置，全部简体中文。
- 原创程序合成音效及可选背景音乐；可关闭震动，低性能模式降低绘制帧率和粒子数量。
- 自动保存进行中的弹珠、待发射队列、道具和进度。进入后台自动暂停，回到首页后可继续。
- 原子写入、版本号及 CRC32 完整性校验，单独保存已解锁关卡，避免单局存档损坏覆盖已完成进度。

道具以球体留在被击碎方块的位置，需要弹珠实际经过后拾取；特殊效果有次数或时长限制，分裂弹珠与待发射队列合计至多 150。所有目标可被清除；不可破坏障碍无需清除。

## 工程与构建

- 包名：com.kimizha.minigame；应用名称：小游戏。
- Android 8.0 / API 26 起；compileSdk / targetSdk 36（Android 16）。
- JDK 17、AGP 8.13.1、Kotlin 2.2.21、Gradle 8.13。
- AndroidX Activity 处理系统返回手势；JUnit 4 用于纯 Kotlin 单元测试。
- 唯一权限是可关闭的震动反馈所需 VIBRATE。
- Canvas 自定义 View，竖屏，系统栏与刘海安全区域内等比显示。

首次 CI 会从 Gradle v8.13.0 官方仓库取得 Wrapper JAR，以固定 Git blob 哈希校验并自动提交到本仓库；后续构建直接使用已经提交的 Wrapper。gradlew 和 Windows 启动脚本已包含。配置 Android SDK 后执行：

```sh
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

APK 原始路径：app/build/outputs/apk/debug/app-debug.apk。CI 自动改名为 小游戏.apk，并生成 SHA-256。

## 结构

- core/Model.kt：独立数据模型、规则、进度和空间网格。
- core/Physics.kt：连续圆形与旋转矩形碰撞，圆角接触，移动物体相对速度反射。
- core/Engine.kt：1/240 秒固定步长，连续发射、道具、回合与胜负状态。
- core/Levels.kt：30 关及可复现种子的无限地图。
- core/SaveCodec.kt：不依赖 Android 的二进制存档格式。
- GameView.kt：Canvas 绘制、触控、界面和受控粒子效果。
- Audio.kt / LocalStore.kt：原创 PCM 合成、原子存档和设置。
- scripts/smoke.py：CI Android 16 模拟器安装、启动、触控和后台恢复检查。

## 验证与限制

包含 17 个单元测试，覆盖高速碰撞、圆角、倾斜与移动障碍、瞄准角度、方块生命值、连续发射、道具收集与上限、效果重置、激光、真实物理通关、超时回收、失败流程、解锁、全部关卡结构、随机地图开放路径、磁盘存档恢复及损坏校验。

GitHub Actions 执行 Kotlin 编译、JUnit、Android lint 和 Debug APK 构建，再在 Android 16 模拟器安装启动、验证发射回合、后台自动暂停、强制结束进程后的存档恢复和崩溃日志，并上传截图。**测试是否通过以实际 Actions 结果为准，包含测试代码不代表已经通过。**

尚需真机验证多品牌全面屏、长时间 150 球性能、音频延迟与全部 30 关的人工难度平衡。随机地图的测试验证无固定障碍封闭目标，不等于已穷举所有反弹轨迹。目标为常规模式约 60 FPS，低性能模式约 30 FPS，未做真机帧率承诺。自绘按钮支持触摸及基础无障碍提示，尚未实现 TalkBack 独立焦点导航。

## 素材

全部图形由 Canvas / VectorDrawable 绘制，音效与旋律由本工程原创算法合成。未使用参考游戏的素材、界面或代码，运行不下载任何资源。不包含签名私钥、密码或 Token。
