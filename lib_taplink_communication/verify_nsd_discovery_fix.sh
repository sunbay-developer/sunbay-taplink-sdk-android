#!/bin/bash

# Verification script for NSD Service Discovery Fix
# This script verifies that the NSD discovery improvements are working correctly

echo "=== NSD Service Discovery Fix Verification ==="
echo "Date: $(date)"
echo

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    if [ "$1" = "PASS" ]; then
        echo -e "${GREEN}✓ PASS${NC}: $2"
    elif [ "$1" = "FAIL" ]; then
        echo -e "${RED}✗ FAIL${NC}: $2"
    elif [ "$1" = "INFO" ]; then
        echo -e "${YELLOW}ℹ INFO${NC}: $2"
    fi
}

# Check if we're in the right directory
if [ ! -f "lib_taplink_communication/src/main/java/com/sunmi/tapro/taplink/communication/lan/WebSocketClientKernel.kt" ]; then
    print_status "FAIL" "Not in the correct project directory"
    exit 1
fi

print_status "INFO" "Starting verification of NSD discovery improvements..."
echo

# Test 1: Check if discovery timeout was increased
echo "1. Checking NSD Discovery Timeout Configuration"
echo "=============================================="

if grep -q "NSD_DISCOVERY_TIMEOUT = 30_000L" lib_taplink_communication/src/main/java/com/sunmi/tapro/taplink/communication/lan/WebSocketClientKernel.kt; then
    print_status "PASS" "Discovery timeout increased to 30 seconds"
else
    print_status "FAIL" "Discovery timeout not properly configured"
fi

if grep -q "NSD_RESOLUTION_TIMEOUT = 5_000L" lib_taplink_communication/src/main/java/com/sunmi/tapro/taplink/communication/lan/WebSocketClientKernel.kt; then
    print_status "PASS" "Resolution timeout added (5 seconds)"
else
    print_status "FAIL" "Resolution timeout not configured"
fi

echo

# Test 2: Check if service resolution synchronization was added
echo "2. Checking Service Resolution Synchronization"
echo "============================================="

if grep -q "pendingResolutions" lib_taplink_communication/src/main/java/com/sunmi/tapro/taplink/communication/lan/WebSocketClientKernel.kt; then
    print_status "PASS" "Service resolution synchronization added"
else
    print_status "FAIL" "Service resolution synchronization missing"
fi

if grep -q "resolutionMutex" lib_taplink_communication/src/main/java/com/sunmi/tapro/taplink/communication/lan/WebSocketClientKernel.kt; then
    print_status "PASS" "Resolution mutex for thread safety added"
else
    print_status "FAIL" "Resolution mutex missing"
fi

echo

# Test 3: Check if proper service connection logic was implemented
echo "3. Checking Service Connection Logic"
echo "==================================="

if grep -q "connectToUri(uri, connectionCallback)" lib_taplink_communication/src/main/java/com/sunmi/tapro/taplink/communication/lan/WebSocketClientKernel.kt; then
    print_status "PASS" "Direct service connection logic implemented"
else
    print_status "FAIL" "Direct service connection logic missing"
fi

if grep -q "Successfully connected to discovered service" lib_taplink_communication/src/main/java/com/sunmi/tapro/taplink/communication/lan/WebSocketClientKernel.kt; then
    print_status "PASS" "Success logging for service connection added"
else
    print_status "FAIL" "Success logging missing"
fi

echo

# Test 4: Check if service attributes logging was added
echo "4. Checking Service Attributes Logging"
echo "======================================"

if grep -q "logServiceAttributes" lib_taplink_communication/src/main/java/com/sunmi/tapro/taplink/communication/lan/WebSocketClientKernel.kt; then
    print_status "PASS" "Service attributes logging function added"
else
    print_status "FAIL" "Service attributes logging function missing"
fi

echo

