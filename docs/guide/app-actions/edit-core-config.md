# Edit Core Config

Opens an app's **type-specific** settings — its source configuration. Tap ⋮ on an app card, then **Edit Core Config**. Each app type has a different core config; every type page documents its own in a detailed **Core config** section.

## What it edits

The type-specific creation form, reused as an editor. Follow each link to the full, sub-categorized field reference:

| App type | Core config covers |
| --- | --- |
| [Web](/guide/app-types/web) | Target & engine, user agent & display, injection, popups *(Web has a single combined editor — see note)* |
| [Multi-Web](/guide/app-types/multi-web) | Sites (name/URL/type/icon/theme/selectors), layout & display, refresh, shared injection |
| [HTML](/guide/app-types/html) | Source, entry file, load mode & port, capabilities, appearance |
| [Offline Pack](/guide/app-types/offline-pack) | Crawl scope, filtering, network |
| [Frontend](/guide/app-types/frontend) | Build output, framework |
| [Gallery](/guide/app-types/gallery) | Content, playback, view, display |

## Notes

- **Web apps don't show this entry** — they have a single **Edit** that covers everything, since web config isn't split.
- For the shared options (appearance, networking, privacy, export), use [Edit Common Config](/guide/app-actions/edit-common-config/).
