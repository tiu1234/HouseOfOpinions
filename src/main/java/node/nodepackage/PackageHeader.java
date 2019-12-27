package node.nodepackage;

public enum PackageHeader {
    HEART_BEAT("Heart Beat"),
    MY_IP("My IP"),
    PEER_IPS("Peer IPs"),
    REQUEST_FOR_PEERS("Request for peers"),
    REQUEST_FOR_REG("Request for registered IPs");;

    private final String content;

    PackageHeader(String s) {
        content = s;
    }

    public String getContent() {
        return content;
    }
}
