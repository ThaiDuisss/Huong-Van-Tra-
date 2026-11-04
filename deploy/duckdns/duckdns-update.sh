#!/usr/bin/env bash
# Cap nhat IP public cua box 1 len DuckDNS.
#
# Box 1 dung IP tu cap (khong phai Elastic IP) nen IP doi sau moi lan stop/start.
# Script nay chay dinh ky de ban ghi A cua DuckDNS luon tro dung may.
#
# Doc cau hinh tu /etc/duckdns.env (chi root doc duoc):
#   DUCKDNS_DOMAIN=hvtra-pos      # chi phan subdomain, KHONG kem .duckdns.org
#   DUCKDNS_TOKEN=xxxxxxxx
#
# De trong tham so ip= thi DuckDNS lay IP NGUON cua request, tuc dung bang IP public
# cua may nay - khong can tu do IP, va khong bi sai khi di qua NAT.
#
# Token duoc dua vao curl qua stdin (-K -) chu khong qua tham so dong lenh, de no
# khong hien trong `ps`.

set -euo pipefail

CONF=/etc/duckdns.env
[ -r "$CONF" ] || { echo "Khong doc duoc $CONF" >&2; exit 1; }
# shellcheck source=/dev/null
. "$CONF"

: "${DUCKDNS_DOMAIN:?thieu DUCKDNS_DOMAIN trong $CONF}"
: "${DUCKDNS_TOKEN:?thieu DUCKDNS_TOKEN trong $CONF}"

resp=$(printf 'url = "https://www.duckdns.org/update?domains=%s&token=%s&ip="\n' \
         "$DUCKDNS_DOMAIN" "$DUCKDNS_TOKEN" \
       | curl -fsS --max-time 20 -K -)

if [ "$resp" = "OK" ]; then
    echo "DuckDNS ${DUCKDNS_DOMAIN}.duckdns.org: OK"
else
    # DuckDNS tra ve dung chu "KO" khi sai token hoac sai domain, khong kem ly do.
    echo "DuckDNS ${DUCKDNS_DOMAIN}.duckdns.org: THAT BAI (tra ve: '${resp}')" >&2
    exit 1
fi
