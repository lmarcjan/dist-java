import visidia.simulation.process.algorithm.Algorithm;
import visidia.simulation.process.messages.IntegerMessage;
import visidia.simulation.process.messages.MessageType;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class FloydWarshall extends Algorithm {

    private static final Double INF = 1.0e9;

    private Double[][] weight;

    public FloydWarshall() {
        weight = GraphUtil.readWeightedGraphMatrix(new File(FloydWarshall.class.getResource("graph-floyd-warshall-example.graph").getFile()));
    }


    @Override
    public Object clone() {
        return new FloydWarshall();
    }

    private Map<Integer, Integer> doorMap = new HashMap<Integer, Integer>();
    private Map<Integer, Integer> doorMapInv = new HashMap<Integer, Integer>();
    private Double[] len;
    private Integer[] parent;

    private static class InitMsg extends IntegerMessage {

        public InitMsg(int from) {
            super(from, new MessageType("init", true, java.awt.Color.gray));
        }

        @Override
        public Object clone() {
            return new InitMsg(getData());
        }

        @Override
        public String toString() {
            return "InitMsg [from=" + getData() + "]";
        }

    }

    private static abstract class TreeMsg extends IntegerMessage {

        public TreeMsg(int pivot, MessageType msgType) {
            super(pivot, msgType);
        }
    }

    private static class InTreeMsg extends TreeMsg {

        public InTreeMsg(int pivot) {
            super(pivot, new MessageType("in_tree", true, java.awt.Color.green));
        }

        @Override
        public Object clone() {
            return new InTreeMsg(getData());
        }

        @Override
        public String toString() {
            return "InTreeMsg [pivot=" + getData() + "]";
        }
    }

    private static class NotInTreeMsg extends TreeMsg {

        public NotInTreeMsg(int pivot) {
            super(pivot, new MessageType("not_in_tree", true, java.awt.Color.yellow));
        }

        @Override
        public Object clone() {
            return new NotInTreeMsg(getData());
        }

        @Override
        public String toString() {
            return "NotInTreeMsg [pivot=" + getData() + "]";
        }
    }

    private static class PivLenMsg extends IntegerMessage {

        private Double[] pivotRow;

        public PivLenMsg(int pivot, Double[] pivotRow) {
            super(pivot, new MessageType("piv_len", true, java.awt.Color.red));
            this.pivotRow = pivotRow;
        }

        @Override
        public Object clone() {
            return new PivLenMsg(getData(), pivotRow);
        }

        @Override
        public String toString() {
            return "PivLenMsg [pivot=" + getData() + ", pivotRow=" + Arrays.toString(pivotRow) + "]";
        }

    }


    @Override
    public void init() {
        int n = getNetSize();
        len = new Double[n];
        for (int i = 0; i < n; i++) len[i] = INF;
        len[getId()] = 0.0;
        parent = new Integer[n];
        parent[getId()] = getId();
        sendAll(new InitMsg(getId()));
        int nbNeighbors = getArity();
        for (int neighborDoor = 0; neighborDoor < nbNeighbors; neighborDoor++) {
            InitMsg initMsg = (InitMsg) receiveFrom(neighborDoor);
            int nbId = initMsg.getData();
            doorMap.put(neighborDoor, nbId);
            doorMapInv.put(nbId, neighborDoor);
            len[nbId] = weight[getId()][nbId];
            parent[nbId] = nbId;

        }
        for (int pivot = 0; pivot < n; pivot++) {
            System.out.println("Doing at " + getId() + ", pivot " + pivot);
            for (Integer neighborDoor = 0; neighborDoor < nbNeighbors; neighborDoor++) {
                int nbId = doorMap.get(neighborDoor);
                if (parent[pivot] != null && parent[pivot].equals(nbId)) {
                    sendTo(neighborDoor, new InTreeMsg(pivot));
                } else {
                    sendTo(neighborDoor, new NotInTreeMsg(pivot));
                }
            }
            Boolean[] nbInTree = new Boolean[nbNeighbors];
            for (Integer neighborDoor = 0; neighborDoor < nbNeighbors; neighborDoor++) {
                TreeMsg treeMsg = (TreeMsg) receiveFrom(neighborDoor);
                if (treeMsg.getData() != pivot) {
                    throw new IllegalStateException("Invalid treeMsg-> " + treeMsg);//just ensuring sync invariant
                }
                if (treeMsg instanceof InTreeMsg) {
                    nbInTree[neighborDoor] = true;
                } else if (treeMsg instanceof NotInTreeMsg) {
                    nbInTree[neighborDoor] = false;
                }
            }
            if (len[pivot] != INF) { // If in pivot tree
                // Get pivot's distance from parent
                Double[] pivotRow = null;
                if (getId() != pivot) {
                    System.out.println("Waiting at " + getId() + " piv distance from " + parent[pivot]);
                    PivLenMsg pivLenMsg = (PivLenMsg) receiveFrom(doorMapInv.get(parent[pivot]));
                    System.out.println("Received at " + getId() + " piv distance");
                    pivotRow = pivLenMsg.pivotRow;
                } else {
                    pivotRow = len;
                }
                // Distribute pivot's distance to children
                for (Integer neighborDoor = 0; neighborDoor < nbNeighbors; neighborDoor++) {
                    if (nbInTree[neighborDoor]) {
                        sendTo(neighborDoor, new PivLenMsg(pivot, pivotRow));
                    }
                }
                // Update distances to all nodes using pivot's distance
                for (int t = 0; t < n; t++) {
                    if (len[pivot] + pivotRow[t] < len[t]) {
                        len[t] = len[pivot] + pivotRow[t];
                        parent[t] = parent[pivot];
                        System.out.println("Updated at " + getId() + ", t=" + t + ", len=" + len[t] + ", p=" + parent[t]);
                    }
                }
            }
        }

        System.out.println("Result at " + getId() + " is:");
        for (int t = 0; t < n; t++) {
            System.out.println("t=" + t + ", len=" + len[t] + ", p=" + parent[t]);
        }

    }
}
