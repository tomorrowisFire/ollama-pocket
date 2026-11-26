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
        System.out.println("ChatBlocksTest PASS: "+n+" assertions");
    }
}
