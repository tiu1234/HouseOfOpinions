package node;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

public class Node implements NodeEventListener {
    private static final List<String> SEED_ADDRESSES = Collections.unmodifiableList(
            Arrays.asList("10.0.0.5"));
    private static final int SEED_PORT = 1050;
    private ThreadPooledServer serverPool;
    private ThreadPooledClient clientPool;
    private final HashMap<String, PeerNode> peerNodes = new HashMap<>();

    public Node() {
        try {
            serverPool = new ThreadPooledServer(InetAddress.getLocalHost().getHostAddress(), this);
        } catch (UnknownHostException e) {
            System.out.println("Failed to create server pool!");
        }
        init();
    }

    public Node(int port) {
        try {
            serverPool = new ThreadPooledServer(InetAddress.getLocalHost().getHostAddress(), port, this);
        } catch (UnknownHostException e) {
            System.out.println("Failed to create server pool!");
        }
        init();
    }

    private void init() {
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

        try {
            connectToPeers();
        } catch (IOException e) {
            System.out.println("Failed to connect to peers!");
        }
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
                    + "Cur Address " + getServerIp() + ":" + getServerPort() + "\n"
                    + "New Peer " + key + "\n"
                    + "--------------";
            System.out.println(msg);
            if (!peerNodes.containsKey(key)) {
                peerNodes.put(key, new PeerNode(ip, port, thread));
                informOtherPeers = true;
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
                    + "Cur Address " + getServerIp() + ":" + getServerPort() + "\n"
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
    public String getServerIp() {
        return serverPool.getServerIp();
    }

    @Override
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

    private void connectToPeers() throws IOException {
        synchronized (peerNodes) {
            for (Map.Entry<String, PeerNode> set : peerNodes.entrySet()) {
                connectToPeer(set.getValue().getIp(), set.getValue().getPort());
            }
        }
    }

    private void connectToPeer(String ip, int port) throws IOException {
        ConnectionRunnable thread = clientPool.connectToNew(ip, port);
        peerNodes.put(ip + ":" + SEED_PORT, new PeerNode(ip, SEED_PORT, thread));
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
