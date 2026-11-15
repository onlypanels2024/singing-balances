# Booking link and booking requests – used by full.sh and, on its own, by quick.sh
section "Booking link and booking requests (real Firebase, throwaway test account)"
bk() { adb shell am start -W --activity-clear-top -n $PKG/$NS.BookingLinkActivity > /dev/null; sleep 2.5; }
reqs() { adb shell am start -W --activity-clear-top -n $PKG/$NS.RequestsActivity "$@" > /dev/null; sleep 4; }
# bklog "what" task "pattern" [extra seeder args]: runs a booking test task and checks its log line
bklog() { adb logcat -c; seeder --es task "$2" "${@:4}"; sleep 5; L=$(adb logcat -d -s UITEST | grep -E "$2|bkanon|bksend|bkpage|bkstate" | tail -1)
          echo "      $L" | cut -c1-700 >> $R; if echo "$L" | grep -qE "$3"; then ok "$1"; else bad "$1 (log should match: $3)"; fi; }
SLUG="test-ci-$(date +%s)"
seeder --es task plan --es plan free
settings; check "Settings has Your booking link" "Your booking link"; check "Row explains the link" "Clients book you from WhatsApp"; shot "REVIEW-bk-settings-row"
$UI tap "Your booking link" > /dev/null; sleep 2; check "Free plan: booking link asks for Pro" "Start my free month"; adb shell input keyevent 4; sleep 1
seeder --es task plan --es plan -
settings; $UI tap "Your booking link" > /dev/null; sleep 2
check "Booking link intro" "Let clients book you with one link"; check "Clients need no account (said on screen)" "don't need an account"
check "Owner signs in with Google" "Sign in with Google"; shot "REVIEW-bk-intro"
$UI tap "Sign in with Google" > /dev/null; sleep 4; shot "REVIEW-bk-google"; nocrash "Sign in with Google button (the test phone has no Google account)"; backto "Let clients book you"
nocrash "Booking link intro"
bklog "Test account signs in" bkanon "bkanon signed in"
bk; check "Signed in: choose your link" "Choose your link"; check "Name filled in" "Maria Borg"
$UI fill "Your link:" "garyfalzon" > /dev/null; $UI fill "Line under your name" "Singer in Malta" > /dev/null
$UI tap "Create my link" 2 > /dev/null; sleep 3; check "Wrong link name is refused (stays on Choose your link)" "Choose your link"
bklog "Test account can't take a real link name" bkstate "slug= accepting"
$UI fill "Your link:" "$SLUG" > /dev/null; shot "REVIEW-bk-choose"
$UI tap "Create my link" 2 > /dev/null; sleep 6
check "Link created" "showfee.app/book/$SLUG"; check "Taking bookings switch" "Taking bookings"; check "Default questions Event" "=Event" 2; shot "REVIEW-bk-settings"
main --es page gigs; sleep 6
bklog "Phone signed up for request notifications" bkstate "push=yes"
bk
bklog "Page saved online with name, line and questions" bkpage "Maria Borg.*Singer in Malta|Singer in Malta.*Maria Borg"
bklog "Page has Event and Venue questions" bkpage "Event.*Venue"
$UI tapdesc "Monday, you work" 2 > /dev/null; sleep 3
bklog "Monday turned off" bkstate "days=\[2, 3, 4, 5, 6, 7\]"
bklog "Monday off on the page too" bkpage '"days":\{"arrayValue":\{"values":\[\{"integerValue":"2"\}'
bk; $UI tap "Add a question" 3 > /dev/null; $UI fill "Question shown to clients" "Number of guests" > /dev/null; $UI tap "=SAVE" > /dev/null || $UI tap "=Save" > /dev/null; sleep 3
check "Question added" "Number of guests" 3
bk; $UI tapdesc "Remove Venue" 3 > /dev/null; $UI tap "=REMOVE" > /dev/null || $UI tap "=Remove" > /dev/null; sleep 3
nocheck "Venue removed" "=Venue"
bk; $UI tap "=Event" 3 > /dev/null; $UI fill "Question shown to clients" "Event type" > /dev/null; $UI tap "=SAVE" > /dev/null || $UI tap "=Save" > /dev/null; sleep 3
check "Question renamed" "Event type" 3; shot "REVIEW-bk-questions"
bklog "Questions saved on the page" bkpage "Event type.*Number of guests"
# ---- ShowFee Business: plans screen and branding ----
seeder --es task plan --es plan free
settings; $UI tap "ShowFee Pro" > /dev/null; sleep 2
check "Plans screen: choose your plan" "Choose your plan"; check "Plans screen: Pro price" "6.99/month"; check "Plans screen: Business plan" "ShowFee Business" 2
check "Plans screen: Business price" "9.99/month" 2; nocheck "Booking link no longer 'coming soon'" "coming soon"; shot "REVIEW-biz-plans"
$UI tap "ShowFee Business" 2 > /dev/null; sleep 1; check "Choosing Business shows its price" "1 month free, then €9.99/month" 3; shot "REVIEW-biz-plans-business"
adb shell input keyevent 4; sleep 1
seeder --es task plan --es plan pro
bk; check "Pro: branding offers Business" "See ShowFee Business" 2; shot "REVIEW-biz-pro-upsell"
seeder --es task plan --es plan business
seeder --es task logo
bk; check "Business: branding section" "Brand colour" 2; check "Business: own web address" "Use my own web address" 4
$UI tapdesc "Show my logo" 2 > /dev/null; sleep 3
$UI tapdesc "Green" 3 > /dev/null; sleep 3
bk; $UI tap "=Light" 3 > /dev/null; sleep 3
bk; $UI tapdesc "Show Powered by ShowFee" 4 > /dev/null; sleep 4
bk; check "Powered by switched off" "only your brand shows" 4; shot "REVIEW-biz-branding"
bklog "Branding saved on the page" bkpage '"color":\{"stringValue":"#0f6b5c"\}'
bklog "Light page saved" bkpage '"theme":\{"stringValue":"light"\}'
bklog "Powered by off saved" bkpage '"powered":\{"booleanValue":false\}'
bklog "Logo saved on the page" bkpage 'data:image/png;base64'
bk; $UI tap "Use my own web address" 4 > /dev/null; sleep 1; $UI fill "The web address" "book.mariaborg.com" > /dev/null
$UI tap "=SEND REQUEST" > /dev/null || $UI tap "=Send request" > /dev/null; sleep 3; left "Own web address opens an email"; backto "Your booking link"
seeder --es task plan --es plan pro
main --es page gigs; sleep 8
bklog "Branding removed when Business ends" bkpage '"accepting"'
if echo "$L" | grep -q '"brand"'; then bad "Page still has branding after Business ended"; else ok "Page has no branding after Business ended"; fi
seeder --es task plan --es plan -
bk; $UI tapdesc "Taking bookings" > /dev/null; sleep 4; check "Pausing shows on screen" "Paused"
bklog "Paused page refuses requests" bksend "bksend refused 403" --es name "Nobody" --es contact "x@y.z"
bk; $UI tapdesc "Taking bookings" > /dev/null; sleep 4; check "Taking bookings again" "Clients can send you requests"
bklog "Client request 1 accepted by Firebase" bksend "bksend sent" --es name "'Paul Grech'" --es contact "'+356 7912 3456'" --es answers "'Birthday party|40'" --es message "'Can you sing for 2 hours?'" --ei days 47
bklog "Client request 2 accepted by Firebase" bksend "bksend sent" --es name "'Claire Zammit'" --es contact "claire@example.com" --es answers "'Christmas dinner|'" --ei days 13
BUSYDAY=$(date -d "+47 days" +%F)
bklog "Page reachable before accepting" bkpage "Maria Borg"; if echo "$L" | grep -q "$BUSYDAY"; then bad "Test date $BUSYDAY should start free"; else ok "Test date $BUSYDAY starts free"; fi
sleep 20
NOTIF=$(adb shell dumpsys notification --noredact | grep -c "New booking request")
if [ "$NOTIF" -gt 0 ]; then ok "Notification arrived for the new requests"; else bad "Notification arrived for the new requests (none found)"; fi
adb shell cmd statusbar expand-notifications; sleep 2; shot "REVIEW-bk-notification"; adb shell cmd statusbar collapse
main --es page gigs; sleep 6; main --es page gigs
check "Home shows the new requests card" "2 new booking requests"; shot "REVIEW-bk-home-card"
$UI tap "new booking requests" > /dev/null; sleep 4
check "Requests list: Paul" "Paul Grech"; check "Requests list: Claire" "Claire Zammit"; check "Requests list: answers summary" "Birthday party"; shot "REVIEW-bk-requests"
$UI tap "Paul Grech" > /dev/null; sleep 2
check "Request shows question" "Number of guests"; check "Request shows answer" "=40"; check "Request shows message" "Can you sing for 2 hours?"
check "Phone request: reply on WhatsApp" "Reply on WhatsApp"; shot "REVIEW-bk-request"
$UI tap "Reply on WhatsApp" 2 > /dev/null; sleep 3; left "Reply opens WhatsApp / browser"; backto "Paul Grech"
$UI tap "=Accept" 2 > /dev/null; sleep 2
check "Accept opens the booking form" "Client (who pays you)"; check "Client name filled in" "Paul Grech"
$UI fill "Fee (" "200" 2 > /dev/null; shot "REVIEW-bk-accept-form"
$UI tap "=SAVE" > /dev/null; sleep 4
if $UI has "Add to your calendar?" > /dev/null; then $UI tap "=NOT NOW" > /dev/null; sleep 3; fi
check "Request marked accepted" "You accepted this request" 0
dcheck "Accepted request saved as a booking" "Paul Grech"
sleep 3
bklog "Accepted date now busy on the page" bkpage "$BUSYDAY"
reqs; $UI tap "Claire Zammit" > /dev/null; sleep 2; check "Email request: reply by email" "Reply by email"
$UI tap "=Decline" 2 > /dev/null; $UI tap "=DECLINE" > /dev/null || $UI tap "=Decline" > /dev/null; sleep 4
check "Request marked declined" "You declined this request"
reqs; check "List shows Accepted" "=Accepted"; check "List shows Declined" "=Declined"; shot "REVIEW-bk-answered"
main --es page gigs; sleep 5; nocheck "No card when nothing is new" "new booking request"
seeder --es task bkoffline --es mode on; reqs; check "Offline: requests still shown" "Paul Grech"; nocrash "Requests offline"
seeder --es task bkoffline --es mode off
settings; check "Settings row shows the link" "showfee.app/book/$SLUG"
bk; $UI tap "Remove my booking link" 6 > /dev/null; $UI tap "=REMOVE" > /dev/null || $UI tap "=Remove" > /dev/null; sleep 6
check "Removed: back to the intro" "Let clients book you with one link"
bklog "Removed page is gone online" bkpage "bkpage missing 404" --es slug "$SLUG"
nocrash "Booking link and requests"

