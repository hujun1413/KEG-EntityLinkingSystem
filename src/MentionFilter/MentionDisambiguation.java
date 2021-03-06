package MentionFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.ansj.splitWord.analysis.NlpAnalysis;
import org.ansj.splitWord.analysis.ToAnalysis;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import XloreAPI.GetEntityAPI;
import Common.Constant;
import Common.FileManipulator;
import EntityIndex.AhoCorasickDoubleArrayTrie;
import EntityIndex.IndexBuilder;
import EntityIndex.AhoCorasickDoubleArrayTrie.Hit;
import Model.CandidateSet;
import Model.Entity;
import Model.Mention;

public class MentionDisambiguation {

	private List<String> extractResult = new ArrayList<String>();
	private List<String> midResult = new ArrayList<String>();
	//private HashMap<String, Integer> scoreMap = new HashMap<String, Integer>();  //label   score
	private HashMap<String, Integer> timeMap = new HashMap<String, Integer>();  //label   score
	private HashMap<String, String> stringMap = new HashMap<String, String>();   //label   label::=value
	
	private List<Integer> PositionStart = new ArrayList<Integer>();
	private List<Integer> PositionEnd = new ArrayList<Integer>();
	private List<Integer> Ps_result = new ArrayList<Integer>();
	private List<Integer> Pe_result = new ArrayList<Integer>();
	
	private HashMap<Mention,CandidateSet> candidateSetMap = new HashMap<Mention,CandidateSet>();
	int count = 0;
	long total_query_time = 0;
	public static final Logger logger = LogManager.getLogger();
	
	private int selectByMaxLength(List<String> result)
	{
		int choice = 0,lengthMax = -1;
		for(int k=0; k<result.size(); k++)
		{
			String[] strsplit = result.get(k).split(";");
			int length = Integer.parseInt(strsplit[1])-Integer.parseInt(strsplit[0]);
			if (length>=lengthMax) {
				lengthMax=length;
				choice = k;
			}
		}
		return choice;
	}
	
	private int selectByPopu(List<String> result, IndexBuilder ibd, String doc)
	{
		int choice = 0;
		int popuMax = 0;
		for(int k=0; k<result.size(); k++)
		{
			String[] strsplit = result.get(k).split(";");
			int score = timeMap.get(doc.substring(Integer.parseInt(strsplit[0]), Integer.parseInt(strsplit[1])));
			if(popuMax < score)
			{
				popuMax = score;
				choice = k;
			}
		}
		return choice;
	}
	
	private List<Integer> selectByExtract(List<String> result, IndexBuilder ibd, String doc)
	{
		List<Integer> choice = new ArrayList<Integer>();
		for(int k=0; k<result.size(); k++)
		{
			String[] strsplit = result.get(k).split(";");
			if(extractResult.contains(doc.substring(Integer.parseInt(strsplit[0]), Integer.parseInt(strsplit[1]))))
			{
				choice.add(k);
			}
		}
		return choice;
	}
	/*private void leaveOneId(List<String> str)  //只留一个编号
	{
		for(int i=0; i<str.size(); i++)
		{
			String[] strsplit = str.get(i).split("::=");
			stringMap.put(strsplit[0], str.get(i));
			if(strsplit.length > 2)
			{
				int max = 1;
				for(int j=1; j<strsplit.length-1; j++)
				{
					String[] idSplit1 = strsplit[max].split("::;");
					String[] idSplit2 = strsplit[j+1].split("::;");
					if(Integer.parseInt(idSplit1[1])<Integer.parseInt(idSplit2[1]))
					{
						max = j+1;
					}
				}
				str.set(i, strsplit[0]+"::="+strsplit[max]);
			}
			//System.out.println(str.get(i));
		}
		midResult.addAll(str);
	}
	
	private void filterbyScore(List<String> str)  //去掉频度不高于5的
	{
		midResult.clear();
		for(int i=0; i<str.size(); i++)
		{
			String[] strsplit = str.get(i).split("::=");
			int score = 0;
			String[] idSplit = strsplit[1].split("::;");
			score += Integer.parseInt(idSplit[1]);
			scoreMap.put(strsplit[0], score);
			Pattern pattern = Pattern.compile("[0-9]*"); 
			if(score>5)
			{
				//System.out.println(str.get(i));
				midResult.add(str.get(i));
				Ps_result.add(PositionStart.get(i));
				Pe_result.add(PositionEnd.get(i));
			}
		}
	}*/
	
