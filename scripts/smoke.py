"""Real-device-UI smoke: install, launch, fire, background, kill and restore."""
import pathlib
import subprocess
import time
import xml.etree.ElementTree as ET

out = pathlib.Path("smoke")
out.mkdir(exist_ok=True)

def adb(*args):
    return subprocess.check_output(["adb", *args], text=True)

def description():
    adb("shell", "uiautomator", "dump", "/sdcard/marble-window.xml")
    xml = adb("shell", "cat", "/sdcard/marble-window.xml")
    return " ".join(node.attrib.get("content-desc", "") for node in ET.fromstring(xml).iter())

adb("install", "-r", "dist/小游戏.apk")
adb("logcat", "-c")
component = "com.kimizha.minigame/.MainActivity"
result = adb("shell", "am", "start", "-W", "-n", component)
assert "Status: ok" in result, result
time.sleep(2)
assert "首页" in description()
size = adb("shell", "wm", "size").strip().split()[-1]
width, height = map(int, size.split("x"))
adb("shell", "input", "tap", str(width // 2), str(int(height * .53)))
time.sleep(1)
assert "游戏中" in description()
adb("shell", "input", "swipe", str(width // 2), str(int(height * .70)),
    str(int(width * .63)), str(int(height * .36)), "500")
time.sleep(2)
state = description()
assert "回合 1" in state, state
with (out / "game.png").open("wb") as f:
    subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f, check=True)
adb("shell", "input", "keyevent", "KEYCODE_HOME")
time.sleep(1)
adb("shell", "am", "start", "-W", "-n", component)
time.sleep(1)
assert "暂停" in description()
adb("shell", "am", "force-stop", "com.kimizha.minigame")
adb("shell", "am", "start", "-W", "-n", component)
time.sleep(2)
assert "首页" in description()
adb("shell", "input", "tap", str(width // 2), str(int(height * .53)))
time.sleep(1)
restored = description()
assert "游戏中" in restored and "回合 1" in restored, restored
assert adb("shell", "pidof", "com.kimizha.minigame").strip()
logs = adb("logcat", "-d")
(out / "logcat.txt").write_text(logs)
assert "FATAL EXCEPTION" not in logs, logs[-8000:]
(out / "result.txt").write_text(
    "PASS: installed, launched, entered native game, fired round 1, backgrounded, "
    "resumed paused, force-stopped and restored saved round 1. No fatal exception.\n"
)
