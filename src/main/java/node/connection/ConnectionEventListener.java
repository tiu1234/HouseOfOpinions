package node.connection;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;

public interface ConnectionEventListener {
    void newClient(AsynchronousSocketChannel socketChannel);

    void newClientFail();

    void connComplete(AsynchronousSocketChannel socketChannel);

    void connFail(AsynchronousSocketChannel socketChannel);

    void newRead(AsynchronousSocketChannel socketChannel, ByteBuffer buffer);

    void newReadFail(AsynchronousSocketChannel socketChannel);

    void writeComplete(AsynchronousSocketChannel socketChannel, Integer result);

    void writeFail(AsynchronousSocketChannel socketChannel);
}