	private void filterNumber(List<String> str)  //去掉数字，日期，时间
	{
		midResult.clear();
		for(int i=0; i<str.size(); i++)
		{
			String[] strsplit = str.get(i).split("::=");
			Pattern pattern = Pattern.compile("[[0-9]+[年|月|日|时|分|秒]*]+"); 
			if(!pattern.matcher(strsplit[0]).matches() )//不是数字
			{
				//System.out.println(str.get(i));
				midResult.add(str.get(i));
				Ps_result.add(PositionStart.get(i));
				Pe_result.add(PositionEnd.get(i));
			}
		}
	}
	
	
	
	private void filterbyPosition(IndexBuilder ibd, String doc) 
	{
		midResult.clear();
		List<String> result = new ArrayList<String>();
		int endMax ;
		for (int i=0; i<Ps_result.size(); i++) 
		{
			if (result.contains(Ps_result.get(i)+";"+Pe_result.get(i))) 
			{
				continue;
			}
			/*for(String tmp : result)
			{
				System.out.println(tmp);
			}*/
			result.clear();
			endMax = Pe_result.get(i);
			result.add(Ps_result.get(i)+";"+Pe_result.get(i));
			for (int j = i+1; j < i+20&&j<Ps_result.size(); j++) 
			{//向下最多找9次
				if (Ps_result.get(j)<endMax) 
				{
					result.add(Ps_result.get(j)+";"+Pe_result.get(j));
					if (Pe_result.get(j)>endMax) 
					{
						endMax = Pe_result.get(j);
					}
				}
			}
			
			//不应该找最长的，有特殊情况，应该找end值最大的
			for (int j = i+1; j < i+20&&j<Ps_result.size(); j++) 
			{//向下找19次
				if (Ps_result.get(j)<endMax) {
					if (result.contains(Ps_result.get(j)+";"+Pe_result.get(j))) 
					{
						continue;
					}
					result.add(Ps_result.get(j)+";"+Pe_result.get(j));
				}
			}
			List<Integer> scoreList = new ArrayList<Integer>();
			for(int s=0; s<result.size(); s++)
	        {
				scoreList.add(0);
	        }
			//选择最长的
			int choice = 0;
			choice = selectByMaxLength(result);
			String[] split = result.get(choice).split(";");
			int length = Integer.parseInt(split[1])-Integer.parseInt(split[0]);
			scoreList.set(choice, scoreList.get(choice)+length);  //+长度
			//System.out.println(choice+" "+scoreList.get(choice));
			
			//选择得分最高的
			//choice = selectByPopu(result, ibd, doc);
			//scoreList.set(choice, scoreList.get(choice)+2);   //+2
			//System.out.println(choice+"  "+scoreList.get(choice));
			for(int m=0; m<result.size(); m++)
			{
				if(extractResult.contains(doc.substring(Integer.parseInt(split[0]), Integer.parseInt(split[1]))))
				{
					scoreList.set(m, scoreList.get(m)+2);
				}
				
			}
			
			//分词结果中含有
			List<Integer> cho = new ArrayList<Integer>();
			cho = selectByExtract(result, ibd, doc);
			for(int s=0; s<cho.size(); s++)
			{
				scoreList.set(s, scoreList.get(s)+2);  //+1
				//System.out.println(s+"     "+scoreList.get(s));
			}
			
			
			int max = 0;
			for(int m=0; m<result.size(); m++)
	        {
				if(max < scoreList.get(m))
				{
					max = scoreList.get(m);
					choice = m;
				}
	        }
			String[] strsplit = result.get(choice).split(";");
			String value = stringMap.get(doc.substring(Integer.parseInt(strsplit[0]), Integer.parseInt(strsplit[1])));
			//System.out.println(value);
			insertMention(Integer.parseInt(strsplit[0]), Integer.parseInt(strsplit[1]), value);
		}
	}
	
