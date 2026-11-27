package com.ollamapocket.app;

import java.util.Set;

/** Admission from Android TTS metadata, not proof of a third-party engine's behavior. */
final class VoiceOfflinePolicy {
    // Android TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED.
    static final String NOT_INSTALLED = "notInstalled";
    private VoiceOfflinePolicy(){}

    static boolean eligible(String name,int localeRank,boolean networkRequired,Set<String> features){
        return name!=null&&!name.trim().isEmpty()&&localeRank>=1&&localeRank<=4&&
            !networkRequired&&features!=null&&!features.contains(NOT_INSTALLED);
    }
    static boolean matches(String selectedName,String activeName,int localeRank,
                           boolean networkRequired,Set<String> features){
        return selectedName!=null&&selectedName.equals(activeName)&&
            eligible(activeName,localeRank,networkRequired,features);
    }
}
