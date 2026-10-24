# TV Home 统一素材源 · v1

本目录属于设计/实现素材，不是已安装App资源包。原生界面方向已获认可；本轮双标签结构和各页面截图仍需用户看图接受。

- `tokens.json`：唯一颜色/间距/字号/动效规则源。布局单位为1920×1080设计画布像素，导入Android布局时根据实际dp视口换算，不把截图px硬写成dp。
- `icons.json`：26个自有矢量图标的唯一path源，24×24视口/1.5描边。无外部字体、网络图标服务或第三方图标素材依赖。颜色由组件状态决定；无绿色/紫色主题。
- `tokens.css` / `tv-system.js` / `icons.svg` / `svg/`：生成的原型使用资产，不手改。
- `android/drawable/` / `android/values/colors.xml`：同源生成的VectorDrawable与颜色，可在下一批Android实现中复用；本轮只做资源编译检查，没有替换当前App。
- 壁纸复用仓库单份`assets/coast-wallpaper.png`，不逐页复制；背景静态，无实时模糊/视频墙。

生成：`python3 scripts/export_design_system.py`；一致性：`python3 scripts/export_design_system.py --check`，均从仓库根运行。Android资源可用现有aapt2本地compile核对，不需安装到电视。

组件约定：WeatherHero/WeatherDetail、DeviceCard、AutomationRow、AppTile、ContextSidebar、BottomTabs、Modal。焦点与选中、开关与可用性分开；设备卡当前状态不因收到操作请求而立即翻转，等待权威状态。原型只等待合成状态，不发送真实请求。

标签约定：底栏=全局四页，侧栏=标签内部分类；桌面无侧栏。颜色不表达唯一语义，开关/运行/不可用均有文字。普通动作没有常驻技术注释；离线、授权失效、未确认属于对应动作的错误状态。
