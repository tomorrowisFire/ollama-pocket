package com.ollamapocket.app;

/** Pure offline provider dashboard classification; no credential values are accepted. */
final class CloudProviderOverview {
    enum ProfileState { MISSING, INVALID, CONFIGURED }
    enum SavedEntry { PRESENT, ABSENT, UNKNOWN }
    enum CredentialState { LAN, SESSION_SAVED, SESSION_ONLY, SESSION_UNKNOWN, SAVED_ONLY, MISSING, UNKNOWN }
    private CloudProviderOverview(){}
    static ProfileState profileState(CloudApi.Provider p,boolean saved,String model,String address){
        if(p==null)return ProfileState.INVALID;
        if(!saved)return ProfileState.MISSING;
        if(!PrivacyPolicy.validModelId(model))return ProfileState.INVALID;
        try{CloudApi.requestTarget(p,address);return ProfileState.CONFIGURED;}
        catch(IllegalArgumentException invalid){return ProfileState.INVALID;}
    }
    static CredentialState credentialState(CloudApi.Provider p,boolean sessionKey,SavedEntry saved){
        if(p==null)return CredentialState.UNKNOWN;
        if(p.remote())return CredentialState.LAN;
        if(saved==null||saved==SavedEntry.UNKNOWN)return sessionKey?CredentialState.SESSION_UNKNOWN:CredentialState.UNKNOWN;
        if(saved==SavedEntry.PRESENT)return sessionKey?CredentialState.SESSION_SAVED:CredentialState.SAVED_ONLY;
        return sessionKey?CredentialState.SESSION_ONLY:CredentialState.MISSING;
    }
}
