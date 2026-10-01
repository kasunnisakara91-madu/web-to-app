# Export Pipeline

The export pipeline turns a `WebApp` model into a signed APK. It lives in `app/src/main/java/com/webtoapp/core/apkbuilder/` (~24 files).

## Key classes

| File | Role |
| --- | --- |
| `ApkBuilder.kt` | Orchestrates APK assembly + signing. Contains `WebApp.toApkConfig(...)`. |
| `ApkConfig.kt` | The master config schema: `data class ApkConfig(meta, activation, adBlock, webView, proxy, dns, html, gallery, multiWeb, ...)`. Everything an exported APK can encode. |
| `ApkConfigJsonFactory.kt` | Serializes `ApkConfig` to the assets JSON the shell reads; includes `ApkConfigValidator`. |
| `ApkTemplate.kt` / `ShellTemplateProvider.kt` | Locate and load the shell template APK. |
| `ApkBuildCache.kt` | Incremental rebuild; defines `enum class IncrementalBuildMode` (`ModifyApkMode` only covers `FULL`/`CONTENT_OVERLAY`). |
| `AxmlEditor` / `AxmlRebuilder` | Edit/rebuild the binary AndroidManifest (AXML). |
| `ArscEditor` / `ArscRebuilder` | Edit/rebuild the binary resource table (resources.arsc). |
| `JarSigner.kt` | Signs the APK with the `com.android.apksig` library directly (`ApkSigner`, V1/V2/V3 toggles). |
| `ZipAligner` / `ZipUtils` | Zip alignment and low-level zip manipulation. |
| `ElfAligner16k.kt` | 16KB-page ELF alignment for native `.so` files. |
| `RuntimeAssetEmbedder.kt` | Embeds packaged project files (Frontend builds, multi-web embedded sites) into the APK assets. |
| `NetworkSecurityConfigBuilder.kt` | Generates the network security config XML. |

Related: `core/playstore/aab/` handles AAB/Play packaging; `core/crypto/` (`AssetEncryptor`, `EncryptedApkBuilder`, `KeyManager`) handles asset encryption.

## The flow

```text
WebApp (editor model)
  → WebApp.toApkConfig()          [ApkBuilder.kt]
  → ApkConfig                     [typed schema, *Block sub-objects]
  → ApkConfigJsonFactory          [serialize to app_config.json]
  → embed into template assets
  → patch AXML / ARSC (identity, permissions, icon)
  → embed packaged content (site files, gallery media)
  → sign (V1/V2/V3)
  → output APK
```

## `ApkConfig` structure

`ApkConfig` is composed of a `MetaBlock` plus dozens of feature blocks (`WebViewBlock`, `ProxyBlock`, `DnsBlock`, `HtmlBlock`, `GalleryBlock`, `MultiWebBlock`, `AdBlockBlock`, …). Convenience getters on `ApkConfig` flatten these (`appName`, `targetUrl`, `adBlockEnabled`, …).

The JSON field names produced by `ApkConfigJsonFactory` **must match** the `@SerializedName` annotations in the shell config class — see [Config Field Drift](/developer/config-drift).

## Incremental rebuild (`ApkBuildCache`)

Three modes (`enum class IncrementalBuildMode`):

| Mode | Meaning |
| --- | --- |
| `FULL` | Rebuild from template. Always used for encrypted builds. |
| `CONTENT_OVERLAY` | Only app content changed; overlay onto a prior build. |
| `REUSE_UNSIGNED` | Re-sign a previously built unsigned APK. |

Rules:

- Cache keys are **content-stable hashes** — never mtime-based.
- Template / entry identities must be content-stable.
- Encrypted builds always force a full rebuild.
- **Do not** feed signed or renamed APKs back into full `modifyApk` as templates.
