package com.ollamapocket.app;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class VoiceOfflinePolicyTest {
    private static int checks;
    private static void check(boolean pass){if(!pass)throw new AssertionError("offline voice "+checks);checks++;}
    public static void main(String[] args){
        String[] names={null,""," ","local-a","local-b"};
        int[] ranks={-1,0,1,2,3,4,5};
        List<Set<String>> features=Arrays.asList(null,Collections.emptySet(),
            Collections.singleton("engine.feature"),Collections.singleton("notInstalled"),
            Set.of("notInstalled","engine.feature"));
        for(int active=0;active<names.length;active++)for(int rank:ranks)
            for(boolean network:new boolean[]{false,true})for(int f=0;f<features.size();f++){
                boolean eligible=active>=3&&rank>=1&&rank<=4&&!network&&(f==1||f==2);
                check(VoiceOfflinePolicy.eligible(names[active],rank,network,features.get(f))==eligible);
                for(int selected=0;selected<names.length;selected++)
                    check(VoiceOfflinePolicy.matches(names[selected],names[active],rank,network,features.get(f))==
                        (eligible&&selected==active));
            }
        // The same voice must remain installed and local before every new chunk.
        check(VoiceOfflinePolicy.matches("local-a","local-a",4,false,Collections.emptySet()));
        check(!VoiceOfflinePolicy.matches("local-a","local-b",4,false,Collections.emptySet()));
        check(!VoiceOfflinePolicy.matches("local-a","local-a",4,true,Collections.emptySet()));
        check(!VoiceOfflinePolicy.matches("local-a","local-a",4,false,Collections.singleton("notInstalled")));
        check(!VoiceOfflinePolicy.matches("local-a","local-a",-1,false,Collections.emptySet()));
        System.out.println("PASS: "+checks+" offline voice admission checks");
    }
}
