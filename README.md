# 洛克精灵册《洛克王国世界》异色图鉴 & 全精灵图鉴

一个用于《洛克王国世界》的 Android 原生应用：**异色（闪光）收集追踪 + 全精灵离线图鉴 + 赛季出没时间表**。

- 语言：Kotlin；布局：XML（原生 View）
- 最低系统：Android 7.0（API 24）；目标系统：Android 14（API 34）
- 应用名：**洛克精灵册**｜包名 `com.example.pokemondex`

## 功能特性

### 1) 异色追踪
- 内置 **S1–S4 全部赛季数据**（RocoM Shiny Helper 数据，同步自洛克王国世界 WIKI）：
  - S4「月涌狂想」：金月异色 9 只 / 赤月异色 8 只（含 常驻异色 / 赛季奇遇 子分类）
  - S3「铅字幻梦」、S2「狂欢怪谈」、S1「暗夜拾光」：按 赛季奇遇 / 常驻异色 分组
- 顶部赛季下拉切换（显示如 `S4 · 月涌狂想`，默认 S4）；标签页随赛季自动使用网站叫法（S4 为 金月异色/赤月异色，S1–S3 为 赛季奇遇/常驻异色）。
- 每行：精灵立绘头像（**解锁后自动切换为异色形态图**，S4 暂以现有立绘/预览图展示）、名称、属性图标、常驻/赛季标签。
- **「解锁模式」开关**：关闭时只读防误触；打开后点击整行精灵即可解锁（「已解锁」打勾，头像切异色），再点取消。
- 解锁状态保存在 SharedPreferences（key 为 `赛季名_精灵名`），切换赛季/重启不丢失；顶部实时显示 当前分类已解锁数/总数 与 赛季总进度百分比。
- 顶部主题色条随赛季变化；标签高亮使用网站金 `#D4A843`、赤 `#C0392B` 配色。

### 2) 特殊异色（通行证）
- 赛季下拉中独立条目 **「特殊异色」**（紫色主题），内置 9 只通行证/特殊异色精灵：
  - S1 通行证：疾光千兽、绒仙子；特殊：雅丹鬃
  - S2 通行证：雪怪、爆焰飞龙；S3 通行证：胡桃王子、离心舞者；S4 通行证：月使鹭纳、圣凯布米龙
- 名单依据官方/社区资料核对；来源标签如 `S1通行证`、`S2通行证` 等。

### 3) 本周大量出没
- 顶部「本周大量出没」卡片：自动按当前日期高亮本周出没精灵 + 日期 + 精灵球；点「▾ 查看全部」查看完整 **S4 大量出没时间表**（8 只）。
- 底部导航第三个「出没时间」页展示全部出没条目（头像 + 日期 + 精灵球）。

### 4) 全精灵图鉴
- 内置 **612 条精灵/形态记录**（编号覆盖 1–1024）：名称、编号、属性、蛋组、种类、身高/体重、总种族值、获取方式、图鉴描述、立绘（**512px 高清版，全部离线**）。
- **搜索**（名称/编号/种类）、**属性筛选**、**蛋组筛选**、**编号排序（升/降）**、**筛选区默认折叠**（点「筛选 ▾」展开）。
- 点击条目弹出详情卡。

### 5) App 内更新检测
- 启动联网查询 GitHub Releases（`api.github.com/repos/MengBi840/roco-shiny-updates/releases/latest`），发现新版弹出「发现新版本 vX.X」并可**一键下载**（系统 DownloadManager）。
- 请先在 AndroidManifest 保留 `INTERNET` 权限；发布新版本后 App 启动即提示。

## 界面布局
- 主界面**底部导航三页**：异色追踪 / 精灵图鉴 / 出没时间。
- 清新卡通风（天空蓝渐变背景、圆角白色面板、卡片列表、药丸形标签）。

