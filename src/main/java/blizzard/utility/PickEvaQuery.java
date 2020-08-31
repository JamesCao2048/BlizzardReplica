package blizzard.utility;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Enzo Cotter on 2020/8/29.
 */
public class PickEvaQuery {
    static double skipRatio = 0.3;

    public static void main(String[] args) {
        String bugFile = "/Users/junming/code/BLIZZARD-Replication-Package-ESEC-FSE2018/sample-input/sample-birt-no-bias-bugs.txt";
        String queryFile = "/Users/junming/code/BLIZZARD-Replication-Package-ESEC-FSE2018/sample-input/sample-birt-query.txt";
        String saveFile = "/Users/junming/code/BLIZZARD-Replication-Package-ESEC-FSE2018/sample-input/eval-birt-no-bias-query.txt";
        PickQuery(bugFile, queryFile, saveFile);
    }

    public static void PickQuery(String bugFile, String queryFile, String saveFile) {
        String[] bugs = ContentLoader.getAllLines(bugFile);
        String[] filteredBugs = Arrays.copyOfRange(bugs, (int)(bugs.length*skipRatio), bugs.length);
        Map<String, String> queryMap = ContentLoader.loadMap(queryFile, "\t");
        Map<String, String> resMap = new HashMap<>();
        for(String bug: filteredBugs) {
            if(queryMap.get(bug) != null)
                resMap.put(bug, queryMap.get(bug));
        }
        ContentWriter.writeMap(saveFile, resMap, "\t");
    }
}
