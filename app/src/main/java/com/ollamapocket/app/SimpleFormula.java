package com.ollamapocket.app;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Intentionally small, offline notation conversion. Unsupported TeX is kept verbatim. */
final class SimpleFormula {
    private static final String NORMAL="0123456789+-=()";
    private static final String SUPER="⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻⁼⁽⁾";
    private static final String SUB="₀₁₂₃₄₅₆₇₈₉₊₋₌₍₎";
    private static final Pattern FLAT_FRACTION=Pattern.compile("\\\\frac\\{([^{}\\\\\\n]{1,80})\\}\\{([^{}\\\\\\n]{1,80})\\}");
    private static final Pattern SCRIPT=Pattern.compile("([_^])(?:\\{([0-9+\\-=()]{1,20})\\}|([0-9]))");
    private SimpleFormula(){}
    static String display(String original){
        if(original==null||original.length()>1000)return original;
        String s=original;
        Matcher f=FLAT_FRACTION.matcher(s);StringBuffer buf=new StringBuffer();
        while(f.find())f.appendReplacement(buf,Matcher.quoteReplacement("("+f.group(1)+") / ("+f.group(2)+")"));
        f.appendTail(buf);s=buf.toString();
        String[][] names={{"alpha","α"},{"beta","β"},{"gamma","γ"},{"theta","θ"},{"pi","π"},{"sigma","σ"},{"omega","ω"},{"times","×"},{"cdot","·"},{"pm","±"},{"leq","≤"},{"geq","≥"},{"neq","≠"},{"infty","∞"}};
        for(String[] name:names)s=s.replaceAll("\\\\"+name[0]+"(?![A-Za-z])",Matcher.quoteReplacement(name[1]));
        Matcher script=SCRIPT.matcher(s);buf=new StringBuffer();
        while(script.find()){
            String digits=script.group(2)==null?script.group(3):script.group(2);
            String alphabet="^".equals(script.group(1))?SUPER:SUB;StringBuilder converted=new StringBuilder();
            for(int i=0;i<digits.length();i++)converted.append(alphabet.charAt(NORMAL.indexOf(digits.charAt(i))));
            script.appendReplacement(buf,Matcher.quoteReplacement(converted.toString()));
        }
        script.appendTail(buf);s=buf.toString();
        // Unknown macros, grouping or script notation: do not partially reinterpret.
        if(s.indexOf('\\')>=0||s.indexOf('{')>=0||s.indexOf('}')>=0||s.indexOf('^')>=0||s.indexOf('_')>=0)return original;
        return s;
    }
}
