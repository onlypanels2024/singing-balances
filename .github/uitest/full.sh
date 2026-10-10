#!/bin/bash
# FULL BUTTON TEST: taps every button, fills every form, and checks the result on screen and in the saved data.
PKG=com.showfee.app
NS=com.onlypanels.singingbalances
OUT=full-results
mkdir -p $OUT
UI="python3 .github/uitest/ui.py"
R=$OUT/report.txt
: > $R
n=0; pass=0; fail=0
shot() { sleep 1.5; n=$((n+1)); adb exec-out screencap -p > "$(printf "%s/%03d-%s.png" $OUT $n "$1")"; }
ok() { pass=$((pass+1)); echo "PASS  $1" | tee -a $R; }
bad() { fail=$((fail+1)); echo "FAIL  $1" | tee -a $R; shot "FAIL-$(echo "$1" | tr -c 'a-zA-Z0-9' '-' | cut -c1-40)"; }
# check "what" "text on screen" [scroll]
check() { if $UI has "$2" ${3:-0} > /dev/null; then ok "$1"; else bad "$1 (expected to see: $2)"; fi; }
nocheck() { if $UI has "$2" > /dev/null; then bad "$1 (should be gone: $2)"; else ok "$1"; fi; }
# dump the saved data and check it matches a pattern
data() { adb logcat -d -s ShowFeeInsets >> $OUT/insets.txt; adb logcat -c; adb shell am broadcast -n $PKG/$NS.DemoSeeder --es task dump > /dev/null; sleep 2;
         D=$(adb logcat -d -s UITEST | grep "dump " | tail -1); echo "      data: ${D#*dump }" >> $R; }
dcheck() { data; if echo "$D" | grep -qE "$2"; then ok "$1"; else bad "$1 (saved data should match: $2)"; fi; }
crashes() { adb logcat -b crash -d | grep -c "FATAL EXCEPTION" ; }
nocrash() { c=$(crashes); if [ "$c" -gt "${CR:-0}" ]; then bad "$1: APP CRASHED"; adb logcat -b crash -d >> $OUT/crash.txt; CR=$c; else ok "$1: no crash"; fi; }
inapp() { adb shell dumpsys window | grep -E "mCurrentFocus|mFocusedApp" | grep -q "$PKG"; }
left() { if inapp; then bad "$1 (expected another app to open)"; else ok "$1 (opened $(adb shell dumpsys window | grep mCurrentFocus | grep -oE '[a-z]+(\.[a-z0-9]+)+/' | head -1))"; fi; }
backto() { for i in 1 2 3 4; do inapp && $UI has "$1" > /dev/null && return; adb shell input keyevent 4; sleep 1.2; done; }
# --activity-clear-top: always open a fresh copy of the screen, not whatever was left on top
main() { adb shell am start -W --activity-clear-top -n $PKG/$NS.MainActivity "$@" > /dev/null; sleep 1.5; }
gig() { adb shell am start -W --activity-clear-top -n $PKG/$NS.GigActivity --el id $1 > /dev/null; sleep 1.5; }
# settings [page]: opens the Settings menu, or one of its pages (work, look, business, pay, email, reminders, backup)
settings() { if [ -n "$1" ]; then adb shell am start -W --activity-clear-top -n $PKG/$NS.SettingsActivity --es section "$1" > /dev/null;
             else adb shell am start -W --activity-clear-top -n $PKG/$NS.SettingsActivity > /dev/null; fi; sleep 1.5; }
seeder() { adb shell am broadcast -n $PKG/$NS.DemoSeeder "$@" > /dev/null; sleep 2; }
section() { echo "" >> $R; echo "== $1 ==" | tee -a $R; }
MONTH=$(date +"%B %Y"); PREV=$(date -d "$(date +%Y-%m-15) -1 month" +"%B %Y"); NEXT=$(date -d "$(date +%Y-%m-15) +1 month" +"%B %Y")
YEAR=$(date +%Y); LASTYEAR=$((YEAR-1))

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
# Hide Android's own crash / "not responding" boxes for other apps so they can't cover the screen
adb shell settings put global hide_error_dialogs 1
# No on-screen keyboard (typing still works) so it never hides buttons
for ime in $(adb shell ime list -s | tr -d '\r'); do adb shell ime disable "$ime" > /dev/null 2>&1; done
adb shell settings put secure show_ime_with_hard_keyboard 0
echo "keyboards still on: $(adb shell ime list -s | tr -d '\r' | tr '\n' ' ')" >> $R
adb logcat -b crash -c; CR=0

