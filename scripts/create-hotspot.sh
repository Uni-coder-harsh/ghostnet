#!/usr/bin/env bash
# GhostNet Linux Hotspot Creator (NetworkManager / nmcli)
# Creates a self-contained offline Wi-Fi access point without Internet dependencies.

set -e

IFACE="${1:-wlan0}"
SSID="${2:-GhostNet}"
PASSWORD="${3:-ghostnet123}"

echo "=========================================================="
echo " GhostNet Offline Hotspot Configuration"
echo " Interface: $IFACE"
echo " SSID:      $SSID"
echo " Password:  $PASSWORD"
echo "=========================================================="

if ! command -v nmcli &> /dev/null; then
    echo "Error: NetworkManager (nmcli) is not installed."
    echo "Refer to docs/networking/HOTSPOT_GUIDE.md for hostapd instructions."
    exit 1
fi

echo "Creating and activating Wi-Fi hotspot on interface $IFACE..."
nmcli device wifi hotspot ifname "$IFACE" ssid "$SSID" password "$PASSWORD"

echo ""
echo "Hotspot active! Connect other devices to '$SSID'."
echo "Clients can access GhostNet once the server is started."
