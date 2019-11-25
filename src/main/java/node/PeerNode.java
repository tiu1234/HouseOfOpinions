package node;

import node.connection.ByteBufferUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.ArrayDeque;
import java.util.Queue;

public class PeerNode extends NodeAttributes {
    private final AsynchronousSocketChannel socketChannel;
    private final Queue<ByteBuffer> queue;
    private long timeOut;
    private long connectionTime;

    public PeerNode(AsynchronousSocketChannel socketChannel, String ip, int port, long timeOut) {
        super(ip, port);
        this.socketChannel = socketChannel;
        connectionTime = System.currentTimeMillis();
        this.timeOut = timeOut;
        queue = new ArrayDeque<>();
    }

    public boolean consumeData(ByteBuffer buffer) throws IOException {
        if (queue.size() >= 20) {
            socketChannel.close();
            return true;
        }
        queue.offer(buffer);
        return !ByteBufferUtil.hasNextPack(buffer);
    }

    public AsynchronousSocketChannel getSocketChannel() {
        return socketChannel;
    }

    public long getConnectionTime() {
        return connectionTime;
    }

    public void updateConnectionTime() {
        this.connectionTime = System.currentTimeMillis();
    }

    public boolean isTimedOut() {
        long curTime = System.currentTimeMillis();
        if (curTime - connectionTime >= timeOut) {
            return true;
        }
        return false;
    }

    public long getTimeOut() {
        return timeOut;
    }

    public void setTimeOut(long timeOut) {
        this.timeOut = timeOut;
    }

    public Queue<ByteBuffer> getQueue() {
        return queue;
    }
}
