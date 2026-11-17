# CONTRACTS (ShowFee Business): sourced by full.sh and quick.sh, after the sample data is loaded.
# Uses the real Firebase project with a throwaway anonymous account (contracts made by it start with "test-").
section "Contracts (ShowFee Business)"
bklog() { adb logcat -c; seeder --es task "$2" "${@:4}"; sleep 5; L=$(adb logcat -d -s UITEST | grep -E "$2" | tail -1)
          echo "      $L" | cut -c1-700 >> $R; if echo "$L" | grep -qE "$3"; then ok "$1"; else bad "$1 (log should match: $3)"; fi; }
K=7   # sample booking: Café del Mar · Acoustic night, pencilled in, 9 days ahead
ktap() { $UI tap "$1" "${2:-0}" > /dev/null || $UI tap "$(echo "$1" | tr '[:lower:]' '[:upper:]')" "${2:-0}" > /dev/null; }

seeder --es task plan --es plan free
gig $K; check "Free plan: contracts offered with Business" "send a contract that you and your client both sign" 3
check "Free plan: button to see Business" "See ShowFee Business" 3; shot "REVIEW-k-free"

seeder --es task plan --es plan business
seeder --es task bkanon; sleep 3
gig $K; check "Business: no contract yet" "No contract yet" 3; check "Business: create button" "=Create contract" 3; shot "REVIEW-k-booking"
$UI tap "=Create contract" 4 > /dev/null; sleep 2
check "Contract screen opens" "Venue (optional)"
check "Wording: your name filled in" "between Maria Borg"
check "Wording: client filled in" "del Mar (\"the client\")"
check "Wording: fee filled in" "Fee: €200.00"
check "Wording: start time filled in" "starting at 20:00"
$UI fill "Venue (optional)" "Hotel Phoenicia" > /dev/null; sleep 1
check "Venue goes into the wording" "at Hotel Phoenicia"
$UI fill "Deposit to confirm" "50" > /dev/null; sleep 1
check "Deposit goes into the wording" "A deposit of €50.00 is due"
shot "REVIEW-k-editor"
$UI tap "Sign & send" 6 > /dev/null; sleep 2
check "Not sent without your signature" "Your signature" 
$UI scribble "Signature box" 3 > /dev/null; shot "REVIEW-k-signed-by-you"
$UI tap "Sign & send" 3 > /dev/null; sleep 8
left "Link goes out through an app (email, WhatsApp…)"; backto "Contract"
gig $K; check "Booking: waiting for the client" "Waiting for Caf" 3; check "Booking: send the link again" "Send the link again" 3
shot "REVIEW-k-waiting"
bklog "Contract is online, waiting" bkkdoc '"status":\{"stringValue":"sent"\}' --el id $K
bklog "Online contract has the venue and deposit" bkkdoc 'Hotel Phoenicia.*€50.00|€50.00.*Hotel Phoenicia' --el id $K
bklog "Your signature saved with it" bkkstate 'v1=sent,psig=true.*mysig=true' --el id $K
bklog "Nobody else can change the wording" bkkhack 'bkkhack refused 403' --el id $K
bklog "Client signs (like the signing page)" bkksign 'bkksign ok' --el id $K
bklog "It can't be signed twice" bkksign 'bkksign refused 403' --el id $K
gig $K; sleep 8
check "App sees the signature" "Contract signed"; shot "REVIEW-k-signed-news"
ktap "=Later"; sleep 2
check "Booking: signed by the client" "Signed by Paul Grech" 3; check "Booking: email signed copy" "Email the signed copy" 3
shot "REVIEW-k-signed"
bklog "Client's signature saved, booking confirmed" bkkstate 'v1=signed\(Paul Grech,sig=true\).*booking=Confirmed' --el id $K
$UI tap "View signed contract" 3 > /dev/null; sleep 4; left "Signed PDF opens"; backto "Signed by"

gig $K; $UI tap "=New version" 4 > /dev/null; sleep 2
check "New version: your saved signature is ready" "Your saved signature"
$UI tap "Sign & send" 6 > /dev/null; sleep 8; left "Version 2 link goes out"; backto "Contract"
gig $K; check "Version 2 waiting" "Version 2" 3
bklog "Both versions kept" bkkstate 'n=2 v2=sent.* v1=signed' --el id $K
$UI tap "Cancel this contract" 5 > /dev/null; sleep 1; ktap "=Cancel contract"; sleep 5
gig $K; check "Cancelled contract shown" "Contract cancelled" 3; check "History shows version 1 signed" "Version 1 · signed by Paul Grech" 4
bklog "Cancelled online too" bkkdoc '"status":\{"stringValue":"void"\}' --el id $K
bklog "A cancelled contract can't be signed" bkksign 'bkksign refused 403' --el id $K
nocrash "Contracts"
seeder --es task plan --es plan -
