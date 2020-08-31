package blizzard.utility;

import com.opencsv.CSVReader;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Array;
import java.util.*;

import blizzard.config.StaticData;

/**
 * Created by Enzo Cotter on 2020/8/17.
 */
public class BugPeckerPreprocess {
    static String BeforebreakChars = ".?";
    static String AfterbreakChars = "-<";
    static int maxLine = 1000;
    static int maxFile = 3000;

    public static void main(String[]args)  throws Exception{
        //convertFSEProjectBlizzard("jdt", true);
        indexAndMoveRepo("jdt", true);
    }

    public static void convertFSEProjectBlizzard(String projectName, boolean forMethod) throws Exception {
        List<List<String>> records = new ArrayList<List<String>>();
        try (CSVReader csvReader = new CSVReader(new FileReader(StaticData.BUGPECKER_DATA_DIR + "/" + projectName + "_blizzard" + ".csv"));) {
            String[] values = null;
            try {
                while ((values = csvReader.readNext()) != null) {
//                if(values[0].length() < 5)
//                    System.out.println(values[0]);
//                else
//                    System.out.println("error bug id");
//                if(values[1].length() > 9)
//                    System.out.println(values[1].substring(4,10));
                    records.add(Arrays.asList(values));
                    if(values.length < 9)
                        System.out.println(values);
                }
            }
            catch (Exception e) {
                List<String> v = records.get(records.size()-2);
                System.out.println(e);
            }
        }
        //generateBugRaw(records, projectName);
        generateGoldenSet(records, projectName, forMethod);
    }

    public static void generateBugRaw(List<List<String>> records, String repo) {
        String output_dir = StaticData.BR_RAW_DIR + "/" + repo + "/";

        for (int i = 1; i < records.size(); i++) {
            List<String> record = records.get(i);
            String fileName = record.get(0) + ".txt";
            String fileContent = "";
            if (record.get(2).isEmpty()) {
                System.out.println("Empty content: " + i);
                fileContent = record.get(1);
            } else {
                fileContent = record.get(1) + "\n" +
                        removeWhiteSpaceBeforeLine(removeDuplciateLineBreak(trimFile(record.get(2))));
            }
            ContentWriter.writeContent(output_dir + fileName, fileContent);
        }
    }

    public static void generateGoldenSet(List<List<String>> records, String repo, boolean forMethod) {

        String gold_output_dir = StaticData.GOLDSET_DIR+"/"+repo+"/";
        if(forMethod)
            gold_output_dir = StaticData.GOLDSET_DIR+"/"+repo+"m/";
        for(int i =1; i< records.size(); i++){
            List<String> record = records.get(i);
            String fileName = record.get(0)+".txt";
            // 这里先加入file level，后续加入method level
            ArrayList<String> list = new ArrayList<String>();
            String fileStr = record.get(3).substring(1, record.get(3).length()-1);
            String[] files = fileStr.split("\\'");
            for(String file: files) {
                if(file.length() >= 3){
                    if(!forMethod){
                        String item = combineFile(file.split("-")[0]);
                        if(!item.equals("") && !list.contains(item))
                            list.add(item.substring(13));
                    }
                    else {
                        if(file.length()>5){
                            if(repo.contains("swt"))
                                list.add(file.substring(29));
                            else if(repo.contains("birt"))
                                list.add(file.substring(4));
                            else if(repo.contains("jdt"))
                                list.add(file.substring(14));
                            else
                                list.add(file);
                        }
                    }
                }
            }
            if(list.isEmpty())
                System.out.println("Empty fix file: " + fileName);
            Set<String> set = new HashSet<String>(list);
            ContentWriter.writeContent(gold_output_dir+fileName, new ArrayList<>(set));
        }
    }

    public static String removeDuplciateLineBreak(String s) {
        StringBuilder sb = new StringBuilder();
        char[] chars = s.toCharArray();
        sb.append(chars[0]);
        for(int i =1; i< chars.length; i++){
            if(!(chars[i] == '\n' && chars[i-1] == '\n'))
                sb.append(chars[i]);
        }
        return sb.toString();
    }

