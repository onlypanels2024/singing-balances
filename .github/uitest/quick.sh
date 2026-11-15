#!/bin/bash
# QUICK TEST: only the part being worked on (now: booking link and requests). Runs when .github/uitest/QUICK exists.
# The full test (full.sh) still runs once before anything is released.
source <(sed -n '1,45p' .github/uitest/full.sh)   # same helpers, install and phone set-up as the full test
seeder --es task seed
source .github/uitest/booking.sh
section "Done"
nocrash "Whole run"
echo "" >> $R; echo "TOTAL: $pass passed, $fail failed" | tee -a $R
adb logcat -d > $OUT/logcat.txt
exit 0
