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
        logs = adb("logcat", "-d")
        (out / "logcat.txt").write_text(logs)
        print("\n".join(line for line in logs.splitlines() if "AndroidRuntime" in line), flush=True)
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
wait_ui("游戏中", "生命 3", "弹珠 1", "等待开球")
# Drag the paddle horizontally while the single main ball remains attached.
adb("shell", "input", "swipe", str(int(width * .50)), str(int(height * .79)),
    str(int(width * .82)), str(int(height * .79)), "400")
state = wait_ui("等待开球")
import re
paddle_x = int(re.search(r"挡板 (\d+)", state).group(1))
assert 560 <= paddle_x <= 610, state
adb("shell", "input", "swipe", str(int(width * .82)), str(int(height * .79)),
    str(width // 2), str(int(height * .79)), "400")
wait_ui("挡板 360")
adb("shell", "input", "tap", str(width // 2), str(int(height * .71)))
wait_ui("开球 1")
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
wait_ui("游戏中", "开球 1")
assert adb("shell", "pidof", "com.kimizha.minigame").strip()
logs = adb("logcat", "-d")
assert "FATAL EXCEPTION" not in logs, logs[-8000:]
# Verify the real Android AtomicFile store reads an original v1 snapshot.
adb("shell", "am", "force-stop", "com.kimizha.minigame")
legacy = pathlib.Path("app/src/test/resources/legacy-v1.dat").read_bytes()
subprocess.run(["adb", "shell", "-T", "run-as com.kimizha.minigame sh -c 'cat > files/game-v1.dat'"],
               input=legacy, check=True)
adb("shell", "am", "start", "-W", "-n", component)
wait_ui("首页", "已解锁 27 关")
tap_start()
wait_ui("第 26 关", "生命 3", "弹珠 1", "等待开球")
# Sudoku is a second game, with its own activity, board tools and save file.
adb("shell", "input", "keyevent", "KEYCODE_BACK")
adb("shell", "input", "keyevent", "KEYCODE_BACK")
wait_ui("首页", "已解锁 27 关")

def tap_design(x, y):
    # Pixel 6 portrait: 720x1200 content is centered within system bar insets.
    adb("shell", "input", "tap", str(round(width * x / 720)),
        str(round(height / 2 + (y - 600) * width / 720)))

tap_design(360, 499)
wait_ui("数独 · 选择难度")
tap_design(360, 638)
state = wait_ui("数独 · 棋盘", "简单", "笔记关")
empty = int(re.search(r"剩余 (\d+) 格", state).group(1))
assert 35 <= empty <= 39, state
# Mark a candidate, undo and redo it, then erase it and enter an actual digit.
tap_design(82, 864)
wait_ui("笔记开")
tap_design(104, 976)
wait_ui("候选 1", "数字 0", f"剩余 {empty} 格")
tap_design(305, 864)
wait_ui("候选 0")
tap_design(416, 864)
wait_ui("候选 1")
tap_design(194, 864)
wait_ui("候选 0")
tap_design(82, 864)
wait_ui("笔记关")
tap_design(104, 976)
wait_ui("数字 1", f"剩余 {empty - 1} 格")
tap_design(305, 864)
wait_ui("数字 0", f"剩余 {empty} 格")
tap_design(527, 864)
wait_ui("提示 1", f"剩余 {empty - 1} 格")
tap_design(625, 69)
wait_ui("数独 · 棋盘", "暂停")
tap_design(360, 548)
wait_ui("进行中")
with (out / "sudoku.png").open("wb") as stream:
    subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=stream, check=True)
# Force-stop the entire app, launch through its home and continue the saved Sudoku.
adb("shell", "am", "force-stop", "com.kimizha.minigame")
adb("shell", "am", "start", "-W", "-n", component)
wait_ui("首页", "已解锁 27 关")
tap_design(360, 499)
wait_ui("数独 · 选择难度", "可继续上局")
tap_design(360, 518)
wait_ui("数独 · 棋盘", "提示 1", f"剩余 {empty - 1} 格")
adb("shell", "input", "keyevent", "KEYCODE_BACK")
wait_ui("数独 · 选择难度")
adb("shell", "input", "keyevent", "KEYCODE_BACK")
wait_ui("首页", "已解锁 27 关")
tap_start()
wait_ui("第 26 关", "生命 3", "弹珠 1")
logs = adb("logcat", "-d")
assert "FATAL EXCEPTION" not in logs, logs[-8000:]
message = ("PASS: installed, entered paddle game, dragged paddle, launched one main ball, "
           "backgrounded/resumed paused, restored saved serve after force-stop, and migrated original "
           "v1 save preserving 27 unlocked levels while restarting level 26 with 3 lives and 1 ball. "
           "Sudoku also passed generation, pencil notes, undo/redo, erase, digit entry, hint, pause, "
           "force-stop save restoration, and return to the unchanged marble progress.")
print(message, flush=True)
(out / "result.txt").write_text(message + "\n")
