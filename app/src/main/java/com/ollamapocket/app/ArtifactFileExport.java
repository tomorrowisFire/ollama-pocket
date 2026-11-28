package com.ollamapocket.app;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Immutable, bounded UTF-8 artifact prepared for a user-selected document destination. */
final class ArtifactFileExport {
    static final int MAX_CHARS=320000, MAX_BYTES=640000;
    enum Format {
        CODE("code","txt","text/plain"), MARKDOWN("table","md","text/markdown"),
        TABLE_CSV("table","csv","text/csv"), CHART_CSV("chart","csv","text/csv"),
        CHART_HTML("chart","html","text/html"),
        REPORT_JSON("report","json","application/json"), REPORT_TEXT("report","txt","text/plain"),
        PROJECT_TEXT("project-version","txt","text/plain");
        final String stem,extension,mime;
        Format(String stem,String extension,String mime){this.stem=stem;this.extension=extension;this.mime=mime;}
    }
    final Format format;
    private final byte[] bytes;
    private ArtifactFileExport(Format format,byte[] bytes){this.format=format;this.bytes=bytes;}
    static ArtifactFileExport prepare(Format format,String text){
        if(format==null||text==null||text.length()>MAX_CHARS||text.indexOf('\0')>=0)
            throw new IllegalArgumentException("Artifact bounds");
        try{
            ByteBuffer encoded=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text));
            if(encoded.remaining()>MAX_BYTES)throw new IllegalArgumentException("Artifact byte limit");
            byte[] bytes=new byte[encoded.remaining()];encoded.get(bytes);
            return new ArtifactFileExport(format,bytes);
        }catch(CharacterCodingException e){throw new IllegalArgumentException("Invalid artifact Unicode",e);}
    }
    int size(){return bytes.length;}
    String filename(long timestamp){
        if(timestamp<0)throw new IllegalArgumentException("Invalid timestamp");
        return "OnHand-AI-"+format.stem+"-"+timestamp+"."+format.extension;
    }
    void writeTo(OutputStream output)throws IOException{
        if(output==null)throw new IOException("Missing document destination");
        // Do not expose the snapshot's backing bytes to a provider implementation.
        output.write(bytes.clone());output.flush();
    }
    /** Convert the existing formula-neutralized TSV representation, not raw/untrusted table cells. */
    static String csvFromSafeTsv(String tsv){
        if(tsv==null||tsv.length()>160000||tsv.indexOf('\r')>=0||tsv.indexOf('\0')>=0)
            throw new IllegalArgumentException("TSV bounds");
        StringBuilder out=new StringBuilder();int columns=-1,cells=0;
        for(String row:tsv.split("\n",-1)){
            String[] fields=row.split("\t",-1);
            if(columns==-1)columns=fields.length;
            if(columns<2||columns>10||fields.length!=columns||cells+columns>96)
                throw new IllegalArgumentException("Table shape");
            cells+=columns;
            for(int i=0;i<fields.length;i++){
                if(i>0)out.append(',');
                out.append('"').append(fields[i].replace("\"","\"\"")).append('"');
            }
            out.append("\r\n");
            if(out.length()>MAX_CHARS)throw new IllegalArgumentException("CSV bounds");
        }
        return out.toString();
    }
}
