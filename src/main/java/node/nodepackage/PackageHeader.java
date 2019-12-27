package node.nodepackage;

public enum PackageHeader {
    HEART_BEAT("Heart Beat"),
    MY_IP("My IP"),
    PEER_IPS("Peer IPs"),
    REQUEST_FOR_PEERS("Request for peers");

    private final String content;

    PackageHeader(String s) {
        content = s;
    }

    public String getContent() {
        return content;
    }
}
