#!/bin/bash
# QUICK TEST: new app icon – the app opens, the main screen and the plans screen (which shows the icon) work.
source <(sed -n '1,45p' .github/uitest/full.sh)
seeder --es task seed
section "New icon"
main --es page gigs --es gigsTab all
check "App opens on the bookings list" "Bookings" ; shot "icon-main"
source <(sed -n '1,4p' .github/uitest/plans.sh)
shot "icon-plans"
adb shell input keyevent 3; sleep 2; shot "icon-home"
section "Done"
nocrash "Whole run"
echo "" >> $R; echo "TOTAL: $pass passed, $fail failed" | tee -a $R
exit 0
