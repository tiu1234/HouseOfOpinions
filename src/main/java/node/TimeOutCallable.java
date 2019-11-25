package node;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

public class TimeOutCallable implements Callable<Object> {
    public static final long STABLE_TIME_OUT = 120 * 1000;
    public static final long UNKNOWN_TIME_OUT = 60 * 1000;
    private final ExecutorService executorService;
    private final PeerNode peer;

    public TimeOutCallable (ExecutorService executorService, PeerNode peer) {
        this.executorService = executorService;
        this.peer = peer;
    }

    @Override
    public Object call() throws Exception {
        if (peer.isTimedOut()) {
            peer.getSocketChannel().close();
        } else {
            executorService.submit(new TimeOutCallable(executorService, peer));
        }

        return null;
    }
}