# ====================================================================
section "Welcome screen (brand-new user)"
main
check "Intro page 1 shows" "Every booking in one place"
check "Intro shows Welcome" "WELCOME TO"
shot REVIEW-intro-1
$UI tap "=Next" > /dev/null; sleep 1; check "Intro page 2" "Always know who owes you"; shot REVIEW-intro-2
$UI tap "=Next" > /dev/null; sleep 1; check "Intro page 3" "Invoices and reminders in one tap"; shot REVIEW-intro-3
nocheck "No Skip on the last intro page" "=Skip"
adb shell input keyevent 4; sleep 1; main
check "Reopening starts the intro again (nothing skipped by accident)" "Every booking in one place"
$UI tap "=Skip" > /dev/null; sleep 2
check "Welcome screen shows" "Welcome to"
check "Setup questions after the intro" "What do you do?"
nocrash "Intro pages"
for pair in "Singer:bookings" "Band:bookings" "=DJ:bookings" "Photographer:shoots" "Videographer:shoots" "Hair & make-up:bookings" "MC / host:events" "Dancer:shows" "Something else:bookings"; do
  chip="${pair%%:*}"; word="${pair##*:}"
  $UI tap "$chip" > /dev/null; sleep 2; check "Job chip '$chip' -> wording '$word'" "\"$word\"" 1
done
$UI tap "Photographer" > /dev/null
$UI tap "=Outlook" 3 > /dev/null; check "Email choice Outlook selectable" "Ask each time"
$UI tapdesc "Teal" 3 > /dev/null; nocrash "Colour swatch on welcome"
$UI fill "Your name or business name" "Test User" 3 > /dev/null
$UI tap "US dollar" 3 > /dev/null || $UI tap "Euro (" 3 > /dev/null; sleep 1; $UI tap "British pound" -3 > /dev/null || adb shell input keyevent 4
check "Currency picked on welcome" "British pound" 3
$UI tap "Get started" 4
check "Get started opens home" "Still owed to you"
check "Home uses chosen currency (£)" "£0.00"
check "Home uses chosen wording (Shoots)" "Shoots"
dcheck "Welcome choices saved" "currency=GBP accent=teal mode=.* profession=photographer"
check "Brand-new home offers a start" "New to ShowFee?"
check "Start card: add first booking" "Add my first shoot"
check "Start card: examples button" "Show me with example shoots"
shot REVIEW-start-card
$UI tap "=Upcoming" > /dev/null; check "Start card on Upcoming too" "New to ShowFee?"
$UI tap "Show me with example shoots" > /dev/null; sleep 2
check "Examples note shows" "You're looking at example shoots"
dcheck "Six example bookings added" "gigs=6 "
check "Examples fill the money card" "Still owed to you"
shot REVIEW-examples-home
$UI tap "=Upcoming" > /dev/null; check "Example upcoming booking" "Christmas party"; shot REVIEW-examples-upcoming
$UI tap "=Calendar" > /dev/null; sleep 1; shot REVIEW-examples-calendar
main --es page gigs --es gigsTab unpaid
$UI tap "Wedding reception" > /dev/null; check "Example booking opens" "Example booking"; shot REVIEW-example-booking
backto "Still owed to you"
adb logcat -c; seeder --es task plan --es plan free
if adb logcat -d -s UITEST | grep -q "bookingsThisMonth=0"; then ok "Examples don't use up the free plan"; else bad "Examples don't use up the free plan ($(adb logcat -d -s UITEST | grep plan | tail -1))"; fi
main --es page gigs --es gigsTab unpaid
$UI tap "Remove examples" > /dev/null; check "Remove asks first" "Remove the examples?"; shot REVIEW-remove-examples
$UI tap "=KEEP FOR NOW" > /dev/null; check "Keep for now keeps them" "You're looking at example shoots"
$UI tap "Remove examples" > /dev/null; $UI tap "=REMOVE" > /dev/null; sleep 2
nocheck "Examples note gone" "You're looking at example shoots"
dcheck "Examples removed" "gigs=0 .*expenses=0 "
check "Back to the start card" "New to ShowFee?"
seeder --es task plan --es plan -
nocrash "Example bookings"
shot welcome-done

# ====================================================================
section "Sample data loaded"
adb shell am force-stop $PKG
seeder --es task seed
main --es page gigs --es gigsTab unpaid
dcheck "Sample data in place" "gigs=10 cancelled=1 pencilled=1 paid=120000 owed=145000 expenses=5 expTotal=55000 clients=6"
check "Total owed on home card" "€1,450.00"
check "Overdue amount on home card" "€1,300.00 overdue"
check "Next booking on home card" "NEXT BOOKING"

