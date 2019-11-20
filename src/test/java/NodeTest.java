import node.Node;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;

class NodeTest {
    private static ArrayList<Node> seedNodes;
    private static ArrayList<Node> normalNodes;

    @BeforeAll
    static void initTest() {
        seedNodes = new ArrayList<>();
        seedNodes.add(new Node());
        for (Node node : seedNodes) {
            node.start();
        }
    }

    @Test
    void newNodeJoinTest() {
        normalNodes = new ArrayList<>();
        normalNodes.add(new Node(1234));
        normalNodes.add(new Node(1235));
        normalNodes.add(new Node(1236));

        for (Node node : normalNodes) {
            Thread thread = new Thread(node.getServerIp() + ":" + node.getServerPort()) {
                public void run() {
                    node.start();
                }
            };

            thread.start();
        }

        try {
            Thread.sleep(5 * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    void badConnectionTest() {
        try {
            Socket serverSocket = new Socket("localhost", 1050);
            DataOutputStream outputStream = new DataOutputStream(serverSocket.getOutputStream());
            outputStream.writeUTF("My IP:10.0.0.5:6666");
            outputStream.writeUTF("My IP:10.0.0.5:asd");
        } catch (IOException e) {
            System.out.println("Failed to connect!");
        }

    }

    @AfterAll
    static void shutDown() {
        for (Node node : seedNodes) {
            node.stop();
        }
        for (Node node : normalNodes) {
            node.stop();
        }
    }
}