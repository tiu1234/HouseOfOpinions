package node.connection;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;

public class ByteBufferUtil {
    public static final int MAX_BUFFER_SIZE = 2048;

    public static Queue copyQueue(Queue<ByteBuffer> queue) {
        if (queue == null) {
            return null;
        }

        final Queue<ByteBuffer> newQueue = new ArrayDeque<>();

        for (ByteBuffer buffer : queue) {
            final int len = buffer.limit() - buffer.position();
            newQueue.offer(ByteBuffer.wrap(buffer.array(), buffer.position(), len));
        }

        return newQueue;
    }

    public static Queue<ByteBuffer> convertSerialToQueue(Serializable object) throws IOException {
        final Queue<ByteBuffer> queue = new ArrayDeque<>();

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final ObjectOutput out =  new ObjectOutputStream(bos);
        out.writeObject(object);
        out.flush();
        final byte[] byteData = bos.toByteArray();
        out.close();

        int curIndex = 0;
        int leftByteCount = byteData.length;

        while (leftByteCount > 0) {
            if (leftByteCount <= MAX_BUFFER_SIZE - 1) {
                final byte[] pack = new byte[leftByteCount + 1];
                pack[0] = 0;
                System.arraycopy(byteData, curIndex, pack, 1, leftByteCount);
                queue.offer(ByteBuffer.wrap(pack));
                leftByteCount = 0;
            } else {
                final byte[] pack = new byte[MAX_BUFFER_SIZE];
                pack[0] = 1;
                System.arraycopy(byteData, curIndex, pack, 1, MAX_BUFFER_SIZE - 1);
                queue.offer(ByteBuffer.wrap(pack));
                leftByteCount -= MAX_BUFFER_SIZE - 1;
                curIndex += MAX_BUFFER_SIZE - 1;
            }
        }

        return queue;
    }

    public static boolean hasNextPack(ByteBuffer buffer) {
        return buffer.limit() == ByteBufferUtil.MAX_BUFFER_SIZE && buffer.get(0) == 1;
    }

    public static Object convertQueueToObject(Queue<ByteBuffer> queue) throws IOException, ClassNotFoundException {
        int size = 0;
        for (ByteBuffer buffer : queue) {
            size += buffer.limit();
        }
        final ByteBuffer merged = ByteBuffer.allocate(size - queue.size());
        for (ByteBuffer buffer : queue) {
            merged.put(buffer.array(), 1, buffer.limit() - 1);
        }
        final ByteArrayInputStream bis = new ByteArrayInputStream(merged.array());
        final ObjectInput in = new ObjectInputStream(bis);
        return in.readObject();
    }
}
