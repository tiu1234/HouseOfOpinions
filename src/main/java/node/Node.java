package node;

import node.connection.ByteBufferUtil;
import node.connection.ConnectionChannel;
import node.connection.ConnectionEventListener;
import node.nodepackage.IPPackage;
import node.nodepackage.IpInfo;
import node.nodepackage.Package;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.*;

import static node.HeartBeatCallable.calNextHeartBeat;
import static node.connection.ConnectionChannel.startWrite;
import static node.nodepackage.PackageHeader.MY_IP;
import static node.nodepackage.PackageHeader.PEER_IPS;

public class Node implements ConnectionEventListener {
    //TODO seed addresses will be loaded from a config file
    private static final List<String> SEED_ADDRESSES = Collections.unmodifiableList(
            Arrays.asList("10.0.0.5"));
    private static final int SEED_PORT = 1050;
    private final ConnectionChannel connectionChannel;
    private final HashSet<String> registeredIps;
    private final HashMap<String, PeerNode> stablePeers;
    private final HashMap<String, PeerNode> ipPeers;
    private final HashMap<String, PeerNode> unknownPeers;
    //TODO public IP and port will be loaded from config file
    private final String localIp;
    private final int localPort;

    public Node() throws IOException {
        connectionChannel = new ConnectionChannel(SEED_PORT, this);
        this.connectionChannel.getExecutorService().submit(new CheckPeersCallable(System.currentTimeMillis(), this.connectionChannel.getExecutorService(), this));
        registeredIps = new HashSet<>();
        stablePeers = new HashMap<>();
        unknownPeers = new HashMap<>();
        ipPeers = new HashMap<>();
        localPort = SEED_PORT;
        localIp = InetAddress.getLocalHost().getHostAddress();
    }

    public Node(int port) throws IOException {
        connectionChannel = new ConnectionChannel(port, this);
        this.connectionChannel.getExecutorService().submit(new CheckPeersCallable(System.currentTimeMillis(), this.connectionChannel.getExecutorService(), this));
        registeredIps = new HashSet<>();
        stablePeers = new HashMap<>();
        unknownPeers = new HashMap<>();
        ipPeers = new HashMap<>();
        localPort = port;
        localIp = InetAddress.getLocalHost().getHostAddress();
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
        System.out.println("New client: " + localIp + ":" + localPort +" received from " + key + "\n");
    }

    @Override
    public void newClientFail() {
    }

