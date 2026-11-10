# ptop

Android 上的极简单色任务管理器：**以 root 身份读 `/proc`**，所以能看到全部进程（不只是自己的）。
界面是 btop 风格的多面板 TUI，只是用 Compose 画在手机上。

```
┌ cpu ──────────────────────────── load 2.10 2.37 1.70 ┐
│ 47%   8 cores 1785MHz              psi.cpu 13%  io 0% │
│ ╱╲___╱╲___╱╲_________________________________________ │
│ 0 ███████░░░░░░░  34%   1 ████████████░░░░  58%       │
│ 2 ███░░░░░░░░░░░  12%   3 █████░░░░░░░░░  21%         │
└───────────────────────────────────────────────────────┘
```

## 特点

- **真 root 视图**：常驻 `su` shell 读 `/proc`，一次快照 ~850 个进程（含 system / 其他 App）。
- **占用低**：纯 Kotlin + Compose，无 Material3、无导航库、无图片资源；R8 全开。
  采样脚本只有一个 `awk` 进程，全量快照约 **100–150ms**，默认 1.5s 刷新一次，可切 1/1.5/2/3/5s。
- **单色极简**：纯黑底 + 5 级灰阶，AMOLED 省电，没有花哨渐变。
- **4 个页面**：over / proc / net / disk。
- **进程操作**：`TERM` `KILL` `STOP` `CONT`（二次确认），进程详情（cmdline / exe / fd / 线程 / maps / status）。

## 页面

| tab | 内容 |
| --- | --- |
| `over` | CPU 总占用 + 折线 + 每核条形、内存/swap、网络与磁盘速率、温度、电池、top 8 进程 |
| `proc` | 全部进程：PID / USER / CPU% / MEM% / THR / NAME；按 cpu/mem/pid/name 排序；搜索过滤；进程树 |
| `net` | 各接口上下行速率、累计流量、吞吐折线 |
| `disk` | 块设备读写速率、内存明细、thermal zones、uptime / load / PSI / 电池 |

点击任意进程打开详情页；点 `proc` 页的 `tree` 切换进程树。

## 安装

从 [Releases](../../releases) 下载 `app-release.apk` 安装。APK 用仓库内自签名证书签名，
升级时请保持安装同一来源的包。

首次打开会请求 root：在 KernelSU / Magisk 弹窗里点**允许**（建议选“记住”）。

## 构建

推送 `main` 或打 tag 时由 GitHub Actions 构建：

```bash
git tag v1.0.0 && git push origin v1.0.0   # 构建并发布 Release
```

本地构建需要 Android SDK 35 + JDK 17：

```bash
./gradlew :app:assembleRelease
```

## 权限说明

- `QUERY_ALL_PACKAGES`：只用于把 uid 映射成包名显示在 USER 列（例如 `u0_a307 → wechat`），不做别的。
- root：全部采样都通过 `su` 执行 `/data/local/tmp/ptop/snap.sh`，脚本在首次启动时从 assets 解压。

## 已知限制

- 只支持单用户 uid 命名（`u0_aN`），多用户 / work profile 会显示成 `u10_aN`。
- 进程详情里的 `maps` 只显示前 24 行。
- 采样脚本依赖 toybox `awk` 的 `getline < file` 与正则，Android 8+ 自带版本可用。