    public static String removeWhiteSpaceBeforeLine(String s) {
        StringBuilder sb = new StringBuilder();
        String[] ss = s.split("\n");
        for(String st: ss){
            sb.append(st.trim());
            sb.append('\n');
        }
        return sb.substring(0, sb.length()-1);
    }

    public static String addLineBreak(String s) {
        StringBuilder sb = new StringBuilder();
        char[] chars = s.toCharArray();
        for(int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if(AfterbreakChars.contains(String.valueOf(c))&& i>=1 &&chars[i-1] == ' ')
                sb.append("\n");
            if(!(c == ' ' && i >=1 && BeforebreakChars.contains(String.valueOf(chars[i-1]))))
                sb.append(c);
            if(BeforebreakChars.contains(String.valueOf(c)) && i+1 < chars.length
                    && chars[i+1] == ' ' && !Character.isDigit(chars[i-1]))
                sb.append("\n");
        }
        return trimFile(sb.toString());
    }

    public static String trimFile(String s) {
        String[] lines = s.split("\n");
        StringBuilder res = new StringBuilder();
        for(String line: lines){
            int count = 0;
            while(count < line.length()){
                res.append(line.substring(count, Math.min(count+maxLine,line.length())));
                count += maxLine;
                if(count < line.length())
                    res.append('\n');
            }
            res.append('\n');
        }
        if(res.charAt(res.length()-1)== '\n')
            res.deleteCharAt(res.length()-1);
        return res.toString().substring(0, Math.min(maxFile, res.length()));
    }

    // 后续要实现一个combineMethod
    public static String combineFile(String token) {
        StringBuilder sb = new StringBuilder();
        String[] tokens = token.split("\\.");
        for(int i =1; i< tokens.length; i++) {
            sb.append(tokens[i]);
            if(i < tokens.length-1)
                sb.append("/");
        }
        sb.append(".java");
        return sb.toString();
    }

    public static void indexAndMoveRepo(String repo, boolean needCopy) throws Exception {
        String dict = StaticData.REPO_DIR + "/" + repo;
        List<String> fileNames = new LinkedList<>();
        listFilesRecursive(dict, fileNames);
        String indexMapFile = StaticData.CORPUS_INDEX_KEY_MAPPING + "/" + repo + ".ckeys";
        ContentWriter.writeContentIndexList(indexMapFile, fileNames, 1);
        int count = 1;
        if(needCopy) {
            //dot slice move
            for (String fileName : fileNames) {
                String target = StaticData.CORPUS_DIR + "/" + repo + "/" + count + ".java";
                CommandUtil.run(new String[]{"cp", fileName, target});
                count++;
            }
        }
    }

    //文件到index编号处理，查看(并验证有多少少的)
    public static void listFilesRecursive(String dictName, List<String> curFiles) {
        File file = new File(dictName);
        File[] fileList = file.listFiles();
        for (int i = 0; i < fileList.length; i++) {
            if (fileList[i].isFile()) {
                String fileName = fileList[i].getName();
                //System.out.println("文件：" + fileName);
                if(fileName.endsWith(".java"))
                    curFiles.add(dotNameToSlice(dictName + "/" + fileName));
            }
            if (fileList[i].isDirectory()) {
                String fileName = fileList[i].getName();
                listFilesRecursive(dictName + "/" + fileName, curFiles);
            }
        }
    }
    public static String dotNameToSlice(String fileName) {
        String[] files = fileName.split("/");
        StringBuilder sb = new StringBuilder();
        for(String file: files) {
            String[] names = file.split("\\.");
            if (names.length > 0) {
                for (String name : names) {
                    sb.append(name);
                    sb.append("/");
                }
            } else {
                sb.append(file);
                sb.append("/");
            }
        }
        sb.deleteCharAt(sb.length()-1);
        return sb.substring(0, sb.length()-5) + ".java";
    }

    //单个文件解析为方法，按照方法索引(需要重新打包)
}
