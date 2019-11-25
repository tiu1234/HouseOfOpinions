package node.connection;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConnectionChannel {
    private final ConnectionEventListener eventListener;
    private final AsynchronousServerSocketChannel serverSocketChannel;
    private final ExecutorService executorService;
    private final AsynchronousChannelGroup group;

    public ConnectionChannel(int port, ConnectionEventListener eventListener) throws IOException {
        this.eventListener = eventListener;
        executorService = Executors.newSingleThreadExecutor();
        group = AsynchronousChannelGroup.withThreadPool(executorService);

        serverSocketChannel = AsynchronousServerSocketChannel.open(group).bind(new InetSocketAddress(port));

        serverSocketChannel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
            @Override
            public void completed(AsynchronousSocketChannel newConnected, Void attachment) {
                serverSocketChannel.accept(null, this);

                startRead(newConnected, eventListener);

                eventListener.newClient(newConnected);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                eventListener.newClientFail();
            }
        });
    }

    private static void startRead(AsynchronousSocketChannel sockChannel, ConnectionEventListener localEvent) {
        final ByteBuffer buffer = ByteBuffer.allocate(ByteBufferUtil.MAX_BUFFER_SIZE);

        sockChannel.read(buffer, sockChannel, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
            @Override
            public void completed(Integer result, AsynchronousSocketChannel socketChannel) {
                buffer.flip();
                if (result == -1) {
                    startRead(socketChannel, localEvent);
                    return;
                }

                localEvent.newRead(socketChannel, buffer);

                startRead(socketChannel, localEvent);
            }

            @Override
            public void failed(Throwable exc, AsynchronousSocketChannel socketChannel) {
                localEvent.newReadFail(socketChannel);
            }
        });
    }

    public static void startWrite(AsynchronousSocketChannel socketChannel, Queue<ByteBuffer> queue, ConnectionEventListener localEvent) {
        final ByteBuffer front = queue.poll();
        if (front == null) {
            return;
        }
        try {
            socketChannel.write(front, socketChannel, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
                @Override
                public void completed(Integer result, AsynchronousSocketChannel socketChannel) {
                    localEvent.writeComplete(socketChannel, result);

                    startWrite(socketChannel, queue, localEvent);
                }

                @Override
                public void failed(Throwable exc, AsynchronousSocketChannel socketChannel) {
                    localEvent.writeFail(socketChannel);
                }

            });
        } catch (WritePendingException e) {
            startWrite(socketChannel, queue, localEvent);
        }
    }

    public void connectToNew(String ip, int port) throws IOException {
        final AsynchronousSocketChannel clientSockChannel = AsynchronousSocketChannel.open(group);

        clientSockChannel.connect(new InetSocketAddress(ip, port), clientSockChannel, new CompletionHandler<Void, AsynchronousSocketChannel>() {
            @Override
            public void completed(Void result, AsynchronousSocketChannel socketChannel) {
                startRead(socketChannel, eventListener);

                eventListener.connComplete(socketChannel);
            }

            @Override
            public void failed(Throwable exc, AsynchronousSocketChannel socketChannel) {
                eventListener.connFail(socketChannel);
            }
        });
    }

    public void forceStop() throws IOException {
        serverSocketChannel.close();
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }
}
