# 澄镜 · 五人协作版

这是从[现有澄镜系统](https://github.com/coderdingyu/chengjing)拆出的五人协作仓库，使用 React 18 + TypeScript + Vite、Spring Boot 3.4 + Java 17。功能按编号逐项提交；模块是否已可用，以对应 PR、测试和 [30 项功能分工](docs/30项功能分工.md) 的验收记录为准。不要把尚未合并的模块视为已完成。

团队按 [30 项功能分工](docs/30项功能分工.md) 各实现 6 项，每项对应一次由实际开发者完成的功能提交。负责人先上传的骨架提交不计入这 30 次。旧系统是实现参考，不直接整包复制，以便每项都有明确的代码和验收结果。

产品定位、用户流程、跨模块规则与整体验收见 [需求分析说明书](docs/需求分析说明书.md)。

## 目录

| 目录 | 负责人 | 内容 |
| --- | --- | --- |
| `backend/src/main/java/com/chengjing/identity`、`frontend/src/features/identity` | 成员 A | 账号与隐私 |
| `.../preparation`、`frontend/src/features/preparation` | 成员 B | 面试准备 |
| `.../interview`、`frontend/src/features/interview` | 成员 C | 面试进行 |
| `.../assessment`、`frontend/src/features/assessment` | 成员 D | 评分与成长 |
| `.../platform`、`frontend/src/features/platform` | 负责人 E | 模型与平台 |

`backend/.../shared`、`frontend/src/api.ts` 和入口路由属于公共契约。改公共文件前先在 PR 中说明接口影响；功能实现、测试优先放在各自目录。五个 `features` 目录随功能提交创建，骨架只预留模块入口。

## 本机启动

需要 Java 17、Node.js 22 与 npm。**骨架端口是 8081 和 5173**，可与原系统的 8080 同时运行。

Windows 上可直接双击 [启动团队版.cmd](启动团队版.cmd)，脚本会安装依赖、运行测试、构建并检查前后端是否启动成功。停止时双击 [停止团队版.cmd](停止团队版.cmd)。详细的本机备份、恢复与服务器迁移见 [部署文档](docs/本机运行与服务器迁移.md)。

```powershell
cd backend
.\mvnw.cmd -s .mvn/settings.xml spring-boot:run
```

另开一个终端：

```powershell
cd frontend
npm ci
npm run dev
```

浏览器打开 http://127.0.0.1:5173 。后端健康接口是 http://127.0.0.1:8081/api/v1/system/health 。提交前在 `frontend` 目录运行 `npm run build`，在 `backend` 目录运行 `.\mvnw.cmd -s .mvn/settings.xml test`。GitHub Actions 也会运行这两项检查。

平台模块提供模型配置、传统语音练习和原生 Live 页面。模型配置、Live 票据依赖 A02 的已登录用户；若 A02 尚未合并，页面会明确要求登录。文本模型调用使用用户选择的连接；Step 5 Preview 是文本模型，Live 需单独配置 StepAudio 3 Realtime。原始麦克风音频不写入数据库。

## 开发规则

- 每个功能一个分支、一个可理解的功能提交和一个 PR；由该功能的实际开发者使用自己的 GitHub 身份提交。[具体步骤](docs/协作上传指南.md)。
- API 使用 `/api/v1` 前缀、`{success,data,message}` 格式；存储与模型都在服务端。详见 [模块契约](docs/模块契约.md)。
- 真实姓名、简历、音视频、API Key、数据库、`.env` 不进入 Git。麦克风与摄像头必须单独征得同意；原始音视频不作为评分依据。
- 评分只根据回答证据；证据不足显示“未覆盖”，不填 0 分。不能把视觉外观用于评分。
