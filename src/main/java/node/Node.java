package node;

import node.connection.ByteBufferUtil;
import node.connection.ConnectionChannel;
import node.connection.ConnectionEventListener;
import node.nodepackage.IPPackage;
import node.nodepackage.Package;
import node.nodepackage.PackageHeader;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.*;

import static node.HeartBeatCallable.calNextHeartBeat;
import static node.connection.ConnectionChannel.startWrite;

public class Node implements ConnectionEventListener {
    //TODO seed addresses will be loaded from a config file
    private static final List<String> SEED_ADDRESSES = Collections.unmodifiableList(
            Arrays.asList("10.0.0.5"));
    private static final int SEED_PORT = 1050;
    private final ConnectionChannel connectionChannel;
    private final HashMap<String, PeerNode> stablePeers;
    //stable peers Hashtable with different keys
    private final HashMap<String, PeerNode> ipPeers;
    private final HashMap<String, PeerNode> unknownPeers;
    private final HashSet<String> ipWaitResponse;
    //TODO public IP and port will be loaded from config file
    private final String localIP;
    private final int localPort;

    public Node() throws IOException {
        connectionChannel = new ConnectionChannel(SEED_PORT, this);
        this.connectionChannel.getExecutorService().submit(new CheckPeersCallable(System.currentTimeMillis(), this.connectionChannel.getExecutorService(), this));
        stablePeers = new HashMap<>();
        unknownPeers = new HashMap<>();
        ipPeers = new HashMap<>();
        ipWaitResponse = new HashSet<>();
        localPort = SEED_PORT;
        localIP = InetAddress.getLocalHost().getHostAddress();
    }

    public Node(int port) throws IOException {
        connectionChannel = new ConnectionChannel(port, this);
        this.connectionChannel.getExecutorService().submit(new CheckPeersCallable(System.currentTimeMillis(), this.connectionChannel.getExecutorService(), this));
        stablePeers = new HashMap<>();
        unknownPeers = new HashMap<>();
        ipPeers = new HashMap<>();
        ipWaitResponse = new HashSet<>();
        localPort = port;
        localIP = InetAddress.getLocalHost().getHostAddress();
    }

    public void start() {
        try {
            connectToSeedNodes();
        } catch (UnknownHostException e) {
            try {
                stop("Failed to find current local address!");
            } catch (IOException ex) {
                System.out.println("Failed to stop!");
            }
        }
    }

    public void checkClosedPeers() {
        final ArrayList<String> remove = new ArrayList<>();

        for (Map.Entry<String, PeerNode> peerEntry : unknownPeers.entrySet()) {
            final PeerNode peer = peerEntry.getValue();
            if (!peer.getSocketChannel().isOpen()) {
                remove.add(peerEntry.getKey());
            }
        }

        for (String key : remove) {
            unknownPeers.remove(key);
        }

        remove.clear();

        for (Map.Entry<String, PeerNode> peerEntry : stablePeers.entrySet()) {
            final PeerNode peer = peerEntry.getValue();
            if (!peer.getSocketChannel().isOpen()) {
                remove.add(peerEntry.getKey());
                ipPeers.remove(peer.getIp() + ":" + peer.getPort());
            }
        }

        for (String key : remove) {
            stablePeers.remove(key);
        }
    }

    private void addTimeOutTask(PeerNode peer) {
        this.connectionChannel.getExecutorService().submit(new TimeOutCallable(this.connectionChannel.getExecutorService(), peer));
    }

    private void shutDownConnection(AsynchronousSocketChannel socketChannel) {
        if (!socketChannel.isOpen()) {
            return;
        }
        try {
            socketChannel.close();
        } catch (IOException ignored) {
        }
    }

    private String getSocketKey(AsynchronousSocketChannel socketChannel) {
        try {
            return socketChannel.getRemoteAddress().toString();
        } catch (IOException e) {
            return null;
        }
    }

    private String getServerIp(AsynchronousSocketChannel socketChannel) {
        try {
            return socketChannel.getRemoteAddress().toString().split("/")[1].split(":")[0];
        } catch (IOException e) {
            return null;
        }
    }

    private int getServerPort(AsynchronousSocketChannel socketChannel) {
        try {
            return Integer.parseInt(socketChannel.getRemoteAddress().toString().split("/")[1].split(":")[1]);
        } catch (IOException e) {
            return -1;
        }
    }

    @Override
    public void newClient(AsynchronousSocketChannel socketChannel) {
        final String key = getSocketKey(socketChannel);
        if (key == null) {
            shutDownConnection(socketChannel);
            return;
        }

        final PeerNode peer = new PeerNode(socketChannel, null, -1, TimeOutCallable.UNKNOWN_TIME_OUT);
        unknownPeers.put(key, peer);
        addTimeOutTask(peer);
        connectionChannel.getExecutorService().submit(new HeartBeatCallable(connectionChannel.getExecutorService(), this, peer, calNextHeartBeat()));
        System.out.println("New client: " + localIP + ":" + localPort +" received from " + key + "\n");
    }

