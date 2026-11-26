package com.ollamapocket.app;

/** A read-only exact source snapshot; no translation or silent truncation. */
final class WorkspaceReadPolicy {
    static final int MAX_TEXT=160000;
    static final class Review {
        final String text,language;
        final int rate;
        private Review(String text,String language,int rate){this.text=text;this.language=language;this.rate=rate;}
        boolean matches(String current){return text.equals(current);}
    }
    private WorkspaceReadPolicy(){}
    static Review review(String text,String language,int rate){
        if(language==null||!language.equals(VoiceInteractionPolicy.language(language))||
            rate<0||rate>=VoiceInteractionPolicy.RATES.length)throw new IllegalArgumentException("Playback options");
        VoiceInteractionPolicy.finalTranscript(text,MAX_TEXT); // validate only; preserve exact reviewed text
        return new Review(text,language,rate);
    }
}
