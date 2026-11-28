package com.ollamapocket.app;

import java.util.List;
import java.util.Random;

public final class MarkdownTableCellsTest {
    private static int checks;
    private static void ok(boolean pass,String label){checks++;if(!pass)throw new AssertionError(label);}
    private static List<String> split(String s){return MarkdownTableCells.split(s,4000,10);}
    private static void eq(String line,String... cells){
        List<String> actual=split(line);
        ok(actual.equals(List.of(cells)),"Expected "+List.of(cells)+" got "+actual);
    }
    public static void main(String[] args){
        eq("| A | B |","A","B");
        eq("A | B","A","B");
        eq("| A | | B |","A","","B");
        eq("| A ||","A","");
        eq("| x\\|y | `a|b` |","x|y","`a|b`");
        eq("| ``a|b`` | x |","``a|b``","x");
        eq("| ``a`|b`` | x |","``a`|b``","x");
        eq("| ```a``|b``` | x |","```a``|b```","x");
        eq("| `a\\|b` | x |","`a|b`","x");
        eq("| `unmatched | x |","`unmatched","x");
        eq("| ``unmatched` | x |","``unmatched`","x");
        eq("| \\`literal | x |","\\`literal","x");
        eq("a"+"\\".repeat(2)+"|b","a"+"\\".repeat(2),"b");
        eq("a"+"\\".repeat(3)+"|b","a"+"\\".repeat(2)+"|b");
        eq("| a\\|","a|");
        eq("| a"+"\\".repeat(2)+"|","a"+"\\".repeat(2));
        eq("| ``a|b`` | `c|d` | 한글😀 |","``a|b``","`c|d`","한글😀");
        ok(split("|"+"x|".repeat(10)).size()==10,"maximum columns");
        ok(split("|"+"x|".repeat(11)).isEmpty(),"column cap");
        ok(split("x".repeat(4001)).isEmpty(),"line cap");
        ok(split(null).isEmpty(),"null");
        ok(MarkdownTableCells.split("x",4001,10).isEmpty(),"invalid limit");
        ok(MarkdownTableCells.split("x",4000,11).isEmpty(),"invalid columns");
        try{split("|a|b|").add("c");throw new AssertionError("mutable");}
        catch(UnsupportedOperationException expected){checks++;}
        Random random=new Random(1102);
        for(int i=0;i<3000;i++){
            int width=2+random.nextInt(10),inner=1+random.nextInt(width-1);
            String marker="`".repeat(width);
            String literal=marker+"code"+"`".repeat(inner)+"|value"+marker;
            String other="한글"+i+"😀";
            eq("| "+literal+" | "+other+" |",literal,other);
            eq("| "+other+" | "+literal+" |",other,literal);
            int slashes=2*random.nextInt(10)+1;
            eq("| x"+"\\".repeat(slashes)+"|y | "+other+" |","x"+"\\".repeat(slashes-1)+"|y",other);
        }
        System.out.println("PASS: "+checks+" Markdown table cell assertions");
    }
}
