import node.Node;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

class NodeTest {
    private static ArrayList<Node> seedNodes;
    private static ArrayList<Node> normalNodes;

    @BeforeAll
    static void InitTest() {
        seedNodes = new ArrayList<>();
        seedNodes.add(new Node());
        for (Node node : seedNodes) {
            node.start();
        }
    }

    @Test
    void NewNodeJoinTest() {
        normalNodes = new ArrayList<>();
        normalNodes.add(new Node(1234));
        normalNodes.add(new Node(1235));
        normalNodes.add(new Node(1236));

        for (Node node : normalNodes) {
            Thread thread = new Thread(node.getServerIp() + ":" + node.getServerPort()) {
                public void run(){
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