## 数据来源与版权
- 赛季数据与图片：整理自 [RocoM Shiny Helper](https://jayegt002.github.io/Rocom-Shiny-Helper/)（GitHub: JayeGT002/Rocom-Shiny-Helper），数据同步自洛克王国世界 WIKI，遵循 CC BY-NC-SA 4.0。
- 全精灵图鉴数据与立绘：整理自 [rocokingdomworld.org](https://rocokingdomworld.org/zh/pokedex) 粉丝图鉴站（2026-09 快照），属性以该站精灵页展示为准。
- 「S4 大量出没时间表」：来自官方/社区资讯（九游 S1 详解、电玩帮 S2 爆料、TapTap S4 爆料等）与玩家提供的表格。
- 游戏图片、动画、文本版权归《洛克王国：世界》项目组所有。本应用仅供个人学习/自用追踪，**请勿商用或批量再分发**。

## 项目结构
```
RocoShinyDex/
├── build.gradle / settings.gradle / gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
├── local.properties              本机 Android SDK 路径（不入库）
├── keystore.properties           签名凭据（不入库）
├── keystore/release.jks          正式签名库（务必备份，勿外发）
├── README.md
├── tools/publish_release.ps1     一键发布 Apk 到 GitHub Releases
└── app/
    ├── build.gradle              版本号、release 签名配置
    └── src/main/
        ├── AndroidManifest.xml   含 INTERNET 权限
        ├── assets/
        │   ├── default_s4_data.json            S4 内置数据（金月/赤月）
        │   ├── default_legacy_seasons.json     S1–S3 内置数据
        │   ├── default_special_seasons.json    特殊异色（通行证）
        │   ├── default_dex.json                全精灵图鉴（612 条）
        │   ├── default_encounters.json         S4 大量出没时间表
        │   └── img/  S1-S4/ dex/ attrs/  special/  精灵/属性/图鉴/高清立绘
        ├── java/com/example/pokemondex/
        │   ├── MainActivity.kt   主界面（追踪/出没/更新检测）
        │   ├── DexActivity.kt    全精灵图鉴（搜索/筛选/排序/详情）
        │   ├── Pokemon.kt / SeasonData.kt / DexEntry.kt
        │   ├── PokemonAdapter.kt / UpdateChecker.kt
        └── res/  layout/{activity_main,dex_activity,item_pokemon,item_dex}.xml
                   drawable/bg_*.xml
                   mipmap-*/ic_launcher.png   应用图标（圣羽翼王）
```

## 环境要求
- JDK 17；Android Studio（Hedgehog 2023.1.1+）；Android SDK Platform 34；命令行构建需 Gradle 8.4。

## 方式一：Android Studio 构建
1. `File → Open` 选择项目根目录（含 `settings.gradle`），等待 Gradle Sync（首次联网下载依赖）。
2. `Build → Build APK(s)` 得到 debug 包；或 `Build → Generate Signed App Bundle or APK` 生成正式包。

## 方式二：命令行 gradlew assembleDebug
若项目内还没有 wrapper 文件：先安装 Gradle 8.4 并在项目根执行一次 `gradle wrapper --gradle-version 8.4`（在 Android Studio 中同步过则已生成）。
```
cd RocoShinyDex
gradlew.bat assembleDebug        # Windows
./gradlew assembleDebug          # macOS / Linux
```
命令行构建需 SDK 位置：项目根 `local.properties` 写 `sdk.dir=C:\\Users\\<用户名>\\AppData\\Local\\Android\\Sdk`，或设置环境变量 `ANDROID_HOME`。

**APK 输出路径：**
- Debug：`app/build/outputs/apk/debug/app-debug.apk`
- Release：`app/build/outputs/apk/release/app-release.apk`（已签名，若 `keystore.properties` 存在）

## 正式签名与发布
- 签名配置自动读取 `keystore.properties`（`storeFile`/`storePassword`/`keyAlias`/`keyPassword`），已 gitignore 不入库。
- 发布脚本：
```
$env:GITHUB_TOKEN = "ghp_..."
powershell -NoProfile -ExecutionPolicy Bypass -File tools\publish_release.ps1 -Version 1.4 -ApkPath "app\build\outputs\apk\release\app-release.apk"
```
脚本会自动：确保公开仓库 `MengBi840/roco-shiny-updates` 存在 → 建 Release `v1.4` → 上传 APK。之后再次启动 App 即会提示更新。

## 版本记录
- **v1.0**：赛季异色追踪初版（Spinner 赛季切换、金星/赤星预览、默认 S4）
- **v1.1**：全精灵图鉴分区（612 条 + 搜索/属性/蛋组/编号排序/详情）、应用名改为「洛克精灵册」
- **v1.2**：特殊异色（通行证）分区、数据迁移兼容
- **v1.3**：本周大量出没卡片 + 出没时间页、底部导航三页、图鉴筛选折叠、图鉴立绘升级 512px 高清、基于 GitHub Releases 的 App 内更新检测与发布脚本

## 数据文件与 JSON 格式（维护者）
- 赛季数据见 `default_s4_data.json` / `default_legacy_seasons.json`：`season`、`name`、`themeColor`、`groups`、`pokemon[{name,type,attr1,attr2,category,img}]`。
- 特殊异色 `default_special_seasons.json`：结构同上（单 `groups`，`category` 标来源赛季）。
- 图鉴 `default_dex.json`：`{"dex":[{no,name,attr1,attr2,egg,species,height,weight,total,obtain,desc,img(slug)}]}`。
- 出没 `default_encounters.json`：`{"encounters":[{pet,season,start,end,ball}]}`（日期 `MM-dd`）。
- 升级数据后请同步**提升 `DATA_VERSION`**（`MainActivity` 常量）并重新打包，App 会自动重建内置数据、保留用户解锁记录。

## 常见问题
- `SDK location not found`：在 `local.properties` 配置 `sdk.dir`。
- `Unsupported class file major version`：使用 JDK 17。
- 更新提示不出现：确认已发布更高 tag 的 Release、手机可联网、Manifest 含 `INTERNET` 权限。
- 分享 APK 时**不要**连同 `keystore` 与 `keystore.properties` 发送（等于交出私钥）。