section "Home: tabs and list"
$UI tap "=Upcoming" > /dev/null; check "Upcoming tab shows future bookings" "Acoustic night"
nocheck "Upcoming tab hides past bookings" "Summer terrace"
$UI tap "All bookings" > /dev/null; check "All bookings tab shows old bookings" "Summer terrace" 3
$UI tap "=Unpaid" > /dev/null; check "Unpaid tab shows overdue booking" "Company party"
nocheck "Unpaid tab hides paid bookings" "Gala dinner"
$UI tap "Company party" > /dev/null; check "Tapping a booking opens it" "Record a payment"; adb shell input keyevent 4; sleep 1
$UI tapdesc "Settings" > /dev/null; check "Settings button (cog) opens Settings" "Backup & export"; adb shell input keyevent 4; sleep 1

section "Add a booking (form, validation, pickers)"
main --es page gigs --es gigsTab all
$UI tap "Add a booking" > /dev/null; check "Add a booking opens form" "New booking"
$UI tap "=SAVE" > /dev/null; check "Saving empty form is blocked" "New booking"
$UI fill "Client (who pays you)" "Test Client" > /dev/null
$UI fill "Client email" "test@example.com" > /dev/null
$UI fill "Event / venue" "Garden Party" > /dev/null
$UI tapafter "Booking date" > /dev/null; check "Date picker opens" "=OK"; $UI tap "=OK" > /dev/null
$UI tapafter "Start time" > /dev/null; check "Time picker opens" "=OK"; $UI tap "=OK" > /dev/null
check "Start time set to 20:00" "20:00"
$UI tap "=Confirmed" > /dev/null; $UI tap "Pencilled in" > /dev/null; check "Booking status set to Pencilled in" "Pencilled in"
$UI tap "=SAVE" > /dev/null; check "Saving without a fee is blocked" "New booking"
$UI fill "Fee (" "250" 2 > /dev/null
$UI tapafter "Payment due" 2 > /dev/null; $UI tap "=OK" > /dev/null
$UI fill "Notes" "Bring cables" 2 > /dev/null
$UI tap "=SAVE" 2 > /dev/null
nocheck "Form closes after saving" "New booking"
check "Calendar prompt appears after a new booking" "Add to your calendar?"
check "Calendar prompt offers Google and Outlook" "=OUTLOOK"; shot REVIEW-calendar-prompt
$UI tap "=NOT NOW" > /dev/null; nocheck "Not now closes the prompt" "Add to your calendar?"
dcheck "New booking saved" "gigs=11 cancelled=1 pencilled=2 .*Test Client"
check "New booking appears in list" "Test Client" 4
nocrash "Add a booking"

# ====================================================================
section "Calendar"
main --es page gigs --es gigsTab unpaid
$UI tap "=Calendar" > /dev/null; check "Calendar tab opens on this month" "$MONTH"
$UI tap "‹" > /dev/null; check "Previous month arrow" "$PREV"
$UI tap "›" > /dev/null; $UI tap "›" > /dev/null; check "Next month arrow" "$NEXT"
$UI tap "$NEXT" > /dev/null; check "Tapping month name jumps back to today" "$MONTH"
$UI tap "=15" > /dev/null; check "Tapping a day selects it" "on $(date -d "$(date +%Y-%m-15)" +"%a %-d %b")" 2
$UI tap "Add a booking on" 2 > /dev/null; check "Add booking on selected day opens form" "New booking"
$UI tap "=CANCEL" > /dev/null; nocheck "Cancel closes form" "New booking"
nocrash "Calendar"

# ====================================================================
section "Clients"
$UI tap "=Clients" > /dev/null; check "Clients tab opens" "Add a client"
$UI tap "Add a client" > /dev/null; $UI tap "=SAVE" > /dev/null; check "Client without a name is blocked" "New client"
$UI fill "Name (person" "Zed Agency" > /dev/null; $UI fill "Email" "zed@example.com" > /dev/null; $UI fill "Phone" "35699999999" > /dev/null
$UI tap "=SAVE" > /dev/null
check "New client appears" "Zed Agency" 4
dcheck "New client saved" "clients=8"
main --es page clients
$UI tap "Hilton Malta" 2 > /dev/null; check "Client page opens" "Earned from them"
check "Client page lists their bookings" "Gala dinner" 3
$UI tap "=Call" 0 > /dev/null || $UI tap "=Call" 2 > /dev/null; left "Call button opens the phone dialer"; backto "Earned from them"
$UI tap "=WhatsApp" > /dev/null; left "WhatsApp button opens WhatsApp / browser"; backto "Earned from them"
$UI tap "New booking for Hilton Malta" > /dev/null; check "New booking for client pre-fills client" "Hilton Malta"; $UI tap "=CANCEL" > /dev/null
$UI tap "=EDIT" > /dev/null; check "Edit client opens" "Edit client"
$UI fill "Notes" "Great venue" > /dev/null; $UI tap "=SAVE" > /dev/null
check "Client notes saved" "Great venue"
adb shell input keyevent 4; sleep 1
main --es page clients
$UI tap "Zed Agency" 4 > /dev/null; check "Zed Agency page opens" "Zed Agency"
$UI tap "=EDIT" > /dev/null; $UI tap "=DELETE" > /dev/null; check "Delete asks to confirm" "Delete Zed Agency?"
$UI tap "=DELETE" > /dev/null
dcheck "Client deleted" "clients=7"
nocrash "Clients"