    @Override
    public void connComplete(AsynchronousSocketChannel socketChannel) {
        final String key = getSocketKey(socketChannel);
        final String ip = getServerIp(socketChannel);
        final int port = getServerPort(socketChannel);
        if (key == null) {
            shutDownConnection(socketChannel);
            return;
        }

        try {
            sendMyIpTo(socketChannel);
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
        System.out.println("Connection complete: " + localIp + ":" + localPort +" connected to " + ip + ":" + port + "\n");
    }

    @Override
    public void connFail(AsynchronousSocketChannel socketChannel) {
        final String ip = getServerIp(socketChannel);
        final int port = getServerPort(socketChannel);
        System.out.println("Connection failed: " + localIp + ":" + localPort +" failed to connect to " + ip + ":" + port + "\n");
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
        System.out.println("Read Fail: " + localIp + ":" + localPort + " on "+ getSocketKey(socketChannel));
        shutDownConnection(socketChannel);
    }

    @Override
    public void writeComplete(AsynchronousSocketChannel socketChannel, Integer result) {
        if (SEED_ADDRESSES.contains(localIp) && localPort == SEED_PORT) {
            shutDownConnection(socketChannel);
        }
    }

    @Override
    public void writeFail(AsynchronousSocketChannel socketChannel) {
        System.out.println("Write Fail: " + localIp + ":" + localPort + " on "+ getSocketKey(socketChannel));
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
                    case PEER_IPS:
                        final IPPackage ipPackage;
                        try {
                            ipPackage = cast(IPPackage.class, pack);
                        } catch (ClassCastException e) {
                            shutDownConnection(socketChannel);
                            break;
                        }
                        for (IpInfo ipInfo : ipPackage.getIps()) {
                            final String ip = ipInfo.getIp();
                            final int port = ipInfo.getPort();
                            if (!ipPeers.containsKey(ip + ":" + port) && (!localIp.equals(ip) || localPort != port)) {
                                connectToPeer(ip, port);
                            }
                        }
                        break;
                    case REQUEST_FOR_PEERS:
                        try {
                            sendPeerIpsTo(socketChannel);
                        } catch (IOException e) {
                            shutDownConnection(socketChannel);
                        }
                        break;
                    case REQUEST_FOR_REG:
                        try {
                            sendRegisteredIpsTo(socketChannel);
                        } catch (IOException e) {
                            shutDownConnection(socketChannel);
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
                        if (ipPackage.getIps().size() != 1) {
                            shutDownConnection(socketChannel);
                            break;
                        }
                        final String ip = ipPackage.getIps().get(0).getIp();
                        final int port = ipPackage.getIps().get(0).getPort();
                        System.out.println("New IP from " + key + " : " + ip + ":" + port);
                        if (ipPeers.containsKey(ip + ":" + port)) {
                            shutDownConnection(socketChannel);
                            break;
                        }
                        if (SEED_ADDRESSES.contains(localIp) && localPort == SEED_PORT) {
                            try {
                                sendRegisteredIpsTo(socketChannel);
                            } catch (IOException e) {
                                shutDownConnection(socketChannel);
                            }
                            registeredIps.add(ip + ":" + port);
                        }
                        unknownPeers.remove(key);
                        peer.setTimeOut(TimeOutCallable.STABLE_TIME_OUT);
                        peer.setIp(ip);
                        peer.setPort(port);
                        stablePeers.put(key, peer);
                        ipPeers.put(ip + ":" + port, peer);
                        break;
                    case PEER_IPS:
                        break;
                    case REQUEST_FOR_PEERS:
                        try {
                            sendPeerIpsTo(socketChannel);
                        } catch (IOException e) {
                            shutDownConnection(socketChannel);
                        }
                        break;
                    case REQUEST_FOR_REG:
                        try {
                            sendRegisteredIpsTo(socketChannel);
                        } catch (IOException e) {
                            shutDownConnection(socketChannel);
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

    private void sendMyIpTo(AsynchronousSocketChannel socketChannel) throws IOException {
        final IPPackage myIp = new IPPackage(MY_IP);
        myIp.addIp(localIp, localPort);
        startWrite(socketChannel, ByteBufferUtil.convertSerialToQueue(myIp), this);
    }

    private void sendPeerIpsTo(AsynchronousSocketChannel socketChannel) throws IOException {
        final IPPackage peerIps = new IPPackage(PEER_IPS);
        peerIps.addIps(getAllPeerIps());
        startWrite(socketChannel, ByteBufferUtil.convertSerialToQueue(peerIps), this);
    }

    private ArrayList<IpInfo> getAllPeerIps() {
        final ArrayList<IpInfo> peerIps = new ArrayList<>();
        for (Map.Entry<String, PeerNode> peerEntry : ipPeers.entrySet()) {
            peerIps.add(peerEntry.getValue().getIpInfo());
        }

        return peerIps;
    }

    private void sendRegisteredIpsTo(AsynchronousSocketChannel socketChannel) throws IOException {
        final IPPackage ips = new IPPackage(PEER_IPS);
        ips.addIps(getAllRegisteredIps());
        startWrite(socketChannel, ByteBufferUtil.convertSerialToQueue(ips), this);
    }

    private ArrayList<IpInfo> getAllRegisteredIps() {
        final ArrayList<IpInfo> ips = new ArrayList<>();
        for (String ip : registeredIps) {
            ips.add(new IpInfo(ip.split(":")[0], Integer.parseInt(ip.split(":")[1])));
        }

        return ips;
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
        if ((localIp.equals(ip) && port == localPort)){
            return;
        }
        System.out.println("Connect to peer: " + localIp + ":" + localPort + " tried to connect to " + ip + ":" + port + "\n");
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
    }

    public String getLocalIp() {
        return localIp;
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

    public HashSet<String> getRegisteredIps() {
        return registeredIps;
    }
}
