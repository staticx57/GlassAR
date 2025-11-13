package com.example.thermalarglass;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Smart server discovery for ThermalAR Glass
 * Uses multiple methods to automatically find processing server
 */
public class ServerDiscovery {

    private static final String TAG = "ServerDiscovery";

    // Discovery protocol constants
    private static final int DISCOVERY_PORT = 8081;
    private static final String DISCOVERY_MESSAGE = "THERMAL_AR_GLASS_DISCOVERY";
    private static final String DISCOVERY_RESPONSE_PREFIX = "THERMAL_AR_SERVER:";
    private static final int DISCOVERY_TIMEOUT_MS = 3000;
    private static final int MAX_RETRIES = 3;

    private Context mContext;
    private ExecutorService mExecutor;
    private List<ServerInfo> mDiscoveredServers;
    private IDiscoveryCallback mCallback;

    public interface IDiscoveryCallback {
        void onServerFound(ServerInfo server);
        void onDiscoveryComplete(List<ServerInfo> servers);
        void onDiscoveryFailed(String error);
    }

    public static class ServerInfo {
        public String name;
        public String address;
        public int port;
        public long lastSeen;
        public String capabilities;  // e.g., "object_detection,thermal_analysis"

        public ServerInfo(String name, String address, int port) {
            this.name = name;
            this.address = address;
            this.port = port;
            this.lastSeen = System.currentTimeMillis();
        }

        public String getUrl() {
            return "http://" + address + ":" + port;
        }

        @Override
        public String toString() {
            return name + " (" + address + ":" + port + ")";
        }
    }

    public ServerDiscovery(Context context) {
        mContext = context;
        mExecutor = Executors.newSingleThreadExecutor();
        mDiscoveredServers = new ArrayList<>();
    }

    /**
     * Start server discovery using UDP broadcast
     */
    public void startDiscovery(IDiscoveryCallback callback) {
        mCallback = callback;
        mDiscoveredServers.clear();

        Log.i(TAG, "Starting server discovery...");

        mExecutor.submit(() -> {
            try {
                // Method 1: UDP Broadcast Discovery
                discoverViaBroadcast();

                // Method 2: mDNS/Zeroconf (if available)
                // discoverViaMDNS();  // TODO: Implement if needed

                // Notify completion
                if (mCallback != null) {
                    mCallback.onDiscoveryComplete(mDiscoveredServers);
                }

                if (mDiscoveredServers.isEmpty()) {
                    Log.w(TAG, "No servers found via discovery");
                    if (mCallback != null) {
                        mCallback.onDiscoveryFailed("No servers found on network");
                    }
                } else {
                    Log.i(TAG, "Discovery complete: found " + mDiscoveredServers.size() + " server(s)");
                }

            } catch (Exception e) {
                Log.e(TAG, "Discovery error", e);
                if (mCallback != null) {
                    mCallback.onDiscoveryFailed(e.getMessage());
                }
            }
        });
    }

    /**
     * Discover servers using UDP broadcast
     */
    private void discoverViaBroadcast() {
        DatagramSocket socket = null;

        try {
            // Create socket for sending broadcast
            socket = new DatagramSocket();
            socket.setBroadcast(true);
            socket.setSoTimeout(DISCOVERY_TIMEOUT_MS);

            // Get broadcast address
            WifiManager wifi = (WifiManager) mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi == null || !wifi.isWifiEnabled()) {
                Log.w(TAG, "WiFi not available for discovery");
                return;
            }

            // Send discovery broadcast
            byte[] sendData = DISCOVERY_MESSAGE.getBytes(StandardCharsets.UTF_8);
            DatagramPacket sendPacket = new DatagramPacket(
                sendData,
                sendData.length,
                InetAddress.getByName("255.255.255.255"),
                DISCOVERY_PORT
            );

            Log.i(TAG, "Sending UDP broadcast discovery on port " + DISCOVERY_PORT);
            socket.send(sendPacket);

            // Listen for responses
            byte[] receiveData = new byte[1024];
            long startTime = System.currentTimeMillis();

            while (System.currentTimeMillis() - startTime < DISCOVERY_TIMEOUT_MS) {
                try {
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    socket.receive(receivePacket);

                    String response = new String(
                        receivePacket.getData(),
                        0,
                        receivePacket.getLength(),
                        StandardCharsets.UTF_8
                    );

                    Log.d(TAG, "Received response: " + response);

                    // Parse response
                    if (response.startsWith(DISCOVERY_RESPONSE_PREFIX)) {
                        parseServerResponse(response, receivePacket.getAddress().getHostAddress());
                    }

                } catch (SocketTimeoutException e) {
                    // Timeout waiting for response - continue
                    break;
                } catch (IOException e) {
                    Log.e(TAG, "Error receiving discovery response", e);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Broadcast discovery error", e);
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    /**
     * Parse server response and add to discovered servers
     * Format: THERMAL_AR_SERVER:name:port:capabilities
     */
    private void parseServerResponse(String response, String address) {
        try {
            String[] parts = response.substring(DISCOVERY_RESPONSE_PREFIX.length()).split(":");

            if (parts.length >= 2) {
                String name = parts[0];
                int port = Integer.parseInt(parts[1]);
                String capabilities = parts.length > 2 ? parts[2] : "";

                ServerInfo server = new ServerInfo(name, address, port);
                server.capabilities = capabilities;

                // Check if already discovered
                boolean exists = false;
                for (ServerInfo existing : mDiscoveredServers) {
                    if (existing.address.equals(address) && existing.port == port) {
                        existing.lastSeen = System.currentTimeMillis();
                        exists = true;
                        break;
                    }
                }

                if (!exists) {
                    mDiscoveredServers.add(server);
                    Log.i(TAG, "✓ Discovered server: " + server.toString());

                    if (mCallback != null) {
                        mCallback.onServerFound(server);
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing server response: " + response, e);
        }
    }

    /**
     * Stop discovery
     */
    public void stopDiscovery() {
        if (mExecutor != null && !mExecutor.isShutdown()) {
            mExecutor.shutdownNow();
        }
    }

    /**
     * Get list of discovered servers
     */
    public List<ServerInfo> getDiscoveredServers() {
        return new ArrayList<>(mDiscoveredServers);
    }
}