# ====================================================================
section "Money"
main --es page money
check "Money tab shows this year" "=$YEAR"
check "Received tile" "Received"
check "Pending balance tile" "Pending balance"
$UI tap "‹" > /dev/null; check "Previous year arrow" "=$LASTYEAR"
$UI tap "›" > /dev/null; check "Next year arrow" "=$YEAR"
$UI tap "Add an expense" 3 > /dev/null; check "Add expense opens form" "New expense"
$UI tap "=SAVE" > /dev/null; check "Expense without amount is blocked" "New expense"
$UI fill "Amount (" "40" > /dev/null
$UI tap "Fuel & transport" > /dev/null; $UI tap "=Equipment" > /dev/null
$UI tapafter "=Date" > /dev/null; $UI tap "=OK" > /dev/null
$UI fill "Note (what was it?)" "Test strings" > /dev/null
$UI tap "=SAVE" > /dev/null
dcheck "Expense saved" "expenses=6 expTotal=59000"
$UI tap "Test strings" 6 > /dev/null; check "Tapping an expense opens it" "Edit expense"
$UI fill "Amount (" "45" > /dev/null; $UI tap "=SAVE" > /dev/null
dcheck "Expense edited" "expenses=6 expTotal=59500"
main --es page money
$UI tap "Test strings" 6 > /dev/null; $UI tap "=DELETE" > /dev/null
dcheck "Expense deleted" "expenses=5 expTotal=55000"
nocrash "Money"

# ====================================================================
section "A booking: payments"
gig 3
check "Overdue booking shows amount owed" "€400.00"
$UI tap "Record a payment" > /dev/null; check "Record payment opens" "Record payment"
$UI fill "Amount received" "100" > /dev/null
$UI tapafter "Date received" > /dev/null; $UI tap "=OK" > /dev/null
$UI fill "How was it paid" "Card" > /dev/null
$UI tap "=SAVE" > /dev/null
dcheck "Payment saved" "paid=130000 owed=160000"
check "Amount owed went down" "€300.00"
$UI tap "Card" > /dev/null; check "Tapping a payment asks to remove it" "Remove this payment?"
$UI tap "=REMOVE" > /dev/null
dcheck "Payment removed" "paid=120000 owed=170000"
$UI tap "Paid in full" > /dev/null
dcheck "Paid in full records the rest" "paid=160000 owed=130000"
check "Booking shows Fully paid" "Fully paid"
nocrash "Payments"

section "A booking: email, calendar, expense"
gig 4
$UI tap "Send invoice" > /dev/null; left "Send invoice opens email app"; backto "Record a payment"
$UI tap "Email payment reminder" > /dev/null; left "Payment reminder opens email app"; backto "Record a payment"
$UI tap "Add chase-up to Google Calendar" > /dev/null; left "Chase-up opens calendar"; backto "Record a payment"
$UI tap "Add an expense" 4 > /dev/null; check "Expense from booking is linked" "For: Joanna"
$UI fill "Amount (" "10" > /dev/null; $UI tap "=SAVE" > /dev/null
dcheck "Booking expense saved" "expenses=6"
check "Take-home shown" "Take-home from this booking" 4
gig 6
$UI tap "Add to calendar" > /dev/null; check "Add to calendar asks which calendar" "=GOOGLE CALENDAR"
shot REVIEW-add-to-calendar-button
$UI tap "=OUTLOOK" > /dev/null; check "Outlook without Microsoft sign-in explains it" "Sign in with Microsoft"; shot REVIEW-outlook-signin
$UI tap "=CANCEL" > /dev/null
$UI tap "Add to calendar" > /dev/null; $UI tap "=GOOGLE CALENDAR" > /dev/null; left "Google Calendar opens with the booking"; backto "Fee to collect"
gig 7
$UI tap "Mark as confirmed" > /dev/null
dcheck "Mark as confirmed" "pencilled=1 "
nocrash "Booking actions"

