import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GraphUtil {

    private GraphUtil() {

    }

    public static List<List<Integer>> readGraphMatrixAsAdj(File file) {
        Boolean[][] adjMatrix = readGraphMatrix(file);
        int n = adjMatrix.length;
        List<List<Integer>> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            List<Integer> adjList = new ArrayList<>();
            for (int j = 0; j < n; j++) {
                if (adjMatrix[i][j]) {
                    adjList.add(j);
                }
            }
            result.add(adjList);
        }
        return result;
    }

    public static Boolean[][] readGraphMatrix(File file) {
        try {
            List<String[]> lines = readLines(file);
            int n = lines.size();
            Boolean[][] graph = new Boolean[n][];
            int i = 0;
            for (String[] line : lines) {
                graph[i] = new Boolean[n];
                for (int j = 0; j < n; j++) {
                    graph[i][j] = Integer.parseInt(line[j]) != 0;
                }
                i++;
            }
            return graph;
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read graph from file '" + file.getAbsolutePath() + "'");
        }
    }

    public static Double[][] readWeightedGraphMatrix(File file) {
        try {
            List<String[]> lines = readLines(file);
            int n = lines.size();
            Double[][] graph = new Double[n][];
            int i = 0;
            for (String[] line : lines) {
                graph[i] = new Double[n];
                for (int j = 0; j < n; j++) {
                    graph[i][j] = Double.parseDouble(line[j]);
                }
                i++;
            }
            return graph;
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read weighted graph from file '" + file.getAbsolutePath() + "'");
        }
    }

    private static List<String[]> readLines(File file) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(file));
        int n = Integer.parseInt(reader.readLine());
        List<String[]> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String[] line = reader.readLine().split(" ");
            result.add(line);
        }
        return result;

    }

}
