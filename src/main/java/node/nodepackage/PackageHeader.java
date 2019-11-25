package node.nodepackage;

public enum PackageHeader {
    HEART_BEAT("Heart Beat"),
    MY_IP("My IP"),
    PEER_IP("Peer IP");

    private final String content;

    PackageHeader(String s) {
        content = s;
    }

    public String getContent() {
        return content;
    }
}
