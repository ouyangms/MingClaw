# MingClaw 设计文档索引

**项目**: MingClaw Android Agent
**版本**: 2.0
**更新日期**: 2025-03-31

---

## 📚 设计文档结构

MingClaw 的设计文档采用模块化组织，每个文档专注于一个特定的系统模块。

### 核心设计文档 (01-12)

| # | 文档 | 描述 | 大小 |
|---|------|------|------|
| 01 | [整体架构](./01-architecture.md) | 系统架构概述、分层设计、核心组件 | 33KB |
| 02 | [自我进化机制](./02-evolution.md) | 行为/知识/能力三路径进化系统 | 53KB |
| 03 | [核心模块设计](./03-core-modules.md) | 微内核、事件总线、配置管理 | 46KB |
| 04 | [上下文管理](./04-context-management.md) | 会话管理、记忆检索、Token窗口 | 51KB |
| 05 | [插件系统](./05-plugin-system.md) | 插件接口、工具系统、加载机制 | 42KB |
| 06 | [记忆管理](./06-memory-management.md) | 记忆存储、向量嵌入、混合搜索 | 48KB |
| 07 | [任务编排引擎](./07-task-orchestration.md) | 任务执行、工作流、依赖管理 | 49KB |
| 08 | [工作区与配置](./08-workspace-config.md) | 工作区结构、文件管理、配置系统 | 49KB |
| 09 | [安全策略](./09-security-strategy.md) | 权限管理、沙箱隔离、加密审计 | 49KB |
| 10 | [技术栈选型](./10-tech-stack.md) | 核心技术、版本管理、依赖策略 | 36KB |
| 11 | [补充设计](./11-supplementary-design.md) | 通信协议、Token计数、向量数据库 | 38KB |
| 12 | [系统质量保证](./12-quality-assurance.md) | 性能优化、错误处理、测试监控 | 28KB |

### 实施文档 (13-14)

| # | 文档 | 描述 | 大小 |
|---|------|------|------|
| 13 | [实施指南](./13-implementation-guide.md) | 开发环境、编码规范、部署流程 | 26KB |
| 14 | [API参考](./14-api-reference.md) | 所有公共接口的完整文档 | 39KB |

---

## 🗂️ 按主题分类

### 架构与设计

- [01-architecture.md](./01-architecture.md) - 整体架构
- [10-tech-stack.md](./10-tech-stack.md) - 技术栈选型

### 核心功能

- [02-evolution.md](./02-evolution.md) - 自我进化机制
- [04-context-management.md](./04-context-management.md) - 上下文管理
- [06-memory-management.md](./06-memory-management.md) - 记忆管理
- [07-task-orchestration.md](./07-task-orchestration.md) - 任务编排

### 系统组件

- [03-core-modules.md](./03-core-modules.md) - 核心模块
- [05-plugin-system.md](./05-plugin-system.md) - 插件系统
- [08-workspace-config.md](./08-workspace-config.md) - 工作区配置
- [09-security-strategy.md](./09-security-strategy.md) - 安全策略

### 质量与实施

- [11-supplementary-design.md](./11-supplementary-design.md) - 补充设计
- [12-quality-assurance.md](./12-quality-assurance.md) - 质量保证
- [13-implementation-guide.md](./13-implementation-guide.md) - 实施指南
- [14-api-reference.md](./14-api-reference.md) - API参考

---

## 📖 阅读顺序建议

### 对于新开发者

1. **了解架构**: [01-architecture.md](./01-architecture.md)
2. **技术栈**: [10-tech-stack.md](./10-tech-stack.md)
3. **实施指南**: [13-implementation-guide.md](./13-implementation-guide.md)

### 对于架构审查

1. **整体架构**: [01-architecture.md](./01-architecture.md)
2. **核心模块**: [03-core-modules.md](./03-core-modules.md)
3. **安全策略**: [09-security-strategy.md](./09-security-strategy.md)
4. **质量保证**: [12-quality-assurance.md](./12-quality-assurance.md)

### 对于功能开发

