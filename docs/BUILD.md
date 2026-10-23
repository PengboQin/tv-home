# 构建与发行记录

使用 Python 3、Git、JDK 17、官方 Android SDK 的 `platforms;android-36` 和 `build-tools;36.0.0`。原生Java/Android View，无 Gradle、NDK、第三方运行时库。系统WebView由设备提供，不随APK捆绑。

从官方 [Android Studio/SDK](https://developer.android.com/studio) 安装工具，自行阅读并接受适用许可。脚本不会下载SDK或替用户接受许可。

```sh
git clone https://github.com/PengboQin/tv-home.git
cd tv-home
export TVHOME_ANDROID_SDK='/你的/Android/SDK'
export TVHOME_JDK='/你的/JDK17/Home'
python3 scripts/test.py
# 已发布版本的releases记录不可覆盖。自建先同步修改以下两处并提交：
# version.json / AndroidManifest.xml：例如versionCode=2、versionName=0.1.0-local.1
# git add version.json AndroidManifest.xml
# git commit -m 'Prepare a separate local candidate'
python3 scripts/build.py
```

也接受 `ANDROID_HOME` / `ANDROID_SDK_ROOT` 和 `JAVA_HOME`。路径要指向真正安装目录，不是上面的字面示例。Python脚本支持具备这些工具的主机；本次仅在macOS实际构建，Windows/其他主机未验收。

构建前工作树须干净，用于嵌入准确source commit。第一次会在被忽略的 `.private/` 生成RSA3072本地签名身份，密码文件权限0600；后续沿用。该私钥不是系统/厂商签名，更不应分享。自建签名与GitHub官方候选不同，不能覆盖官方包。备份本项目自己的签名文件；不要复用其他App密钥。

产物：

- `artifacts/tvhome-<版本>-b<versionCode>.apk`：已签名候选；不自动安装或运行。
- `releases/NNNN.json`：源commit、版本、APK SHA256、证书指纹、权限与宿主测试结果。
- `build/`：固定可重建中间目录；复用而非逐轮复制。

每个versionCode只产生一个不可覆盖候选。修改后先同时更新 `version.json`、`AndroidManifest.xml` 的versionName/versionCode，再提交、构建。`releases/`已有同版本记录时拒绝重建，不能删除正式记录绕过。只想检查源码时用 `scripts/test.py`，无需生成新候选。

构建脚本检查manifest权限、无HOME/服务/receiver、debug=false，并跑URL/按键宿主策略测试。APK签名验证通过不代表电视安装或兼容性通过。构建环境影响字节级可复现性，不能声称不同主机SHA必然相同。

发布流程：源提交 → 测试/签名/哈希 → 保留候选和签名身份 → 提交release记录 → tag/release注明对应源commit与设备验证范围。Tag可能包含稍后写入的release元数据，APK内sourceCommit明确指向构建时源码提交。GitHub自动生成源码zip不是已签名APK。公开发布只上传APK、release JSON和SHA256SUMS，不上传私钥、构建缓存或设备日志。

最小SDK26、目标SDK30是当前实验兼容选择，**不是满足当前Google Play上架要求的声明**；上架前需另行适配与审核。不要为支持新SDK误加系统权限。
