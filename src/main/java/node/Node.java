package node;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

public class Node implements NodeEventListener {
    private static final List<String> SEED_ADDRESSES = Collections.unmodifiableList(
            Arrays.asList("10.0.0.5"));
    private static final int SEED_PORT = 1050;
    private final ThreadPooledServer serverPool;
    private final ThreadPooledClient clientPool;
    private final HashMap<String, PeerNode> peerNodes = new HashMap<>();

    public Node() throws UnknownHostException {
        serverPool = new ThreadPooledServer(InetAddress.getLocalHost().getHostAddress(), this);
        clientPool = new ThreadPooledClient(this);
    }

    public Node(int port) throws UnknownHostException {
        serverPool = new ThreadPooledServer(InetAddress.getLocalHost().getHostAddress(), port, this);
        clientPool = new ThreadPooledClient(this);
    }

    public void start() {
        new Thread(serverPool).start();

        try {
            connectToSeedNodes();
        } catch (UnknownHostException e) {
            stop("Unknown Host");
            return;
        }

        connectToPeers();
    }

    @Override
    public void curAddressFromPeer(String ip, int port, ConnectionRunnable thread) {
        if (ip.equals(getServerIp()) && port == getServerPort()) {
            return;
        }
        String key = ip + ":" + port;
        boolean informOtherPeers = false;
        synchronized (peerNodes) {
            String msg = "\n--------------\n"
                    + "Node Address " + getServerIp() + ":" + getServerPort() + "\n"
                    + "New Cur " + key + "\n"
                    + "--------------";
            System.out.println(msg);
            if (!peerNodes.containsKey(key)) {
                peerNodes.put(key, new PeerNode(ip, port, thread));
                thread.setThreadKey(key);
                informOtherPeers = true;
            } else {
                peerNodes.get(key).setThread(thread);
                thread.setThreadKey(key);
            }
        }

        if (informOtherPeers) {
            spreadPeerAddress(ip, port, key);
        }
    }

    @Override
    public void peerAddressFromPeer(String ip, int port, String source) {
        if (ip.equals(getServerIp()) && port == getServerPort()) {
            return;
        }
        String key = ip + ":" + port;
        boolean informOtherPeers = false;

        synchronized (peerNodes) {
            String msg = "\n--------------\n"
                    + "Node Address " + getServerIp() + ":" + getServerPort() + "\n"
                    + "New Peer " + key + "\n"
                    + "--------------";
            System.out.println(msg);
            if (!peerNodes.containsKey(key)) {
                try {
                    connectToPeer(ip, port);
                } catch (IOException e) {
                    System.out.println("Failed to connect to " + ip + ":" + port);
                }
                informOtherPeers = true;
            }
        }

        if (informOtherPeers) {
            spreadPeerAddress(ip, port, source);
        }
    }

    @Override
    public void removeThread(String threadKey) {
        synchronized (peerNodes) {
            if (peerNodes.containsKey(threadKey)) {
                peerNodes.remove(threadKey);
                System.out.println(threadKey + " removed!");
            }
        }
    }

    public String getServerIp() {
        return serverPool.getServerIp();
    }

    public int getServerPort() {
        return serverPool.getServerPort();
    }

    private void spreadPeerAddress(String ip, int port, String preventSource) {
        String key = ip + ":" + port;
        synchronized (peerNodes) {
            for (Map.Entry<String, PeerNode> set : peerNodes.entrySet()) {
                if (!key.equals(set.getKey()) && !preventSource.equals(set.getKey())) {
                    ConnectionRunnable peerThread = set.getValue().getThread();
                    try {
                        peerThread.sendPeerAddressToPeer(ip, port);
                    } catch (IOException e) {
                        System.out.println("Failed to send peer address " + ip + ":" + port + "to " + set.getKey());
                    }
                }
            }
        }
    }

    private void connectToSeedNodes() throws UnknownHostException {
        InetAddress inetAddress = InetAddress.getLocalHost();

        //connect to seed nodes if not one of seed nodes
        if (!SEED_ADDRESSES.contains(inetAddress.getHostAddress()) || serverPool.getServerPort() != SEED_PORT) {
            for (String ip : SEED_ADDRESSES) {
                //we will add the thread when we connect to peers later
                peerNodes.put(ip + ":" + SEED_PORT, new PeerNode(ip, SEED_PORT, null));
            }
        }
    }

    private void connectToPeers() {
        synchronized (peerNodes) {
            ArrayList<String> failedPeersKey = new ArrayList<>();
            for (Map.Entry<String, PeerNode> set : peerNodes.entrySet()) {
                try {
                    connectToPeer(set.getValue().getIp(), set.getValue().getPort());
                } catch (IOException e) {
                    System.out.println("Failed to connect to " + set.getKey());
                    failedPeersKey.add(set.getKey());
                }
            }
            for (String failedKey : failedPeersKey) {
                peerNodes.remove(failedKey);
            }
        }
    }

    private void connectToPeer(String ip, int port) throws IOException {
        ConnectionRunnable thread = clientPool.connectToNew(ip, port);
        peerNodes.put(ip + ":" + port, new PeerNode(ip, port, thread));
        thread.sendMyAddressToPeer(getServerIp(), getServerPort());
    }

    public void stop(String msg) {
        System.out.println(msg);
        stop();
    }

    public void stop() {
        serverPool.stop();
        clientPool.stop();
    }
}