    @Override
    public void newClientFail() {
    }

    @Override
    public void connComplete(AsynchronousSocketChannel socketChannel) {
        final String key = getSocketKey(socketChannel);
        final String ip = getServerIp(socketChannel);
        final int port = getServerPort(socketChannel);
        if (key == null || !ipWaitResponse.contains(ip + ":" + port)) {
            shutDownConnection(socketChannel);
            return;
        }
        ipWaitResponse.remove(ip + ":" + port);

        try {
            final IPPackage ipPackage = new IPPackage(PackageHeader.MY_IP, localIP, localPort);
            startWrite(socketChannel, ByteBufferUtil.convertSerialToQueue(ipPackage), this);
        } catch (IOException e) {
            shutDownConnection(socketChannel);
            return;
        }

        //TODO validation process of my address
        PeerNode peer = new PeerNode(socketChannel, ip, port, TimeOutCallable.STABLE_TIME_OUT);
        stablePeers.put(key, peer);
        ipPeers.put(ip + ":" + port, peer);
        addTimeOutTask(peer);
        connectionChannel.getExecutorService().submit(new HeartBeatCallable(connectionChannel.getExecutorService(), this, peer, calNextHeartBeat()));
        System.out.println("Connection complete: " + localIP + ":" + localPort +" connected to " + ip + ":" + port + "\n");
    }

    @Override
    public void connFail(AsynchronousSocketChannel socketChannel) {
        final String ip = getServerIp(socketChannel);
        final int port = getServerPort(socketChannel);
        ipWaitResponse.remove(ip + ":" + port);
    }

    @Override
    public void newRead(AsynchronousSocketChannel socketChannel, ByteBuffer buffer) {
        final String key = getSocketKey(socketChannel);
        if (key == null) {
            shutDownConnection(socketChannel);
            return;
        }

        if (!checkReadStableNode(socketChannel, key, buffer) && !checkReadUnknownNode(socketChannel, key, buffer)) {
            shutDownConnection(socketChannel);
        }
    }

    @Override
    public void newReadFail(AsynchronousSocketChannel socketChannel) {
        System.out.println("Read Fail: " + localIP + ":" + localPort + " on "+ getSocketKey(socketChannel));
        shutDownConnection(socketChannel);
    }

    @Override
    public void writeComplete(AsynchronousSocketChannel socketChannel, Integer result) {
    }

    @Override
    public void writeFail(AsynchronousSocketChannel socketChannel) {
        System.out.println("Write Fail: " + localIP + ":" + localPort + " on "+ getSocketKey(socketChannel));
        shutDownConnection(socketChannel);
    }

    private boolean checkReadStableNode(AsynchronousSocketChannel socketChannel, String key, ByteBuffer buffer) {
        if (!stablePeers.containsKey(key)) {
            return false;
        }

        final PeerNode peer = stablePeers.get(key);
        peer.updateConnectionTime();
        boolean packFinished = false;
        try {
            packFinished = peer.consumeData(buffer);
        } catch (IOException e) {
            shutDownConnection(socketChannel);
            return true;
        }
        if (packFinished) {
            final Package pack;
            try {
                pack = cast(Package.class, ByteBufferUtil.convertQueueToObject(peer.getQueue()));
                peer.getQueue().clear();
            } catch (IOException | ClassNotFoundException | ClassCastException e) {
                shutDownConnection(socketChannel);
                return true;
            }

            if (pack != null) {
                switch (pack.getHeader()) {
                    case HEART_BEAT:
                        break;
                    case MY_IP:
                        break;
                    case PEER_IP:
                        final IPPackage ipPackage;
                        try {
                            ipPackage = cast(IPPackage.class, pack);
                        } catch (ClassCastException e) {
                            shutDownConnection(socketChannel);
                            break;
                        }
                        final String ip = ipPackage.getIp();
                        final int port = ipPackage.getPort();
                        if (!ipPeers.containsKey(ip + ":" + port) && (!localIP.equals(ip) || localPort != port) && !ipWaitResponse.contains(ip + ":" + port)) {
                            connectToPeer(ip, port);
                            spreadPeerAddress(ip, port, peer.getIp() + ":" + peer.getPort());
                        }
                        break;
                    default:
                        shutDownConnection(socketChannel);
                        break;
                }
            }
        }

        return true;
    }

    private <T> T cast(Class<T> tClass, Object from) {
        return tClass.cast(from);
    }

