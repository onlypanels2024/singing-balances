#!/bin/bash
# QUICK TEST: the parts the keyboard got in the way of (new booking form, extras and VAT). Runs when .github/uitest/QUICK exists.
# The full test (full.sh) still runs once before anything is released.
source <(sed -n '1,45p' .github/uitest/full.sh)
seeder --es task seed
source <(sed -n '/^section "Add a booking (form/,/^nocrash "Add a booking"/p' .github/uitest/full.sh)
source .github/uitest/extras.sh
section "Done"
nocrash "Whole run"
echo "" >> $R; echo "TOTAL: $pass passed, $fail failed" | tee -a $R
adb logcat -d > $OUT/logcat.txt
exit 0
