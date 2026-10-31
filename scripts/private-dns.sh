#!/bin/sh
# Blocks ads and trackers in every app with Private DNS (DNS over TLS).
# Default: AdGuard DNS. Note: YouTube ads come from the same servers as the
# videos, so DNS can't remove those.
#
# Usage: scripts/private-dns.sh [hostname]   e.g. dns.adguard-dns.com (default)
#        scripts/private-dns.sh off
set -eu

TV=${TV:-$("$(dirname "$0")/tv-connect.sh")}
. "$(dirname "$0")/changelog.sh"
require_backup

hostname=${1:-dns.adguard-dns.com}
if [ "$hostname" = "off" ]; then
    put_setting global private_dns_mode opportunistic
    exit 0
fi
put_setting global private_dns_specifier "$hostname"
put_setting global private_dns_mode hostname
echo "Private DNS set to $hostname. Undo with scripts/restore.sh"