参考对应的模块文档：
- 进化功能 → [02-evolution.md](./02-evolution.md)
- 插件开发 → [05-plugin-system.md](./05-plugin-system.md)
- 任务处理 → [07-task-orchestration.md](./07-task-orchestration.md)

### 对于 API 集成

1. **API参考**: [14-api-reference.md](./14-api-reference.md)
2. **上下文管理**: [04-context-management.md](./04-context-management.md)
3. **记忆管理**: [06-memory-management.md](./06-memory-management.md)

---

## 🔗 文档关系图

```
                    ┌─────────────────────┐
                    │  01-architecture.md  │
                    │      (整体架构)       │
                    └──────────┬──────────┘
                               │
         ┌─────────────────────┼─────────────────────┐
         │                     │                     │
         ▼                     ▼                     ▼
┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐
│ 02-evolution.md │   │ 03-core-modules │   │10-tech-stack.md │
│  (自我进化)      │   │   (核心模块)     │   │  (技术栈)       │
└────────┬────────┘   └────────┬────────┘   └────────┬────────┘
         │                     │                     │
         ▼                     ▼                     ▼
┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐
│04-context mgmt  │   │05-plugin-system │   │11-supplementary │
│ (上下文管理)     │   │  (插件系统)      │   │  (补充设计)     │
└────────┬────────┘   └────────┬────────┘   └────────┬────────┘
         │                     │                     │
         ▼                     ▼                     ▼
┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐
│06-memory mgmt   │   │07-task orch.    │   │12-quality ass.  │
│ (记忆管理)       │   │(任务编排)        │   │ (质量保证)       │
└────────┬────────┘   └────────┬────────┘   └────────┬────────┘
         │                     │                     │
         ▼                     ▼                     ▼
┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐
│08-workspace cfg │   │09-security strat │  │13-implementation│
│(工作区配置)      │   │  (安全策略)       │  │   (实施指南)     │
└─────────────────┘   └────────┬────────┘   └────────┬────────┘
                               │                     │
                               ▼                     ▼
                     ┌─────────────────┐   ┌─────────────────┐
                     │14-api-reference │   │   (开发完成)      │
                     │  (API参考)       │   │                  │
                     └─────────────────┘   └─────────────────┘
```

---

## 📝 文档规范

### 每个文档包含

1. **标题与元数据**
   - 文档版本
   - 创建日期
   - 当前状态
   - 作者信息

2. **目录**
   - 完整的章节索引

3. **主要内容**
   - 概述
   - 设计目标
   - 架构图
   - 接口定义 (Kotlin)
   - 实现示例
   - 依赖关系

4. **附录**
   - 相关文档链接
   - 数据类定义
   - 枚举类型
   - 参考资源

### 代码规范

- **语言**: Kotlin
- **包名**: `com.loy.mingclaw.*`
- **命名**: 驼峰命名法
- **注释**: KDoc 格式

### 图表规范

- **架构图**: ASCII Art
- **流程图**: Mermaid 兼容格式
- **数据表**: Markdown 表格

---

## 🔄 文档维护

### 更新频率

| 文档类型 | 更新频率 |
|----------|----------|
| 核心设计 (01-07) | 每月或重大变更时 |
| 配置与安全 (08-09) | 每季度 |
| 质量保证 (12) | 每周 |
| API参考 (14) | 每次API变更 |

### 版本控制

所有文档使用 Git 版本控制：
- 主要变更创建新版本
- 小修正直接更新
- 保留历史版本备份

### 贡献流程

1. 创建文档分支
2. 修改文档内容
3. 更新版本号和变更日志
4. 提交 Pull Request
5. 通过审查后合并

---

## 📮 反馈与建议

如果您对设计文档有任何疑问或建议，请：

1. **提交 Issue**: [GitHub Issues](https://github.com/ouyangms/MingClaw/issues)
2. **Pull Request**: [GitHub PRs](https://github.com/ouyangms/MingClaw/pulls)
3. **邮件联系**: ouyangms@example.com

---

## 📄 许可证

本设计文档遵循项目许可证：MIT License

---

**最后更新**: 2025-03-31
**维护者**: MingClaw Team
