package node;

import node.nodepackage.IpInfo;

public class NodeAttributes {
    protected final IpInfo ipInfo;
    protected int latency;

    public NodeAttributes(String ip, int port) {
        ipInfo = new IpInfo(ip, port);
    }

    public String getIp() {
        return ipInfo.getIp();
    }

    public int getPort() {
        return ipInfo.getPort();
    }

    public void setIp(String ip) {
        ipInfo.setIp(ip);
    }

    public void setPort(int port) {
        ipInfo.setPort(port);
    }

    public IpInfo getIpInfo() {
        return ipInfo;
    }

    public int getLatency() {
        return latency;
    }

    public void setLatency(int latency) {
        this.latency = latency;
    }
}
