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
import java.util.SortedSet;
import java.util.TreeSet;


public class App
{

  public static int RADIX = 4;

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

//all counts must be strictly above mincount
  public static void encode(String vocabFile, String inFile, int gramsize, int mincount) throws Exception {

    BufferedReader brIn = new BufferedReader(new FileReader(inFile));
        TIntMap<String> dict = buildDict(vocabFile);
    String sLine;
    int [] curLine = new int[gramsize];
    int [] prevLine = new int[gramsize];

    LongArray out=new LongArray(100L);
    BitList bl = new BitList();
    bl.addShort((short)gramsize);
    bl.addShort((short)mincount);
    int count = 0;
    int countPartialGrams = 0;
    TreeSet<String> lines = new TreeSet<String>();
    while((sLine = brIn.readLine())!= null) {
      lines.add(sLine);
    }
    brIn.close();
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
        bl.addAll(CompressionUtils.variableCompress(0, RADIX));
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
      bl.addAll(CompressionUtils.variableCompress(Integer.parseInt(fields[gramsize])-mincount, RADIX));

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
    encode(args[0], args[1], Integer.parseInt(args[2]),
            Integer.parseInt(args[3]));
  }
}