	public HashMap<Mention,CandidateSet> disambiguating(IndexBuilder ibd, String doc, String news_path) throws IOException
	{
		//System.out.println(NlpAnalysis.parse("奥巴马"));
		String extract = NlpAnalysis.parse(doc).toStringWithOutNature("&&");
		String[] extractList = extract.split("&&");
		for(String tmp: extractList)
		{
			extractResult.add(tmp);
			//System.out.println(tmp);
		}
    	List<String> str = new ArrayList<String>();
    	
    	long start = System.currentTimeMillis();
    	List<AhoCorasickDoubleArrayTrie<String>.Hit<String>> wordList = ibd.parseText(doc);
    	long end = System.currentTimeMillis(); 
        logger.info("Parsing doc finish! Time:" + (float)(end - start)/1000);
    	System.out.println("Parsing doc finish! Time:" + (float)(end - start)/1000);
    	logger.info(candidateSetMap.toString());
    	
    	for(AhoCorasickDoubleArrayTrie<String>.Hit<String> tmp_hit:wordList){
    		String label = doc.substring(tmp_hit.begin, tmp_hit.end);
        	str.add(label+"::="+tmp_hit.value);
        	stringMap.put(label, label+"::="+tmp_hit.value);
        	if(timeMap.containsKey(doc.substring(tmp_hit.begin, tmp_hit.end)))
        	{
        		timeMap.put(doc.substring(tmp_hit.begin, tmp_hit.end), timeMap.get(doc.substring(tmp_hit.begin, tmp_hit.end))+1);
        	}
        	else 
        	{
        		timeMap.put(doc.substring(tmp_hit.begin, tmp_hit.end), 1);
        	}
        	PositionStart.add(tmp_hit.begin);
        	PositionEnd.add(tmp_hit.end);
        }
    	/*for (Map.Entry<String, Integer> entry : timeMap.entrySet()) {
			String key = entry.getKey();
			int value = entry.getValue();
			System.out.println(key + " " + value);
    	}*/
    	FileManipulator.outputStringList(str, System.getProperty("user.dir") + news_path+"_original.txt");
    	
		//leaveOneId(str);
		//filterbyScore(str);
    	filterNumber(str);
		filterbyPosition(ibd, doc);
		
		FileManipulator.outputStringList(midResult, System.getProperty("user.dir") + news_path+"_filter.txt");
		logger.info("Query total time:" + (total_query_time)/1000 + "s, #query times:" + count + ", average:" + (float)(total_query_time/count)/1000 + "s");
		System.out.println("Query total time:" + (total_query_time)/1000 + "s, #query times:" + count + ", average:" + (float)(total_query_time/count)/1000 + "s");
		
		return candidateSetMap;
	}
	
	private void insertMention(int begin, int end, String item)
	{
		String label = item.split("::=", 2)[0];
		String value = item.split("::=", 2)[1];
    	long start1 = 0;
    	long end1 = 0;
    	logger.info(item);
    	midResult.add(item);
    	Mention mention = new Mention();
    	mention.setLabel(label);
    	mention.setPos_start(begin);
    	mention.setPos_end(end);
    	CandidateSet cs = new CandidateSet();
    	String[] tmp_c = value.split("::=");
    	for(String ss : tmp_c){
    		String[] tmp_uri = ss.split("::;");
    		String id = tmp_uri[0];
    		// get entity details from XLore API
    		start1 = System.currentTimeMillis();
    		Entity tmp_e = GetEntityAPI.getEntityDetailByID(id);
    		end1 = System.currentTimeMillis();
    		total_query_time += end1 - start1;
    		count += 1;
    		if(tmp_uri.length > 1){
    			tmp_e.setDesc(tmp_uri[1]);
    		}
			cs.addElement(id, tmp_e);
    	}
    	candidateSetMap.put(mention, cs);
	}

}
