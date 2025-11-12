# Aruba Instant AP Configuration for Thermal AR System

## Overview
Optimize your Aruba Instant access points for low-latency video streaming between Google Glass and ThinkPad P16.

## Prerequisites
- Aruba Instant APs with WiFi 6 capability
- Admin access to Aruba Instant UI
- Both Glass and P16 on same network

## Configuration Steps

### 1. Basic SSID Configuration

**Access Aruba Instant UI:**
```
1. Open browser: http://instant.arubanetworks.com
   OR use AP IP address
2. Login with admin credentials
3. Navigate to: Configuration → Wireless
```

**Create/Configure SSID:**
```
SSID Name: ThermalAR (or use existing)
Security: WPA3-Personal or WPA2-Personal
Passphrase: [your secure passphrase]

Radio Settings:
- Enable both 2.4GHz and 5GHz
- Prefer 5GHz for Glass (lower latency)
```

### 2. Enable WiFi 6 (802.11ax)

**Path:** Configuration → Radio → RF Management

```
5GHz Radio:
☑ Enable 802.11ax
☑ Enable OFDMA (improves multi-device performance)
☑ Enable TWT (Target Wake Time) - optional, saves battery

Channel Width: 40MHz or 80MHz
  - 80MHz gives more throughput but may have more interference
  - 40MHz is more stable in busy environments
  
Transmit Power: Auto (or manual if you have dead zones)
```

### 3. Configure Fast Roaming (802.11r)

**Path:** Configuration → Wireless → [Your SSID] → Security

```
Fast Transition: Enabled

Mobility Domain ID: 0x1234 (any hex value, must be same across all APs)

Over-the-DS: Enabled
  - Allows roaming without returning to wired network
  
Note: This enables <50ms handoffs between APs
Critical for walking inspections through building
```

### 4. Band Steering (Force 5GHz)

**Path:** Configuration → Radio → ARM (Adaptive Radio Management)

```
Band Steering:
☑ Enabled

Mode: Force dual-band capable clients to 5GHz
  - Glass EE2 is dual-band capable
  - 5GHz has lower latency than 2.4GHz
  
Prefer 5GHz: Yes
5GHz signal threshold: -70 dBm
```

### 5. QoS Configuration

**Path:** Configuration → Advanced → Roles & Policies

```
Create Firewall Policy:
Name: ThermalAR_QoS

Rule 1: Mark video traffic
  Service: Any
  Source: Glass IP (or any)
  Destination: P16 IP (or any)
  Protocol: UDP
  Port: Any
  Action: Set DSCP to EF (46)
  
Rule 2: Prioritize return traffic
  Service: Any
  Source: P16 IP
  Destination: Glass IP
  Protocol: UDP or TCP
  Port: 8080
  Action: Set DSCP to EF (46)

Apply policy to SSID: ThermalAR
```

**Alternative simplified QoS:**
```
Configuration → Wireless → [SSID] → Access

Traffic Type: Voice
  - This automatically prioritizes traffic on this SSID
  - Good enough for most setups
```

### 6. Advanced Optimizations (Optional)

#### Airtime Fairness

**Path:** Configuration → Radio → RF Management

```
Airtime Fairness: Enabled
  - Prevents slow clients from monopolizing airtime
  - Improves performance for Glass (fast client)
```

#### Client Match

**Path:** Configuration → Radio → ARM

```
Client Match: Enabled
  - Steers clients to best AP automatically
  - Reduces sticky client issues
  
Threshold: Medium or Aggressive
```

#### RTS/CTS Threshold

**Path:** Configuration → Radio → RF Management → Advanced

```
RTS/CTS Threshold: 2346 (disabled)
  - Don't enable unless you have interference issues
  - Adds overhead that increases latency
```

### 7. Verify Configuration

#### Check Glass connection:

**Via ADB:**
```bash
# Connect Glass via USB
adb shell

# Check WiFi status
dumpsys wifi | grep "Frequency"
# Should show 5180-5825 MHz (5GHz band)

dumpsys wifi | grep "Link speed"
# Should show 400+ Mbps (WiFi 6)

# Check signal strength
dumpsys wifi | grep "RSSI"
# Should be > -70 dBm for good performance
```

#### Test latency:

```bash
# From P16 to Glass (get Glass IP from Aruba client list)
ping [GLASS_IP]
# Should be < 5ms

# Test with larger packets (video-sized)
ping -s 1400 [GLASS_IP]
# Should still be < 10ms
```

#### Monitor in Aruba UI:

```
Monitoring → Clients → [Find Glass device]

Check:
- Connected to 5GHz: Yes
- Data Rate: 400+ Mbps
- Signal: -40 to -70 dBm (good to excellent)
- Airtime %: Should be reasonable
```

### 8. Static IP for P16 (Recommended)

**Option A: DHCP Reservation (Preferred)**

```
Configuration → System → DHCP → DHCP Pools

Add Reservation:
MAC Address: [P16 WiFi MAC]
IP Address: 192.168.1.100 (or your choice)
Hostname: ThermalAR-Server
```

**Option B: Static on P16**

