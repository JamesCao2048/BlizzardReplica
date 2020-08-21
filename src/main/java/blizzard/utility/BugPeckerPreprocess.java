package blizzard.utility;

import com.opencsv.CSVReader;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import blizzard.config.StaticData;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by Enzo Cotter on 2020/8/17.
 */
public class BugPeckerPreprocess {
    public static void main(String[]args)  throws Exception{
        //convertFSEProject("tomcat");
        indexAndMoveRepo("tomcat", false);
    }

    public static void classifyTest(String projectName) throws Exception {

    }

    public static void convertFSEProject(String projectName) throws Exception {
        List<List<String>> records = new ArrayList<List<String>>();
        try (CSVReader csvReader = new CSVReader(new FileReader(StaticData.BUGPECKER_DATA_DIR+"/"+projectName+".csv"));) {
            String[] values = null;
            while ((values = csvReader.readNext()) != null) {
                records.add(Arrays.asList(values));
            }
        }
        //generate bug raw
        String output_dir = StaticData.BR_RAW_DIR+"/"+projectName+"_fse/";
        for(int i =1; i< records.size(); i++){
            List<String> record = records.get(i);
            String fileName = record.get(0)+".txt";
            String fileContent = record.get(2)+"\n"+record.get(3);
            ContentWriter.writeContent(output_dir+fileName,fileContent);
        }
        //generate golden set
        String gold_output_dir = StaticData.GOLDSET_DIR+"/"+projectName+"_fse/";
        for(int i =1; i< records.size(); i++){
            List<String> record = records.get(i);
            String fileName = record.get(0)+".txt";
            // 这里先加入file level，后续加入method level
            ArrayList list = new ArrayList<String>();
            String fileStr = record.get(4).substring(1, record.get(4).length()-1);
            String[] files = fileStr.split("\\'");
            for(String file: files) {
                if(file.length() >= 3){
                    String item = combineFile(file.split("-")[0]);
                    if(!item.equals("") && !list.contains(item))
                        list.add(item);
                }
            }
            if(list.isEmpty())
                System.out.println("Empty fix file: " + fileName);
            ContentWriter.writeContent(gold_output_dir+fileName, list);
        }
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
            for (String fileName : fileNames) {
                String target = StaticData.CORPUS_DIR + "/" + repo + "/" + count + ".java";
                CommandUtil.run("cp " + fileName + " " + target);
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
                    curFiles.add(dictName + "/" + fileName);
            }
            if (fileList[i].isDirectory()) {
                String fileName = fileList[i].getName();
                listFilesRecursive(dictName + "/" + fileName, curFiles);
            }
        }
    }

    //单个文件解析为方法，按照方法索引(需要重新打包)
}
