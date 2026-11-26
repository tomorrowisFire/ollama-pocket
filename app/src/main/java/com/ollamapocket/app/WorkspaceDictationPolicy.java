package com.ollamapocket.app;

/** Exact reviewed append only. No inference, storage or speech side effects. */
final class WorkspaceDictationPolicy {
    static final class Review {
        final String original,spoken,suffix,merged;
        private Review(String original,String spoken,String suffix){
            this.original=original;this.spoken=spoken;this.suffix=suffix;this.merged=original+suffix;
        }
        boolean matches(String current){return original.equals(current);}
        String apply(String current){
            if(!matches(current))throw new IllegalStateException("Draft changed");
            return merged;
        }
    }
    private WorkspaceDictationPolicy(){}
    private static void validate(String text,boolean multiline){
        for(int i=0;i<text.length();){
            int cp=text.codePointAt(i);i+=Character.charCount(cp);
            if(cp>=0xD800&&cp<=0xDFFF)throw new IllegalArgumentException("Invalid Unicode");
            if(!multiline&&(Character.isISOControl(cp)||cp==0x2028||cp==0x2029||
                (Character.getType(cp)==Character.FORMAT&&cp!=0x200C&&cp!=0x200D)))
                throw new IllegalArgumentException("Single line required");
        }
    }
    static Review review(String original,String recognized,int limit,boolean multiline){
        if(original==null||limit<1||original.length()>limit)throw new IllegalArgumentException("Draft limit");
        validate(original,multiline);
        String spoken=VoiceInteractionPolicy.finalTranscript(recognized,limit);
        validate(spoken,multiline);
        String separator=original.isEmpty()?"":multiline?"\n":" ";
        if((long)original.length()+separator.length()+spoken.length()>limit)
            throw new IllegalArgumentException("Append limit");
        return new Review(original,spoken,separator+spoken);
    }
}
