package node.nodepackage;

import java.util.ArrayList;

public class IPPackage extends Package {
    private static final long serialVersionUID = 3092401656804136720L;
    private final ArrayList<IpInfo> ips;

    public IPPackage(PackageHeader header) {
        super(header);
        this.ips = new ArrayList<>();
    }

    public void addIp(String ip, int port) {
        ips.add(new IpInfo(ip, port));
    }

    public void addIp(IpInfo ipInfo) {
        ips.add(ipInfo);
    }

    public void addIps(ArrayList<IpInfo> ipInfoList) {
        ips.addAll(ipInfoList);
    }

    public ArrayList<IpInfo> getIps() {
        return ips;
    }
}