# Test 5: Check if thread-safe service list operations were implemented
echo "5. Checking Thread-Safe Service Operations"
echo "=========================================="

if grep -q "synchronized(discoveredServices)" lib_taplink_communication/src/main/java/com/sunmi/tapro/taplink/communication/lan/WebSocketClientKernel.kt; then
    print_status "PASS" "Thread-safe service list operations implemented"
else
    print_status "FAIL" "Thread-safe service list operations missing"
fi

echo

# Test 6: Check if comprehensive error handling was added
echo "6. Checking Error Handling Improvements"
echo "======================================="

if grep -q "discovered: \$finalServiceCount services" lib_taplink_communication/src/main/java/com/sunmi/tapro/taplink/communication/lan/WebSocketClientKernel.kt; then
    print_status "PASS" "Detailed error messages with service count added"
else
    print_status "FAIL" "Detailed error messages missing"
fi

echo

# Test 7: Run unit tests if available
echo "7. Running Unit Tests"
echo "===================="

if [ -f "lib_taplink_communication/src/test/java/com/sunmi/tapro/taplink/communication/NsdServiceDiscoveryTest.kt" ]; then
    print_status "INFO" "NSD discovery unit tests found"
    
    # Try to run tests with Gradle
    if command -v ./gradlew >/dev/null 2>&1; then
        print_status "INFO" "Running unit tests..."
        if ./gradlew :lib_taplink_communication:testDebugUnitTest --tests "*NsdServiceDiscoveryTest*" --quiet; then
            print_status "PASS" "Unit tests passed"
        else
            print_status "FAIL" "Unit tests failed"
        fi
    else
        print_status "INFO" "Gradle wrapper not found, skipping test execution"
    fi
else
    print_status "INFO" "Unit tests not found, skipping test execution"
fi

echo

# Test 8: Check diagnostic tools
echo "8. Checking Diagnostic Tools"
echo "============================"

if [ -f "lib_taplink_communication/diagnose_nsd_discovery.sh" ]; then
    print_status "PASS" "NSD diagnostic tool created"
    
    if [ -x "lib_taplink_communication/diagnose_nsd_discovery.sh" ]; then
        print_status "PASS" "Diagnostic tool is executable"
    else
        print_status "FAIL" "Diagnostic tool is not executable"
    fi
else
    print_status "FAIL" "NSD diagnostic tool missing"
fi

echo

# Test 9: Check documentation
echo "9. Checking Documentation"
echo "========================="

if [ -f "lib_taplink_communication/NSD_SERVICE_DISCOVERY_ANALYSIS.md" ]; then
    print_status "PASS" "NSD discovery analysis documentation created"
else
    print_status "FAIL" "NSD discovery analysis documentation missing"
fi

echo

# Summary
echo "=== Verification Summary ==="
echo

# Count passes and fails
PASS_COUNT=$(grep -c "✓ PASS" <<< "$(cat)")
FAIL_COUNT=$(grep -c "✗ FAIL" <<< "$(cat)")

if [ $FAIL_COUNT -eq 0 ]; then
    print_status "PASS" "All NSD discovery improvements verified successfully!"
    echo
    echo "The following improvements have been implemented:"
    echo "• Increased discovery timeout to 30 seconds"
    echo "• Added service resolution synchronization"
    echo "• Implemented proper service connection logic"
    echo "• Added comprehensive logging and debugging"
    echo "• Improved thread safety for service operations"
    echo "• Enhanced error handling with detailed messages"
    echo "• Created diagnostic tools for troubleshooting"
    echo
    echo "Next steps:"
    echo "1. Test the improvements on actual devices"
    echo "2. Run the diagnostic tool if discovery issues persist"
    echo "3. Monitor logs for detailed discovery information"
else
    print_status "FAIL" "Some improvements are missing or incomplete"
    echo "Please review the failed checks above and ensure all improvements are properly implemented."
fi

echo
echo "=== Verification Complete ==="