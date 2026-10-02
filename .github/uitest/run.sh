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
seeder() { adb shell am broadcast -n $PKG/.DemoSeeder "$@" > /dev/null; sleep 2; }

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
adb logcat -c

# ---- 1. Brand-new user: welcome screen ----
main;                                         shot welcome; texts welcome; swipe; shot welcome-2
$UI tap "Photographer" 2;                     shot welcome-photographer; texts welcome-photographer
adb shell am force-stop $PKG

# ---- 2. Sample data, default look ----
seeder --es task seed
main;                                         shot gigs-unpaid; texts gigs-unpaid
main --es page gigs --es gigsTab upcoming;    shot gigs-upcoming
main --es page gigs --es gigsTab all;         shot gigs-all; swipe; shot gigs-all-scrolled
main --es page gigs --es gigsTab unpaid
$UI tap Calendar;                             shot calendar-by-tap; swipe; shot calendar-scrolled
$UI tap Clients;                              shot clients-by-tap; texts clients
$UI tap Money;                                shot money; texts money; swipe; shot money-2; swipe; shot money-3

for id in 3 4 5 6 7 9 1; do
  adb shell am start -W -n $PKG/.GigActivity --el id $id > /dev/null
  shot gig-$id; swipe; shot gig-$id-scrolled
  back
done

adb shell am start -W -n $PKG/.ClientActivity --es name 'Hilton\ Malta' > /dev/null; shot client-hilton; swipe; shot client-hilton-scrolled; back

seeder --es task logo
adb shell am start -W -n $PKG/.SettingsActivity > /dev/null
shot settings; texts settings
for i in 2 3 4 5 6 7; do swipe; shot settings-$i; done
back

# Forms
main --es page gigs --es gigsTab unpaid
$UI tap "Add a booking";                          shot form-new-gig; back; back
adb shell am start -W -n $PKG/.GigActivity --el id 3 > /dev/null
$UI tap "Record a payment";                   shot form-payment; back; back
adb shell am start -W -n $PKG/.GigActivity --el id 4 > /dev/null
$UI tap "Add an expense" 4;                   shot form-expense; back; back
main --es page clients
$UI tap "Add a client";                       shot form-client; back; back

# Actions that hand over to other apps
adb shell am start -W -n $PKG/.GigActivity --el id 4 > /dev/null
$UI tap "Send invoice";                       shot action-send-invoice; back; back
adb shell am start -W -n $PKG/.GigActivity --el id 6 > /dev/null
$UI tap "calendar";                           shot action-calendar; back; back

# Invoices (with logo), reminders
seeder --es task invoice --el id 4
seeder --es task invoice --el id 1
seeder --es task notify
adb shell cmd statusbar expand-notifications; shot notifications; adb shell cmd statusbar collapse
adb shell dumpsys notification --noredact > $OUT/notifications.txt

# ---- 3. Another user's look: photographer, dark mode, teal, pounds ----
seeder --es task look --es profession photographer --es accent teal --es mode dark --es currency GBP --es tab -
main --es page gigs --es gigsTab unpaid;      shot dark-gigs; texts dark-gigs
main --es page gigs --es gigsTab upcoming;    shot dark-upcoming
$UI tap Calendar;                             shot dark-calendar
$UI tap Money;                                shot dark-money
adb shell am start -W -n $PKG/.GigActivity --el id 4 > /dev/null; shot dark-gig-4; back
adb shell am start -W -n $PKG/.SettingsActivity > /dev/null; shot dark-settings; swipe; shot dark-settings-2; back
main --es page gigs --es gigsTab unpaid
$UI tap "Add a shoot";                        shot dark-form-new; back; back
seeder --es task invoice --el id 3

# ---- 4. Light mode, rose, make-up artist, dollars ----
seeder --es task look --es profession makeup --es accent rose --es mode light --es currency USD --es tab - --es email outlook
main --es page gigs --es gigsTab unpaid;      shot rose-gigs; texts rose-gigs
main --es page clients;                       shot rose-clients
adb shell am start -W -n $PKG/.GigActivity --el id 4 > /dev/null; shot outlook-gig-4; texts outlook-gig-4
$UI tap "Send invoice";                       shot outlook-send-invoice; back; back
adb shell am start -W -n $PKG/.SettingsActivity > /dev/null; $UI tap "Send emails with" 6; shot outlook-settings; back
seeder --es task look --es email ask
adb shell am start -W -n $PKG/.GigActivity --el id 4 > /dev/null; shot ask-gig-4; texts ask-gig-4
$UI tap "Send invoice";                       shot ask-send-invoice; back; back

# ---- Google sign-in: settings when signed out, then the send preview when signed in ----
seeder --es task look --es email gmail --es google -
adb shell am start -W -n $PKG/.SettingsActivity > /dev/null; $UI tap "Send straight from OutRo" 6; shot google-settings-signed-out; texts google-settings-signed-out
$UI tap "Sign in with Google";                shot google-signin-tap; sleep 3; shot google-signin-tap-2; back; back; back
seeder --es task look --es google maria.sings@gmail.com
adb shell am start -W -n $PKG/.SettingsActivity > /dev/null; $UI tap "Send straight from OutRo" 6; shot google-settings-signed-in; back
adb shell am start -W -n $PKG/.GigActivity --el id 4 > /dev/null; shot google-gig-4; texts google-gig-4
$UI tap "Send invoice";                       shot google-compose; texts google-compose
$UI tap "=SEND";                              sleep 4; shot google-after-send; texts google-after-send; back; back; back
seeder --es task look --es google -

# ---- 5. Existing user upgrading (your phone): data kept, purple, "Singing", euro, no welcome screen ----
seeder --es task migrate
adb shell am force-stop $PKG
main;                                         shot upgrade-gigs; texts upgrade-gigs
$UI tap Money;                                shot upgrade-money

# Backup round trip + client stats
seeder --es task backup
seeder --es task clientstats
sleep 2
adb pull /sdcard/Android/data/$PKG/files/ $OUT/files/
main --es page money; shot money-after-restore

adb logcat -d > $OUT/logcat.txt
grep -E "FATAL|AndroidRuntime|UITEST" $OUT/logcat.txt > $OUT/summary.txt
echo "---- summary ----"; cat $OUT/summary.txt
exit 0
