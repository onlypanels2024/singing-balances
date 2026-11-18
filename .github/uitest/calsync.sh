# CALENDAR SYNC: Settings › Calendar sync puts every booking in a calendar on the phone and keeps it up to date.
section "Calendar sync"
type bklog > /dev/null 2>&1 || bklog() { adb logcat -c; seeder --es task "$2" "${@:4}"; sleep 5; L=$(adb logcat -d -s UITEST | grep -E "$2" | tail -1)
          echo "      $L" | cut -c1-700 >> $R; if echo "$L" | grep -qE "$3"; then ok "$1"; else bad "$1 (log should match: $3)"; fi; }
adb shell pm grant $PKG android.permission.READ_CALENDAR; adb shell pm grant $PKG android.permission.WRITE_CALENDAR
adb logcat -c; seeder --es task bkcalmake; sleep 2; adb logcat -d -s UITEST:* | grep -A4 bkcalmake | head -6 | sed "s/^/      /" >> $R
settings; $UI tap "Calendar sync" 4 > /dev/null; sleep 2
check "Calendar sync screen opens from Settings" "Keep my bookings in my calendar"; check "Calendar sync off at first" "=Off"; shot "REVIEW-calsync-off"
$UI tap "Keep my bookings in my calendar" > /dev/null; sleep 2
check "Calendar: choose which calendar" "Which calendar?"; check "Calendar: the phone's calendar is listed" "Test calendar"; shot "REVIEW-calsync-pick"
$UI tap "Test calendar" > /dev/null; sleep 5
check "Calendar sync on" "Calendar: Test calendar"; shot "REVIEW-calsync-on"
bklog "Calendar: bookings added to the calendar" bkcalcheck 'events=[1-9][0-9]? '
seeder --es task bkcalgig --es do add; sleep 4
bklog "Calendar: a new booking goes in by itself" bkcalcheck 'Cal Test · Calendar test'
seeder --es task bkcalgig --es do rename; sleep 4
bklog "Calendar: a changed booking is updated" bkcalcheck 'Cal Test · Renamed in test'
seeder --es task bkcalgig --es do cancel; sleep 4
adb logcat -c; seeder --es task bkcalcheck; sleep 3; L=$(adb logcat -d -s UITEST | grep bkcalcheck | tail -1); echo "      $L" | cut -c1-700 >> $R
if echo "$L" | grep -q "events=" && ! echo "$L" | grep -q "Cal Test"; then ok "Calendar: a cancelled booking is taken out"; else bad "Calendar: a cancelled booking is taken out"; fi
seeder --es task bkcalgig --es do delete; sleep 2
settings; check "Settings shows calendar sync on" "On · Test calendar" 4
gig 7; nocheck "Booking: no 'Add to calendar' button when synced" "Add to calendar" 3
adb shell am start -W -n $PKG/$NS.CalendarSyncActivity > /dev/null; sleep 2
$UI tap "Keep my bookings in my calendar" > /dev/null; sleep 1
check "Switching off asks what to do" "Take them out"; shot "REVIEW-calsync-switch-off"
$UI tap "Take them out" > /dev/null; sleep 4
bklog "Calendar: switched off and events taken out" bkcalcheck 'events=0 '
nocrash "Calendar sync"
