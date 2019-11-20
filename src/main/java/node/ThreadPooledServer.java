package node;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadPooledServer implements Runnable {

    protected final Node curNode;
    protected final String serverIP;
    protected final ExecutorService threadPool =
            Executors.newFixedThreadPool(10);
    protected int serverPort = 1050;
    protected ServerSocket serverSocket = null;
    protected boolean isStopped = false;
    protected Thread runningThread = null;

    public ThreadPooledServer(String ip, Node node) {
        curNode = node;
        serverIP = ip;
    }

    public ThreadPooledServer(String ip, int port, Node node) {
        serverPort = port;
        serverIP = ip;
        curNode = node;
    }

    @Override
    public void run() {
        synchronized (this) {
            this.runningThread = Thread.currentThread();
        }
        openServerSocket();
        while (!isStopped()) {
            Socket clientSocket = null;
            try {
                clientSocket = this.serverSocket.accept();
            } catch (IOException e) {
                if (isStopped()) {
                    System.out.println("Prepare to stop the server.");
                    break;
                }
                throw new RuntimeException(
                        "Error accepting client connection", e);
            }
            try {
                this.threadPool.execute(
                        new ConnectionRunnable(clientSocket, null, curNode));
            } catch (IOException e) {
                System.out.println("Failed to start thread");
            }
        }
        this.threadPool.shutdown();
        System.out.println("Server Stopped.");
    }

    private synchronized boolean isStopped() {
        return this.isStopped;
    }

    public synchronized void stop() {
        this.isStopped = true;
        try {
            this.serverSocket.close();
        } catch (IOException e) {
            throw new RuntimeException("Error closing server", e);
        }
    }

    private void openServerSocket() {
        try {
            this.serverSocket = new ServerSocket(this.serverPort);
        } catch (IOException e) {
            throw new RuntimeException("Cannot open port " + this.serverPort, e);
        }
    }

    public int getServerPort() {
        return serverPort;
    }

    public String getServerIp() {
        return serverIP;
    }
}
