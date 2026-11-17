# Plans screen: Pro €9.99 / Business €19.99 a month, or yearly (€107.99 / €215.99, save 10%). Needs "plan free".
section "Plans: monthly and yearly"
settings; $UI tap "ShowFee Pro" > /dev/null; sleep 2
check "Plans screen: choose your plan" "Choose your plan"; check "Plans screen: Pro price" "€9.99/month"; check "Plans screen: Business plan" "ShowFee Business" 2
check "Plans screen: Business price" "€19.99/month" 2; nocheck "Booking link no longer 'coming soon'" "coming soon"
check "Plans screen: monthly / yearly choice" "Monthly"; check "Plans screen: yearly saves 10%" "Yearly · save 10%"; shot "REVIEW-plans-monthly"
check "Monthly: free month offered" "Start my free month" 3; check "Monthly: renews monthly" "renews monthly" 3
$UI tap "ShowFee Business" 2 > /dev/null; sleep 1; check "Choosing Business shows its price" "1 month free, then €19.99/month" 3; shot "REVIEW-plans-business-monthly"
$UI tap "Yearly" -3 > /dev/null; sleep 1
check "Yearly: Business price" "€215.99/year" 3; check "Yearly: Pro price" "€107.99/year" 3
check "Yearly: Subscribe button" "Subscribe" 3; nocheck "Yearly: no free month promised" "Start my free month"; check "Yearly: renews every year" "renews every year" 3
shot "REVIEW-plans-business-yearly"
$UI tap "=ShowFee Pro" -3 > /dev/null; sleep 1; check "Yearly: choosing Pro keeps yearly" "€107.99/year" 3; shot "REVIEW-plans-pro-yearly"
$UI tap "=Monthly" -3 > /dev/null; sleep 1
check "Back to monthly" "1 month free, then €9.99/month" 3
adb shell input keyevent 4; sleep 1
