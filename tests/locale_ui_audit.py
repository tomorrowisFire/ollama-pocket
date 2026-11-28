"""Targeted guard for obvious direct Hangul UI literals; not a full localization proof."""
from pathlib import Path
import re
root=Path("app/src/main/java/com/ollamapocket/app")
names=("MainActivity.java","CoopUi.java","HuggingFaceCatalog.java","OllamaFamilyPicker.java",
       "OllamaQuantPicker.java","ModelBenchmark.java","HardwareSelfTest.java","AdSupport.java",
       "CoopService.java","OllamaForegroundService.java","CloudChatPanel.java","CloudChatActivity.java")
literal=r'"(?:[^"\\\n]|\\.)*[\uac00-\ud7a3](?:[^"\\\n]|\\.)*"'
patterns=[
    re.compile(r'\b(?:setText|setTitle|setMessage|setHint|setContentDescription|setError|setPositiveButton|setNegativeButton|setNeutralButton|toast|text|button)\s*\(\s*'+literal),
    re.compile(r'\b(?:label|button|edit|makeText)\s*\(\s*(?:activity|a|this|owner)\s*,\s*'+literal)
]
issues=[]
for name in names:
    s=(root/name).read_text(encoding="utf-8")
    for pattern in patterns:
        for hit in pattern.finditer(s):
            issues.append(f"{name}:{s.count(chr(10),0,hit.start())+1}: {hit.group(0)}")
print(f"Direct Hangul UI-literal audit: {len(names)} files; {len(issues)} findings")
for issue in issues:print(issue)
assert not issues,"Direct Hangul UI literals require explicit resource-backed display"

# Explicit cross-locale regression contract for the new form/preset/code controls.
import xml.etree.ElementTree as ET
res=Path("app/src/main/res")
variants=("values","values-ko","values-ja","values-b+zh+Hans","values-b+zh+Hant")
for variant in variants:
    strings={item.get("name"):"".join(item.itertext()) for item in ET.parse(res/variant/"strings.xml").getroot().findall("string")}
    for key in ("batch_goal_templates","batch_goal_summary","batch_goal_compare","batch_goal_extract",
                "batch_goal_review","batch_goal_code","chat_show_line_numbers","chat_hide_line_numbers","chat_code_line_count","knowledge_excerpt_count","search_partial_lookup"):
        assert strings.get(key,"").strip(),f"{variant}: missing {key}"
    for key in ("batch_default_goal","batch_goal_compare_text","batch_goal_extract_text","batch_goal_review_text","batch_goal_code_text"):
        goal=strings.get(key,"")
        assert 0<len(goal.encode("utf-16-le"))//2<=300 and goal.strip(),f"{variant}: invalid {key}"
    for key in ("editor","notice","name","digest","instruction","temperature","tokens","retain","edges","node",
                "condition","previous","next","true","false","invalid","advanced","empty","branch_summary","agent_summary","bad_summary","missing_target","row_invalid","bind_model","bound"):
        assert strings.get("integration_form_"+key,"").strip(),f"{variant}: missing form {key}"
print("Visual form, code and document goal localization: five locale resource sets verified")

# Localized display labels only: machine-readable status/stop enums remain unchanged.
status_keys=["integration_branch_ready","integration_outcome_pending","integration_outcome_verified","integration_outcome_true","integration_outcome_false","integration_outcome_uncertain","batch_status_stopped","batch_status_failed","run_stop_user","run_stop_interrupted","run_stop_unknown"]
base_strings={item.get("name"):"".join(item.itertext()) for item in ET.parse(res/"values"/"strings.xml").getroot().findall("string")}
for variant in variants:
    labels={item.get("name"):"".join(item.itertext()) for item in ET.parse(res/variant/"strings.xml").getroot().findall("string")}
    for key in status_keys:
        assert labels.get(key,"").strip(),f"{variant}: missing localized execution status {key}"
        if variant!="values":
            assert labels[key]!=base_strings[key],f"{variant}: untranslated execution status {key}"
print("Branch, conference and document execution status localization verified")

# The redesigned launcher uses the same localized group captions in all five locales.
for variant in variants:
    labels={item.get("name"):"".join(item.itertext()) for item in ET.parse(res/variant/"strings.xml").getroot().findall("string")}
    for key in ("brand_quick_actions","brand_app_preferences","brand_model_filters"):
        assert labels.get(key,"").strip(),f"{variant}: missing brand group {key}"
print("OnHand quick-action, settings and model-filter captions verified")

# All string keys, duplicate detection and Android printf argument signatures, not merely selected controls.
from collections import Counter
format_re=re.compile(r"%(?:\d+\$)?[-#+ 0,(<]*\d*(?:\.\d+)?[a-zA-Z%]")
def signature(value):
    return Counter(match.group(0) for match in format_re.finditer(value) if match.group(0)!="%%")
def load_strings(variant):
    nodes=ET.parse(res/variant/"strings.xml").getroot().findall("string")
    names=[node.get("name") for node in nodes]
    assert len(names)==len(set(names)),f"{variant}: duplicate string key"
    return {node.get("name"):"".join(node.itertext()) for node in nodes}
full_base=load_strings("values")
localized_labels=("header_cloud","coop_worker_status","engine_invalid_response","chat_stream_size_limit",
                  "model_pull_invalid_response","chat_request_cancelled")
for variant in variants:
    labels=load_strings(variant)
    assert labels.keys()==full_base.keys(),f"{variant}: missing {full_base.keys()-labels.keys()} extra {labels.keys()-full_base.keys()}"
    for key,original in full_base.items():
        assert signature(labels[key])==signature(original),f"{variant}: format placeholder mismatch {key}"
    for key in localized_labels:
        assert labels[key].strip(),f"{variant}: blank {key}"
        if variant!="values":
            assert labels[key]!=full_base[key],f"{variant}: untranslated {key}"
print(f"Full string resource parity: {len(variants)} locale sets, {len(full_base)} keys each; format signatures verified")
ui=(root/"UiText.java").read_text(encoding="utf-8")
main=(root/"MainActivity.java").read_text(encoding="utf-8")
for code,key in (("LOCAL_ENGINE_PROTOCOL","engine_invalid_response"),("CHAT_STREAM_LIMIT","chat_stream_size_limit"),
                 ("CHAT_CANCELLED","chat_request_cancelled"),("PULL_PROTOCOL","model_pull_invalid_response")):
    assert f'case "{code}":return c.getString(R.string.{key});' in ui,f"Missing localized error: {code}"
    if code!="PULL_PROTOCOL":
        assert f'if("{code}".equals(error))return getString(R.string.{key});' in main,f"Missing chat error: {code}"
print("Engine protocol, stream bound, cancellation and download errors use localized resources")
