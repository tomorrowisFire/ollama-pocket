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
