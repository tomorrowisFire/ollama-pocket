package com.ollamapocket.app;

import android.content.Context;

/** Localize known application labels without changing stored identifiers, user text or wire values. */
final class UiText {
    private UiText(){}
    static String[] cityLabels(Context c){
        int[] ids={R.string.city_device,R.string.city_seoul,R.string.city_incheon,R.string.city_busan,
            R.string.city_daegu,R.string.city_daejeon,R.string.city_gwangju,R.string.city_jeju,
            R.string.city_tokyo,R.string.city_london,R.string.city_newyork};
        if(ids.length!=SolarTheme.CITY_CODES.length)throw new IllegalStateException("City label mismatch");
        String[] labels=new String[ids.length];for(int i=0;i<ids.length;i++)labels[i]=c.getString(ids[i]);
        return labels;
    }
    private static int modelKey(String v){
        switch(v){
            case "최저 사양 · 빠른 테스트용":return R.string.model_desc_minimal;
            case "한국어 입문 · 초경량":return R.string.model_desc_ko_tiny;
            case "짧은 질문 · 가벼운 대화":return R.string.model_desc_short;
            case "한국어 일상 대화 · 균형형":return R.string.model_desc_ko_balanced;
            case "영문 중심 · 경량":return R.string.model_desc_en_light;
            case "한국어 추론 · 중급":return R.string.model_desc_ko_reason;
            case "영문·일반 대화 · 중급":return R.string.model_desc_en_chat;
            case "상급 기기 · CPU 속도 주의":return R.string.model_desc_advanced;
            case "대형 모델 · 상당히 느릴 수 있음":return R.string.model_desc_large;
            case "코딩 · 대형 모델":return R.string.model_desc_coding;
            case "일상 대화 · 경량":return R.string.model_desc_daily;
            case "최신 계열 · 호환성 확인":return R.string.model_desc_compatibility;
            case "초경량 · 빠른 시작":return R.string.model_desc_quick;
            case "기기에 맞춘 추천":return R.string.model_desc_device;
            case "내 기기에 설치된 모델":case "이 기기에 설치됨":return R.string.model_installed_here;
            case "양자화 확인 필요":return R.string.model_quant_check_needed;
            case "로컬 모델":return R.string.local;
            case "미지원":return R.string.suitability_unsupported;
            case "원활":return R.string.suitability_smooth;
            case "보통":return R.string.suitability_moderate;
            case "느림":return R.string.suitability_slow;
            case "메모리 주의":return R.string.suitability_memory;
            case "권장하지 않음":return R.string.suitability_not_recommended;
            case "주의":return R.string.suitability_caution;
            case "실행 여유 예상":return R.string.suitability_expected;
            default:return 0;
        }
    }
    static String modelDescription(Context c,String value){
        if(value==null||value.isEmpty())return "";
        int key=modelKey(value);if(key!=0)return c.getString(key);
        int sep=value.lastIndexOf(" · ");
        if(sep>0){
            int first=modelKey(value.substring(0,sep)),last=modelKey(value.substring(sep+3));
            if(first!=0&&last!=0)return c.getString(first)+" · "+c.getString(last);
        }
        return value;
    }
    static String error(Context c,String value){
        if(value==null||value.isEmpty())return c.getString(R.string.coop_unknown_error);
        switch(value){
            case "연결 오류":return c.getString(R.string.error_connection);
            case "온라인 확인 실패":return c.getString(R.string.error_online_lookup);
            case "잘못된 모델명":return c.getString(R.string.model_tag_invalid);
            case "모델을 호스트에서 먼저 다운로드하세요":return c.getString(R.string.rpc_model_host_required);
            case "일반 Ollama library 모델 이름만 지원됩니다":return c.getString(R.string.rpc_model_name_limit);
            case "모델 메타데이터가 너무 큽니다":return c.getString(R.string.rpc_metadata_large);
            case "모델 메타데이터를 읽지 못함":return c.getString(R.string.rpc_metadata_read);
            case "GGUF 가중치를 찾지 못했습니다":return c.getString(R.string.rpc_weights_missing);
            default:return value;
        }
    }
}
