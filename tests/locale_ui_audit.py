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
