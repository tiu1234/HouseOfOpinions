package node;

import node.connection.ByteBufferUtil;
import node.nodepackage.Package;
import node.nodepackage.PackageHeader;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import static node.connection.ConnectionChannel.startWrite;

public class HeartBeatCallable implements Callable<Object> {
    public static final long HEART_BEAT_GAP = 1000;
    private final ExecutorService executorService;
    private final Node node;
    private final PeerNode peer;
    private final long nextTime;

    public HeartBeatCallable(ExecutorService executorService, Node node, PeerNode peer, long nextTime) {
        this.executorService = executorService;
        this.node = node;
        this.peer = peer;
        this.nextTime = nextTime;
    }

    @Override
    public Object call() throws Exception {
        if (!peer.getSocketChannel().isOpen()) {
            return null;
        }
        final long curTime = System.currentTimeMillis();
        if (curTime >= nextTime) {
            startWrite(peer.getSocketChannel(), ByteBufferUtil.convertSerialToQueue(new Package(PackageHeader.HEART_BEAT)), node);
            executorService.submit(new HeartBeatCallable(executorService, node, peer, curTime + HEART_BEAT_GAP));
        } else {
            executorService.submit(new HeartBeatCallable(executorService, node, peer, nextTime));
        }

        return null;
    }

    public static long calNextHeartBeat() {
        return System.currentTimeMillis() + HEART_BEAT_GAP;
    }
}