Configure static IP on P16 matching your network:
```
IP: 192.168.1.100
Subnet: 255.255.255.0
Gateway: 192.168.1.1
DNS: 8.8.8.8, 8.8.4.4
```

Update in Glass app:
```java
private static final String SERVER_URL = "http://192.168.1.100:8080";
```

### 9. Firewall Rules (If Needed)

**Path:** Configuration → Advanced → Firewall

```
Create Allow Rule:
Name: Allow_ThermalAR
Source: Glass IP or any
Destination: P16 IP
Service: TCP/UDP Port 8080
Action: Allow

Priority: 1 (high)
```

### 10. Coverage Verification

Walk through inspection areas while monitoring:

```
On Glass (via ADB):
adb shell ping -c 100 [P16_IP]

Check packet loss: Should be 0%
Check latency: Should be consistently < 5ms
Check for spikes: Occasional spike to 10-20ms OK, frequent >50ms problematic
```

**If you experience dead zones:**

1. Add additional APs
2. Adjust AP placement
3. Adjust transmit power
4. Check for interference sources

## Recommended SSID Profiles

### Profile 1: Dedicated AR Network (Most Secure)

```
SSID: ThermalAR
Hidden: Yes
Security: WPA3-Personal
Client isolation: No (Glass and P16 need to communicate)
Band steering: 5GHz only
Fast roaming: Enabled
```

### Profile 2: Shared Corporate Network

```
SSID: Corporate
Security: WPA2-Enterprise or WPA3
Client isolation: No (or allow specific IPs)
VLAN: AR devices on same VLAN
QoS: Enabled for video traffic
Fast roaming: Enabled
```

## Monitoring Dashboard

**Real-time monitoring:**

```
Aruba Instant UI → Monitoring → Clients

Watch for:
- Glass connected to 5GHz
- Signal strength stable
- No frequent roaming (indicates signal issues)
- Throughput consistent

Monitoring → APs → [Each AP]
- Channel utilization < 50% (ideal)
- Client count reasonable
- No errors
```

## Troubleshooting

### Glass keeps switching between 2.4GHz and 5GHz

**Fix:**
```
Configuration → Radio → ARM
Band Steering: Force to 5GHz
Minimum 5GHz RSSI: Increase to -75 dBm
```

### High latency spikes

**Check:**
1. Channel utilization (should be <50%)
2. Interference from other networks
3. Too many clients on single AP
4. Glass signal strength

**Fix:**
```
Configuration → Radio → ARM
Enable Auto Channel Selection: Yes
Dynamic Channel Assignment: 10 minutes
```

### Glass won't roam between APs

**Fix:**
```
Configuration → Wireless → [SSID]
Fast Transition (802.11r): Enabled
Mobility Domain: Set to same value across all APs

Configuration → Radio → ARM
Client Match: Enabled
Threshold: Aggressive
```

### Packet loss during movement

**Likely cause:** Fast roaming not working

**Fix:**
```
Verify 802.11r is enabled
Check mobility domain is same on all APs
Ensure AP coverage overlaps adequately
Test with sticky client: Walk slowly and monitor ping
```

## Performance Targets

**Optimal:**
- Latency: <3ms
- Jitter: <1ms
- Packet loss: 0%
- Signal: -40 to -60 dBm
- Data rate: 600+ Mbps

**Acceptable:**
- Latency: <8ms
- Jitter: <3ms
- Packet loss: <0.1%
- Signal: -60 to -70 dBm
- Data rate: 400+ Mbps

**Problematic:**
- Latency: >10ms
- Packet loss: >0.5%
- Signal: <-75 dBm
- Frequent disconnections

## Advanced: Network Diagram

```
Internet
    ↓
Router (10Gbps uplink)
    ↓
Aruba Instant Cluster (Virtual Controller)
    ↓
    ├── AP1 (5GHz, Channel 36)
    ├── AP2 (5GHz, Channel 48)
    └── AP3 (5GHz, Channel 149)
         ↓
    Wireless Clients:
    ├── Google Glass (192.168.1.50)
    └── ThinkPad P16 (192.168.1.100)
```

## Testing Tools

### From P16:

```bash
# iPerf3 bandwidth test (requires iPerf on Glass - advanced)
iperf3 -c [GLASS_IP] -u -b 100M

# Continuous ping with statistics
ping -i 0.2 [GLASS_IP] > ping_results.txt
# Let run during test inspection
# Analyze for packet loss and latency variance
```

### From Aruba CLI (if available):

```
show ap monitor debug
show datapath user [GLASS_MAC]
show station-table [GLASS_MAC]
```

## Summary Checklist

Before deployment:

- [ ] WiFi 6 enabled on 5GHz
- [ ] Fast roaming (802.11r) configured
- [ ] Band steering to 5GHz
- [ ] QoS/DSCP marking for video
- [ ] Static IP for P16 (or DHCP reservation)
- [ ] Firewall allows port 8080
- [ ] Coverage tested in all work areas
- [ ] Latency verified <10ms
- [ ] No packet loss during roaming test

## Contact

For Aruba support:
- Documentation: https://www.arubanetworks.com/techdocs/Instant_86_WebHelp/
- Community: https://community.arubanetworks.com/
