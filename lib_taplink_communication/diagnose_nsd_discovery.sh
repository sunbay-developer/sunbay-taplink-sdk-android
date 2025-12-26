#!/bin/bash

# NSD Service Discovery Diagnostic Tool
# This script helps diagnose NSD service discovery issues

echo "=== NSD Service Discovery Diagnostic Tool ==="
echo "Date: $(date)"
echo

# Check network connectivity
echo "1. Network Connectivity Check"
echo "================================"

# Check if we can ping multicast address
echo "Testing multicast connectivity (mDNS)..."
if ping -c 3 224.0.0.251 >/dev/null 2>&1; then
    echo "✓ Multicast connectivity OK"
else
    echo "✗ Multicast connectivity FAILED - this may prevent mDNS discovery"
fi

# Check network interfaces
echo
echo "Active network interfaces:"
ip addr show | grep -E "^[0-9]+:|inet " | grep -v "127.0.0.1"

echo
echo "2. mDNS Service Discovery Test"
echo "=============================="

# Test with avahi-browse if available
if command -v avahi-browse >/dev/null 2>&1; then
    echo "Testing mDNS discovery with avahi-browse..."
    timeout 10s avahi-browse -t _taplink._tcp
    echo
else
    echo "avahi-browse not available, skipping mDNS test"
fi

# Test with dns-sd if available (macOS)
if command -v dns-sd >/dev/null 2>&1; then
    echo "Testing mDNS discovery with dns-sd..."
    timeout 10s dns-sd -B _taplink._tcp
    echo
else
    echo "dns-sd not available, skipping mDNS test"
fi

echo "3. Port and Service Check"
echo "========================"

# Common Taplink ports to check
COMMON_PORTS=(8080 8081 8082 9090 9091 9092)

echo "Scanning for common Taplink service ports..."
for port in "${COMMON_PORTS[@]}"; do
    if nc -z -w3 localhost $port 2>/dev/null; then
        echo "✓ Port $port is open on localhost"
    fi
done

echo
echo "4. Android-specific Checks"
echo "=========================="

# Check if running on Android (via adb)
if command -v adb >/dev/null 2>&1; then
    echo "Checking Android device connectivity..."
    
    # Check if device is connected
    if adb devices | grep -q "device$"; then
        echo "✓ Android device connected"
        
        # Check network state
        echo "Network state:"
        adb shell dumpsys connectivity | grep -E "NetworkInfo|mNetworkInfo" | head -5
        
        # Check if NSD service is running
        echo
        echo "NSD service status:"
        adb shell dumpsys servicediscovery | head -10
        
    else
        echo "✗ No Android device connected via ADB"
    fi
else
    echo "ADB not available, skipping Android checks"
fi

echo
echo "5. Firewall and Security Check"
echo "============================="

# Check iptables rules (Linux)
if command -v iptables >/dev/null 2>&1 && [ "$EUID" -eq 0 ]; then
    echo "Checking iptables rules for multicast..."
    iptables -L | grep -i multicast || echo "No specific multicast rules found"
else
    echo "Cannot check iptables (not root or not available)"
fi

echo
echo "6. Recommendations"
echo "=================="
echo "If service discovery is failing, try:"
echo "1. Ensure both devices are on the same network/subnet"
echo "2. Check if corporate firewall blocks multicast (port 5353)"
echo "3. Verify router supports mDNS/Bonjour"
echo "4. Try increasing discovery timeout to 30+ seconds"
echo "5. Check Android app has INTERNET and ACCESS_NETWORK_STATE permissions"
echo "6. Consider adding CHANGE_WIFI_MULTICAST_STATE permission"
echo "7. Test on different network (mobile hotspot)"

echo
echo "=== Diagnostic Complete ==="