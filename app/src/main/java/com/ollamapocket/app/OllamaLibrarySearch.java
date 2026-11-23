package com.ollamapocket.app;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Public Ollama library model directory, for searching BEYOND the six
 * on-device recommendations. Network search terms go only to ollama.com.
 * No credentials or private model list is sent.
 */
public final class OllamaLibrarySearch {
    private OllamaLibrarySearch(){}

    private static final int MAX_HTML = 2_500_000;
    private static final int MAX_RESULTS = 24;
    private static final Pattern OFFICIAL_LINK = Pattern.compile(
        "(?i)\\bhref\\s*=\\s*[\"'](?:https?://(?:www\\.)?ollama\\.com)?/library/" +
        "([A-Za-z0-9][A-Za-z0-9._-]{0,68})(?:[/?#][^\"']*)?[\"']");

    /** Input from an untrusted public webpage is strictly restricted. */
    public static List<String> parseOfficialModels(String html){
        if(html==null||html.isEmpty())return Collections.emptyList();
        Set<String> found=new LinkedHashSet<>();
        Matcher matcher=OFFICIAL_LINK.matcher(html);
        int inspections=0;
        while(matcher.find()&&inspections++<900&&found.size()<MAX_RESULTS){
            String model=matcher.group(1);
            if(model!=null && model.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,68}"))
                found.add(model.toLowerCase(Locale.ROOT));
        }
        return Collections.unmodifiableList(new ArrayList<>(found));
    }

    public static List<String> search(String text) throws Exception {
        String query=text==null?"":text.trim();
        if(query.length()<2 || query.length()>80)
            return Collections.emptyList();
        URL url=new URL("https://ollama.com/search?q="+
            URLEncoder.encode(query,StandardCharsets.UTF_8.name()));
        HttpURLConnection conn=(HttpURLConnection)url.openConnection();
        conn.setConnectTimeout(8_000);
        conn.setReadTimeout(12_000);
        conn.setUseCaches(true);
        conn.setRequestProperty("Accept","text/html");
        conn.setRequestProperty("Accept-Encoding","identity");
        conn.setRequestProperty("User-Agent","AIinPocket-Android/0.9.8");
        try{
            int status=conn.getResponseCode();
            if(status!=200)throw new Exception("Ollama 모델 검색 HTTP "+status);
            try(InputStream stream=conn.getInputStream();
                ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                byte[] data=new byte[8192];int n;
                while((n=stream.read(data))!=-1){
                    if(bytes.size()+n>MAX_HTML)throw new Exception("모델 검색 응답 크기 초과");
                    bytes.write(data,0,n);
                }
                return parseOfficialModels(bytes.toString(StandardCharsets.UTF_8.name()));
            }
        }finally{conn.disconnect();}
    }
}
