package edu.northwestern.eecs.websail;

import edu.berkeley.nlp.lm.array.*;
import edu.berkeley.nlp.lm.bits.BitList;
import edu.berkeley.nlp.lm.bits.CompressionUtils;
import edu.berkeley.nlp.lm.collections.TIntMap;
import gnu.trove.map.hash.TIntDoubleHashMap;
import gnu.trove.set.hash.THashSet;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.SortedSet;
import java.util.TreeSet;


public class App
{

  public static int RADIX = 4;
  public static final boolean USELOG2 = false;

  public static int getID(TIntMap<String> dict, String word) {
    if(!dict.containsKey(word))
      dict.put(word, dict.size()+1);
    return dict.get(word, -1);
  }

  public static TIntMap<String> buildDict(String vocabFile) throws Exception {
    BufferedReader brIn = new BufferedReader(new FileReader(vocabFile));
    TIntMap<String> dict = new TIntMap<String>();
    String sLine;
    TreeSet<String> words = new TreeSet<String>();
    while ((sLine = brIn.readLine()) != null) {
      words.add(sLine);
    }
    for(String s : words) {
      getID(dict, s);
    }
    System.out.println("dict size " + dict.size());
    return dict;
  }

  static TreeSet<String> readLinesToTreeSet(String inFile) throws Exception {
    BufferedReader brIn = new BufferedReader(new FileReader(inFile));
    TreeSet<String> lines = new TreeSet<String>();
    String sLine;
    while((sLine = brIn.readLine())!= null) {
      lines.add(sLine);
    }
    brIn.close();
    return lines;
  }

  static String getKey(String [] fields, int gramsize) {
    String key = "";
    for(int i=0; i<gramsize-1; i++) {
      key += fields[i] + "_!_";
    }
    return key;
  }

  static HashMap<String, Integer> getSubGrams(TreeSet<String> lines, int gramsize) {
    HashMap<String, Integer> out = new HashMap<>();
    for (String s : lines) {
      String[] fields = s.split(" |\t");
      if (fields.length != gramsize)
        continue;

      out.put(getKey(fields, gramsize), Integer.parseInt(fields[gramsize-1]));
    }
    return out;
  }

  //prepares counts for output
  public static int countProcess(int in) {
    if(!USELOG2)
      return in;
    else
      return 31 - Integer.numberOfLeadingZeros(in);
  }

//all counts must be strictly above mincount
  //inFile contains the n-gram counts to be compressed
  //inFileSubGrams contains the (n-1)-gram counts to be compressed
  public static void encode(String vocabFile, String inFile, String inFileSubGrams, int gramsize, int mincount) throws Exception {

        TIntMap<String> dict = buildDict(vocabFile);
    String sLine;
    int [] curLine = new int[gramsize];
    int [] prevLine = new int[gramsize];

    TreeSet<String> smallLines = readLinesToTreeSet(inFileSubGrams);
    HashMap<String, Integer> subGrams = getSubGrams(smallLines, gramsize);
    LongArray out=new LongArray(100L);
    BitList bl = new BitList();
    bl.addShort((short)gramsize);
    bl.addShort((short)mincount);
    int count = 0;
    int countPartialGrams = 0;
    TreeSet<String> lines = readLinesToTreeSet(inFile);
    for(String s : lines) {
      String [] fields = s.split(" |\t");
      if(fields.length != gramsize+1)
        continue;
      for(int i=0; i<fields.length - 1; i++) {
        curLine[i] = getID(dict, fields[i]);
      }
      int newSubgramIdx = -1;
      for(int i=0; i<curLine.length-1; i++) {
        if(curLine[i]!=prevLine[i]) {
          newSubgramIdx = i;
          break;
        }
      }
      if(newSubgramIdx >= 0) {
        int sizeAtStart = bl.size();
        String key = getKey(fields, gramsize);
        if(!subGrams.containsKey(key))
          System.err.println("error, bigram not found!!  Do not trust results.");
        int subgramCount = subGrams.get(key);

        bl.addAll(CompressionUtils.variableCompress(0, RADIX));
        bl.addAll(CompressionUtils.variableCompress(countProcess(subgramCount-mincount), RADIX));
        bl.addAll(CompressionUtils.variableCompress(newSubgramIdx, RADIX));
        bl.addAll(CompressionUtils.variableCompress(curLine[newSubgramIdx]-prevLine[newSubgramIdx], RADIX));
        for(int i=newSubgramIdx+1; i<curLine.length - 1; i++) {
          bl.addAll(CompressionUtils.variableCompress(curLine[i], RADIX));
        }
        //System.out.println("size after new subgram: " + (bl.size() - sizeAtStart));
        countPartialGrams++;
      }
      else {
        bl.addAll(CompressionUtils.variableCompress(curLine[gramsize-1]-prevLine[gramsize-1], RADIX));
        if(curLine[gramsize-1]-prevLine[gramsize-1] < 0)
          System.out.println("problems.");
      }
      bl.addAll(CompressionUtils.variableCompress(countProcess(Integer.parseInt(fields[gramsize])-mincount), RADIX));

      for(int i=0; i<curLine.length; i++) {
        prevLine[i] = curLine[i];
      }
      count++;
    }
    System.out.println("total size: " + bl.size() + " bits, for " + count + " " + gramsize + "-grams and "
    + countPartialGrams + " " + (gramsize-1) + "-grams.");
  }
  public static void main( String[] args ) throws Exception
  {
    encode(args[0], args[1], args[2], Integer.parseInt(args[3]),
            Integer.parseInt(args[4]));
  }
}
