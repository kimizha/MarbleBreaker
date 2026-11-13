"""Basic install, launch, touch, background and resume smoke test. Not a full playthrough."""
import pathlib
import subprocess
import time

out = pathlib.Path("smoke")
out.mkdir(exist_ok=True)

def adb(*args):
    return subprocess.check_output(["adb", *args], text=True)

adb("install", "-r", "dist/小游戏.apk")
adb("logcat", "-c")
component = "com.kimizha.minigame/.MainActivity"
result = adb("shell", "am", "start", "-W", "-n", component)
assert "Status: ok" in result, result
time.sleep(3)
assert adb("shell", "pidof", "com.kimizha.minigame").strip()
size = adb("shell", "wm", "size").strip().split()[-1]
width, height = map(int, size.split("x"))
# Canvas fits into the status/navigation insets. Read actual insets through UI screenshot
# coordinates using the known centered 720x1200 design; start is around 53% of screen.
adb("shell", "input", "tap", str(width // 2), str(int(height * .53)))
time.sleep(1)
adb("shell", "input", "swipe", str(width // 2), str(int(height * .70)),
    str(int(width * .63)), str(int(height * .36)), "500")
time.sleep(4)
with (out / "game.png").open("wb") as f:
    subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f, check=True)
adb("shell", "input", "keyevent", "KEYCODE_HOME")
time.sleep(1)
adb("shell", "am", "start", "-W", "-n", component)
time.sleep(1)
assert adb("shell", "pidof", "com.kimizha.minigame").strip()
logs = adb("logcat", "-d")
(out / "logcat.txt").write_text(logs)
assert "FATAL EXCEPTION" not in logs, logs[-8000:]
(out / "result.txt").write_text("PASS: APK installed, launched, touch input sent, backgrounded and resumed; no fatal exception. Gameplay correctness is covered separately by unit tests.\n")
