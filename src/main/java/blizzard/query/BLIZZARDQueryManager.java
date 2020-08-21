package blizzard.query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import blizzard.utility.BugReportLoader;
import blizzard.utility.ContentLoader;

public class BLIZZARDQueryManager {

	String repoName;
	String bugIDFile;
	HashMap<Integer, String> reportMap; //[bugid, bugContent(string)]
	HashMap<Integer, String> reportTitleMap; // title是content第一行元素
	HashMap<Integer, String> suggestedQueryMap; //[bugid, reformulate后推荐的query]
	ConcurrentHashMap<Integer, String> suggestedQueryCurMap;
	ExecutorService fixedThreadPool;

	public BLIZZARDQueryManager(String repoName, String bugIDFile) {
		this.repoName = repoName;
		this.bugIDFile = bugIDFile;  //里面每行是bugId(int)
		this.suggestedQueryMap = new HashMap<>();
		this.suggestedQueryCurMap = new ConcurrentHashMap<>();
		this.reportTitleMap = new HashMap<Integer, String>();
		this.reportMap = loadReportMap();
		this.reportTitleMap = loadReportTitleMap(reportMap);
		this.fixedThreadPool = Executors.newFixedThreadPool(12);
	}

	protected String extractTitle(String reportContent) {
		String title = new String();
		String[] lines = reportContent.split("\n");
		if (lines.length > 0) {
			title = lines[0];
		}
		return title;
	}

	protected HashMap<Integer, String> loadReportMap() {
		ArrayList<Integer> bugs = ContentLoader.getAllLinesInt(this.bugIDFile);
		HashMap<Integer, String> reportMap = new HashMap<>();
		for (int bugID : bugs) {
			String reportContent = BugReportLoader.loadBugReport(repoName, bugID);
			reportMap.put(bugID, reportContent);
		}
		return reportMap;
	}

	protected HashMap<Integer, String> loadReportTitleMap(HashMap<Integer, String> reportMap) {
		HashMap<Integer, String> titleMap = new HashMap<Integer, String>();
		for (int bugID : reportMap.keySet()) {
			String reportContent = reportMap.get(bugID);
			String title = extractTitle(reportContent);
			titleMap.put(bugID, title);
		}
		return titleMap;
	}

	public HashMap<Integer, String> getSuggestedQueries() {
		System.out.println("Query reformulation may take a few minutes. Please wait...");
		for (int bugID : this.reportMap.keySet()) {
			String reportContent = this.reportMap.get(bugID);
			String title=this.reportTitleMap.get(bugID);
			BLIZZARDQueryProvider provider = new BLIZZARDQueryProvider(this.repoName, bugID, title, reportContent);
			String suggestedQuery = provider.provideBLIZZARDQuery();
			this.suggestedQueryMap.put(bugID, suggestedQuery);
		}
		System.out.println("Query Reformulation completed successfully :-)");
		return this.suggestedQueryMap;
	}

	private class queryReformTask implements Runnable {
		int  bugID;
		String repoName;
		String reportContent;
		String title;
		ConcurrentHashMap<Integer, String> curMap;
		public queryReformTask(int bid, String rName, String content, String t, ConcurrentHashMap map) {
			bugID = bid;
			repoName = rName;
			reportContent = content;
			title = t;
			curMap = map;
		}

		public void run() {
			double start = System.currentTimeMillis();
			System.out.println("Execute ThreadName=" + Thread.currentThread().getName() + " bugid="+bugID);
			BLIZZARDQueryProvider provider = new BLIZZARDQueryProvider(this.repoName, this.bugID, this.title, this.reportContent);
			String suggestedQuery = provider.provideBLIZZARDQuery();
			double finish = System.currentTimeMillis();
			double elapsed  = (finish-start)/1000;
			System.out.println("Done: " + bugID + " CurMapSize: "+ this.curMap.size() +" Elapsed Time: " +elapsed +"s");
			this.curMap.put(bugID, suggestedQuery);
		}
	}

	public HashMap<Integer, String> getSuggestedQueriesConcur() {
		System.out.println("Concurrent Query reformulation may take a few minutes. Please wait...");
		for (int bugID : this.reportMap.keySet()) {
			fixedThreadPool.execute(new queryReformTask(bugID, this.repoName, this.reportMap.get(bugID), this.reportTitleMap.get(bugID), this.suggestedQueryCurMap));
		}
		try{
			fixedThreadPool.awaitTermination(1, TimeUnit.DAYS);}
		catch (Exception e) {
			System.out.println("Main Terminate: "+ e);
		}
		System.out.println("Concurrent Query Reformulation completed successfully :-)");
		return new HashMap<Integer, String>(this.suggestedQueryCurMap);
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		String repoName = "tomcat";
		String bugIDFile = "/Users/junming/code/BLIZZARD-Replication-Package-ESEC-FSE2018/sample-input/sample-tomcat-bugs.txt";
		//System.out.println(new BLIZZARDQueryManager(repoName, bugIDFile).getSuggestedQueries());
		System.out.println(new BLIZZARDQueryManager(repoName, bugIDFile).getSuggestedQueriesConcur());
	}
}
