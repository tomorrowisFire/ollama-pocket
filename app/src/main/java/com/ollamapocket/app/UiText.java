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
    static String error(Context c,String value){return error(c,value,0);}
    private static String error(Context c,String value,int depth){
        if(depth>3)return value;
        if(value==null||value.isEmpty())return c.getString(R.string.coop_unknown_error);
        switch(value){
            case "MODEL_INSTALL_UNCONFIRMED":return c.getString(R.string.model_install_unconfirmed);
            case "PULL_INCOMPLETE":return c.getString(R.string.chat_stream_incomplete);
            case "PULL_PROTOCOL":return c.getString(R.string.model_pull_invalid_response);
            case "LOCAL_ENGINE_PROTOCOL":return c.getString(R.string.engine_invalid_response);
            case "CHAT_STREAM_LIMIT":return c.getString(R.string.chat_stream_size_limit);
            case "CHAT_CANCELLED":return c.getString(R.string.chat_request_cancelled);
            case "CHAT_STREAM_INCOMPLETE":return c.getString(R.string.chat_stream_incomplete);
            case "LOCAL_ENGINE_ERROR":return c.getString(R.string.error_connection);
            case "연결 오류":return c.getString(R.string.error_connection);
            case "온라인 확인 실패":return c.getString(R.string.error_online_lookup);
            case "잘못된 모델명":return c.getString(R.string.model_tag_invalid);
            case "모델을 호스트에서 먼저 다운로드하세요":return c.getString(R.string.rpc_model_host_required);
            case "일반 Ollama library 모델 이름만 지원됩니다":return c.getString(R.string.rpc_model_name_limit);
            case "모델 메타데이터가 너무 큽니다":return c.getString(R.string.rpc_metadata_large);
            case "모델 메타데이터를 읽지 못함":return c.getString(R.string.rpc_metadata_read);
            case "GGUF 가중치를 찾지 못했습니다":return c.getString(R.string.rpc_weights_missing);
            case "검색 취소":return c.getString(R.string.error_search_cancelled);
            case "모델 목록 크기 초과":return c.getString(R.string.error_catalog_size);
            case "분산 RPC 엔진이 없는 APK입니다. 협력용 APK를 설치하세요.":
            case "RPC 바이너리가 APK에 없습니다":case "보조 RPC 엔진 없음":
            case "RPC llama-server 실행 파일이 APK에 없습니다":return c.getString(R.string.rpc_missing_apk);
            case "Wi-Fi의 사설 IPv4 주소를 확인할 수 없습니다.":return c.getString(R.string.rpc_wifi_address);
            case "자동 검색 등록 실패 · IP 수동 입력 가능":
            case "NSD 검색 불가 · IP 직접 입력 가능":return c.getString(R.string.rpc_discovery_manual);
            case "연결 요청 거부: 코드 또는 RPC 엔진 버전 불일치":return c.getString(R.string.rpc_rejected_version);
            case "반복 요청 거부":return c.getString(R.string.rpc_rejected_repeat);
            case "호스트 노드 연결 한도 초과":return c.getString(R.string.rpc_node_limit);
            case "호스트 IPv4 주소를 확인하세요":return c.getString(R.string.rpc_host_address);
            case "사설 Wi-Fi 네트워크의 호스트만 연결할 수 있습니다":return c.getString(R.string.rpc_private_only);
            case "호스트의 연결 코드를 확인하세요":return c.getString(R.string.rpc_code_check);
            case "32바이트 세션 키가 필요합니다. 호스트 코드를 그대로 붙여넣으세요":return c.getString(R.string.rpc_code_length);
            case "호스트 세션 ID를 확인하세요":return c.getString(R.string.rpc_session_check);
            case "동일 Wi-Fi의 기기 IPv4 주소가 필요합니다":return c.getString(R.string.rpc_same_wifi);
            case "기기 과열로 협력 노드를 종료합니다":return c.getString(R.string.rpc_thermal_stop);
            case "호스트 세션을 발견하지 못했습니다. 검색 후 선택하세요":return c.getString(R.string.rpc_host_not_found);
            case "호스트가 연결을 거부함":return c.getString(R.string.rpc_host_denied);
            case "호스트 인증 또는 기기 상태 서명 검증에 실패했습니다":return c.getString(R.string.rpc_auth_failed);
            case "캐시 폴더 생성 실패":return c.getString(R.string.rpc_cache_failed);
            case "RPC worker가 시작되지 않았습니다. 기기 호환성 또는 로그 확인 필요":return c.getString(R.string.rpc_worker_failed);
            case "호스트 세션 또는 활성 보조 노드가 없습니다":return c.getString(R.string.coop_connect_first);
            case "RAM 조건을 만족하는 RPC 노드가 없습니다":return c.getString(R.string.rpc_no_ram_node);
            case "모델 로딩 중 보조 노드가 연결 해제되었습니다":return c.getString(R.string.rpc_node_disconnected);
            case "RPC 모델 로딩 시간 초과":return c.getString(R.string.rpc_load_timeout);
            case "RPC 호스트 종료 · RAM/버전/모델 호환성 확인":return c.getString(R.string.rpc_host_exited);
            default:
                String[] prefixes={"NSD 등록 실패 ","검색 시작 실패 ","호스트 연결 확인 실패: ",
                    "호스트의 GGUF 모델 확인 실패: ","Ollama 태그 조회 실패: ",
                    "GPU 오프로드 불가: ","분산 모델 로딩 실패: "};
                int[] ids={R.string.rpc_nsd_register,R.string.rpc_nsd_start,R.string.rpc_host_check_failed,
                    R.string.rpc_weights_check,R.string.error_ollama_tags,R.string.error_gpu_offload,R.string.error_distributed_load};
                for(int i=0;i<prefixes.length;i++)if(value.startsWith(prefixes[i]))
                    return c.getString(ids[i],error(c,value.substring(prefixes[i].length()),depth+1));
                return value;
        }
    }
}
