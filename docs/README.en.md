# TV Home

An experimental, free Android TV home-screen app: embed your existing Home Assistant dashboard, browse launchable installed apps, open a web bookmark, show a clock and choose built-in backgrounds. MIT licensed.

**This is a normal TV app, not a default HOME replacement.** It does not debloat, root, flash firmware, modify HDMI/CEC/audio, start at boot or run an Agent control service. Native source uses Android Views and the device's WebView. Min SDK26 / target30; INTERNET only. Public preview has debugging disabled. The interactive design under `design/` is a synthetic mock, not a promise of implemented native features.

Start with [the Agent guide](AGENT-QUICKSTART.md), [risks](RISKS.md), [recovery](RECOVERY.md), [build instructions](BUILD.md) and root `AGENTS.md` (primarily Chinese). Ask your trusted Agent to read them first, perform only human-authorized read-only checks, verify the exact TV identity and propose an installation/recovery plan. Never hand unknown services your ADB keys, HA credentials or signing key. Every ADB operation must explicitly target the verified serial; no automatic first-device selection.

The public candidate builds and passes host URL/key-policy checks, APK alignment and signature verification. **It has not been installed on a real TV.** An earlier, separate internal debug prototype ran on one TCL/FFalcon Android11 TV, showed the HA login page and exercised same-signature debug APK rollback. This is not proof of public-package compatibility, authenticated HA dashboard navigation, physical-remote support, reboot reliability or non-debug downgrade support. See [validation](VALIDATION.md).

ADB grants broad device access. HA pages can operate real household devices after login. Keep the stock launcher available, do not change system settings as a workaround, and do not expose ADB or a generic shell endpoint to the Internet. If this app fails, return to stock HOME/input or force-stop only this package. Downgrading may be blocked; do not uninstall or clear user data automatically.

Build with installed official Android SDK36/build-tools36.0.0, JDK17, Python3 and Git. `scripts/build.py` makes a stable private local signing key and signed APK, never installs it. Self-built keys differ from official preview keys. Preserve your key privately for updates.

[Repository](https://github.com/PengboQin/tv-home) · [Experimental downloads](https://github.com/PengboQin/tv-home/releases). Please report only redacted compatibility evidence; do not post household data, raw logs or secrets.