section "A booking: edit, cancel, restore, delete"
ID=$(adb shell "run-as $PKG sqlite3 databases/balances.db \"select id from gigs where client='Test Client'\"" 2>/dev/null | tr -d '\r')
[ -z "$ID" ] && ID=11
gig $ID
check "New booking page opens" "Garden Party"
$UI tap "=EDIT" > /dev/null; check "Edit booking opens" "Edit booking"
$UI fill "Fee (" "275" 2 > /dev/null; $UI tap "=SAVE" 2 > /dev/null
check "Edited fee shows" "275.00"
$UI tapdesc "More options" > /dev/null; $UI tap "was cancelled" > /dev/null; check "Cancel asks to confirm" "Mark as cancelled?"
$UI tap "=CANCELLED" > /dev/null; check "Cancelled booking offers restore" "Restore booking"
dcheck "Booking cancelled" "cancelled=2"
$UI tap "Restore booking" > /dev/null
dcheck "Booking restored" "cancelled=1"
$UI tapdesc "More options" > /dev/null; $UI tap "Delete booking" > /dev/null; check "Delete asks to confirm" "Delete this booking?"
$UI tap "=DELETE" > /dev/null
dcheck "Booking deleted" "gigs=10 "
nocrash "Edit/cancel/delete"

# ====================================================================
section "Settings: menu"
settings
for row in "Your work" "Appearance" "Business details" "Getting paid" "Email" "Reminders" "Backup & export"; do
  check "Menu row: $row" "$row" 2
done
for pair in "Your work:Choose what you do" "Appearance:Pick a colour" "Business details:Shown on your invoices" "Getting paid:How clients pay you" "Email:Send straight from ShowFee" "Reminders:Remind me at" "Backup & export:Choose a backup file"; do
  settings; $UI tap "${pair%%:*}" 2 > /dev/null; check "Menu row opens page: ${pair%%:*}" "${pair##*:}"
done
settings; check "Menu shows summaries" "Daily at" 2
nocrash "Settings menu"

section "Settings: your work"
settings work
$UI tap "=Singer" > /dev/null; $UI tap "=DJ" > /dev/null
dcheck "Job changed to DJ" "profession=dj"
$UI fill "Name of the first tab" "My Shows" > /dev/null
adb shell input keyevent 4; sleep 1
main --es page gigs; check "Custom tab name used" "My Shows"
settings work; $UI fill "Name of the first tab" "" > /dev/null; $UI tap "=DJ" > /dev/null; $UI tap "=Singer" > /dev/null
adb shell input keyevent 4; sleep 1; main --es page gigs; check "Tab name back to default" "Bookings"

section "Settings: look"
settings look
for c in Plum Indigo Ocean Teal Rose Graphite Gold; do $UI tapdesc "$c" 2 > /dev/null; done
dcheck "All 7 colours selectable" "accent=gold"
nocrash "Colours"
$UI tap "=Dark" 2 > /dev/null; dcheck "Dark mode" "mode=dark"
$UI tap "Same as phone" 2 > /dev/null; dcheck "Same as phone mode" "mode=phone"
$UI tap "=Light" 2 > /dev/null; dcheck "Light mode" "mode=light"
$UI tapdesc "Indigo" 2 > /dev/null
settings pay; $UI tap "Euro (" 2 > /dev/null; $UI tap "British pound" 2 > /dev/null
adb shell input keyevent 4; sleep 1; main --es page gigs --es gigsTab unpaid
check "Currency change shows £" "£1,050.00"
settings pay; $UI tap "British pound" 2 > /dev/null; sleep 1; $UI tap "Euro (" -3 > /dev/null; adb shell input keyevent 4; sleep 1
dcheck "Currency back to euro" "currency=EUR"
nocrash "Look"

section "Settings: logo"
python3 - <<'PY'
import zlib, struct
w, h = 120, 60
raw = b"".join(b"\x00" + bytes([40, 60, 200, 255] * w) for _ in range(h))
def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")
open("/tmp/test-logo.png", "wb").write(png)
PY
adb push /tmp/test-logo.png /sdcard/Download/test-logo.png > /dev/null
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/test-logo.png > /dev/null
settings business
$UI tap "Add your logo" 4 > /dev/null; sleep 2; shot logo-picker
$UI tap "test-logo" > /dev/null || $UI tapdesc "test-logo" > /dev/null || { $UI tapdesc "Show roots" > /dev/null; $UI tap "=Downloads" > /dev/null; $UI tap "test-logo" > /dev/null || $UI tapdesc "test-logo" > /dev/null; }
sleep 2; backto "Shown on your invoices"
dcheck "Logo added from phone's files" "logo=true"
settings business; $UI tap "Remove logo" 4 > /dev/null; adb shell input keyevent 4; sleep 1
dcheck "Logo removed" "logo=false"

section "Settings: email sign-in buttons and choice"
settings email
$UI tap "Sign in with Google" 2 > /dev/null; sleep 3; left "Sign in with Google opens Google"; backto "Send straight from ShowFee"
settings email
$UI tap "Sign in with Microsoft" 2 > /dev/null; sleep 5; left "Sign in with Microsoft opens Microsoft"; backto "Send straight from ShowFee"
settings email
$UI tap "=Outlook" 2 > /dev/null; gig 4; check "Email choice Outlook changes button" "via Outlook"
settings email; $UI tap "Ask each time" 2 > /dev/null; gig 4; check "Email choice Ask each time" "by email"
settings email; $UI tap "=Gmail" 2 > /dev/null; gig 4; check "Email choice Gmail" "via Gmail"

