package com.ollamapocket.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/** Bounded display-only block parsing. The original response is never modified. */
final class ChatBlocks {
    enum Kind { TEXT, CODE, TABLE, MATH }
    static final int MAX_SOURCE=160000, MAX_LINE=4000, MAX_BLOCKS=24, MAX_CELLS=96, MAX_COLUMNS=10;
    static final class Block {
        final Kind kind;final String raw,body,language;final List<List<String>> rows;
        Block(Kind kind,String raw,String body,String language,List<List<String>> rows){
            this.kind=kind;this.raw=raw;this.body=body;this.language=language;this.rows=rows;
        }
    }
    private static final Pattern FENCE=Pattern.compile("^ {0,3}\u0060\u0060\u0060[ \\t]*[A-Za-z0-9_+.#-]{0,40}[ \\t]*$");
    private static final Pattern DIVIDER=Pattern.compile("^:?-{3,}:?$");
    private ChatBlocks(){}
    private static Block text(String raw){return new Block(Kind.TEXT,raw,raw,"",Collections.emptyList());}
    static List<Block> parse(String source){
        if(source==null)source="";
        if(source.length()>MAX_SOURCE)return Collections.singletonList(text(source));
        String[] lines=source.split("\n",-1);
        int[] offsets=new int[lines.length+1];
        for(int i=0;i<lines.length;i++){
            if(lines[i].length()>MAX_LINE)return Collections.singletonList(text(source));
            offsets[i+1]=offsets[i]+lines[i].length()+(i<lines.length-1?1:0);
        }
        List<Block> blocks=new ArrayList<>();int plainStart=0,cells=0;
        for(int i=0;i<lines.length;){
            String line=clean(lines[i]),trim=line.trim();Block special=null;int end=i+1;
            if(FENCE.matcher(line).matches()){
                int close=i+1;
                while(close<lines.length&&!clean(lines[close]).trim().equals("\u0060\u0060\u0060"))close++;
                if(close==lines.length)break; // Unclosed fence remains original plain text.
                end=close+1;
                String lang=trim.substring(3).trim();
                String raw=source.substring(offsets[i],offsets[end]);
                String code=source.substring(offsets[i+1],offsets[close]); // exact interior including final line ending
                special=new Block(Kind.CODE,raw,code,lang,Collections.emptyList());
            }else if(trim.startsWith("$$")||trim.startsWith("\\[")){
                String opener=trim.startsWith("$$")?"$$":"\\[",closer="$$".equals(opener)?"$$":"\\]";
                String rest=trim.substring(2);
                String body=null;
                if(rest.endsWith(closer)&&rest.length()>=2)body=rest.substring(0,rest.length()-2).trim();
                else if(rest.trim().isEmpty()){
                    int close=i+1;
                    while(close<lines.length&&!clean(lines[close]).trim().equals(closer))close++;
                    if(close<lines.length){end=close+1;body=source.substring(offsets[i+1],offsets[close]).trim();}
                    else break;
                }
                if(body!=null&&!body.isEmpty()&&body.length()<=1000)
                    special=new Block(Kind.MATH,source.substring(offsets[i],offsets[end]),body,"",Collections.emptyList());
            }else if(line.indexOf('|')>=0&&i+1<lines.length){
                List<String> header=splitCells(line),divider=splitCells(clean(lines[i+1]));
                boolean valid=header.size()>=2&&header.size()<=MAX_COLUMNS&&header.size()==divider.size();
                for(String d:divider)valid&=DIVIDER.matcher(d.trim()).matches();
                if(valid){
                    List<List<String>> data=new ArrayList<>();data.add(header);
                    int j=i+2;boolean malformed=false;
                    while(j<lines.length&&!clean(lines[j]).trim().isEmpty()&&clean(lines[j]).indexOf('|')>=0){
                        List<String> row=splitCells(clean(lines[j]));
                        if(row.size()!=header.size()||(long)(data.size()+1)*header.size()+cells>MAX_CELLS){malformed=true;break;}
                        data.add(row);j++;
                    }
                    if(!malformed&&data.size()*header.size()+cells<=MAX_CELLS){
                        cells+=data.size()*header.size();end=j;
                        special=new Block(Kind.TABLE,source.substring(offsets[i],offsets[end]),"","",Collections.unmodifiableList(data));
                    }
                }
            }
            if(special==null){i++;continue;}
            if(offsets[i]>plainStart)blocks.add(text(source.substring(plainStart,offsets[i])));
            blocks.add(special);plainStart=offsets[end];i=end;
            if(blocks.size()>MAX_BLOCKS)return Collections.singletonList(text(source));
        }
        if(plainStart<source.length())blocks.add(text(source.substring(plainStart)));
        if(blocks.size()>MAX_BLOCKS)return Collections.singletonList(text(source));
        if(blocks.isEmpty())blocks.add(text(source));
        return Collections.unmodifiableList(blocks);
    }
    private static String clean(String line){return line.endsWith("\r")?line.substring(0,line.length()-1):line;}
    private static List<String> splitCells(String input){
        String s=input.trim();List<String> out=new ArrayList<>();
        if(s.startsWith("|"))s=s.substring(1);
        if(s.endsWith("|")&&!s.endsWith("\\|"))s=s.substring(0,s.length()-1);
        StringBuilder cell=new StringBuilder();boolean code=false;
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            if(c=='\\'&&i+1<s.length()&&s.charAt(i+1)=='|'){cell.append('|');i++;continue;}
            if(c=='\u0060')code=!code;
            if(c=='|'&&!code){
                out.add(cell.toString().trim());cell.setLength(0);
                if(out.size()>MAX_COLUMNS)return Collections.emptyList();
            }else cell.append(c);
        }
        out.add(cell.toString().trim());return Collections.unmodifiableList(out);
    }
    /** Explicit derived paste format, not raw Markdown or a typed spreadsheet.
     * Escape control separators and neutralize formula-like/quote-led text conservatively.
     * Destination spreadsheet typing and interpretation remain application dependent. */
    private static final Pattern SIMPLE_NUMBER=Pattern.compile("[+-]?[0-9]+(?:\\.[0-9]+)?");
    static String spreadsheetTsv(Block table){
        if(table==null||table.kind!=Kind.TABLE||table.rows==null||table.rows.isEmpty())
            throw new IllegalArgumentException("Table required");
        List<String> first=table.rows.get(0);
        if(first==null)throw new IllegalArgumentException("Table shape");
        int columns=first.size();
        if(columns<2||columns>MAX_COLUMNS||(long)table.rows.size()*columns>MAX_CELLS)
            throw new IllegalArgumentException("Table bounds");
        StringBuilder out=new StringBuilder();
        for(int i=0;i<table.rows.size();i++){
            List<String> row=table.rows.get(i);
            if(row==null||row.size()!=columns)throw new IllegalArgumentException("Table shape");
            if(i>0)out.append('\n');
            for(int j=0;j<columns;j++){
                if(j>0)out.append('\t');
                appendSpreadsheetCell(out,row.get(j));
                if(out.length()>MAX_SOURCE)throw new IllegalArgumentException("TSV limit");
            }
        }
        return out.toString();
    }
    private static boolean separatorOrControl(int cp){
        return Character.isISOControl(cp)||cp==0x2028||cp==0x2029||
            (Character.getType(cp)==Character.FORMAT&&cp!=0x200C&&cp!=0x200D)||
            (cp>=0xD800&&cp<=0xDFFF);
    }
    private static boolean formulaPrefix(int cp){
        return cp=='='||cp=='+'||cp=='-'||cp=='@'||cp=='"'||cp=='\''||
            cp==0xFF1D||cp==0xFF0B||cp==0xFF0D||cp==0xFF20||cp==0xFF02||cp==0xFF07||cp==0x2212;
    }
    private static void appendSpreadsheetCell(StringBuilder out,String value){
        if(value==null||value.length()>MAX_LINE)throw new IllegalArgumentException("Cell limit");
        int first=-1;
        for(int i=0;i<value.length();){
            int cp=value.codePointAt(i);i+=Character.charCount(cp);
            if(Character.isWhitespace(cp)||Character.isSpaceChar(cp)||separatorOrControl(cp)||
                Character.getType(cp)==Character.FORMAT)continue;
            first=cp;break;
        }
        // A plain signed decimal contains no references, functions or operators beyond its sign.
        if(first>=0&&formulaPrefix(first)&&!SIMPLE_NUMBER.matcher(value).matches())out.append('\'');
        for(int i=0;i<value.length();){
            int cp=value.codePointAt(i);i+=Character.charCount(cp);
            if(cp=='\t')out.append("\\t");
            else if(cp=='\r')out.append("\\r");
            else if(cp=='\n')out.append("\\n");
            else if(separatorOrControl(cp)){
                // Visible escapes avoid adding an actual field or record separator.
                if(cp<=0xFFFF){
                    out.append("\\u");
                    for(int shift=12;shift>=0;shift-=4)out.append(Character.forDigit((cp>>>shift)&15,16));
                }else out.append("\\u{").append(Integer.toHexString(cp)).append('}');
            }else out.appendCodePoint(cp);
        }
    }
    static boolean hasSpecial(List<Block> blocks){for(Block b:blocks)if(b.kind!=Kind.TEXT)return true;return false;}
}
