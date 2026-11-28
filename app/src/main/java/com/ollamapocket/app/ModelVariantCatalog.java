package com.ollamapocket.app;

import java.util.*;
import java.util.regex.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Exact catalog variants; sizes are advertised estimates, never inferred from parameter count. */
public final class ModelVariantCatalog {
    private ModelVariantCatalog(){}
    public static final long GIB=1024L*1024L*1024L;
    private static final String SEG="[A-Za-z0-9][A-Za-z0-9._-]{0,98}";
    private static final Pattern QUANT=Pattern.compile("(?i)(?:^|[-_.])(Q[2-8](?:_[A-Z0-9]+){0,3}|IQ[1-4](?:_[A-Z0-9]+){0,3}|FP16|BF16|F16|MXFP4)(?=$|[-.])");
    private static final Pattern SIZE=Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s*(TB|GB|MB|KB)\\b");
    private static final Pattern ANCHOR=Pattern.compile("(?is)<a\\b[^>]*?\\bhref\\s*=\\s*(['\"])(.*?)\\1[^>]*>(.*?)</a\\s*>");
    public static final class Variant {
        public final String source,model,file,quant;
        public final long bytes;
        public Variant(String source,String model,String file,long bytes){
            this.source=source;this.model=model;this.file=file;
            this.bytes=Math.max(0,bytes);this.quant=quantization(file.isEmpty()?model:file);
        }
        public boolean hf(){return "Hugging Face".equals(source);}
        public String id(){return hf()?"hf:"+model+"#"+file:model;}
        public String family(){return hf()?model:model.split(":",2)[0];}
        public String label(){return hf()?file:model;}
        public String group(){return hf()?model+"#"+stripQuant(file):stripQuant(model);}
    }
    public static String stripQuant(String value){
        Matcher m=QUANT.matcher(value);
        if(!m.find())return value;
        return value.substring(0,m.start())+value.substring(m.end());
    }
    public static String quantization(String value){
        Matcher m=QUANT.matcher(value==null?"":value);
        return m.find()?m.group(1).toUpperCase(Locale.ROOT):"미확인";
    }
    public static boolean validFamily(String value){
        return value!=null&&!value.contains("..")&&value.matches(SEG+"(?:/"+SEG+")?");
    }
    public static String familyPath(String family){
        if(!validFamily(family))throw new IllegalArgumentException("잘못된 모델명");
        return family.contains("/")?"/"+family:"/library/"+family;
    }
    private static String path(String url){
        try {
            String value=URLDecoder.decode(url.replace("&amp;","&"),"UTF-8");
            if(value.startsWith("https://ollama.com/"))value=value.substring("https://ollama.com".length());
            else if(value.startsWith("https://www.ollama.com/"))value=value.substring("https://www.ollama.com".length());
            if(!value.startsWith("/")||value.startsWith("//"))return "";
            return value.split("[?#]",2)[0];
        }catch(Exception e){return "";}
    }
    private static String plain(String html){
        return html.replaceAll("(?is)<(?:script|style)\\b.*?</(?:script|style)>"," ")
            .replaceAll("(?s)<[^>]*>"," ").replace("&nbsp;"," ").replace("&#160;"," ").replace("\u00a0"," ");
    }
    public static long advertisedBytes(String text){
        Matcher m=SIZE.matcher(text);
        if(!m.find())return 0;
        try{
            double n=Double.parseDouble(m.group(1));
            switch(m.group(2).toUpperCase(Locale.ROOT)){
                case "TB":n*=1e12;break;
                case "GB":n*=1e9;break;
                case "MB":n*=1e6;break;
                default:n*=1e3;
            }
            return n>0&&n<Long.MAX_VALUE?(long)n:0;
        }catch(Exception e){return 0;}
    }
    /** Support official library and explicitly named community repositories, never navigation links. */
    public static List<String> parseFamilies(String html){
        Set<String> result=new LinkedHashSet<>();
        Matcher a=ANCHOR.matcher(html==null?"":html);
        while(a.find()&&result.size()<24){
            String p=path(a.group(2)), family="";
            if(p.startsWith("/library/"))family=p.substring(9);
            else if(p.startsWith("/")&&p.substring(1).contains("/")){
                String proposed=p.substring(1);
                if(plain(a.group(3)).toLowerCase(Locale.ROOT).contains(proposed.toLowerCase(Locale.ROOT)))
                    family=proposed;
            }
            if(validFamily(family)&&!family.startsWith("library/"))result.add(family);
        }
        return new ArrayList<>(result);
    }
    public static List<Variant> parseTags(String family,String html){
        String prefix=familyPath(family)+":";
        Map<String,Variant> found=new LinkedHashMap<>();
        Matcher a=ANCHOR.matcher(html==null?"":html);
        while(a.find()&&found.size()<220){
            String p=path(a.group(2));
            if(!p.regionMatches(true,0,prefix,0,prefix.length()))continue;
            String tag=p.substring(prefix.length());
            String lower=tag.toLowerCase(Locale.ROOT);
            if(!tag.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,100}")||
                lower.equals("cloud")||lower.contains("-cloud")||lower.contains("-mlx"))continue;
            String id=family+":"+tag;
            long bytes=advertisedBytes(plain(a.group(3)));
            Variant previous=found.get(id.toLowerCase(Locale.ROOT));
            if(previous==null||previous.bytes==0&&bytes>0)
                found.put(id.toLowerCase(Locale.ROOT),new Variant("Ollama",id,"",bytes));
        }
        return new ArrayList<>(found.values());
    }
    public static String readPage(String address) throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();
        c.setConnectTimeout(6500);c.setReadTimeout(9500);
        c.setRequestProperty("Accept","text/html,application/xhtml+xml");
        c.setRequestProperty("Accept-Encoding","identity");
        c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 14) AIinPocket/0.10");
        c.setUseCaches(false);
        try{
            int status=c.getResponseCode();
            if(status!=200)throw new IOException("Ollama HTTP "+status);
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int n;
                while((n=in.read(buffer))!=-1){
                    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("검색 취소");
                    if(out.size()+n>5_000_000)throw new IOException("모델 목록 크기 초과");
                    out.write(buffer,0,n);
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        }finally{c.disconnect();}
    }
    public static List<Variant> fetchTags(String family) throws Exception{
        String root="https://ollama.com"+familyPath(family);
        List<Variant> tags=parseTags(family,readPage(root+"/tags"));
        if(tags.isEmpty())tags=parseTags(family,readPage(root));
        return tags;
    }
    /** Preserve partial results with explicit failed lookup counts. Only complete batches are cacheable. */
    public static final class SearchBatch {
        public final List<Variant> values;
        public final int failedLookups;
        SearchBatch(List<Variant> values,int failures){
            if(failures<0)throw new IllegalArgumentException("Failure count");
            this.values=Collections.unmodifiableList(new ArrayList<>(values));this.failedLookups=failures;
        }
        public boolean complete(){return failedLookups==0;}
    }
    static SearchBatch collect(List<? extends Future<List<Variant>>> futures)throws InterruptedException{
        List<Variant> values=new ArrayList<>();int failures=0;
        for(Future<List<Variant>> f:futures){
            if(Thread.currentThread().isInterrupted())throw new InterruptedException("Search cancelled");
            try{
                List<Variant> found=f.get();
                if(found==null)failures++;else values.addAll(found);
            }catch(ExecutionException|CancellationException failed){failures++;}
        }
        return new SearchBatch(values,failures);
    }
    /** Parallel bounded per-family requests. Timeouts do not discard other valid results. */
    public static SearchBatch fetchFamiliesDetailed(Collection<String> families)throws Exception{
        ExecutorService workers=Executors.newFixedThreadPool(4);
        List<Callable<List<Variant>>> jobs=new ArrayList<>();
        for(String family:new LinkedHashSet<>(families))if(validFamily(family)&&jobs.size()<16)
            jobs.add(()->fetchTags(family));
        try{return collect(workers.invokeAll(jobs,28,TimeUnit.SECONDS));}
        finally{workers.shutdownNow();}
    }
    public static List<Variant> fetchFamilies(Collection<String> families)throws Exception{
        SearchBatch batch=fetchFamiliesDetailed(families);
        if(batch.values.isEmpty()&&!batch.complete())throw new IOException("Ollama tag lookup failed");
        return new ArrayList<>(batch.values);
    }
    public static SearchBatch searchOllamaDetailed(String text)throws Exception{
        String q=text.trim();
        String family=q.split("[:\\s]",2)[0];
        if(q.length()<2||q.length()>120)return new SearchBatch(Collections.emptyList(),0);
        Set<String> families=new LinkedHashSet<>();
        Exception failure=null;
        try{
            String page=readPage("https://ollama.com/search?q="+URLEncoder.encode(family,"UTF-8"));
            families.addAll(parseFamilies(page));
            if(families.isEmpty())families.addAll(OllamaLibrarySearch.parseOfficialModels(page));
        }catch(InterruptedIOException e){
            if(Thread.currentThread().isInterrupted())throw e;
            failure=e;
        }catch(Exception e){failure=e;}
        if(Thread.currentThread().isInterrupted())throw new InterruptedException("Search cancelled");
        if(validFamily(family)){
            // Exact-family lookup recovers direct tags and temporary directory parsing failures.
            Set<String> ordered=new LinkedHashSet<>();
            if(families.stream().anyMatch(x->x.equalsIgnoreCase(family))||families.isEmpty()||q.contains(":"))
                ordered.add(family.toLowerCase(Locale.ROOT));
            ordered.addAll(families);families=ordered;
        }
        if(families.isEmpty()&&failure!=null)throw failure;
        SearchBatch batch=fetchFamiliesDetailed(families);
        List<Variant> out=new ArrayList<>(batch.values);
        if(q.contains(":")){
            String needle=q.toLowerCase(Locale.ROOT);
            out.removeIf(v->!v.model.toLowerCase(Locale.ROOT).contains(needle));
        }else if(q.contains(" ")){
            String[] tokens=q.toLowerCase(Locale.ROOT).split("\\s+");
            out.removeIf(v->{for(String token:tokens)if(!v.model.toLowerCase(Locale.ROOT).contains(token))return true;return false;});
        }
        return new SearchBatch(out,batch.failedLookups+(failure==null?0:1));
    }
    public static List<Variant> searchOllama(String text)throws Exception{
        SearchBatch batch=searchOllamaDetailed(text);
        if(batch.values.isEmpty()&&!batch.complete())throw new IOException("Ollama tag lookup failed");
        return new ArrayList<>(batch.values);
    }
    /** Safety categories: 0 good estimate, 1 tight, 2 pressure, 3 unknown, 4 unsuitable. */
    public static int tier(long bytes,boolean hf,boolean installed,boolean arm64,boolean low,
                           double total,double free,double storage){
        if(!arm64)return 4;
        if(bytes<=0)return 3;
        double weight=bytes/(double)GIB, need=weight*1.15+0.65;
        if((!installed&&(!Double.isFinite(storage)||storage<(hf?2*weight+0.5:weight+0.5)))||
            !Double.isFinite(total)||total<=0||total<need+0.9)return 4;
        if(low)return 2;
        if(!Double.isFinite(free)||free<=0||free>total)return 1;
        if(free<need*0.80)return 2;
        if(free<need)return 1;
        return 0;
    }
    public static int quantPenalty(String quant){
        quant=quant==null||quant.trim().isEmpty()?"미확인":quant.trim().toUpperCase(Locale.ROOT);
        if("Q4_K_M".equals(quant))return 0;
        if("Q5_K_M".equals(quant))return 2;
        if(quant.startsWith("Q4"))return 4;
        if(quant.startsWith("Q5"))return 6;
        if(quant.startsWith("Q6"))return 10;
        if(quant.startsWith("Q8"))return 14;
        if(quant.startsWith("Q3")||quant.startsWith("IQ3"))return 18;
        if(quant.startsWith("Q2")||quant.startsWith("IQ"))return 25;
        if("미확인".equals(quant))return 60;
        return 20;
    }
    public static double score(long bytes,String quant,boolean hf,boolean installed,boolean arm64,
                               boolean low,double total,double free,double storage,int cores){
        int t=tier(bytes,hf,installed,arm64,low,total,free,storage);
        double usable=Double.isFinite(total)&&total>0?total:0;
        if(Double.isFinite(free)&&free>0&&free<=usable)usable=free;
        double target=Math.max(0.2,Math.min(Math.max(0.2,usable*0.45),cores>=8?3.5:1.7));
        double weight=Math.max(0L,bytes)/(double)GIB;
        return 1000*t+quantPenalty(quant)+Math.min(90,Math.abs(weight-target)*10)+(installed?-1:0);
    }
    public static int compareSize(long a,long b,boolean descending){
        if(a<=0||b<=0)return a<=0?(b<=0?0:1):-1;
        return descending?Long.compare(b,a):Long.compare(a,b);
    }
    public static int columns(int width,int minCard,int gap){
        return Math.max(1,(Math.max(1,width)+gap)/(Math.max(1,minCard)+gap));
    }
}
