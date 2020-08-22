package blizzard.query.evaluation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import blizzard.lucenecheck.ClassResultRankMgr;
import blizzard.lucenecheck.LuceneSearcher;
import blizzard.utility.ContentLoader;
import blizzard.utility.GoldsetLoader;

public class BLIZZARDResultProvider {

	String repoName;
	int TOPK;
	String resultKey;
	String[] resultKeys;
	String queryFile;
	public HashMap<Integer, String> queryMap;
	double TopkAcc;
	double Top1Acc;
	double Top1Count;
	double Top5Acc;
	double Top5Count;
	double Top10Acc;
	double Top10Count;
	double Top20Acc;
	double Top20Count;
	double Top50Acc;
	double Top50Count;
	double mapK;
	double mrrK;
	double mrK;

	public BLIZZARDResultProvider(String repoName, int TOPK, String queryFile) {
		this.repoName = repoName;
		this.TOPK = TOPK;
		this.queryMap = extractQueryMap(queryFile);
	}

	protected String extractQuery(String line) {
		String[] words = line.split("\\s+");
		String temp = new String();
		for (int i = 1; i < words.length; i++) {
			temp += words[i] + "\t";
		}
		return temp.trim();
	}

	protected HashMap<Integer, String> extractQueryMap(String queryFile) {
		// extracting queries
		ArrayList<String> lines = ContentLoader.getAllLinesList(queryFile);
		HashMap<Integer, String> queryMap = new HashMap<>();
		for (String line : lines) {
			String query = extractQuery(line);
			int bugID = Integer.parseInt(line.split("\\s+")[0].trim());
			queryMap.put(bugID, query);
		}
		return queryMap;
	}

	protected double getRR(int firstGoldIndex) {
		if (firstGoldIndex <= 0)
			return 0;
		return 1.0 / firstGoldIndex;
	}

	protected double getRR(ArrayList<Integer> foundIndices) {
		if (foundIndices.isEmpty())
			return 0;
		double min = 10000;
		for (int index : foundIndices) {
			if (index > 0) {
				if (index < min) {
					min = index;
				}
			} else {
				return 0;
			}
		}
		return 1 / min;
	}

	protected double getAP(ArrayList<Integer> foundIndices) {
		// calculating the average precision
		int indexcount = 0;
		double sumPrecision = 0;
		if (foundIndices.isEmpty())
			return 0;
		HashSet<Integer> uniquesIndices=new HashSet<Integer>(foundIndices);
		for (int index : uniquesIndices) {
			indexcount++;
			double precision = (double) indexcount / index;
			sumPrecision += precision;
		}
		return sumPrecision / indexcount;
	}

	protected void setTopK(ArrayList<Integer> foundIndices) {
		HashSet<Integer> uniquesIndices=new HashSet<Integer>(foundIndices);
		for (int index : uniquesIndices) {
			if(index == 1)
				this.Top1Count++;
			if(index <= 5)
				this.Top5Count++;
			if(index <= 10)
				this.Top10Count++;
			if(index <= 20)
				this.Top20Count++;
			if(index <= 50)
				this.Top50Count++;
		}
	}

	protected double getRecall(ArrayList<Integer> foundIndices,
			ArrayList<String> goldset) {
		// calculating recall
		return (double) foundIndices.size() / goldset.size();
	}

	public HashMap<Integer, ArrayList<String>> collectBLIZZARDResults() {
		// collect BLIZZARD results
		System.out.println("Collection of results started. Please wait..");
		HashMap<Integer, ArrayList<String>> resultMap = new HashMap<>();
		for (int bugID : this.queryMap.keySet()) {
			String singleQuery = this.queryMap.get(bugID);
			LuceneSearcher searcher = new LuceneSearcher(bugID, repoName,
					singleQuery);
			ArrayList<String> ranked = searcher.performVSMSearchList(false);
			resultMap.put(bugID, ranked);
		}
		System.out.println("Localization results collected successfully :-)");
		return resultMap;
	}

	public HashMap<Integer, ArrayList<String>> collectBLIZZARDResultsAll() {
		// collect BLIZZARD results
		System.out.println("Collection of results started. Please wait..");
		HashMap<Integer, ArrayList<String>> resultMap = new HashMap<>();
		for (int bugID : this.queryMap.keySet()) {
			String singleQuery = this.queryMap.get(bugID);
			LuceneSearcher searcher = new LuceneSearcher(bugID, repoName,
					singleQuery);
			ArrayList<String> ranked = searcher.performVSMSearchList(true);
			resultMap.put(bugID, ranked);
		}
		System.out.println("Localization results collected successfully :-)");
		return resultMap;
	}
	
	public void calculateBLIZZARDPerformance(
			HashMap<Integer, ArrayList<String>> resultMap) {
		double sumRR = 0;
		double sumAP = 0;
		double sumAcc = 0;
		System.out.println("Bug Localization Performance:");

		for (int bugID : resultMap.keySet()) {
			ArrayList<String> results = resultMap.get(bugID);
			ArrayList<String> goldset = GoldsetLoader.goldsetLoader(repoName,
					bugID);
			ClassResultRankMgr clsRankMgr = new ClassResultRankMgr(repoName,
					results, goldset);
			ArrayList<Integer> indices = clsRankMgr.getCorrectRanksDotted(TOPK);

			double rr = 0, ap = 0, rec = 0;

			if (!indices.isEmpty()) {
				rr = getRR(indices);
				if (rr > 0) {
					sumRR += rr;
				}
				ap = getAP(indices);
				if (ap > 0) {
					sumAP += ap;
					sumAcc++;
				}
				setTopK(indices);
			}
		}

		// now calculate the mean performance
		this.TopkAcc = (double) sumAcc / resultMap.size();
		this.mrrK = sumRR / resultMap.size();
		this.mapK = sumAP / resultMap.size();
		this.Top1Acc = this.Top1Count / resultMap.size();
		this.Top5Acc = this.Top5Count / resultMap.size();
		this.Top10Acc = this.Top10Count / resultMap.size();
		this.Top20Acc = this.Top20Count / resultMap.size();
		this.Top50Acc = this.Top50Count / resultMap.size();

		// System.out.println(repoName + " " + this.TopkAcc);

		System.out.println("System: " + repoName);
		System.out.println("Hit@" + TOPK + " Accuracy: " + this.getTopKAcc());
		System.out.println("Hit@" + 1 + " Accuracy: " + this.Top1Acc);
		System.out.println("Hit@" + 5 + " Accuracy: " + this.Top5Acc);
		System.out.println("Hit@" + 10 + " Accuracy: " + this.Top10Acc);
		System.out.println("Hit@" + 20 + " Accuracy: " + this.Top20Acc);
		System.out.println("Hit@" + 50 + " Accuracy: " + this.Top50Acc);
		System.out.println("MRR@" + TOPK + ": " + this.getMRRK());
		System.out.println("MAP@" + TOPK + ": " + this.getMAPK());

		// clear the key map file
		ClassResultRankMgr.keyMap.clear();
	}

	public double getTopKAcc() {
		return this.TopkAcc;
	}

	public double getMAPK() {
		return this.mapK;
	}

	public double getMRK() {
		return this.mrK;
	}

	public double getMRRK() {

		return this.mrrK;
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		String repoName = "ecf";
		String queryFile = "./input/query.txt";
		int TOPK = 10;
		// System.out.println(new BLIZZARDResultProvider(repoName, TOPK,
		// queryFile).collectBLIZZARDResults());
	}
}