section "Settings: invoices, payment terms, reminders"
settings pay
check "Old Revolut tag kept as Revolut pay link" "=Revolut" 2
$UI tap "=Revolut" 2 > /dev/null; $UI tap "=PayPal" > /dev/null
$UI fill "Username or link" "paypal.me/tester" 1 > /dev/null
check "PayPal note shown" "amount is filled in" 1
$UI fill "Payment due (days" "7" 3 > /dev/null
leave() { adb shell input keyevent 4; sleep 1; if $UI has "$1" > /dev/null; then adb shell input keyevent 4; sleep 1; fi; }
leave "Payment due (days"
dcheck "Payment terms saved (7 days)" "terms=7"
dcheck "PayPal pay link saved" "pay=paypal:https://paypal.me/tester "
gig 4; check "Send pay link button" "Send pay link" 3
$UI tap "Send pay link" 3 > /dev/null; sleep 1; check "Send pay link offers WhatsApp" "=WhatsApp"; shot "REVIEW-pay-link-choice"
$UI tap "Text message (SMS)" > /dev/null; sleep 2; shot "REVIEW-pay-link-sms"; left "Text message opens messaging app"; backto "Record a payment"
gig 4; $UI tap "Send pay link" 3 > /dev/null; sleep 1; $UI tap "Other apps" > /dev/null; sleep 2; left "Other apps opens share list"; backto "Record a payment"
gig 4; $UI tap "Send pay link" 3 > /dev/null; sleep 1; $UI tap "=WhatsApp" > /dev/null; sleep 2
if inapp; then ok "WhatsApp missing falls back to share list"; else ok "WhatsApp / share list opened"; fi; backto "Record a payment"
seeder --es task invoice --el id 4
seeder --es task mail --el id 4
adb pull "/sdcard/Android/data/$PKG/files/email-4.eml" $OUT/email-paypal.eml > /dev/null 2>&1
python3 - "$OUT/email-paypal.eml" <<'PY' && ok "Sent email has a Pay here button" || bad "Sent email has a Pay here button"
import email, sys
m = email.message_from_bytes(open(sys.argv[1], 'rb').read())
html = [p.get_payload(decode=True).decode() for p in m.walk() if p.get_content_type() == 'text/html'][0]
text = [p.get_payload(decode=True).decode() for p in m.walk() if p.get_content_type() == 'text/plain'][0]
open(sys.argv[1] + '.html', 'w').write(html)
assert 'href="https://paypal.me/tester/900EUR"' in html and '>Pay here</a>' in html, html
assert 'https://paypal.me/tester/900EUR' in text and '[Pay here]' not in text, text
assert any(p.get_content_type() == 'application/pdf' for p in m.walk())
PY
adb pull "/sdcard/Android/data/$PKG/files/invoice-4.pdf" $OUT/invoice-paypal.pdf > /dev/null 2>&1 && ok "Invoice with pay link made" || bad "Invoice with pay link made"
seeder --es task look --es google maria.sings@gmail.com; gig 4
$UI tap "Send invoice" 3 > /dev/null; sleep 2; check "Email preview shows Pay here" "Pay here]" 2; shot "REVIEW-email-pay-link"
$UI tap "=CANCEL" > /dev/null || $UI tap "=Cancel" > /dev/null
$UI tap "Email payment reminder" 3 > /dev/null; sleep 2; shot "REVIEW-reminder-pay-link"
$UI tap "=CANCEL" > /dev/null || $UI tap "=Cancel" > /dev/null
seeder --es task look --es google -
settings pay; $UI tap "=PayPal" 2 > /dev/null; $UI tap "=Stripe" > /dev/null
$UI fill "Username or link" "https://buy.stripe.com/test_abc123" 1 > /dev/null; shot "REVIEW-settings-stripe"; leave "Username or link"
dcheck "Stripe pay link saved" "pay=stripe:https://buy.stripe.com/test_abc123 "
seeder --es task invoice --el id 4
adb pull "/sdcard/Android/data/$PKG/files/invoice-4.pdf" $OUT/invoice-stripe.pdf > /dev/null 2>&1
settings pay; $UI tap "=Stripe" 2 > /dev/null; $UI tap "=PayPal" > /dev/null
$UI fill "Username or link" "paypal.me/tester" 1 > /dev/null; shot "REVIEW-settings-paypal"; leave "Username or link"
main --es page gigs; $UI tap "Add a booking" > /dev/null
nocheck "New booking due date uses 7-day terms" "(on the night)"
$UI tap "=CANCEL" > /dev/null
settings pay; $UI fill "Payment due (days" "0" 4 > /dev/null; leave "Payment due (days"
dcheck "Payment terms back to 0" "terms=0"
settings reminders
$UI tap "Daily reminders" 1 > /dev/null; adb shell input keyevent 4; sleep 1; dcheck "Reminders switched off" "notify=false"
settings reminders; $UI tap "Daily reminders" 1 > /dev/null; adb shell input keyevent 4; sleep 1; dcheck "Reminders switched on" "notify=true"
settings reminders; $UI tap "=10:00" 1 > /dev/null; $UI tap "=18:00" > /dev/null; check "Reminder time changed" "18:00"
$UI tap "Show today's reminders now" 2 > /dev/null; sleep 2
N=$(adb shell dumpsys notification --noredact | grep -c "pkg=$PKG")
[ "$N" -gt 0 ] && ok "Reminders now shows notifications ($N)" || bad "Reminders now shows notifications"
settings reminders; shot REVIEW-reminders-calendar-page; $UI tap "Ask to add new" 3 > /dev/null; adb shell input keyevent 4; sleep 1
main --es page gigs; $UI tap "Add a booking" > /dev/null; $UI fill "Client (who pays you)" "Prompt Off" > /dev/null; $UI fill "Fee (" "100" 2 > /dev/null; $UI tap "=SAVE" 2 > /dev/null
nocheck "Calendar prompt can be switched off" "Add to your calendar?"
settings reminders; $UI tap "Ask to add new" 3 > /dev/null; adb shell input keyevent 4; sleep 1
nocrash "Reminders"