    private boolean checkReadUnknownNode(AsynchronousSocketChannel socketChannel, String key, ByteBuffer buffer) {
        if (!unknownPeers.containsKey(key)) {
            return false;
        }

        final PeerNode peer = unknownPeers.get(key);

        peer.updateConnectionTime();
        boolean packFinished = false;
        try {
            packFinished = peer.consumeData(buffer);
        } catch (IOException e) {
            shutDownConnection(socketChannel);
            return true;
        }
        if (packFinished) {
            final Package pack;
            try {
                pack = cast(Package.class, ByteBufferUtil.convertQueueToObject(peer.getQueue()));
                peer.getQueue().clear();
            } catch (IOException | ClassNotFoundException | ClassCastException e) {
                shutDownConnection(socketChannel);
                return true;
            }

            if (pack != null) {
                switch (pack.getHeader()) {
                    case HEART_BEAT:
                        break;
                    case MY_IP:
                        final IPPackage ipPackage;
                        try {
                            ipPackage = cast(IPPackage.class, pack);
                        } catch (ClassCastException e) {
                            shutDownConnection(socketChannel);
                            break;
                        }
                        final String ip = ipPackage.getIp();
                        final int port = ipPackage.getPort();
                        System.out.println("New IP from " + key + " : " + ip + ":" + port);
                        ipWaitResponse.remove(ip + ":" + port);
                        if (ipPeers.containsKey(ip + ":" + port)) {
                            if (localIP.compareTo(ip) > 0 || (localIP.compareTo(ip) == 0 && localPort > port)) {
                                shutDownConnection(socketChannel);
                                break;
                            }
                            System.out.println("New IP from " + key + " : " + ip + ":" + port + " overwrites old one");
                            final AsynchronousSocketChannel stableChannel = ipPeers.get(ip + ":" + port).getSocketChannel();
                            final String stableKey = getSocketKey(stableChannel);
                            shutDownConnection(stableChannel);
                            stablePeers.remove(stableKey);
                        }
                        unknownPeers.remove(key);
                        peer.setTimeOut(TimeOutCallable.STABLE_TIME_OUT);
                        peer.setIp(ip);
                        peer.setPort(port);
                        stablePeers.put(key, peer);
                        ipPeers.put(ip + ":" + port, peer);
                        spreadPeerAddress(ip, port, ip + ":" + port);
                        break;
                    case PEER_IP:
                        break;
                    default:
                        shutDownConnection(socketChannel);
                        break;
                }
            }
        }

        return true;
    }

    private void spreadPeerAddress(String ip, int port, String preventDataSource) {
        final String key = ip + ":" + port;
        for (Map.Entry<String, PeerNode> peerEntry : ipPeers.entrySet()) {
            final String curKey = peerEntry.getKey();
            final String curIP = curKey.split(":")[0];
            final int curPort = Integer.parseInt(curKey.split(":")[1]);
            if ((!curIP.equals(localIP) || curPort != localPort) && !curKey.equals(key) && !curKey.equals(preventDataSource)) {
                final IPPackage ipPackage = new IPPackage(PackageHeader.PEER_IP, ip, port);
                try {
                    startWrite(peerEntry.getValue().getSocketChannel(), ByteBufferUtil.convertSerialToQueue(ipPackage), this);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void connectToSeedNodes() throws UnknownHostException {
        final InetAddress inetAddress = InetAddress.getLocalHost();

        //connect to seed nodes if not one of seed nodes
        if (!SEED_ADDRESSES.contains(inetAddress.getHostAddress()) || localPort != SEED_PORT) {
            for (String ip : SEED_ADDRESSES) {
                connectToPeer(ip, SEED_PORT);
            }
        }
    }

    private void connectToPeer(String ip, int port) {
        if ((localIP.equals(ip) && port == localPort) || ipWaitResponse.contains(ip + ":" + port)){
            return;
        }
        System.out.println("Connect to peer: " + localIP + ":" + localPort + " tried to connect to " + ip + ":" + port + "\n");
        ipWaitResponse.add(ip + ":" + port);
        try {
            connectionChannel.connectToNew(ip, port);
        } catch (IOException ignored) {
        }
    }

    public void stop(String msg) throws IOException {
        System.out.println(msg);
        stop();
    }

    public void stop() throws IOException {
        connectionChannel.forceStop();

        for (Map.Entry<String, PeerNode> peerEntry : unknownPeers.entrySet()) {
            peerEntry.getValue().getSocketChannel().close();
        }
        unknownPeers.clear();

        for (Map.Entry<String, PeerNode> peerEntry : stablePeers.entrySet()) {
            peerEntry.getValue().getSocketChannel().close();
        }
        stablePeers.clear();

        ipPeers.clear();

        ipWaitResponse.clear();
    }

    public String getLocalIP() {
        return localIP;
    }

    public int getLocalPort() {
        return localPort;
    }

    public HashMap<String, PeerNode> getStablePeers() {
        return stablePeers;
    }

    public HashMap<String, PeerNode> getIpPeers() {
        return ipPeers;
    }

    public HashMap<String, PeerNode> getUnknownPeers() {
        return unknownPeers;
    }
}
