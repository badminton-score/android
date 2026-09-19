#!/bin/bash
# 打一个签好名的 Release APK。
# 用的是仓库里公开的 keystore/badminton.jks（密码见 README），
# 这样所有人装到的是同一个签名的包，以后能直接覆盖升级。
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -d ".jdk" ]; then
  H=$(find .jdk -maxdepth 3 -name Home -type d 2>/dev/null | head -1)
  [ -n "$H" ] && export JAVA_HOME="$PWD/$H"
fi

echo "==> 跑测试"
./gradlew --no-daemon --console=plain :app:testDebugUnitTest 2>&1 | tail -3

echo "==> 打 Release APK"
./gradlew --no-daemon --console=plain :app:assembleRelease 2>&1 | tail -3

SRC=app/build/outputs/apk/release/app-release.apk
[ -f "$SRC" ] || { echo "没有产出：$SRC" >&2; exit 1; }

V=$(grep -oE 'versionName = "[^"]+"' app/build.gradle.kts | head -1 | cut -d'"' -f2)
OUT="build/BadmintonScore-${V}-android.apk"
cp "$SRC" "$OUT"

echo
echo "完成: ${OUT}  ($(du -h "${OUT}" | cut -f1))"
echo "sha256: $(shasum -a 256 "${OUT}" | cut -d' ' -f1)"
