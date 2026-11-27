package com.ollamapocket.app;
import java.util.List;

public final class ChatBlocksTest {
    private static int n;
    private static void ok(boolean b,String label){if(!b)throw new AssertionError(label);n++;}
    public static void main(String[] args){
        String src="Intro\n\u0060\u0060\u0060java\r\nint x=2;\r\n\u0060\u0060\u0060\nAfter";
        List<ChatBlocks.Block> blocks=ChatBlocks.parse(src);
        ok(blocks.size()==3&&blocks.get(1).kind==ChatBlocks.Kind.CODE,"code section");
        ok(blocks.get(1).body.equals("int x=2;\r\n")&&blocks.get(1).language.equals("java"),"exact code bytes");
        StringBuilder raw=new StringBuilder();for(ChatBlocks.Block b:blocks)raw.append(b.raw);
        ok(raw.toString().equals(src),"raw preserved");
        ok(ChatBlocks.codeLineCount(blocks.get(1).body)==1&&ChatBlocks.codeLineGutter(blocks.get(1).body).equals("1\n"),"CRLF exact line gutter");
        ok(ChatBlocks.codeLineCount("x\n\n")==2&&ChatBlocks.codeLineGutter("x\n\n").equals("1\n2\n"),"Blank line and trailing newline");
        ok(ChatBlocks.codeLineCount("a\nb")==2&&ChatBlocks.codeLineGutter("a\nb").equals("1\n2"),"Unterminated last line");
        ok(ChatBlocks.codeLineCount("")==0&&ChatBlocks.codeLineGutter("").isEmpty(),"Empty code");
        ok(ChatBlocks.codeLineGutter("\n".repeat(1000)).endsWith("1000\n"),"Numbering limit");
        ok(ChatBlocks.codeLineCount("\n".repeat(1001))==1001&&ChatBlocks.codeLineGutter("\n".repeat(1001)).isEmpty(),"Large source still countable without gutter");
        ok(!ChatBlocks.hasSpecial(ChatBlocks.parse("\u0060\u0060\u0060\nunclosed")),"unclosed fence");
        blocks=ChatBlocks.parse("| A | B |\n| --- | :---: |\n| x\\|y | \u0060a|b\u0060 |\n");
        ok(blocks.get(0).kind==ChatBlocks.Kind.TABLE&&blocks.get(0).rows.size()==2,"native table data");
        ok("x|y".equals(blocks.get(0).rows.get(1).get(0))&&"\u0060a|b\u0060".equals(blocks.get(0).rows.get(1).get(1)),"escaped and inline pipes");
        ok(!ChatBlocks.hasSpecial(ChatBlocks.parse("| A | B |\n| --- |\n| 1 | 2 |")),"bad table");
        ok(!ChatBlocks.hasSpecial(ChatBlocks.parse("| A | B |\n| --- | --- |\n| 1 |")),"bad row");
        ok(!ChatBlocks.hasSpecial(ChatBlocks.parse("x".repeat(4001))),"long line limit");
        ok(ChatBlocks.parse("$$x^2 + \\alpha$$").get(0).kind==ChatBlocks.Kind.MATH,"inline display math");
        ok(ChatBlocks.parse("\\[\n\\frac{1}{2}\n\\]").get(0).body.equals("\\frac{1}{2}"),"multiline math");
        ok(SimpleFormula.display("x^2+\\alpha").equals("x²+α"),"simple math");
        ok(SimpleFormula.display("\\frac{1}{2}").equals("(1) / (2)"),"fraction notation");
        ok(SimpleFormula.display("x_{12}").equals("x₁₂"),"subscript");
        ok(SimpleFormula.display("\\unknown{x}^2").equals("\\unknown{x}^2"),"unknown preserved");
        ok(!ChatBlocks.hasSpecial(ChatBlocks.parse("<script>alert(1)</script> ![x](https://example.org)")),"no executable markup");
        String table="| A | B |\n| --- | --- |\n"+"| 1 | 2 |\n".repeat(60);
        ok(!ChatBlocks.hasSpecial(ChatBlocks.parse(table)),"table resource limit");
        String original="| A | B | C |\n| --- | --- | --- |\n| 한글 😀 | x\\|y | |\n| -12.50 | +3 | 001 |";
        ChatBlocks.Block parsed=ChatBlocks.parse(original).get(0);
        ok(parsed.kind==ChatBlocks.Kind.TABLE,"exportable table");
        ok(ChatBlocks.spreadsheetTsv(parsed).equals("A\tB\tC\n한글 😀\tx|y\t\n-12.50\t+3\t001"),"TSV order, empty cells, numbers and Unicode");
        ok(parsed.raw.equals(original),"source retained after export");
        java.util.function.Function<List<List<String>>,ChatBlocks.Block> fixture=rows->
            new ChatBlocks.Block(ChatBlocks.Kind.TABLE,"raw","","",rows);
        ChatBlocks.Block risky=fixture.apply(List.of(
            List.of("A","B","C"),
            List.of("=SUM(A1:A9)"," \t+1+2","-4+5"),
            List.of("@SUM(1)","\"=1+1\"","'literal"),
            List.of("＋SUM(1)","\u00a0＝1","\u2212x"),
            List.of("a\tb","x\r\ny","p\u2028q"),
            List.of("\uFEFF=1","a\u0000b","👩‍💻")));
        String tsv=ChatBlocks.spreadsheetTsv(risky);
        ok(tsv.equals("A\tB\tC\n'=SUM(A1:A9)\t' \\t+1+2\t'-4+5\n'@SUM(1)\t'\"=1+1\"\t''literal\n'＋SUM(1)\t'\u00a0＝1\t'\u2212x\na\\tb\tx\\r\\ny\tp\\u2028q\n'\\ufeff=1\ta\\u0000b\t👩‍💻"),"formula, quote and control handling");
        ok(tsv.split("\n",-1).length==6,"no injected record separators");
        for(String line:tsv.split("\n",-1))ok(line.split("\t",-1).length==3,"no injected column separators");
        for(ChatBlocks.Block invalid:List.of(
                fixture.apply(List.of()),fixture.apply(List.of(List.of("a"))),
                fixture.apply(List.of(List.of("a","b"),List.of("x"))),
                fixture.apply(List.of(List.of("x".repeat(4001),"b"))),
                fixture.apply(java.util.Collections.nCopies(49,List.of("a","b"))),
                fixture.apply(java.util.Collections.nCopies(24,List.of("\u0000".repeat(4000),"\u0000".repeat(4000)))))){
            boolean rejected=false;try{ChatBlocks.spreadsheetTsv(invalid);}catch(IllegalArgumentException e){rejected=true;}
            ok(rejected,"reject table shape or size");
        }
        boolean nonTable=false;try{ChatBlocks.spreadsheetTsv(ChatBlocks.parse("plain").get(0));}
        catch(IllegalArgumentException e){nonTable=true;}ok(nonTable,"reject non-table");
        String fence="\u0060\u0060\u0060\nx\n\u0060\u0060\u0060\n";
        String exactly=fence.repeat(ChatBlocks.MAX_BLOCKS);
        ok(ChatBlocks.parse(exactly).size()==ChatBlocks.MAX_BLOCKS,"exact block limit");
        String over=exactly+"trailing";
        List<ChatBlocks.Block> fallback=ChatBlocks.parse(over);
        ok(fallback.size()==1&&fallback.get(0).kind==ChatBlocks.Kind.TEXT&&fallback.get(0).raw.equals(over),"trailing block cannot exceed bound");
        System.out.println("ChatBlocksTest PASS: "+n+" assertions");
    }
}
