import node.Node;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;

class NodeTest {
    private static ArrayList<Node> seedNodes;
    private static ArrayList<Node> normalNodes;

    @BeforeAll
    static void initTest() throws IOException {
        seedNodes = new ArrayList<>();
        seedNodes.add(new Node());
        for (Node node : seedNodes) {
            node.start();
        }
    }

    @Test
    void newNodeJoinTest() throws IOException {
        normalNodes = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            normalNodes.add(new Node(1230 + i));
        }

        for (Node node : normalNodes) {
            node.start();
        }

        try {
            Thread.sleep(10 * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @AfterAll
    static void shutDown() throws IOException {
        for (Node node : seedNodes) {
            node.stop();
        }
        for (Node node : normalNodes) {
            node.stop();
        }
    }
}