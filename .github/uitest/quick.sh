#!/bin/bash
# Quick diagnostic: welcome job chips and currency dropdowns only.
PKG=com.showfee.app
NS=com.onlypanels.singingbalances
OUT=full-results; mkdir -p $OUT
UI="python3 .github/uitest/ui.py"
adb install -r app/build/outputs/apk/debug/app-debug.apk
for ime in $(adb shell ime list -s | tr -d '\r'); do adb shell ime disable "$ime" > /dev/null 2>&1; done
adb shell am start -W -n $PKG/$NS.MainActivity > /dev/null; sleep 3
$UI tap "Photographer"; sleep 2
adb exec-out screencap -p > $OUT/q1-after-chip.png
$UI texts > $OUT/q1-texts.txt
adb shell uiautomator dump /sdcard/ui.xml; adb pull /sdcard/ui.xml $OUT/q1-ui.xml
$UI tap "US dollar" 3; sleep 2
adb exec-out screencap -p > $OUT/q2-dropdown.png
$UI texts > $OUT/q2-texts.txt
adb shell uiautomator dump /sdcard/ui.xml; adb pull /sdcard/ui.xml $OUT/q2-ui.xml
echo done > $OUT/report.txt