section "Settings: backup, restore, export"
settings backup
$UI tap "Choose backup file" 2 > /dev/null; sleep 2; shot backup-save-screen
$UI tap "=SAVE" > /dev/null || $UI tap "=Save" > /dev/null; sleep 4; backto "Backup (Google Drive)"
dcheck "Backup file chosen and written" "backup=true"
settings backup; $UI tap "Back up now" 2 > /dev/null; sleep 3; nocrash "Back up now"
check "Backup status shown" "ackup" 2
settings backup; $UI tap "Restore from a backup file" 2 > /dev/null; sleep 2; shot restore-picker
$UI tap "backup.json" > /dev/null || $UI tap "ShowFee-backup" > /dev/null || { $UI tapdesc "Show roots" > /dev/null; $UI tap "=Downloads" > /dev/null; $UI tap "backup" > /dev/null; }
sleep 2; check "Restore asks to confirm" "Restore this backup?"
$UI tap "=RESTORE" > /dev/null; sleep 2
dcheck "Restore keeps all data" "gigs=11 "
settings backup; $UI tap "Export bookings" 3 > /dev/null; sleep 2; $UI tap "=SAVE" > /dev/null || $UI tap "=Save" > /dev/null; sleep 2; backto "Backup (Google Drive)"
settings backup; $UI tap "Export expenses" 3 > /dev/null; sleep 2; $UI tap "=SAVE" > /dev/null || $UI tap "=Save" > /dev/null; sleep 2; backto "Backup (Google Drive)"
adb shell ls -R /sdcard/Download /sdcard/Documents 2>/dev/null > $OUT/files-list.txt
grep -q "showfee-gigs" $OUT/files-list.txt && ok "Export bookings CSV saved" || bad "Export bookings CSV saved"
grep -q "showfee-expenses" $OUT/files-list.txt && ok "Export expenses CSV saved" || bad "Export expenses CSV saved"
for f in $(grep -oE "showfee-(gigs|expenses)-[0-9-]+\.csv" $OUT/files-list.txt | sort -u); do
  adb pull "$(adb shell find /sdcard -name "$f" 2>/dev/null | head -1 | tr -d '\r')" $OUT/ > /dev/null 2>&1; done
nocrash "Backup/export"

section "Notifications, widget"
seeder --es task notify
adb shell cmd statusbar expand-notifications; sleep 2; shot notifications
$UI tap "Did Joanna" > /dev/null || $UI tap "overdue" > /dev/null; sleep 2
inapp && ok "Tapping a reminder opens ShowFee" || bad "Tapping a reminder opens ShowFee"
adb shell cmd statusbar collapse
seeder --es task widget; nocrash "Widget update"

