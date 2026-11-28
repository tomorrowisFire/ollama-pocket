package com.ollamapocket.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure Java Ollama library-tag parser. Only registry-advertised quantizations
 * and versions already installed on the device are selectable. Never fabricate
 * q4/q5 tags from the desired quantization name.
 */
public final class OllamaQuantCatalog {
    private OllamaQuantCatalog(){}

    public static final class Variant {
        public final String tag;
        public final String quantization;
        public final boolean installed;
        public final boolean defaultTag;
        Variant(String tag,String quantization,boolean installed,boolean defaultTag){
            this.tag=tag;this.quantization=quantization;
            this.installed=installed;this.defaultTag=defaultTag;
        }
        public String display() {
            return (installed?"✓ 설치됨 · ":"") + quantization + "\n" + tag;
        }
    }

    public static boolean validBaseTag(String base){
        if(base==null||base.length()>120)return false;
        int sep=base.indexOf(':');
        return sep>0 && sep==base.lastIndexOf(':') &&
            base.substring(0,sep).matches("[A-Za-z0-9][A-Za-z0-9._-]{0,65}") &&
            base.substring(sep+1).matches("[A-Za-z0-9][A-Za-z0-9._-]{0,60}");
    }
    /** Cloud-only tags must not be offered by the local download pickers. */
    private static boolean localTag(String tag){
        if(!validBaseTag(tag))return false;
        String version=tag.substring(tag.indexOf(':')+1).toLowerCase(Locale.ROOT);
        return !version.equals("cloud")&&!version.startsWith("cloud-")&&!version.contains("-cloud");
    }
    public static String repoName(String baseTag) {
        if(!validBaseTag(baseTag))throw new IllegalArgumentException("잘못된 모델명");
        return baseTag.substring(0,baseTag.indexOf(':'));
    }

    /** True for base version or an explicitly registered variant of that size. */
    public static boolean belongsToModel(String baseTag,String actualTag) {
        if(!validBaseTag(baseTag)||actualTag==null)return false;
        return actualTag.equalsIgnoreCase(baseTag) ||
            actualTag.toLowerCase(Locale.ROOT).startsWith(
                baseTag.toLowerCase(Locale.ROOT)+"-");
    }

    private static String quantization(String baseTag,String fullTag){
        if(fullTag.equalsIgnoreCase(baseTag))return "기본 양자화";
        if(!belongsToModel(baseTag,fullTag))return null;
        String suffix=fullTag.substring(baseTag.length()+1);
        Matcher m=Pattern.compile(
           "(?i)(?:^|[-_])(q[2-8](?:_[A-Z0-9]+){0,3}|fp16|bf16|f16)$")
            .matcher(suffix);
        if(!m.find())return null;
        String quant=m.group(1).toUpperCase(Locale.ROOT);
        String modifier=suffix.substring(0,m.start());
        if(!modifier.isEmpty())modifier=modifier.replaceAll("[-_]+$","");
        return modifier.isEmpty()?quant:quant+" · "+modifier;
    }

    public static List<Variant> parse(String baseTag,String html,List<String> installed){
        if(!localTag(baseTag))throw new IllegalArgumentException("잘못된 모델명");
        if(html==null)html="";
        Set<String> known=new LinkedHashSet<>();
        if(installed!=null)for(String name:installed)
            if(localTag(name)&&belongsToModel(baseTag,name))known.add(name);
        // The official /library/{model}/tags HTML includes exact tag names,
        // as link targets and as visible text. Restrict matches to this model.
        String normalized=html.replace("%3A",":").replace("%3a",":")
            .replace("\\u003a",":");
        Pattern pattern=Pattern.compile(
            "(?i)(?<![A-Za-z0-9._:/-])"+
            Pattern.quote(baseTag)+"(?:-[A-Za-z0-9._-]{1,68})?(?![A-Za-z0-9._:/-])");
        Matcher matcher=pattern.matcher(normalized);
        int count=0;
        while(matcher.find()&&count++<1200){
            String found=matcher.group();
            if(localTag(found)&&quantization(baseTag,found)!=null)known.add(found);
        }
        // Preserve the card's original official library tag as an option;
        // it may be the default quantization without an explicit suffix.
        known.add(baseTag);
        List<Variant> list=new ArrayList<>();
        for(String name:known){
            String q=quantization(baseTag,name);
            if(q!=null)list.add(new Variant(name,q,
                 installed!=null&&installed.contains(name),name.equalsIgnoreCase(baseTag)));
        }
        list.sort((a,b)->{
            if(a.installed!=b.installed)return a.installed?-1:1;
            if(a.defaultTag!=b.defaultTag)return a.defaultTag?-1:1;
            int x=rank(a.quantization),y=rank(b.quantization);
            if(x!=y)return Integer.compare(x,y);
            return a.tag.compareToIgnoreCase(b.tag);
        });
        // Bound untrusted remote HTML expansion.
        return Collections.unmodifiableList(new ArrayList<>(list.subList(0,Math.min(30,list.size()))));
    }

