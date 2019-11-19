package node;

public interface NodeEventListener {
    void curAddressFromPeer(String ip, int port, ConnectionRunnable thread);

    void peerAddressFromPeer(String ip, int port, String source);

    String getServerIp();

    int getServerPort();
}
