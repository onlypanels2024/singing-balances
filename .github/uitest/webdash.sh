# WEB DASHBOARD (every plan, stage 1): sourced by full.sh and quick.sh, after the sample data is loaded.
section "Web dashboard (every plan)"
bklog() { adb logcat -c; seeder --es task "$2" "${@:4}"; sleep 5; L=$(adb logcat -d -s UITEST | grep -E "$2" | tail -1)
          echo "      $L" | cut -c1-700 >> $R; if echo "$L" | grep -qE "$3"; then ok "$1"; else bad "$1 (log should match: $3)"; fi; }
webdash() { adb shell am start -W --activity-clear-top -n $PKG/$NS.WebDashActivity > /dev/null; sleep 2; }

seeder --es task plan --es plan free
settings; check "Settings has Web dashboard" "Web dashboard" 3; check "Row explains it" "on a computer" 3
webdash; nocheck "Free plan: not locked to Business" "part of ShowFee Business"
seeder --es task bkanon; sleep 3
webdash; check "Free plan: switch shown" "on the web dashboard"; check "Free plan: off at first" "nothing is stored online"
shot "REVIEW-web-off"
$UI tap "on the web dashboard" > /dev/null; sleep 8
webdash; check "Switched on: shows when it was updated" "Updated"; check "Open dashboard button" "Open showfee.app/dashboard"
shot "REVIEW-web-on"
bklog "Bookings, clients, payments and expenses are online" bkdash 'gigs=1[0-9].*clients=[1-9].*payments=[1-9].*expenses=[1-9]'
bklog "Bank details and settings are never sent" bkdash 'iban=no settings=no'
bklog "Nobody else can read it" bkdashother 'bkdashother refused 403'

# ---- changes made on the web dashboard (written exactly like the website writes them)
T=$(( $(date +%s) / 86400 ))
b64() { echo -n "$1" | base64 -w0; }
op() { seeder --es task bkop --es t "$1" --es d64 "$(b64 "$2")"; sleep 2; }
op gig '{"ref":"wtest1","client":"Web Client","event":"From the web","gig_day":'$((T+3))',"due_day":'$((T+3))',"fee_cents":33300,"status":1,"start_min":1200}'
op pay '{"ref":"wtest2","gig":"wtest1","cents":11100,"day":'$T',"note":"Cash"}'
op client '{"ref":"wtest3","name":"Web New Client","email":"web@example.com","phone":"+356 7900 0000","notes":""}'
op exp '{"ref":"wtest4","day":'$T',"cents":4200,"category":"Equipment","note":"Cable","gig":"wtest1"}'
main --es page gigs; sleep 10   # opening the app brings the changes in
bklog "Web: new booking, payment, client and expense reach the phone" bkcheck 'gig\[Web Client\|From the web\|fee=33300\|paid=11100\|status=1\|start=1200\|exp=1\] client=web@example.com/\+356 7900 0000 waiting=0'
main --es page gigs; $UI tap "=Upcoming" > /dev/null; check "Web: booking made on the web is in the app" "Web Client" 4; shot "REVIEW-web-edit-phone"
op gig '{"id":"wtest1","client":"Web Client","event":"Changed on the web","gig_day":'$((T+3))',"due_day":'$((T+5))',"fee_cents":44400,"status":0,"start_min":-1}'
op payDel '{"id":"wtest2"}'
sleep 6   # the silent message should bring these in by itself, with the app in the background
bklog "Web: changes arrive by themselves (silent message)" bkcheck 'Changed on the web\|fee=44400\|paid=0'
bklog "Web: nothing left to apply" bkpull 'applied=[0-2]'
bklog "Web: booking changed and payment removed" bkcheck 'gig\[Web Client\|Changed on the web\|fee=44400\|paid=0\|status=0\|start=-1\|exp=1\].* waiting=0'
bklog "Web: dashboard copy updated afterwards" bkdash 'gigs=1[0-9]'
op gigDel '{"id":"wtest1"}'
bklog "Web: delete applied" bkpull 'applied=[01]'
bklog "Web: booking deleted on the phone" bkcheck 'bkcheck client=web@example.com'
bklog "Web: no changes waiting means nothing to do" bkpull 'applied=0'
# ---- date clash warning: a second booking on the same day as Bob's Bar (today, 21:00)
op gig '{"ref":"wclash","client":"Web Clash","event":"Birthday","gig_day":'$T',"due_day":'$T',"fee_cents":10000,"status":0,"start_min":1140}'
bklog "Clash: second booking on the same day saved" bkpull 'applied=[01]'
main --es page gigs; $UI tap "=Upcoming" > /dev/null; $UI tap "Bob's Bar" 3 > /dev/null; sleep 2
check "Clash: booking screen warns about the other booking" "You already have Web Clash at 19:00 on this day"; shot "REVIEW-clash-booking"
$UI tap "=EDIT" > /dev/null || $UI tap "=Edit" > /dev/null; sleep 2
check "Clash: booking form warns too" "You already have Web Clash at 19:00 on this day"; shot "REVIEW-clash-form"
adb shell input keyevent 4; sleep 1; adb shell input keyevent 4; sleep 1
op gigDel '{"id":"wclash"}'
bklog "Clash: test booking removed" bkpull 'applied=[01]'
bklog "Web: another account can't send changes" bkop 'bkop refused 403' --es t gig --es d64 "$(b64 '{"ref":"x","client":"Hacker","gig_day":1,"due_day":1,"fee_cents":1,"status":0,"start_min":-1}')" --es as other
nocrash "Web dashboard changes"
gig 7; $UI tap "Mark as confirmed" 3 > /dev/null; sleep 2; main --es page gigs; sleep 8
bklog "A change on the phone is sent again" bkdash 'gigs=1[0-9]'
settings; check "Settings row shows it's on" "On · showfee.app/dashboard" 3
webdash; $UI tap "on the web dashboard" > /dev/null; sleep 2
check "Switching off asks first" "Switch off the web dashboard?"; ktapw() { $UI tap "=$1" > /dev/null || $UI tap "=$(echo "$1" | tr '[:lower:]' '[:upper:]')" > /dev/null; }
ktapw "Switch off"; sleep 6
webdash; check "Switched off" "nothing is stored online"
bklog "Online copy deleted" bkdash 'bkdash missing 404'
nocrash "Web dashboard"
seeder --es task plan --es plan -
