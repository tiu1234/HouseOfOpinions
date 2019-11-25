package node.nodepackage;

import java.io.Serializable;

public class Package implements Serializable {
    private static final long serialVersionUID = -4262577301953054247L;
    private final PackageHeader header;

    public Package(PackageHeader header) {
        this.header = header;
    }

    public PackageHeader getHeader() {
        return header;
    }
}
