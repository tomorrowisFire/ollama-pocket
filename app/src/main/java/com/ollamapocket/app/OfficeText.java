package com.ollamapocket.app;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

/** Read-only OOXML excerpt reader. No file extraction, relationship fetching or formula execution. */
final class OfficeText {
    static final int MAX_BYTES=8*1024*1024, MAX_EXPANDED=16*1024*1024,
        MAX_PART=4*1024*1024, MAX_ENTRIES=512, MAX_SHEETS=12;
    private static final String W="http://schemas.openxmlformats.org/wordprocessingml/2006/main",
        WS="http://purl.oclc.org/ooxml/wordprocessingml/main",
        S="http://schemas.openxmlformats.org/spreadsheetml/2006/main",
        SS="http://purl.oclc.org/ooxml/spreadsheetml/main",
        R="http://schemas.openxmlformats.org/officeDocument/2006/relationships",
        RS="http://purl.oclc.org/ooxml/officeDocument/relationships",
        P="http://schemas.openxmlformats.org/package/2006/relationships";
    private OfficeText(){}
    private static AttachmentText.Failure bad(AttachmentText.Problem p){return new AttachmentText.Failure(p);}
    private static AttachmentText.Failure invalid(){return bad(AttachmentText.Problem.INVALID);}
    private static AttachmentText.Failure large(){return bad(AttachmentText.Problem.TOO_LARGE);}
    static boolean supports(String name){
        if(name==null)return false;
        String lower=name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".docx")||lower.endsWith(".xlsx");
    }
    static AttachmentText.Result read(String name,InputStream source)throws IOException{return read(name,source,1);}
    static AttachmentText.Result read(String name,InputStream source,int segment)throws IOException{
        if(!DocumentWindow.validSegment(segment))throw bad(AttachmentText.Problem.RANGE);
        if(!supports(name))throw bad(AttachmentText.Problem.UNSUPPORTED);
        if(source==null)throw invalid();
        try{
            Map<String,byte[]> parts=archive(bounded(source,MAX_BYTES));
            Excerpt out=new Excerpt(DocumentWindow.prefixLimit(segment));
            if(name.toLowerCase(Locale.ROOT).endsWith(".docx")){
                byte[] document=parts.get("word/document.xml");
                if(document==null)throw invalid();
                parse(document,new Word(out));
            }else{
                byte[] book=parts.get("xl/workbook.xml"),rels=parts.get("xl/_rels/workbook.xml.rels");
                if(book==null||rels==null)throw invalid();
                Workbook workbook=new Workbook();Relations relations=new Relations();
                parse(book,workbook);parse(rels,relations);
                SharedStrings shared=new SharedStrings();
                if(parts.containsKey("xl/sharedStrings.xml"))parse(parts.get("xl/sharedStrings.xml"),shared);
                if(workbook.sheets.isEmpty())throw invalid();
                int seen=0;
                for(SheetRef sheet:workbook.sheets){
                    if(seen++>=MAX_SHEETS||out.text.length()>=out.limit){out.partial=true;break;}
                    String path=relations.paths.get(sheet.id);
                    if(path==null||!parts.containsKey(path)){out.partial=true;continue;}
                    parse(parts.get(path),new Cells(out,sheet.name,shared.values));
                }
            }
            if(!out.hasData)throw bad(AttachmentText.Problem.EMPTY);
            return AttachmentText.Result.window(name,DocumentWindow.select(out.text.toString(),segment,out.partial),true);
        }catch(AttachmentText.Failure e){throw e;}
        catch(InterruptedIOException e){throw e;}
        catch(Exception e){
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
            throw invalid();
        }
    }
    private static byte[] bounded(InputStream source,int limit)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
        while((n=source.read(buf))!=-1){
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
            if(n==0)continue;
            if((long)out.size()+n>limit)throw large();
            out.write(buf,0,n);
        }
        return out.toByteArray();
    }
    private static boolean wanted(String path){
        return path.equals("word/document.xml")||path.equals("xl/workbook.xml")||
            path.equals("xl/_rels/workbook.xml.rels")||path.equals("xl/sharedStrings.xml")||
            path.matches("xl/worksheets/[A-Za-z0-9_.-]{1,128}\\.xml");
    }
    private static Map<String,byte[]> archive(byte[] data)throws IOException{
        if(data.length<4||data[0]!='P'||data[1]!='K'||data[2]!=3||data[3]!=4)throw invalid();
        Map<String,byte[]> parts=new HashMap<>();Set<String> names=new HashSet<>();
        long total=0;int entries=0;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(data))){
            ZipEntry entry;byte[] buf=new byte[8192];
            while((entry=zip.getNextEntry())!=null){
                if(++entries>MAX_ENTRIES)throw large();
                String name=entry.getName();
                if(name.length()>512||name.startsWith("/")||name.indexOf('\\')>=0||
                    Arrays.asList(name.split("/")).contains("..")||!names.add(name))throw invalid();
                boolean keep=!entry.isDirectory()&&wanted(name);
                ByteArrayOutputStream part=keep?new ByteArrayOutputStream():null;
                int count=0,n;
                while((n=zip.read(buf))!=-1){
                    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
                    if(n==0)continue;
                    total+=n;count+=n;
                    if(total>MAX_EXPANDED||(keep&&count>MAX_PART))throw large();
                    if(keep)part.write(buf,0,n);
                }
                if(keep)parts.put(name,part.toByteArray());
                zip.closeEntry();
            }
        }
        return parts;
    }
    private static String xmlText(byte[] data)throws Exception{
        // Accept UTF-8 and BOM-marked UTF-16 only; inspect decoded characters, not raw bytes.
        Charset charset=StandardCharsets.UTF_8;int skip=0;
        if(data.length>=3&&(data[0]&255)==239&&(data[1]&255)==187&&(data[2]&255)==191)skip=3;
        else if(data.length>=2&&(data[0]&255)==255&&(data[1]&255)==254){charset=StandardCharsets.UTF_16LE;skip=2;}
        else if(data.length>=2&&(data[0]&255)==254&&(data[1]&255)==255){charset=StandardCharsets.UTF_16BE;skip=2;}
        String text=charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(data,skip,data.length-skip)).toString();
        if(text.contains("<!DOCTYPE")||text.contains("<!ENTITY"))throw invalid();
        for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c<32&&c!='\n'&&c!='\r'&&c!='\t')throw invalid();}
        return text;
    }
    private static void parse(byte[] xml,Handler handler)throws Exception{
        String source=xmlText(xml);
        SAXParserFactory factory=SAXParserFactory.newInstance();factory.setNamespaceAware(true);factory.setValidating(false);
        // DTDs are rejected above. Disable external access where the runtime supports the SAX feature.
        XMLReader reader=factory.newSAXParser().getXMLReader();
        for(String feature:new String[]{"http://xml.org/sax/features/external-general-entities",
            "http://xml.org/sax/features/external-parameter-entities",
            "http://apache.org/xml/features/nonvalidating/load-external-dtd"}){
            try{reader.setFeature(feature,false);}catch(SAXNotRecognizedException|SAXNotSupportedException ignored){}
        }
        reader.setEntityResolver((publicId,systemId)->{throw new SAXException("External entity denied");});
        reader.setContentHandler(handler);reader.setErrorHandler(handler);
        reader.parse(new InputSource(new StringReader(source)));
        if(!handler.rootSeen)throw invalid();
    }
    private static boolean word(String ns){return W.equals(ns)||WS.equals(ns);}
    private static boolean sheet(String ns){return S.equals(ns)||SS.equals(ns);}
    private static abstract class Handler extends DefaultHandler {
        int depth,elements;boolean rootSeen;
        abstract boolean root(String ns,String tag);
        @Override public final void startElement(String ns,String tag,String q,Attributes attrs)throws SAXException{
            if(Thread.currentThread().isInterrupted()||++depth>64||++elements>200000)throw new SAXException("XML limit");
            if(depth==1){if(!root(ns,tag))throw new SAXException("Unexpected XML root");rootSeen=true;}
            start(ns,tag,attrs);
        }
        @Override public final void endElement(String ns,String tag,String q)throws SAXException{end(ns,tag);depth--;}
        @Override public final void characters(char[] c,int start,int len)throws SAXException{
            if(Thread.currentThread().isInterrupted())throw new SAXException("Interrupted");
            chars(c,start,len);
        }
        @Override public final void error(SAXParseException e)throws SAXException{throw e;}
        @Override public final void fatalError(SAXParseException e)throws SAXException{throw e;}
        void start(String ns,String tag,Attributes a)throws SAXException{}
        void end(String ns,String tag)throws SAXException{}
        void chars(char[] c,int start,int len)throws SAXException{}
    }
    private static final class Excerpt{
        final StringBuilder text=new StringBuilder();boolean partial,hasData;
        final int limit;
        Excerpt(int limit){this.limit=limit;}
        void add(String value){
            int room=limit-text.length();
            if(value.length()<=room){text.append(value);return;}
            int end=Math.max(room,0);
            if(end>0&&Character.isHighSurrogate(value.charAt(end-1)))end--;
            text.append(value,0,end);partial=true;
        }
        void record(String label,String value){
            if(value.trim().isEmpty())return;
            hasData=true;add(label);add(value);add("\n");
        }
    }
    private static final class Word extends Handler{
        final Excerpt out;int bodyDepth,paragraphDepth,textDepth,paragraphNumber;
        StringBuilder paragraph;
        Word(Excerpt o){out=o;}
        boolean root(String ns,String tag){return word(ns)&&tag.equals("document");}
        @Override void start(String ns,String tag,Attributes a){
            if(!word(ns))return;
            if(tag.equals("body"))bodyDepth=depth;
            if(bodyDepth==0)return;
            if(tag.equals("p")&&paragraphDepth==0){paragraphDepth=depth;paragraphNumber++;paragraph=new StringBuilder();}
            if(paragraphDepth==0)return;
            if(tag.equals("t"))textDepth=depth;
            if(tag.equals("tab"))append("\t");
            if(tag.equals("br")||tag.equals("cr"))append("\n");
        }
        void append(String value){
            if(paragraph==null)return;
            int room=out.limit-paragraph.length();
            if(value.length()>room){
                int take=Math.max(room,0);
                if(take>0&&Character.isHighSurrogate(value.charAt(take-1)))take--;
                paragraph.append(value,0,take);out.partial=true;
            }else paragraph.append(value);
        }
        @Override void end(String ns,String tag){
            if(!word(ns))return;
            if(depth==textDepth)textDepth=0;
            if(depth==paragraphDepth){
                out.record("[Paragraph "+paragraphNumber+"] ",paragraph.toString());
                paragraph=null;paragraphDepth=0;
            }
            if(depth==bodyDepth)bodyDepth=0;
        }
        @Override void chars(char[] c,int start,int len){if(textDepth>0)append(new String(c,start,len));}
    }
    private static final class SheetRef{
        final String name,id;SheetRef(String n,String i){name=n;id=i;}
    }
    private static final class Workbook extends Handler{
        final List<SheetRef> sheets=new ArrayList<>();
        boolean root(String ns,String tag){return sheet(ns)&&tag.equals("workbook");}
        @Override void start(String ns,String tag,Attributes a)throws SAXException{
            if(!sheet(ns)||!tag.equals("sheet"))return;
            String id=a.getValue(R,"id");if(id==null)id=a.getValue(RS,"id");
            String name=a.getValue("name");
            if(id==null||name==null||name.isEmpty()||name.length()>128||sheets.size()>=512)throw new SAXException("Workbook sheet limit");
            sheets.add(new SheetRef(name,id));
        }
    }
    private static final class Relations extends Handler{
        final Map<String,String> paths=new HashMap<>();
        boolean root(String ns,String tag){return P.equals(ns)&&tag.equals("Relationships");}
        @Override void start(String ns,String tag,Attributes a)throws SAXException{
            if(!P.equals(ns)||!tag.equals("Relationship"))return;
            String type=a.getValue("Type"),mode=a.getValue("TargetMode"),target=a.getValue("Target"),id=a.getValue("Id");
            if(type==null||!(type.equals(R+"/worksheet")||type.equals(RS+"/worksheet"))||
                (mode!=null&&!mode.equals("Internal"))||target==null||id==null)return;
            String path=target.startsWith("/")?target.substring(1):"xl/"+target;
            if(!path.matches("xl/worksheets/[A-Za-z0-9_.-]{1,128}\\.xml"))return;
            if(paths.put(id,path)!=null)throw new SAXException("Duplicate relationship");
        }
    }
    private static final class SharedStrings extends Handler{
        final List<String> values=new ArrayList<>();StringBuilder current;int capture;long total;
        boolean root(String ns,String tag){return sheet(ns)&&tag.equals("sst");}
        @Override void start(String ns,String tag,Attributes a)throws SAXException{
            if(!sheet(ns))return;
            if(tag.equals("si")){if(values.size()>=20000)throw new SAXException("Shared string count");current=new StringBuilder();}
            if(tag.equals("t")&&current!=null)capture=depth;
        }
        @Override void end(String ns,String tag){
            if(!sheet(ns))return;
            if(capture==depth)capture=0;
            if(tag.equals("si")&&current!=null){values.add(current.toString());current=null;}
        }
        @Override void chars(char[] c,int start,int len)throws SAXException{
            if(capture>0&&current!=null){total+=len;if(total>2000000)throw new SAXException("Shared string size");current.append(c,start,len);}
        }
    }
    private static final class Cells extends Handler{
        final Excerpt out;final String name;final List<String> shared;
        String ref,type;int cellDepth,captureDepth;StringBuilder v,f,t,capture;boolean heading;
        Cells(Excerpt out,String name,List<String> shared){this.out=out;this.name=name;this.shared=shared;}
        boolean root(String ns,String tag){return sheet(ns)&&tag.equals("worksheet");}
        @Override void start(String ns,String tag,Attributes a){
            if(!sheet(ns))return;
            if(tag.equals("c")&&cellDepth==0){
                cellDepth=depth;ref=a.getValue("r");type=a.getValue("t");
                v=new StringBuilder();f=new StringBuilder();t=new StringBuilder();capture=null;
            }else if(cellDepth>0){
                if(tag.equals("v"))capture=v;else if(tag.equals("f"))capture=f;else if(tag.equals("t"))capture=t;
                else return;
                captureDepth=depth;
            }
        }
        @Override void end(String ns,String tag)throws SAXException{
            if(!sheet(ns)||cellDepth==0)return;
            if(depth==captureDepth){capture=null;captureDepth=0;}
            if(depth!=cellDepth)return;
            String value=v.toString();
            if("s".equals(type)&&!value.isEmpty()){
                try{int index=Integer.parseInt(value.trim());if(index<0||index>=shared.size())throw new NumberFormatException();value=shared.get(index);}
                catch(NumberFormatException e){throw new SAXException("Invalid shared string reference");}
            }else if("inlineStr".equals(type))value=t.toString();
            if(f.length()>0)value="[formula: "+f+"] [cached value: "+value+"]";
            if(!value.trim().isEmpty()){
                if(!heading){out.add("\n[Sheet: "+name+"]\n");heading=true;}
                out.record((ref!=null&&ref.matches("[A-Za-z]{1,3}[0-9]{1,7}")?ref:"?")+": ",value);
            }
            cellDepth=0;capture=null;
        }
        @Override void chars(char[] c,int start,int len)throws SAXException{
            if(capture!=null){
                if((long)capture.length()+len>MAX_PART)throw new SAXException("Cell text limit");
                capture.append(c,start,len);
            }
        }
    }
}
