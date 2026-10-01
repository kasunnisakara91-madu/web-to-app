# Create App

Tap [Create](/guide/main-screen/create-app) on My Apps to open the app-type picker. Choose the type that matches your input; each opens its own creation flow, then shares the same [app configuration](/guide/config/) options.

## The types

| Type | Input | Output | Good for |
| --- | --- | --- | --- |
| [Web](/guide/app-types/web) | A URL | WebView APK | Landing pages, tools, dashboards, docs |
| [Multi-Web](/guide/app-types/multi-web) | Several URLs | Tab/card/feed/drawer APK | Link hubs, portals |
| [HTML](/guide/app-types/html) | Local HTML / zip | File-protocol APK (localhost optional) | Static builds, offline web apps |
| [Offline Pack](/guide/app-types/offline-pack) | A URL (scraped) | Self-contained offline APK | Archiving a site |
| [Frontend](/guide/app-types/frontend) | Built front-end project | File-protocol APK (localhost optional) | React, Vue, Vite builds |
| [Gallery](/guide/app-types/gallery) | A media collection | Gallery APK | Albums, portfolios |

## The creation flow

Every type follows the same shape:

1. **Type-specific form** — e.g. Web asks for a URL; Frontend asks for the built project directory; Gallery asks for media and layout.
2. **Basic info** — name and icon. The icon can be fetched from the website's favicon, picked from the **Icon Library** (AI-generated or your own uploaded images — uploads go through a crop step with square / free / circle modes, WeChat-avatar style), or selected directly from the gallery.
3. **Save** — the app is created and appears on [My Apps](/guide/main-screen/my-apps).

After creation, use the app's ⋮ menu: [Edit Core Config](/guide/app-actions/edit-core-config) returns to the type-specific form; [Edit Common Config](/guide/app-actions/edit-common-config/) opens the shared options.

::: tip Try a sample first
The app bundles sample projects (React, Vue, Vite, Hexo, MkDocs, VitePress, and more). Use one to see a working configuration for your stack.
:::
