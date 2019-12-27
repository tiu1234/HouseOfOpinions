package node.nodepackage;

import java.io.Serializable;

public class IpInfo implements Serializable {
    private static final long serialVersionUID = -5694862294320156672L;
    private String ip;

    private int port;

    public IpInfo(IpInfo ipInfo) {
        this.ip = ipInfo.getIp();
        this.port = ipInfo.getPort();
    }

    public IpInfo(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
