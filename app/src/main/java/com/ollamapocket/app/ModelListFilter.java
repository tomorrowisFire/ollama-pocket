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

    private static final java.util.regex.Pattern FAMILY=java.util.regex.Pattern.compile(
        "(?<![a-z0-9])(qwen|llama|gemma|deepseek|mistral|mixtral|phi(?=[-:0-9_./\\s]|$)|glm|gpt-oss)");
    private static String knownFamily(String value){
        java.util.regex.Matcher m=FAMILY.matcher(value==null?"":value.toLowerCase(Locale.ROOT));
        if(!m.find())return null;
        switch(m.group(1)){
            case "qwen":return "Qwen";
            case "llama":return "Llama";
            case "gemma":return "Gemma";
            case "deepseek":return "DeepSeek";
            case "mistral":case "mixtral":return "Mistral";
            case "phi":return "Phi";
            case "glm":return "GLM";
            case "gpt-oss":return "GPT-OSS";
            default:return null;
        }
    }
    public static String family(String name,String metadata) {
        // Provider "Ollama" is not a Llama family; explicit identifier wins over descriptive metadata.
        String found=knownFamily(name);
        if(found==null)found=knownFamily(metadata);
        return found==null?"기타":found;
    }
    public static String band(String raw) {
        String value=normalizeQuantization(raw);
        for(String q:new String[]{"IQ1","IQ2","IQ3","IQ4","Q2","Q3","Q4","Q5","Q6","Q8","MXFP4"})
            if(value.equals(q)||value.startsWith(q+"_"))return q;
        if("FP16".equals(value)||"BF16".equals(value)||"F16".equals(value))return "F16/BF16";
        return "미확인".equals(value)?"미확인":"기타";
    }

    public static boolean matchesQuantization(String selected, String quant) {
        if(selected==null||"전체 양자화".equals(selected))return true;
        String normalized=normalizeQuantization(quant);
        if(selected.endsWith(" 전체"))
            return selected.substring(0,selected.length()-3).equals(band(normalized));
        return normalizeQuantization(selected).equals(normalized);
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
