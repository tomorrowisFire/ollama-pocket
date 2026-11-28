package com.ollamapocket.app;

/** Shared presentation decisions for registry and verified local model cards. */
final class ModelCardPresentation {
    enum Action { WAIT, CHOOSE_VARIANT, OPEN_CHAT }
    private ModelCardPresentation(){}
    static Action action(boolean downloading,boolean catalogVariant,boolean exactInstalled){
        if(downloading)return Action.WAIT;
        return catalogVariant||!exactInstalled?Action.CHOOSE_VARIANT:Action.OPEN_CHAT;
    }
    static String provider(boolean installed,String catalogSource,String deviceLabel,String registryLabel){
        if(deviceLabel==null||deviceLabel.isEmpty()||registryLabel==null||registryLabel.isEmpty())
            throw new IllegalArgumentException("Provider labels");
        return installed?deviceLabel:catalogSource==null||catalogSource.isEmpty()?registryLabel:catalogSource;
    }
    static String summary(String quantization,String size,String provider,String benchmarkSuffix){
        if(quantization==null||quantization.isEmpty()||size==null||size.isEmpty()||
            provider==null||provider.isEmpty()||benchmarkSuffix==null)throw new IllegalArgumentException("Card metadata");
        return quantization+" · "+size+" · "+provider+benchmarkSuffix;
    }
}
