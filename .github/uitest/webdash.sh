# WEB DASHBOARD (ShowFee Business, stage 1): sourced by full.sh and quick.sh, after the sample data is loaded.
section "Web dashboard (ShowFee Business)"
bklog() { adb logcat -c; seeder --es task "$2" "${@:4}"; sleep 5; L=$(adb logcat -d -s UITEST | grep -E "$2" | tail -1)
          echo "      $L" | cut -c1-700 >> $R; if echo "$L" | grep -qE "$3"; then ok "$1"; else bad "$1 (log should match: $3)"; fi; }
webdash() { adb shell am start -W --activity-clear-top -n $PKG/$NS.WebDashActivity > /dev/null; sleep 2; }

seeder --es task plan --es plan free
settings; check "Settings has Web dashboard" "Web dashboard" 3; check "Row explains it" "on a computer" 3
webdash; check "Free plan: dashboard is part of Business" "part of ShowFee Business"; check "Free plan: see Business" "See ShowFee Business"
shot "REVIEW-web-free"

seeder --es task plan --es plan business
seeder --es task bkanon; sleep 3
webdash; check "Business: switch shown" "on the web dashboard"; check "Business: off at first" "nothing is stored online"
shot "REVIEW-web-off"
$UI tap "on the web dashboard" > /dev/null; sleep 8
webdash; check "Switched on: shows when it was updated" "Updated"; check "Open dashboard button" "Open showfee.app/dashboard"
shot "REVIEW-web-on"
bklog "Bookings, clients, payments and expenses are online" bkdash 'gigs=1[0-9].*clients=[1-9].*payments=[1-9].*expenses=[1-9]'
bklog "Bank details and settings are never sent" bkdash 'iban=no settings=no'
bklog "Nobody else can read it" bkdashother 'bkdashother refused 403'
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
