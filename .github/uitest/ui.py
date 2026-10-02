"""Tiny helper for the emulator test: find on-screen text and tap it."""
import re, subprocess, sys, time

def adb(*a):
    return subprocess.run(["adb", "shell", *a], capture_output=True, text=True).stdout

def nodes():
    adb("uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb("cat", "/sdcard/ui.xml")
    out = []
    for m in re.finditer(r'<node [^>]*?text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        t = m.group(1).replace("&#10;", " ").replace("&amp;", "&")
        x1, y1, x2, y2 = map(int, m.group(2, 3, 4, 5))
        out.append((t, (x1 + x2) // 2, (y1 + y2) // 2))
    return out

def tap(text, scroll=0):
    for attempt in range(scroll + 2):
        for t, x, y in nodes():
            hit = (t == text[1:]) if text.startswith("=") else (text.lower() in t.lower())
            if hit:
                adb("input", "tap", str(x), str(y))
                print(f"tapped '{t}' at {x},{y}")
                time.sleep(1.5)
                return 0
        if attempt < scroll:
            adb("input", "swipe", "540", "1700", "540", "700", "400")
        time.sleep(1)
    print(f"NOT FOUND: '{text}'")
    return 1

if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "tap":
        sys.exit(tap(sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else 0))
    if cmd == "texts":
        for t, x, y in nodes():
            if t.strip():
                print(t)
