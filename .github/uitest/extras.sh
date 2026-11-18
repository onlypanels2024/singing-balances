# EXTRAS AND VAT ON THE BILL: Settings › Getting paid › Charge VAT, then extras + VAT on a booking, the booking screen and the invoice.
section "Extras and VAT on the bill"
type leave > /dev/null 2>&1 || leave() { adb shell input keyevent 4; sleep 1; if $UI has "$1" > /dev/null; then adb shell input keyevent 4; sleep 1; fi; }
settings pay; $UI tap "=Charge VAT" 6 > /dev/null; sleep 1
check "VAT: rate shown when switched on" "VAT rate (%)" 2; check "VAT: added on top or included" "VAT added on top" 2; shot "REVIEW-vat-settings"
leave "VAT rate"
dcheck "VAT: setting saved" "vat_charge=1"
gig 4; $UI tap "=EDIT" > /dev/null; sleep 2
$UI tap "Add an extra" 5 > /dev/null; sleep 1
$UI fill "Extras on the bill" "Travel to Gozo" > /dev/null
$UI fill "Travel to Gozo" "40" > /dev/null
check "Form: VAT switch offered" "Charge VAT (18%, added on top)" 2
$UI tap "Charge VAT (18%" > /dev/null; sleep 1
check "Form: total for the client with VAT" "Total for the client: " 2; check "Form: VAT amount shown" "(VAT 18%: " 2; shot "REVIEW-extras-form"
$UI tap "=SAVE" > /dev/null; sleep 2
check "Booking: extra line shown" "Travel to Gozo €40.00" 2; check "Booking: VAT shown" "VAT 18%" 2; shot "REVIEW-extras-booking"
dcheck "Booking: extras and VAT saved" "extras=4000 vat=1800"
seeder --es task invoice --el id 4
adb pull "/sdcard/Android/data/$PKG/files/invoice-4.pdf" $OUT/invoice-extras-vat.pdf > /dev/null 2>&1 && ok "Invoice with extras and VAT made" || bad "Invoice with extras and VAT made"
if command -v pdftoppm > /dev/null; then pdftoppm -png -r 110 -f 1 -l 1 $OUT/invoice-extras-vat.pdf $OUT/REVIEW-invoice-extras-vat > /dev/null 2>&1; fi
settings pay; $UI tap "=Charge VAT" 6 > /dev/null; sleep 1; leave "Payment due (days"
dcheck "VAT: switched off again" "vat_charge= "
nocrash "Extras and VAT"
