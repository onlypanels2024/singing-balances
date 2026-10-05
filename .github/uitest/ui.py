"""Helper for the emulator tests: find things on screen, tap them, type into fields, check text."""
import re, subprocess, sys, time, html

def adb(*a):
    return subprocess.run(["adb", "shell", *a], capture_output=True, text=True).stdout

def nodes():
    ns = _nodes()
    # A system "<app> isn't responding" box (not ShowFee's) can cover the screen on the virtual phone: dismiss it
    for n in ns:
        if "isn't responding" in n["text"] or "isn’t responding" in n["text"]:
            for m in ns:
                if m["text"] in ("Wait", "Close app"):
                    adb("input", "tap", str(m["x"]), str(m["y"]))
                    print("dismissed a system 'not responding' box")
                    time.sleep(1.5)
                    return _nodes()
    return ns

def _nodes():
    adb("uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb("cat", "/sdcard/ui.xml")
    out = []
    for m in re.finditer(r'<node ([^>]*?)/?>', xml):
        # Android writes attributes in "double" quotes, or 'single' quotes when the text itself contains "
        attrs = {k: (a if a != "" or b == "" else b) for k, a, b in
                 re.findall(r'([\w-]+)=(?:"([^"]*)"|\'([^\']*)\')', m.group(1))}
        b = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', attrs.get("bounds", ""))
        if not b:
            continue
        x1, y1, x2, y2 = map(int, b.groups())
        t = html.unescape(attrs.get("text", "")).replace("\n", " ")
        d = html.unescape(attrs.get("content-desc", ""))
        out.append({"text": t, "desc": d, "cls": attrs.get("class", ""), "x": (x1 + x2) // 2, "y": (y1 + y2) // 2,
                    "h": y2 - y1})
    return out

def match(n, text, field="text"):
    v = n[field]
    if text.startswith("="):
        return v == text[1:]
    return text.lower() in v.lower()

def swipe(up=False):
    # slow drag (no fling) so the page has stopped moving before we read it again
    if up:
        adb("input", "swipe", "540", "900", "540", "1500", "900")
    else:
        adb("input", "swipe", "540", "1500", "540", "900", "900")
    time.sleep(1.8)

def find(text, scroll=0, field="text", nth=0):
    """scroll > 0 swipes down the page to look further; scroll < 0 swipes up (e.g. in a dropdown list)."""
    time.sleep(0.8)  # let the screen settle after the last tap
    for attempt in range(abs(scroll) + 1):
        hits = [n for n in nodes() if match(n, text, field) and n["h"] > 0]
        if len(hits) > nth:
            return hits[nth]
        if attempt < abs(scroll):
            swipe(up=scroll < 0)
    return None

def tap(text, scroll=0, field="text", nth=0):
    n = find(text, scroll, field, nth)
    if not n:
        print(f"NOT FOUND: '{text}'")
        return 1
    adb("input", "tap", str(n["x"]), str(n["y"]))
    print(f"tapped '{n[field]}' at {n['x']},{n['y']}")
    time.sleep(1.5)
    return 0

def fill(label, value, scroll=0):
    """Types into the input field that follows a label."""
    for attempt in range(scroll + 1):
        ns = nodes()
        for i, n in enumerate(ns):
            if match(n, label):
                for m in ns[i + 1:]:
                    if m["cls"].endswith("EditText") or m["cls"].endswith("AutoCompleteTextView"):
                        adb("input", "tap", str(m["x"]), str(m["y"]))
                        time.sleep(0.5)
                        adb("input", "keycombination", "113", "29")  # select all
                        adb("input", "keyevent", "67")              # delete
                        adb("input", "text", value.replace(" ", "%s"))
                        time.sleep(0.5)
                        print(f"filled '{label}' = {value}")
                        return 0
        if attempt < scroll:
            swipe()
    print(f"FIELD NOT FOUND: '{label}'")
    return 1

def tapafter(label, scroll=0):
    """Taps the button that follows a label (date and time pickers in forms)."""
    for attempt in range(scroll + 1):
        ns = nodes()
        for i, n in enumerate(ns):
            if match(n, label):
                for m in ns[i + 1:]:
                    if m["cls"].endswith("Button"):
                        adb("input", "tap", str(m["x"]), str(m["y"]))
                        print(f"tapped button after '{label}': '{m['text']}'")
                        time.sleep(1.5)
                        return 0
        if attempt < scroll:
            swipe()
    print(f"BUTTON NOT FOUND after: '{label}'")
    return 1

if __name__ == "__main__":
    if sys.argv[1] == "tapafter":
        sys.exit(tapafter(sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else 0))
    cmd = sys.argv[1]
    if cmd == "tap":
        sys.exit(tap(sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else 0))
    if cmd == "tapnth":
        sys.exit(tap(sys.argv[2], 0, "text", int(sys.argv[3])))
    if cmd == "tapdesc":
        sys.exit(tap(sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else 0, "desc"))
    if cmd == "has":
        sys.exit(0 if find(sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else 0) else 1)
    if cmd == "fill":
        sys.exit(fill(sys.argv[2], sys.argv[3], int(sys.argv[4]) if len(sys.argv) > 4 else 0))
    if cmd == "texts":
        for n in nodes():
            if n["text"].strip():
                print(n["text"])
