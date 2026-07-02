# v4.0 (2026-07-03) — 全面个人化配置

- 🎯 架构重构：移除全部全局设置，所有参数改为目标用户独立配置
- ⏱️ 计时器统一：muteMin/Max → muteBase+Jitter, revokeMin/Max → revokeBase+Jitter, stepMin/Max → stepBase+Jitter
- 📝 TargetRule 扩展：新增 stepBase/stepJitter/imgPath/cardName 等 15 个字段
- 🔧 控制台重写：生效范围 + 云端词库 + 添加用户(全参数) + 用户列表(显示个配) + 保存
- 💾 配置格式 v4（15字段），自动兼容 v3(11字段) 和 v2(5字段) 旧格式
- 🗑️ 修复删除按钮：改用 QQ 号匹配删除，不再依赖 BeanShell 闭包索引
- ☁️ 云端词库默认地址内置 GitHub 直链
- 🐛 修复 BeanShell ((msg) fMsg) 类型解析崩溃
- 🛡️ loadCiku 增加 try-catch 保护

---

# v2.5 (2026-07-02)

- 群名片读取 API 从废弃的 troopnick 字段迁移到新版 nickInfo 对象
- 新增 getMemberQQNick / getDisplayNick / lookupDisplayName 方法
- 控制台目标用户列表新增昵称展示：群名片 > QQ昵称 > QQ号

---

# v2.4 (2026-07-02)

- 六项功能全部独立化为每个目标用户的独立开关，废除全局统一开关
- 群号填 0 即开启全群通缉模式，所有群聊全部生效
- 控制台界面全面重写
- 旧版配置文件自动识别并迁移到新格式

---

# v2.1 (2026-06-30)

- 首次发布
