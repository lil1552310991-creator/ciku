# AI 开发规则 — 辱诺大杂烩项目

> 本文件为 AI 助手的项目外置记忆。每次修改项目时遵循以下规则。

---

## 核心原则

1. **改代码必须同步文档**：每次修改 `main.java` 后，必须同步更新 `info.prop`、`desc.txt`、`updlog.md`、`用前必看.md`
2. **不要全局设置**：所有配置项必须属于每个目标用户（TargetRule），全局只保留「生效群号」和「云端词库地址」
3. **UI 保持简洁**：控制台默认只显示目标列表，添加用户的表单折叠起来，点击才展开
4. **有问题先看 error.txt**：用户手机模块目录里有报错文件，崩溃时先读取分析

---

## 项目文件职责

| 文件 | 用途 | 更新规则 |
|------|------|---------|
| `main.java` | 主代码 | 实现功能 |
| `info.prop` | 元信息 | 版本号 `version=` |
| `desc.txt` | 功能描述 | 版本号、功能介绍、更新说明 |
| `updlog.md` | 更新日志 | 顶部新增版本条目 |
| `用前必看.md` | 技术文档 | 架构/API 变化时更新 |
| `工作流程.md` | 开发规范 | 用户看的开发流程 |
| `AI开发规则.md` | AI 记忆 | 本文件，AI 的行为准则 |
| `Ciku/*.txt` | 词库文件 | 云端同步的 txt 词库 |
| `data.json` | 运行时数据 | 自动生成 |
| `error.txt` | 错误日志 | 脚本崩溃自动生成 |

---

## 版本号

- 格式 `v主.次`，如 v4.1
- 大改（架构重构）→ 主版本 +1
- 小改（修 bug、UI 优化）→ 次版本 +1

---

## 计时器格式

所有计时器统一为「标准值 ± 随机浮动」：
- 禁言：`muteBase ± muteJitter`（秒）
- 撤回：`revokeBase ± revokeJitter`（秒）
- 连招：`stepBase ± stepJitter`（毫秒）
- 实际值 = base - jitter + random(0, jitter * 2 + 1)

---

## TargetRule 字段（v4 格式）

```
uin|emoji|mute|insult|image|revoke|nick|muteBase|muteJitter|revokeBase|revokeJitter|stepBase|stepJitter|imgPath|cardName
```

---

## BeanShell 注意事项

- 不用泛型：`List` 而非 `List<String>`
- 不用 Lambda、注解、枚举
- 匿名内部类中访问局部变量要用 `final`
- 避免 `(类型) 变量` 强制转换，BeanShell 可能误解析类型名
- 网络下载词库用 `HttpURLConnection`，记得 try-catch-finally

---

## 推送方式

1. FTP：`ftp://192.168.1.10:2121`，**必须加 `--ftp-create-dirs`**
2. 中文文件名走 FTP 会乱码——单独推 `main.java`（英文名），其余打包 zip
3. 目标路径：`/Android/media/com.tencent.mobileqq/.模了个块/Plugin/Q群猎魔大杂烩/`
4. 电脑备份：`C:/Users/15322/Downloads/Q群猎魔大杂烩_项目/Q群猎魔大杂烩/`

---

## 用户偏好

- ❌ 不要全局设置（只有群号和云端地址是全局的）
- ❌ 控制台不要塞满所有设置，默认只显示用户列表
- ✅ 添加用户时展开配置面板，添加完自动折叠
- ✅ 配置项标签要清晰说明含义
- ✅ 图片路径和群昵称锁定分开两个输入框
- ✅ 云端词库地址已内置 GitHub 直链
- ✅ 每次改完代码主动同步文档和推送