    /**
     * Search results identify model FAMILIES, not a specific size. Read the
     * actual /library/{family}/tags links so users can choose size and
     * quantization together, without creating unlisted versions.
     */
    public static List<Variant> parseFamily(String family,String html,List<String> installed){
        if(family==null || !family.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,68}"))
            throw new IllegalArgumentException("지원하지 않는 모델 이름");
        if(html==null)html="";
        Set<String> tags=new LinkedHashSet<>();
        if(installed!=null)for(String name:installed){
            if(name!=null && name.toLowerCase(Locale.ROOT).startsWith(
                family.toLowerCase(Locale.ROOT)+":") && localTag(name))
                tags.add(name);
        }
        Pattern links=Pattern.compile(
            "(?i)\\bhref\\s*=\\s*[\"'](?:https?://(?:www\\.)?ollama\\.com)?/library/"+
            Pattern.quote(family)+":([A-Za-z0-9][A-Za-z0-9._-]{0,65})(?:[?#][^\"']*)?[\"']");
        Matcher link=links.matcher(html);
        int scans=0;
        while(link.find()&&scans++<1800&&tags.size()<250){
            String name=family+":"+link.group(1);
            // Cloud-only variants cannot run in the Android on-device engine.
            if(localTag(name))
                tags.add(name);
        }
        List<Variant> out=new ArrayList<>();
        for(String model:tags){
            String size=model.substring(model.indexOf(':')+1).split("-")[0];
            String base=family+":"+size;
            String kind=quantization(base,model);
            if(kind==null)kind="기본/기타 버전";
            boolean local=installed!=null&&installed.contains(model);
            out.add(new Variant(model,size+" · "+kind,local,"latest".equalsIgnoreCase(size)));
        }
        out.sort((a,b)->{
            if(a.installed!=b.installed)return a.installed?-1:1;
            double sa=parameterBillions(a.tag),sb=parameterBillions(b.tag);
            if(sa!=sb)return Double.compare(sa,sb);
            return a.tag.compareToIgnoreCase(b.tag);
        });
        return Collections.unmodifiableList(
            new ArrayList<>(out.subList(0,Math.min(40,out.size()))));
    }

    private static double parameterBillions(String tag) {
        int at=tag.indexOf(':');
        if(at<0)return Double.MAX_VALUE;
        Matcher size=Pattern.compile("(?i)^(\\d+(?:\\.\\d+)?)([bm])(?:-|$)")
             .matcher(tag.substring(at+1));
        if(!size.find())return Double.MAX_VALUE;
        try {
            double value=Double.parseDouble(size.group(1));
            return "m".equalsIgnoreCase(size.group(2))?value/1000.0:value;
        }catch(Exception ignored){return Double.MAX_VALUE;}
    }

    private static int rank(String q){
        if(q.startsWith("Q4"))return 0;
        if(q.startsWith("Q5"))return 1;
        if(q.startsWith("Q3"))return 2;
        if(q.startsWith("Q6"))return 3;
        if(q.startsWith("Q8"))return 4;
        if(q.startsWith("Q2"))return 5;
        if(q.startsWith("FP16")||q.startsWith("BF16")||q.startsWith("F16"))return 6;
        return 7;
    }
}
