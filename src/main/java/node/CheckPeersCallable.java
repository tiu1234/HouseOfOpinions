package node;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

public class CheckPeersCallable implements Callable<Object> {
    private static final long CHECK_GAP = 200;
    private final ExecutorService executorService;
    private final Node node;
    private long lastCheckTime;

    public CheckPeersCallable (long time, ExecutorService executorService, Node node) {
        this.executorService = executorService;
        this.node = node;
        lastCheckTime = time;
    }

    @Override
    public Object call() throws Exception {
        final long curTime = System.currentTimeMillis();
        if (curTime - lastCheckTime >= CHECK_GAP) {
            lastCheckTime = curTime;
            node.checkClosedPeers();
        }
        executorService.submit(new CheckPeersCallable(lastCheckTime, executorService, node));

        return null;
    }
}