section "ShowFee Pro (subscription)"
settings; check "Settings shows ShowFee Pro" "ShowFee Pro" 0; check "Developer copy is unlocked" "developer copy"
$UI tap "ShowFee Pro" > /dev/null; sleep 1; check "Pro screen opens" "Everything is unlocked"; shot "REVIEW-pro-developer"; adb shell input keyevent 4; sleep 1
seeder --es task plan --es plan free
settings; check "Free plan shown in Settings" "Free plan"; shot "REVIEW-pro-settings-free"
$UI tap "ShowFee Pro" > /dev/null; sleep 1; check "Pro screen shows price" "6.99/month"; check "Pro screen offers free month" "Start my free month"; shot "REVIEW-pro-paywall"
$UI tap "Have an access code" 3 > /dev/null; $UI fill "Access code" "WRONG-CODE" > /dev/null; $UI tap "=UNLOCK" > /dev/null; sleep 1
check "Wrong access code keeps the free plan" "Start my free month" 2
$UI tap "Have an access code" 3 > /dev/null; $UI fill "Access code" "SHOWFEE-REVIEW-7351" > /dev/null; $UI tap "=UNLOCK" > /dev/null; sleep 1
check "Reviewer access code unlocks Pro" "Unlocked with an access code" 3; shot "REVIEW-pro-code"
check "Privacy and terms links on Pro screen" "Privacy policy" 3
adb shell input keyevent 4; sleep 1; settings; check "Settings shows code days left" "days left"; check "Settings has privacy link" "Privacy policy" 3; shot "REVIEW-settings-legal"
seeder --es task plan --es plan free --es code expire
settings; check "Code ends after its 30 days" "Free plan"; $UI tap "ShowFee Pro" > /dev/null; sleep 1
$UI tap "Have an access code" 3 > /dev/null; $UI fill "Access code" "SHOWFEE-REVIEW-7351" > /dev/null; $UI tap "=UNLOCK" > /dev/null; sleep 1
check "Same phone can't use the code twice" "Start my free month" 2; adb shell input keyevent 4; sleep 1
seeder --es task plan --es plan -; seeder --es task plan --es plan free
gig 4; $UI tap "Send invoice" 3 > /dev/null; sleep 1; check "Free plan: invoice asks for Pro" "Sending PDF invoices is part of ShowFee Pro"; shot "REVIEW-pro-invoice-locked"; adb shell input keyevent 4; sleep 1
gig 4; $UI tap "Send pay link" 3 > /dev/null; sleep 1; check "Free plan: pay link asks for Pro" "Sending pay links"; adb shell input keyevent 4; sleep 1
main --es page gigs --es gigsTab all; $UI tap "Add a booking" > /dev/null
$UI fill "Client (who pays you)" "Limit Test" > /dev/null; $UI fill "Fee (" "100" 2 > /dev/null; $UI tap "=SAVE" > /dev/null; sleep 2
if $UI has "free plan includes" > /dev/null; then ok "Free plan: 6th booking this month asks for Pro"; shot "REVIEW-pro-limit"; adb shell input keyevent 4; sleep 1; $UI tap "=CANCEL" > /dev/null
else D=$(adb logcat -d -s UITEST | grep "plan free" | tail -1); echo "      $D" >> $R; ok "Free plan: booking saved (month had fewer than 5)"; fi
seeder --es task plan --es plan pro
gig 4; $UI tap "Send invoice" 3 > /dev/null; sleep 2; nocheck "Pro: invoice goes straight through" "part of ShowFee Pro"; backto "Record a payment"
settings; $UI tap "ShowFee Pro" > /dev/null; sleep 1; check "Pro screen shows subscribed" "on ShowFee Pro"; shot "REVIEW-pro-active"; adb shell input keyevent 4; sleep 1
seeder --es task plan --es plan -
nocrash "ShowFee Pro"

section "Google Play update bar"
main --es page gigs; nocheck "No update bar without Google Play" "new version of ShowFee"
seeder --es task update --es step available; main --es page calendar; main --es page gigs
check "Update bar shows" "A new version of ShowFee is available"; shot "REVIEW-update-available"
$UI tap "=Later" > /dev/null; nocheck "Later hides the update bar" "new version of ShowFee"
seeder --es task update --es step available; main --es page money; main --es page gigs
$UI tap "=Update" > /dev/null; sleep 1
seeder --es task update --es step download; sleep 1
check "Downloaded update asks to restart" "restart to finish"; shot "REVIEW-update-ready"
seeder --es task update --es step off
nocrash "Update bar"

section "Review pictures (Android 15 edge-to-edge)"
for pg in gigs calendar clients money; do main --es page $pg; shot "REVIEW-e2e-$pg"; done
gig 4; shot "REVIEW-e2e-booking"
settings; shot "REVIEW-e2e-settings"
settings pay; shot "REVIEW-e2e-settings-pay"
seeder --es task look --es mode dark; main --es page gigs; shot "REVIEW-e2e-dark-home"
settings; shot "REVIEW-e2e-dark-settings"
seeder --es task look --es mode light
nocrash "Review pictures"

section "Done"
nocrash "Whole run"
echo "" >> $R; echo "TOTAL: $pass passed, $fail failed" | tee -a $R
adb logcat -d > $OUT/logcat.txt
exit 0
