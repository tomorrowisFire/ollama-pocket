package com.ollamapocket.app;

public final class CloudProviderOverviewTest {
    private static int n;
    private static void check(boolean ok){n++;if(!ok)throw new AssertionError("provider dashboard assertion "+n);}
    private static final CloudApi.Provider OPEN=CloudApi.Provider.OPENAI, PC=CloudApi.Provider.PC_OLLAMA;
    private static final CloudProviderOverview.SavedEntry PRESENT=CloudProviderOverview.SavedEntry.PRESENT,
        ABSENT=CloudProviderOverview.SavedEntry.ABSENT, UNKNOWN=CloudProviderOverview.SavedEntry.UNKNOWN;
    public static void main(String[] args){
        for(CloudApi.Provider p:CloudApi.Provider.values()){
            check(CloudProviderOverview.profileState(p,false,"","")==CloudProviderOverview.ProfileState.MISSING);
            check(CloudProviderOverview.profileState(p,true,"model","192.168.1.9:1234")==CloudProviderOverview.ProfileState.CONFIGURED);
            check(CloudProviderOverview.profileState(p,true,"bad model","192.168.1.9:1234")==CloudProviderOverview.ProfileState.INVALID);
        }
        check(CloudProviderOverview.profileState(PC,true,"model","8.8.8.8:1234")==CloudProviderOverview.ProfileState.INVALID);
        check(CloudProviderOverview.profileState(PC,true,"model","192.168.1.9:0")==CloudProviderOverview.ProfileState.INVALID);
        check(CloudProviderOverview.profileState(OPEN,true,"model","")==CloudProviderOverview.ProfileState.CONFIGURED);
        check(CloudProviderOverview.profileState(null,true,"model","")==CloudProviderOverview.ProfileState.INVALID);
        check(CloudProviderOverview.credentialState(OPEN,true,PRESENT)==CloudProviderOverview.CredentialState.SESSION_SAVED);
        check(CloudProviderOverview.credentialState(OPEN,true,ABSENT)==CloudProviderOverview.CredentialState.SESSION_ONLY);
        check(CloudProviderOverview.credentialState(OPEN,true,UNKNOWN)==CloudProviderOverview.CredentialState.SESSION_UNKNOWN);
        check(CloudProviderOverview.credentialState(OPEN,false,PRESENT)==CloudProviderOverview.CredentialState.SAVED_ONLY);
        check(CloudProviderOverview.credentialState(OPEN,false,ABSENT)==CloudProviderOverview.CredentialState.MISSING);
        check(CloudProviderOverview.credentialState(OPEN,false,UNKNOWN)==CloudProviderOverview.CredentialState.UNKNOWN);
        check(CloudProviderOverview.credentialState(OPEN,false,null)==CloudProviderOverview.CredentialState.UNKNOWN);
        check(CloudProviderOverview.credentialState(PC,true,PRESENT)==CloudProviderOverview.CredentialState.LAN);
        check(CloudProviderOverview.credentialState(PC,false,ABSENT)==CloudProviderOverview.CredentialState.LAN);
        check(CloudProviderOverview.credentialState(null,true,PRESENT)==CloudProviderOverview.CredentialState.UNKNOWN);
        CloudUsageLedger l=new CloudUsageLedger();
        check(l.trackedScopes()==0&&l.canTrack("OPENAI"));
        l.record("OPENAI",1000000,500000,2,4);
        l.record("OPENAI",-1,-1,2,4);
        l.record("OPENAI@192.168.1.9:1234",50,50,2,4);
        l.record("OPENAI_OTHER",100,200,2,4);
        l.record("GEMINI",90,10,1,1);
        l.record("PC_OLLAMA@192.168.1.1:1234",20,3,-1,-1);
        l.record("PC_OLLAMA@192.168.1.2:1234",30,4,-1,-1);
        l.record("PC_OLLAMA@192.168.1.1:1234",-1,2,-1,-1);
        l.record("PC_OLLAMA",50,50,1,1);
        l.record("PC_OLLAMA@",60,60,1,1);
        l.record("PC_OLLAMA_OTHER@192.168.1.1:1234",70,70,-1,-1);
        l.record("PC_LM_STUDIO@192.168.1.1:1234",100,10,-1,-1);
        CloudUsageLedger.Totals h=l.snapshotForProvider("OPENAI",false);
        check(h.requests==2&&h.input==1000000&&h.output==500000);
        check(h.estimated==4&&h.unknown==1&&h.unpriced==1);
        h.requests=0;h.estimated=0;h.unknown=0;
        check(l.get("OPENAI").requests==2&&l.get("OPENAI").estimated==4&&l.get("OPENAI").unknown==1);
        CloudUsageLedger.Totals lan=l.snapshotForProvider("PC_OLLAMA",true);
        check(lan.requests==3&&lan.input==50&&lan.output==9&&lan.unknown==1&&lan.unpriced==3&&lan.estimated==0);
        check(l.snapshotForProvider("PC_LM_STUDIO",true).requests==1);
        check(l.snapshotForProvider("GEMINI",false).input==90);
        check(l.snapshotForProvider("CLAUDE",false).requests==0);
        check(l.snapshotForProvider("PC_OLLAMA@",true).requests==0);
        check(l.snapshotForProvider(null,true).requests==0);
        l.clear();check(l.trackedScopes()==0&&l.snapshotForProvider("OPENAI",false).requests==0);
        l.record("PC_OLLAMA@1",Long.MAX_VALUE,Long.MAX_VALUE,-1,-1);
        l.record("PC_OLLAMA@2",20,30,-1,-1);
        lan=l.snapshotForProvider("PC_OLLAMA",true);
        check(lan.input==Long.MAX_VALUE&&lan.output==Long.MAX_VALUE);
        l.clear();
        check(!l.canTrack(null)&&!l.canTrack(""));
        for(int i=0;i<CloudUsageLedger.MAX_SCOPES;i++)l.record("PC_OLLAMA@"+i,1,2,-1,-1);
        check(l.trackedScopes()==128);
        check(l.canTrack("PC_OLLAMA@0")&&!l.canTrack("OPENAI"));
        check(!l.allows("OPENAI",-1,2,4)&&!l.allows("OPENAI",10,2,4));
        l.record("OPENAI",1,1,2,4);check(l.snapshotForProvider("OPENAI",false).requests==0);
        l.record("PC_OLLAMA@0",3,4,-1,-1);check(l.snapshotForProvider("PC_OLLAMA",true).requests==129);
        l.clear();check(l.canTrack("OPENAI")&&l.trackedScopes()==0);
        System.out.println("PASS: "+n+" provider dashboard, credential-state and bounded ledger assertions");
    }
}
