"""Validate the selected artwork, shared Android theme and native UI wiring; not a screenshot test."""
from pathlib import Path
import hashlib
import xml.etree.ElementTree as ET
res=Path("app/src/main/res")
src=Path("app/src/main/java/com/ollamapocket/app")
a="{http://schemas.android.com/apk/res/android}"
asset=res/"drawable-nodpi/onhand_approved_icon.webp"
assert hashlib.sha256(asset.read_bytes()).hexdigest()=="8fa18315645a1293401de6126006d7553f2e14bc72c4ba9263f43dee651626fd"
assert asset.read_bytes()[:4]==b"RIFF" and asset.read_bytes()[8:12]==b"WEBP"
for filename in ("ic_launcher.xml","ic_launcher_round.xml"):
    adaptive=ET.parse(res/"mipmap-anydpi-v26"/filename).getroot()
    assert adaptive.find("foreground").get(a+"drawable")=="@drawable/onhand_launcher_inset"
inset=ET.parse(res/"drawable/onhand_launcher_inset.xml").getroot()
assert inset.get(a+"drawable")=="@drawable/onhand_approved_icon"
for edge in ("Left","Right","Top","Bottom"):
    assert inset.get(a+"inset"+edge)=="26dp"
manifest=ET.parse("app/src/main/AndroidManifest.xml").getroot().find("application")
assert manifest.get(a+"theme")=="@style/OnHandTheme.Light"
assert manifest.get(a+"icon")=="@mipmap/ic_launcher"
assert manifest.get(a+"roundIcon")=="@mipmap/ic_launcher_round"
theme=ET.parse(res/"values/onhand_brand_theme.xml").getroot()
colors={e.get("name"):e.text for e in theme.findall("color")}
styles={e.get("name"):{i.get("name"):i.text for i in e.findall("item")} for e in theme.findall("style")}
policy=(src/"WorkbenchVisualPolicy.java").read_text()
for mode in ("Light","Dark"):
    lower=mode.lower()
    activity=styles["OnHandTheme."+mode];dialog=styles["OnHandDialog."+mode]
    assert activity["android:alertDialogTheme"]=="@style/OnHandDialog."+mode
    for key in ("android:colorAccent","android:textColorPrimary","android:textColorSecondary","android:colorControlActivated"):
        assert activity[key]==dialog[key],(mode,key)
    for token in ("canvas","surface","ink","muted","accent","focus"):
        assert "0xff"+colors["onhand_"+token+"_"+lower][1:].lower() in policy
    assert dialog["android:windowBackground"]=="@drawable/onhand_dialog_"+lower
    panel=ET.parse(res/"drawable"/("onhand_dialog_"+lower+".xml")).getroot()
    assert panel.find("./shape/corners").get(a+"radius")=="24dp"
main=(src/"MainActivity.java").read_text()
for required in ("BG=p.canvas","BUBBLE=p.bubble","brandSection(content,R.string.brand_model_filters",
    "brandSection(content,R.string.brand_app_preferences","brandSection(content,R.string.display",
    "brandSection(content,R.string.local_inference","brandSection(content,R.string.privacy",
    "brandArtwork(68)","OnHandGlyphs.spinner(modelChooser)","OnHandGlyphs.tile(this,glyph)",
    "iconAction(sendButton,OnHandGlyphs.Kind.SEND)","OnHandGlyphs.refresh(v)"):
    assert required in main,required
starter=main.split("private LinearLayout starterCard(",1)[1].split("private void openKnowledgeWorkspace()",1)[0]
assert "card.setOrientation(inlineIcon?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL)" in starter
assert "card.setMinimumHeight(dp(ChatWorkspacePolicy.starterMinHeightDp(inlineIcon)))" in starter
assert "iconParams.setMarginEnd(dp(12))" in starter
assert "margin(heading,labels,0,0);margin(caption,labels,3,0)" in starter
assert 'card.setContentDescription(title+" · "+detail)' in starter
assert "card.setOnClickListener(v->action.run())" in starter
assert "glyphs[index],ChatWorkspacePolicy.starterInlineIcon(columns)" in main
for f in ("AutomationUi.java","ProjectWorkspaceUi.java","KnowledgeWorkspaceUi.java","DocumentBatchUi.java","VoiceModelsUi.java","CloudChatPanel.java"):
    source=(src/f).read_text()
    assert "WorkbenchStyle." in source and "WorkbenchWindow.fit(" in source,f
assert "WorkbenchStyle.dialogActions(owner,dialog)" in (src/"WorkbenchWindow.java").read_text()
assert "ads.register(downloadAdSlot,AdSupport.PLACEMENT_DOWNLOAD)" in main
print("PASS: approved launcher SHA-256, both adaptive masks, light/dark dialog palettes and native UI wiring")
