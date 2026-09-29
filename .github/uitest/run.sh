#!/bin/bash
# Automated screenshot test on the emulator. Keeps going on errors so we see everything.
PKG=com.onlypanels.singingbalances
OUT=ui-results
mkdir -p $OUT
UI="python3 .github/uitest/ui.py"
n=0
shot() { sleep 2.5; n=$((n+1)); f=$(printf "%s/%02d-%s.png" $OUT $n "$1"); adb exec-out screencap -p > "$f"; echo "screenshot $f"; }
texts() { $UI texts > "$OUT/$(printf %02d $n)-$1.txt"; }
main() { adb shell am start -W -n $PKG/.MainActivity "$@" > /dev/null; }
back() { adb shell input keyevent 4; sleep 1; }
swipe() { adb shell input swipe 540 1800 540 600 500; }

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
adb logcat -c
adb shell am broadcast -n $PKG/.DemoSeeder --es task seed
sleep 3

main;                                         shot gigs-unpaid; texts gigs-unpaid
main --es page gigs --es gigsTab upcoming;    shot gigs-upcoming
main --es page gigs --es gigsTab all;         shot gigs-all; swipe; shot gigs-all-scrolled
main --es page gigs --es gigsTab unpaid
$UI tap Calendar;                             shot calendar-by-tap; swipe; shot calendar-scrolled
$UI tap Clients;                              shot clients-by-tap; texts clients
$UI tap Money;                                shot money; texts money; swipe; shot money-2; swipe; shot money-3; swipe; shot money-4

# gig screens (ids from the seeder: 3 overdue part-paid, 4 yesterday, 5 tonight, 6 tomorrow, 7 pencilled, 9 cancelled, 1 paid)
for id in 3 4 5 6 7 9 1; do
  adb shell am start -W -n $PKG/.GigActivity --el id $id > /dev/null
  shot gig-$id; swipe; shot gig-$id-scrolled
  back
done

adb shell am start -W -n $PKG/.ClientActivity --es name "Hilton Malta" > /dev/null; shot client-hilton; swipe; shot client-hilton-scrolled; back

adb shell am start -W -n $PKG/.SettingsActivity > /dev/null; shot settings; swipe; shot settings-2; swipe; shot settings-3; swipe; shot settings-4; back

# Forms
main --es page gigs --es gigsTab unpaid
$UI tap "Add a gig";                          shot form-new-gig; back; back
adb shell am start -W -n $PKG/.GigActivity --el id 3 > /dev/null
$UI tap "Record a payment";                   shot form-payment; back; back
$UI tap "Add an expense" 4;                   shot form-expense; back; back
back
main --es page clients
$UI tap "Add a client";                       shot form-client; back; back

# Actions that hand over to other apps (no Outlook on the emulator: expect a chooser or a message, not a crash)
adb shell am start -W -n $PKG/.GigActivity --el id 4 > /dev/null
$UI tap "Send invoice";                       shot action-send-invoice; back; back
adb shell am start -W -n $PKG/.GigActivity --el id 4 > /dev/null
$UI tap "payment reminder";                   shot action-reminder; back; back
adb shell am start -W -n $PKG/.GigActivity --el id 6 > /dev/null
$UI tap "calendar";                           shot action-calendar; back; back

# Paid in full on the overdue gig, then check the home total changes
adb shell am start -W -n $PKG/.GigActivity --el id 3 > /dev/null
$UI tap "Paid in full";                       shot gig-3-after-paid; back
main --es page gigs --es gigsTab unpaid;      shot gigs-unpaid-after-paid

# Invoice PDF, reminders, backup round trip, client stats
adb shell am broadcast -n $PKG/.DemoSeeder --es task invoice --el id 4
adb shell am broadcast -n $PKG/.DemoSeeder --es task invoice --el id 1
adb shell am broadcast -n $PKG/.DemoSeeder --es task notify
sleep 3
adb shell cmd statusbar expand-notifications; shot notifications; adb shell cmd statusbar collapse
adb shell dumpsys notification --noredact > $OUT/notifications.txt
adb shell am broadcast -n $PKG/.DemoSeeder --es task backup
adb shell am broadcast -n $PKG/.DemoSeeder --es task clientstats
sleep 3
adb pull /sdcard/Android/data/$PKG/files/ $OUT/files/

# Rotate / relaunch after restore to be sure nothing crashes
main --es page money; shot money-after-restore

adb logcat -d > $OUT/logcat.txt
grep -E "FATAL|AndroidRuntime|UITEST" $OUT/logcat.txt > $OUT/summary.txt
echo "---- summary ----"; cat $OUT/summary.txt
exit 0
