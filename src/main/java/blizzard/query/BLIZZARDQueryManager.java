package blizzard.query;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import blizzard.utility.BugReportLoader;
import blizzard.utility.ContentLoader;
import blizzard.utility.ContentWriter;

public class BLIZZARDQueryManager {

	String repoName;
	String bugIDFile;
	HashMap<Integer, String> reportMap; //[bugid, bugContent(string)]
	HashMap<Integer, String> reportTitleMap; // title是content第一行元素
	HashMap<Integer, String> suggestedQueryMap; //[bugid, reformulate后推荐的query]
	ConcurrentHashMap<Integer, String> suggestedQueryCurMap;
	ExecutorService fixedThreadPool;
	ConcurrentHashMap<Integer, Boolean> brSTMap;
	ConcurrentHashMap<Integer, Boolean> brPEMap;
	ConcurrentHashMap<Integer, Boolean> brNLMap;

	public BLIZZARDQueryManager(String repoName, String bugIDFile) {
		this.repoName = repoName;
		this.bugIDFile = bugIDFile;  //里面每行是bugId(int)
		this.suggestedQueryMap = new HashMap<>();
		this.suggestedQueryCurMap = new ConcurrentHashMap<>();
		this.reportTitleMap = new HashMap<Integer, String>();
		this.reportMap = loadReportMap();
		this.reportTitleMap = loadReportTitleMap(reportMap);
		this.fixedThreadPool = Executors.newFixedThreadPool(12);
		this.brNLMap = new ConcurrentHashMap<>();
		this.brPEMap = new ConcurrentHashMap<>();
		this.brSTMap = new ConcurrentHashMap<>();
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
		fixedThreadPool.shutdown();
		try{
		fixedThreadPool.awaitTermination(5, TimeUnit.MINUTES);}
		catch (Exception e) {
			System.out.println(e);
		}
		while(!fixedThreadPool.isTerminated()){
			Thread.yield();
		}
		System.out.println("Concurrent Query Reformulation completed successfully :-)");
		return new HashMap<Integer, String>(this.suggestedQueryCurMap);
	}

	private class bugReportClassifyTask implements Runnable {
		int  bugID;
		String repoName;
		String reportContent;
		String title;
		ConcurrentHashMap<Integer, Boolean> stMap;
		ConcurrentHashMap<Integer, Boolean> peMap;
		ConcurrentHashMap<Integer, Boolean> nlMap;
		public bugReportClassifyTask(int bid, String rName, String content, String t,
									 ConcurrentHashMap<Integer, Boolean> st, ConcurrentHashMap<Integer, Boolean> pe, ConcurrentHashMap<Integer, Boolean> nl) {
			bugID = bid;
			repoName = rName;
			reportContent = content;
			title = t;
			stMap = st;
			peMap = pe;
			nlMap = nl;
		}

		public void run() {
			double start = System.currentTimeMillis();
			System.out.println("Execute ThreadName=" + Thread.currentThread().getName() + " bugid="+bugID);
			BLIZZARDQueryProvider provider = new BLIZZARDQueryProvider(this.repoName, this.bugID, this.title, this.reportContent);
			if(provider.reportGroup.equals("PE")) {
				this.peMap.put(bugID, true);
			}
			else if(provider.reportGroup.equals("NL")) {
				this.nlMap.put(bugID, true);
			}
			else if(provider.reportGroup.equals("ST")) {
				this.stMap.put(bugID, true);
			}
			double finish = System.currentTimeMillis();
			double elapsed  = (finish-start)/1000;
			System.out.println("Done: " + bugID + " Type: "+ provider.reportGroup +" Elapsed Time: " +elapsed +"s");
			System.out.println("Cur count: ST" + this.stMap.size() + " PE: "+ this.peMap.size() +" NL: " + this.nlMap.size());
		}
	}

	public void classifyBugReportConcur(String peFile, String stFile, String nlFile) {
		System.out.println("Concurrent Bug Report classify may take a few minutes. Please wait...");
		for (int bugID : this.reportMap.keySet()) {
			fixedThreadPool.execute(new bugReportClassifyTask(bugID, this.repoName, this.reportMap.get(bugID),
					this.reportTitleMap.get(bugID), this.brSTMap, this.brPEMap, this.brNLMap));
		}
		fixedThreadPool.shutdown();
//		try{
////			fixedThreadPool.awaitTermination(5, TimeUnit.MINUTES);}
////		catch (Exception e) {
////			System.out.println(e);
////		}
		while(!fixedThreadPool.isTerminated()){
			Thread.yield();
		}
		System.out.println("Concurrent Bug Report classify completed successfully :-)");
		System.out.println("Total count: ST" + this.brSTMap.size() + " PE: "+ this.brPEMap.size()+" NL: " + this.brNLMap.size());
		ContentWriter.writeContent(peFile, mapToList(this.brPEMap));
		ContentWriter.writeContent(stFile, mapToList(this.brSTMap));
		ContentWriter.writeContent(nlFile, mapToList(this.brNLMap));
	}

	public static List<String> mapToList(ConcurrentHashMap<Integer, Boolean> map) {
		List<String> res = new LinkedList<>();
		Enumeration<Integer> en = map.keys();
		while(en.hasMoreElements()){
			res.add(en.nextElement().toString());
		}
		return res;
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		String repoName = "tomcat";
		String bugIDFile = "/Users/junming/code/BLIZZARD-Replication-Package-ESEC-FSE2018/sample-input/sample-tomcat-bugs.txt";
		//System.out.println(new BLIZZARDQueryManager(repoName, bugIDFile).getSuggestedQueries());
		System.out.println(new BLIZZARDQueryManager(repoName, bugIDFile).getSuggestedQueriesConcur());
	}
}
