package node.nodepackage;

public class IPPackage extends Package {
    private static final long serialVersionUID = 3092401656804136720L;
    private final String ip;
    private final int port;

    public IPPackage(PackageHeader header, String ip, int port) {
        super(header);
        this.ip = ip;
        this.port = port;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }
}
