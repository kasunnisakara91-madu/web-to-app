# 创建应用

在[我的应用](/zh/guide/main-screen/create-app)点击 **创建**,打开应用类型选择器。选择与你的输入相匹配的类型;每种都打开自己的创建流程,然后共享同一套[应用配置](/zh/guide/config/)选项。

## 类型

| 类型 | 输入 | 输出 | 适用场景 |
| --- | --- | --- | --- |
| [网页](/zh/guide/app-types/web) | 一个 URL | WebView APK | 落地页、工具、仪表盘、文档 |
| [多站点](/zh/guide/app-types/multi-web) | 多个 URL | 标签/卡片/信息流/抽屉 APK | 链接枢纽、门户 |
| [HTML](/zh/guide/app-types/html) | 本地 HTML / zip | 文件协议 APK(可切本地托管) | 静态构建、离线 Web 应用 |
| [离线包](/zh/guide/app-types/offline-pack) | 一个 URL(抓取) | 自包含离线 APK | 把网站归档 |
| [前端](/zh/guide/app-types/frontend) | 已构建的前端项目 | 文件协议 APK(可切本地托管) | React、Vue、Vite 构建 |
| [画廊](/zh/guide/app-types/gallery) | 一个媒体集合 | 画廊 APK | 相册、作品集 |

## 创建流程

每种类型都遵循相同的形态:

1. **类型专属表单** —— 例如网页要求填 URL;前端要求填已构建的项目目录;画廊要求填媒体和布局。
2. **基本信息** —— 名称和图标。图标可以来自网站 favicon 抓取、**图标库**(AI 生成的图标,或你自己上传的图片——上传需经过裁剪步骤,支持方形/自由/圆形三种模式,类似微信头像设置),也可以直接从相册选择。
3. **保存** —— 应用被创建并出现在[我的应用](/zh/guide/main-screen/my-apps)上。

创建后,使用应用的 ⋮ 菜单:[编辑核心配置](/zh/guide/app-actions/edit-core-config)回到类型专属表单;[编辑通用配置](/zh/guide/app-actions/edit-common-config/)打开共享选项。

::: tip 先试一个示例
应用内置了示例项目(React、Vue、Vite、Hexo、MkDocs、VitePress 等)。先用一个看看你的技术栈的可工作配置。
:::
