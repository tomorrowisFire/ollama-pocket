package com.ollamapocket.app;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

/** Read-only packaged office text reader. No file extraction, external relationships or execution. */
final class OfficeText {
    static final int MAX_BYTES=32*1024*1024, MAX_EXPANDED=64*1024*1024,
        MAX_PART=8*1024*1024, MAX_RETAINED=24*1024*1024, MAX_ENTRIES=2048,
        MAX_SHEETS=32,MAX_SLIDES=256,MAX_SECTIONS=256;
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
    private enum Kind { WORD,SHEET,PRESENTATION,HWPX,ODT,ODS,ODP }
    private static Kind kind(String name){
        if(name==null)return null;
        String n=name.toLowerCase(Locale.ROOT);int dot=n.lastIndexOf('.');
        if(dot<0||n.indexOf('/')>=0||n.indexOf('\\')>=0)return null;
        switch(n.substring(dot+1)){
            case "docx":case "docm":case "dotx":case "dotm":return Kind.WORD;
            case "xlsx":case "xlsm":case "xltx":case "xltm":return Kind.SHEET;
            case "pptx":case "pptm":case "ppsx":case "ppsm":case "potx":case "potm":return Kind.PRESENTATION;
            case "hwpx":return Kind.HWPX;
            case "odt":case "ott":return Kind.ODT;
            case "ods":case "ots":return Kind.ODS;
            case "odp":case "otp":return Kind.ODP;
            default:return null;
        }
    }
    static boolean supports(String name){return kind(name)!=null;}
    static AttachmentText.Result read(String name,InputStream source)throws IOException{return read(name,source,1);}
    static AttachmentText.Result read(String name,InputStream source,int segment)throws IOException{
        if(segment<1||segment>DocumentWindow.MAX_OFFICE_SEGMENTS)throw bad(AttachmentText.Problem.RANGE);
        AttachmentText.Prefix prefix=extract(name,source,DocumentWindow.prefixLimit(segment));
        return AttachmentText.Result.window(name,DocumentWindow.select(prefix.text,segment,prefix.partial),true);
    }
    static AttachmentText.Prefix extract(String name,InputStream source,int limit)throws IOException{
        if(limit<1||limit>DocumentWindow.prefixLimit(DocumentWindow.MAX_OFFICE_SEGMENTS))throw bad(AttachmentText.Problem.RANGE);
        Kind type=kind(name);
        if(type==null)throw bad(AttachmentText.Problem.UNSUPPORTED);
        if(source==null)throw invalid();
        try{
            Map<String,byte[]> parts=archive(source,type);
            Excerpt out=new Excerpt(limit);
            if(type==Kind.WORD){
                byte[] document=parts.get("word/document.xml");
                if(document==null)throw invalid();
                parse(document,new Word(out));
            }else if(type==Kind.SHEET){
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
            }else if(type==Kind.PRESENTATION){
                readPresentation(parts,out);
            }else if(type==Kind.HWPX){
                readHwpx(parts,out);
            }else{
                byte[] content=parts.get("content.xml"),mime=parts.get("mimetype");
                if(content==null||mime==null)throw invalid();
                String actual=new String(mime,StandardCharsets.US_ASCII);
                String expected=type==Kind.ODT?"text":type==Kind.ODS?"spreadsheet":"presentation";
                if(!actual.equals("application/vnd.oasis.opendocument."+expected)&&
                        !actual.equals("application/vnd.oasis.opendocument."+expected+"-template"))throw invalid();
                parse(content,new Odf(out,type));
                out.partial=true; // style, layout and repeated cell expansion are deliberately omitted.
            }
            if(!out.hasData)throw bad(AttachmentText.Problem.EMPTY);
            return new AttachmentText.Prefix(out.text.toString(),out.partial);
        }catch(AttachmentText.Failure e){throw e;}
        catch(InterruptedIOException e){throw e;}
        catch(Exception e){
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
            throw invalid();
        }
    }
    private static boolean wanted(String path,Kind type){
        if(type==Kind.WORD)return path.equals("word/document.xml");
        if(type==Kind.SHEET)return path.equals("xl/workbook.xml")||
            path.equals("xl/_rels/workbook.xml.rels")||path.equals("xl/sharedStrings.xml")||
            path.matches("xl/worksheets/[A-Za-z0-9_.-]{1,128}\\.xml");
        if(type==Kind.PRESENTATION)return path.equals("ppt/presentation.xml")||
            path.equals("ppt/_rels/presentation.xml.rels")||path.matches("ppt/slides/slide[0-9]{1,9}\\.xml");
        if(type==Kind.HWPX)return path.matches("Contents/section[0-9]{1,9}\\.xml");
        return path.equals("content.xml")||path.equals("mimetype");
    }
    private static Map<String,byte[]> archive(InputStream source,Kind type)throws IOException{
        AttachmentText.BoundedSource raw=new AttachmentText.BoundedSource(source,MAX_BYTES);
        PushbackInputStream input=new PushbackInputStream(raw,4);
        byte[] magic=new byte[4];int got=0;
        while(got<4){int n=input.read(magic,got,4-got);if(n<0)break;got+=n;}
        if(got!=4||magic[0]!='P'||magic[1]!='K'||magic[2]!=3||magic[3]!=4){
            raw.drain();throw invalid();
        }
        input.unread(magic);
        Map<String,byte[]> parts=new HashMap<>();Set<String> names=new HashSet<>();
        long total=0,retained=0;int entries=0;
        // The caller owns the original stream. Closing the ZIP releases only its inflater.
        try(ZipInputStream zip=new ZipInputStream(new FilterInputStream(input){
            @Override public void close(){}
        })){
            ZipEntry entry;byte[] buf=new byte[8192];
            while((entry=zip.getNextEntry())!=null){
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
                if(++entries>MAX_ENTRIES)throw large();
                String name=entry.getName();
                if(name.length()>512||name.startsWith("/")||name.indexOf('\\')>=0||
                    Arrays.asList(name.split("/")).contains("..")||!names.add(name))throw invalid();
                boolean keep=!entry.isDirectory()&&wanted(name,type);
                ByteArrayOutputStream part=keep?new ByteArrayOutputStream():null;
                int count=0,n;
                while((n=zip.read(buf))!=-1){
                    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
                    if(n==0)throw invalid();
                    total+=n;count+=n;
                    if(total>MAX_EXPANDED||(keep&&(count>MAX_PART||retained+count>MAX_RETAINED)))throw large();
                    if(keep)part.write(buf,0,n);
                }
                if(keep){retained+=count;parts.put(name,part.toByteArray());}
                zip.closeEntry();
            }
            // Include central directory and trailing physical bytes in the compressed-file cap.
            raw.drain();
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
        final StringBuilder text=new StringBuilder();boolean partial,hasData,sealed;
        final int limit;
        Excerpt(int limit){this.limit=limit;}
        void add(String value){
            if(sealed){if(!value.isEmpty())partial=true;return;}
            int room=limit-text.length();
            if(value.length()<=room){text.append(value);return;}
            int end=Math.max(room,0);
            if(end>0&&Character.isHighSurrogate(value.charAt(end-1)))end--;
            text.append(value,0,end);partial=true;sealed=true;
        }
        void record(String label,String value){
            if(!AttachmentText.hasContent(value))return;
            hasData=true;add(label);add(value);add("\n");
        }
    }
    private static final class Word extends Handler{
        final Excerpt out;int bodyDepth,paragraphDepth,textDepth,paragraphNumber;
        StringBuilder paragraph;boolean paragraphSealed;
        Word(Excerpt o){out=o;}
        boolean root(String ns,String tag){return word(ns)&&tag.equals("document");}
        @Override void start(String ns,String tag,Attributes a){
            if(!word(ns))return;
            if(tag.equals("body"))bodyDepth=depth;
            if(bodyDepth==0)return;
            if(tag.equals("p")&&paragraphDepth==0){paragraphDepth=depth;paragraphNumber++;paragraph=new StringBuilder();paragraphSealed=false;}
            if(paragraphDepth==0)return;
            if(tag.equals("t"))textDepth=depth;
            if(tag.equals("tab"))append("\t");
            if(tag.equals("br")||tag.equals("cr"))append("\n");
        }
        void append(String value){
            if(paragraph==null||paragraphSealed)return;
            int room=out.limit-paragraph.length();
            if(value.length()>room){
                int take=Math.max(room,0);
                if(take>0&&Character.isHighSurrogate(value.charAt(take-1)))take--;
                paragraph.append(value,0,take);out.partial=true;paragraphSealed=true;
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
            if(AttachmentText.hasContent(value)){
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
    private static final String PPT="http://schemas.openxmlformats.org/presentationml/2006/main",
        PPT_STRICT="http://purl.oclc.org/ooxml/presentationml/main",
        DRAW="http://schemas.openxmlformats.org/drawingml/2006/main",
        DRAW_STRICT="http://purl.oclc.org/ooxml/drawingml/main",
        HS="http://www.hancom.co.kr/hwpml/2011/section",
        HP="http://www.hancom.co.kr/hwpml/2011/paragraph",
        O="urn:oasis:names:tc:opendocument:xmlns:office:1.0",
        T="urn:oasis:names:tc:opendocument:xmlns:text:1.0",
        TB="urn:oasis:names:tc:opendocument:xmlns:table:1.0",
        D="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0";
    private static boolean ppt(String ns){return PPT.equals(ns)||PPT_STRICT.equals(ns);}
    private static boolean drawing(String ns){return DRAW.equals(ns)||DRAW_STRICT.equals(ns);}
    private static void readPresentation(Map<String,byte[]> parts,Excerpt out)throws Exception{
        byte[] presentation=parts.get("ppt/presentation.xml"),rels=parts.get("ppt/_rels/presentation.xml.rels");
        if(presentation==null||rels==null)throw invalid();
        SlideOrder order=new SlideOrder();SlideRelations relations=new SlideRelations();
        parse(presentation,order);parse(rels,relations);
        if(order.ids.isEmpty())throw invalid();
        int index=0;
        for(String id:order.ids){
            if(++index>MAX_SLIDES||out.sealed){out.partial=true;break;}
            String path=relations.paths.get(id);
            if(path==null||!parts.containsKey(path)){out.partial=true;continue;}
            parse(parts.get(path),new ParagraphParts(out,"[Slide "+index+"] ",false));
        }
        out.partial=true; // speaker notes, images, diagrams, layout and other parts not included.
    }
    private static final class SlideOrder extends Handler{
        final List<String> ids=new ArrayList<>();int list;
        boolean root(String ns,String tag){return ppt(ns)&&tag.equals("presentation");}
        @Override void start(String ns,String tag,Attributes a)throws SAXException{
            if(!ppt(ns))return;
            if(tag.equals("sldIdLst"))list=depth;
            if(list>0&&tag.equals("sldId")){
                String id=a.getValue(R,"id");if(id==null)id=a.getValue(RS,"id");
                if(id==null||ids.size()>=2048||ids.contains(id))throw new SAXException("Slide identity");
                ids.add(id);
            }
        }
        @Override void end(String ns,String tag){if(depth==list)list=0;}
    }
    private static final class SlideRelations extends Handler{
        final Map<String,String> paths=new HashMap<>();
        boolean root(String ns,String tag){return P.equals(ns)&&tag.equals("Relationships");}
        @Override void start(String ns,String tag,Attributes a)throws SAXException{
            if(!P.equals(ns)||!tag.equals("Relationship"))return;
            String type=a.getValue("Type"),mode=a.getValue("TargetMode"),target=a.getValue("Target"),id=a.getValue("Id");
            if(!( (R+"/slide").equals(type)||(RS+"/slide").equals(type))||
                mode!=null&&!mode.equals("Internal")||target==null||id==null)return;
            String path=target.startsWith("/")?target.substring(1):"ppt/"+target;
            if(!path.matches("ppt/slides/slide[0-9]{1,9}\\.xml"))return;
            if(paths.put(id,path)!=null)throw new SAXException("Duplicate slide relationship");
        }
    }
    private static void readHwpx(Map<String,byte[]> parts,Excerpt out)throws Exception{
        TreeMap<Integer,byte[]> sections=new TreeMap<>();
        for(Map.Entry<String,byte[]> item:parts.entrySet()){
            String name=item.getKey();
            if(!name.matches("Contents/section[0-9]{1,9}\\.xml"))continue;
            int n=Integer.parseInt(name.substring("Contents/section".length(),name.length()-4));
            if(sections.put(n,item.getValue())!=null)throw invalid();
        }
        if(sections.isEmpty())throw invalid();
        int count=0;
        for(Map.Entry<Integer,byte[]> item:sections.entrySet()){
            if(++count>MAX_SECTIONS||out.sealed){out.partial=true;break;}
            parse(item.getValue(),new ParagraphParts(out,"[Section "+(item.getKey()+1)+"] ",true));
        }
        out.partial=true; // HWPX main section text only; no binary HWP or visual rendering.
    }
    private static final class ParagraphParts extends Handler{
        final Excerpt out;final String label;final boolean hwp;
        int paragraph,capture;StringBuilder text;
        ParagraphParts(Excerpt out,String label,boolean hwp){this.out=out;this.label=label;this.hwp=hwp;}
        boolean root(String ns,String tag){return hwp?HS.equals(ns)&&"sec".equals(tag):ppt(ns)&&"sld".equals(tag);}
        boolean matching(String ns){return hwp?HP.equals(ns):drawing(ns);}
        @Override void start(String ns,String tag,Attributes a){
            if(!matching(ns))return;
            if(tag.equals("p")){
                if(paragraph==0){paragraph=depth;text=new StringBuilder();}
                else append("\n");
            }
            if(paragraph>0&&tag.equals("t"))capture=depth;
            if(paragraph>0&&(tag.equals("br")||tag.equals("lineBreak")))append("\n");
            if(paragraph>0&&tag.equals("tab"))append("\t");
        }
        void append(String value){
            if(text==null)return;
            int room=out.limit-text.length(),end=Math.min(Math.max(0,room),value.length());
            if(end<value.length()){out.partial=true;if(end>0&&Character.isHighSurrogate(value.charAt(end-1)))end--;}
            if(end>0)text.append(value,0,end);
        }
        @Override void chars(char[] c,int start,int len){if(capture>0)append(new String(c,start,len));}
        @Override void end(String ns,String tag){
            if(depth==capture)capture=0;
            if(depth==paragraph){out.record(label,text.toString());text=null;paragraph=0;}
        }
    }
    private static final class Odf extends Handler{
        final Excerpt out;final Kind type;
        int body,paragraph,cell;String label="",cached="",formula="";
        StringBuilder text,cellText;
        Odf(Excerpt out,Kind type){this.out=out;this.type=type;}
        boolean root(String ns,String tag){return O.equals(ns)&&"document-content".equals(tag);}
        String boundedAttr(Attributes a,String ns,String key)throws SAXException{
            String value=a.getValue(ns,key);
            if(value==null)return "";
            if(value.length()>8192)throw new SAXException("ODF attribute limit");
            return value;
        }
        @Override void start(String ns,String tag,Attributes a)throws SAXException{
            if(O.equals(ns)&&tag.equals("body"))body=depth;
            if(body==0)return;
            if(type==Kind.ODS&&TB.equals(ns)&&tag.equals("table"))label="[Sheet "+boundedAttr(a,TB,"name")+"] ";
            if(type==Kind.ODP&&D.equals(ns)&&tag.equals("page"))label="[Slide "+boundedAttr(a,D,"name")+"] ";
            if(type==Kind.ODS&&TB.equals(ns)&&tag.equals("table-cell")&&cell==0){
                cell=depth;cellText=new StringBuilder();cached="";formula=boundedAttr(a,TB,"formula");
                for(String key:new String[]{"string-value","value","date-value","time-value","boolean-value"}){
                    cached=boundedAttr(a,O,key);if(!cached.isEmpty())break;
                }
            }
            if(T.equals(ns)&&(tag.equals("p")||tag.equals("h"))&&paragraph==0){
                paragraph=depth;text=new StringBuilder();
            }
            if(paragraph>0&&T.equals(ns)){
                if(tag.equals("tab"))append(text,"\t");
                if(tag.equals("line-break"))append(text,"\n");
                if(tag.equals("s")){
                    String raw=boundedAttr(a,T,"c");int n=1;
                    if(!raw.isEmpty())try{n=Integer.parseInt(raw);}catch(NumberFormatException e){throw new SAXException("ODF space count");}
                    if(n<1||n>10000)throw new SAXException("ODF space count");
                    char[] spaces=new char[n];Arrays.fill(spaces,' ');append(text,new String(spaces));
                }
            }
        }
        void append(StringBuilder b,String value){
            int end=Math.min(value.length(),Math.max(0,out.limit-b.length()));
            if(end<value.length()){out.partial=true;if(end>0&&Character.isHighSurrogate(value.charAt(end-1)))end--;}
            if(end>0)b.append(value,0,end);
        }
        @Override void chars(char[] c,int start,int len){if(paragraph>0)append(text,new String(c,start,len));}
        @Override void end(String ns,String tag){
            if(depth==paragraph){
                if(cell>0){if(cellText.length()>0)append(cellText,"\n");append(cellText,text.toString());}
                else out.record(label,text.toString());
                paragraph=0;text=null;
            }
            if(depth==cell){
                String value=cellText.length()>0?cellText.toString():cached;
                if(!formula.isEmpty())value="[formula: "+formula+"] [stored text/value: "+value+"]";
                out.record(label+"[Cell record] ",value);cell=0;cellText=null;
            }
            if(depth==body)body=0;
        }
    }

}
