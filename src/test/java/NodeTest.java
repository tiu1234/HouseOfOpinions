import node.Node;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;

class NodeTest {
    private static final int NORMAL_NUM = 20;
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
        for (int i = 0; i < NORMAL_NUM; i++) {
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

        for (Node node : seedNodes) {
            assert node.getStablePeers().size() == 0;
            assert node.getIpPeers().size() == 0;
            assert node.getUnknownPeers().size() == 0;
            assert node.getRegisteredIps().size() == NORMAL_NUM;
        }

        for (Node node : normalNodes) {
            assert node.getStablePeers().size() == NORMAL_NUM - 1;
            assert node.getIpPeers().size() == NORMAL_NUM - 1;
            assert node.getUnknownPeers().size() == 0;
            assert node.getRegisteredIps().size() == 0;
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