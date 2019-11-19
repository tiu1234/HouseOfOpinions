package node;

public class PeerNode extends NodeAttributes {
    private ConnectionRunnable thread;

    public PeerNode(String ip, int port, ConnectionRunnable thread) {
        super(ip, port);
        this.thread = thread;
    }

    public ConnectionRunnable getThread() {
        return thread;
    }

    public void setThread(ConnectionRunnable thread) {
        this.thread = thread;
    }
}
