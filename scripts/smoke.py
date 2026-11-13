"""Native Android smoke test with UI readiness checks and failure evidence."""
import atexit
import hashlib
import pathlib
import subprocess
import time
import xml.etree.ElementTree as ET

out = pathlib.Path("smoke")
out.mkdir(exist_ok=True)

def adb(*args):
    return subprocess.check_output(["adb", *args], text=True)

def evidence():
    try:
        (out / "logcat.txt").write_text(adb("logcat", "-d"))
        with (out / "screen.png").open("wb") as stream:
            subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=stream, check=True)
    except Exception as error:
        print("Evidence collection:", error, flush=True)

atexit.register(evidence)

def description():
    try:
        adb("shell", "uiautomator", "dump", "/sdcard/marble-window.xml")
        xml = adb("shell", "cat", "/sdcard/marble-window.xml")
        (out / "window.xml").write_text(xml)
        return " ".join(node.attrib.get("content-desc", "") for node in ET.fromstring(xml).iter())
    except (ET.ParseError, subprocess.CalledProcessError):
        return ""

def wait_ui(*expected):
    deadline = time.monotonic() + 35
    last = ""
    while time.monotonic() < deadline:
        last = description()
        if all(value in last for value in expected):
            print("UI verified:", last, flush=True)
            return last
        time.sleep(1)
    raise AssertionError("UI did not show " + repr(expected) + "; observed: " + repr(last))

apk = pathlib.Path("dist/小游戏.apk")
print("APK SHA-256:", hashlib.sha256(apk.read_bytes()).hexdigest(), flush=True)
adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
adb("shell", "wm", "dismiss-keyguard")
adb("install", "-r", str(apk))
adb("logcat", "-c")
component = "com.kimizha.minigame/.MainActivity"
result = adb("shell", "am", "start", "-W", "-n", component)
assert "Status: ok" in result, result
wait_ui("首页")
size = adb("shell", "wm", "size").strip().split()[-1]
width, height = map(int, size.split("x"))

def tap_start():
    adb("shell", "input", "tap", str(width // 2), str(int(height * .53)))

tap_start()
wait_ui("游戏中")
adb("shell", "input", "swipe", str(width // 2), str(int(height * .70)),
    str(int(width * .63)), str(int(height * .36)), "500")
wait_ui("回合 1")
with (out / "game.png").open("wb") as stream:
    subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=stream, check=True)
adb("shell", "input", "keyevent", "KEYCODE_HOME")
time.sleep(1)
adb("shell", "am", "start", "-W", "-n", component)
wait_ui("暂停")
adb("shell", "am", "force-stop", "com.kimizha.minigame")
adb("shell", "am", "start", "-W", "-n", component)
wait_ui("首页")
tap_start()
wait_ui("游戏中", "回合 1")
assert adb("shell", "pidof", "com.kimizha.minigame").strip()
logs = adb("logcat", "-d")
assert "FATAL EXCEPTION" not in logs, logs[-8000:]
message = ("PASS: installed, launched, entered native game, fired round 1, backgrounded, "
           "resumed paused, force-stopped and restored saved round 1. No fatal exception.")
print(message, flush=True)
(out / "result.txt").write_text(message + "\n")
