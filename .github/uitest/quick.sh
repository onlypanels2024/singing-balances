#!/bin/bash
# PROBE (not a test): how the virtual phone reports the keyboard, so the test helper can avoid tapping under it.
source <(sed -n '1,45p' .github/uitest/full.sh)
seeder --es task seed
P=$OUT/probe.txt
kb() { echo "== $1" >> $P; adb shell dumpsys input_method | grep -E 'mInputShown|mIsInputViewShown|mWindowVisible|mDecorViewVisible|isInputViewShown' >> $P; adb shell dumpsys window InputMethod | grep -E 'mViewVisibility|isVisible|mHasSurface|mFrame=|frame=|mGivenContentInsets|touchable' | head -12 >> $P; }
kb "home, no keyboard"
main --es page gigs --es gigsTab all
$UI tap "Add a booking" > /dev/null
kb "form open, before typing"
$UI fill "Client (who pays you)" "Test Client" > /dev/null; sleep 1
kb "after typing client"; shot "probe-typing"
adb shell input keyevent 4; sleep 1
kb "after one back"; shot "probe-after-back"
echo "TOTAL: 0 passed, 0 failed" >> $R
exit 0
