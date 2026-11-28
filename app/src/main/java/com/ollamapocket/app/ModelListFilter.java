package com.ollamapocket.app;
import java.util.Locale;

/** Pure-Java model category, quantization and text filter logic. */
public final class ModelListFilter {
    private ModelListFilter() {}

    /** The user's existing choice remains available even when a partial result batch lacks it. */
    public static int retainChoice(java.util.List<String> options,String selected){
        if(options==null||options.isEmpty())throw new IllegalArgumentException("Filter options");
        if(selected==null||selected.trim().isEmpty())return 0;
        int index=options.indexOf(selected);
        if(index>=0)return index;
        options.add(selected);
        return options.size()-1;
    }
    public static String normalizeQuantization(String raw) {
        return raw==null||raw.trim().isEmpty()?"미확인":raw.trim().toUpperCase(Locale.ROOT);
    }

    public static String family(String name,String metadata) {
        String value=((name==null?"":name)+" "+(metadata==null?"":metadata)).toLowerCase(Locale.ROOT);
        if(value.contains("qwen"))return "Qwen";
        if(value.contains("llama"))return "Llama";
        if(value.contains("gemma"))return "Gemma";
        if(value.contains("deepseek"))return "DeepSeek";
        if(value.contains("mistral")||value.contains("mixtral"))return "Mistral";
        if(value.contains("phi-")||value.contains("phi3")||value.contains("phi4")
                ||value.startsWith("phi:"))return "Phi";
        if(value.contains("glm"))return "GLM";
        if(value.contains("gpt-oss"))return "GPT-OSS";
        return "기타";
    }

    public static String band(String raw) {
        String value=normalizeQuantization(raw);
        for(String q:new String[]{"Q2","Q3","Q4","Q5","Q6","Q8"})
            if(value.startsWith(q))return q;
        if(value.contains("BF16")||value.contains("F16"))return "F16/BF16";
        return "미확인".equals(value)?"미확인":"기타";
    }

    public static boolean matchesQuantization(String selected, String quant) {
        if(selected==null||"전체 양자화".equals(selected))return true;
        String normalized=normalizeQuantization(quant);
        if(selected.endsWith(" 전체"))
            return selected.substring(0,selected.length()-3).equals(band(normalized));
        return selected.equals(normalized);
    }

    public static boolean matches(String name,String description,String quant,
                                  String search,String familyChoice,String quantChoice) {
        String label=name==null?"":name;
        String detail=description==null?"":description;
        String key=search==null?"":search.trim().toLowerCase(Locale.ROOT);
        if(!key.isEmpty()&&!label.toLowerCase(Locale.ROOT).contains(key)
                &&!detail.toLowerCase(Locale.ROOT).contains(key))return false;
        if(familyChoice!=null&&!"전체 모델".equals(familyChoice)
                &&!familyChoice.equals(family(label,detail)))return false;
        return matchesQuantization(quantChoice,quant);
    }
}
