package com.ollamapocket.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Deterministic, offline data for the large accessible model selection cards. */
public final class ModelTileCatalog {
    private ModelTileCatalog(){}

    public static final class Tile {
        public final String id, title, description, quantization;
        public final long bytes;
        public final boolean installed, featured;
        Tile(String id,String title,String description,String quant,long bytes,
                boolean installed,boolean featured){
            this.id=id;this.title=title;this.description=description;
            this.quantization=quant;this.bytes=bytes;
            this.installed=installed;this.featured=featured;
        }
    }

    public static String familyName(String id) {
        String base=id==null?"":id.split(":",2)[0].toLowerCase(Locale.ROOT);
        switch(base){
            case "qwen3-coder":return "Qwen3 Coder";
            case "qwen3":return "Qwen3";
            case "gemma4":return "Gemma 4";
            case "gemma3":return "Gemma 3";
            case "qwen2.5":return "Qwen 2.5";
            case "llama3.2":return "Llama 3.2";
            case "deepseek-r1":return "DeepSeek R1";
            default:
                if(base.isEmpty())return "모델";
                String normalized=base.replace('_',' ').replace('-',' ');
                return normalized.substring(0,1).toUpperCase(Locale.ROOT)+normalized.substring(1);
        }
    }

    public static String status(Tile item,String installing){
        if(item.id.equals(installing))return "다운로드 중";
        return item.installed?"설치됨":"미설치";
    }

    private static final Tile[] FEATURED = {
        new Tile("qwen3-coder:30b","Qwen3 Coder",
                "코딩 · 대형 모델", "Q4_K_M", 19_000_000_000L,false,true),
        new Tile("qwen3:1.7b","Qwen3",
                "일상 대화 · 경량", "Q4_K_M",1_400_000_000L,false,true),
        new Tile("gemma4:e2b","Gemma 4",
                "최신 계열 · 호환성 확인", "미확인",4_600_000_000L,false,true),
        new Tile("qwen3:0.6b","Qwen3 Mini",
                "초경량 · 빠른 시작", "Q4_K_M",523_000_000L,false,true)
    };

    /**
     * Installed models first; add curated examples only for absent exact tags.
     * No empty placeholder cards and no fictitious installing state.
     */
    public static List<Tile> combine(List<String> installed,
            Map<String,Long> sizes,Map<String,String> quant) {
        List<Tile> result=new ArrayList<>();
        Set<String> seen=new LinkedHashSet<>();
        if(installed!=null) for(String id:installed){
            if(id==null||id.isEmpty()||!seen.add(id))continue;
            long bytes=sizes==null?0:Math.max(0,sizes.getOrDefault(id,0L));
            String q=quant==null?"미확인":quant.getOrDefault(id,"미확인");
            result.add(new Tile(id,familyName(id),"내 기기에 설치된 모델",q,bytes,true,false));
        }
        for(Tile item:FEATURED){
            if(seen.contains(item.id))continue;
            boolean familyAlreadyInstalled=false;
            if(installed!=null)for(String name:installed)
                if(name!=null&&name.split(":",2)[0].equalsIgnoreCase(
                    item.id.split(":",2)[0])){
                    familyAlreadyInstalled=true;break;
                }
            // Avoid duplicating family branding if a variant is already installed.
            if(familyAlreadyInstalled)continue;
            seen.add(item.id);
            result.add(item);
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Only device-admitted recommendations appear in the visible tile grid.
     * Installed recommended tags appear first; remaining slots use the
     * advisor's priority order. Never add unsupported/filler tiles.
     */
    public static List<Tile> forDevice(List<String> recommendedIds,
            Map<String,String> summaries,Map<String,Long> estimatedBytes,
            List<String> installed,Map<String,Long> sizes,
            Map<String,String> quantizations,int limit) {
        List<Tile> result=new ArrayList<>();
        if(recommendedIds==null||limit<=0)return Collections.emptyList();
        Set<String> already=new LinkedHashSet<>();
        Set<String> installedIds=installed==null?Collections.emptySet():
                new LinkedHashSet<>(installed);
        // Two passes: recommended models already present first, then new ones.
        for(int pass=0;pass<2;pass++){
            for(String id:recommendedIds){
                if(id==null||id.trim().isEmpty()||result.size()>=limit)continue;
                if(already.contains(id))continue;
                boolean isInstalled=installedIds.contains(id);
                if((pass==0)!=isInstalled)continue;
                long bytes=isInstalled&&sizes!=null
                        ?Math.max(0,sizes.getOrDefault(id,0L)):
                        estimatedBytes==null?0:Math.max(0,estimatedBytes.getOrDefault(id,0L));
                String quant=quantizations==null?"미확인":
                    quantizations.getOrDefault(id,"미확인");
                String explanation=summaries==null?"기기에 맞춘 추천":
                    summaries.getOrDefault(id,"기기에 맞춘 추천");
                result.add(new Tile(id,familyName(id),explanation,
                        quant,bytes,isInstalled,true));
                already.add(id);
            }
        }
        return Collections.unmodifiableList(result);
    }

}
