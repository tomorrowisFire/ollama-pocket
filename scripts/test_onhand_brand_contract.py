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
dialog=(src/"WorkbenchStyle.java").read_text().split("static void dialogActions(",1)[1].split("static TextView fieldLabel(",1)[0]
for required in ("p.dialogInk(positive,true)","p.dialogFill(false)","p.dialogFill(true)","p.dialogRipple()",
    "android.R.attr.state_focused","android.R.attr.state_enabled","b.setPaddingRelative(start,top,end,bottom)",
    "b.setMinimumWidth(Math.max(b.getMinimumWidth(),dp(a,WorkbenchVisualPolicy.MIN_TARGET_DP)))"):
    assert required in dialog,required
for forbidden in ("setOnClickListener(","setOnDismissListener(","setText(","dismiss()","setEnabled(","setVisibility("):
    assert forbidden not in dialog,forbidden
# F10A49: local tools are adjacent to the draft; no top-header duplicate or new transmission route.
chat=main.split("private void createChatPage()",1)[1].split("private void showChatActions()",1)[0]
assert "rowItem(modelRow,chatActionsButton" not in chat
assert chat.count("chatActionsButton=button(")==1
assert "rowItem(composerActions,chatActionsButton,0,4)" in chat
assert chat.index("rowItem(composerActions,attachFileButton")<chat.index("rowItem(composerActions,chatActionsButton")<chat.index("rowItem(composerActions,voiceButton")<chat.index("rowItem(composerActions, sendButton")
assert "chatActionsButton.setOnClickListener(v->showChatActions())" in chat
assert "chatActionsButton.setContentDescription(getString(R.string.chat_actions))" in chat
assert "composerActionScroll.setFillViewport(true)" in chat
assert "composerActionScroll.addView(composerActions,new FrameLayout.LayoutParams(-2,-2))" in chat
assert "composer.addView(composerActionScroll,lp(-2,-2))" in chat
assert "composerActionScroll.setLayoutParams(lp(stacked?-1:-2,-2))" in main
attachment=main.split("private void pickChatAttachment()",1)[1].split("private void chooseDocumentBatch(",1)[0]
assert attachment.count("getString(R.string.integration_vision)")==2
assert "else if(w==3){if(scope.equals(loadedHistoryScope)&&ownsLoadedScope())pickVisionForAutomation();}" in attachment
assert "else if(w==4)removeLastAttachment();" in attachment
assert "guestOnly||cloudMode||isUiBusy()||!historyReadable||!ownsLoadedScope()" in attachment
vision=main.split("void pickVisionForAutomation()",1)[1].split("private void openPickedVision()",1)[0]
assert "guestOnly||cloudMode||isUiBusy()||activeTab!=0" in vision and "visionPickerPending" in vision
assert "Intent.ACTION_OPEN_DOCUMENT" in vision and "PICK_VISION" in vision
for locale in ("values","values-ko","values-ja","values-b+zh+Hans","values-b+zh+Hant"):
    strings=ET.parse(res/locale/"strings.xml").getroot()
    assert strings.find("string[@name='integration_vision']").text
# F10A50: reflow both filter pairs without replacing their state or listeners.
pair=main.split("private void responsiveModelFilterPair(",1)[1].split("private void createModelPage()",1)[0]
for required in ("pair.setOrientation(LinearLayout.VERTICAL)","pair.addOnLayoutChangeListener(",
    "getConfiguration().fontScale","WorkspaceLayoutPolicy.stackedModelFilters(",
    "if(pair.getOrientation()==orientation)return","first.setLayoutParams(a);second.setLayoutParams(z)",
    "a.setMarginEnd(dp(WorkspaceLayoutPolicy.GAP_DP))"):
    assert required in pair,required
for forbidden in ("setAdapter(","setSelection(","setOnItemSelectedListener(","removeView(","addView("):
    assert forbidden not in pair,forbidden
for required in ("responsiveModelFilterPair(searchOptions,sourceFilter,sortFilter)",
    "responsiveModelFilterPair(filterRow,familyFilter,quantizationFilter)"):
    assert required in main,required
assert "ads.register(downloadAdSlot,AdSupport.PLACEMENT_DOWNLOAD)" in main
print("PASS: approved launcher SHA-256, both adaptive masks, light/dark dialog palettes and native UI wiring")
