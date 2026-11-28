"""Validate the selected artwork, shared Android theme and native UI wiring; not a screenshot test."""
from pathlib import Path
import hashlib
import xml.etree.ElementTree as ET
res=Path("app/src/main/res")
src=Path("app/src/main/java/com/ollamapocket/app")
a="{http://schemas.android.com/apk/res/android}"
# User-selected right-hand icon, traced at the original 1254px resolution.
import math
import re
asset=res/"drawable/onhand_approved_icon.xml"
foreground=res/"drawable/onhand_launcher_inset.xml"
assert hashlib.sha256(asset.read_bytes()).hexdigest()=="ea6fc87f399009eb5817b879c65d7e677da534179c2aebf086494a601e59a0d3"
assert hashlib.sha256(foreground.read_bytes()).hexdigest()=="72dafff8cd5e53f885873792221069d19d5fba1ed0ca3e98b41dcb2f9f03c833"
assert not (res/"drawable-nodpi/onhand_approved_icon.webp").exists()
assert not (res/"drawable-nodpi/ic_launcher_foreground.png").exists()
vector=ET.parse(asset).getroot()
layer=ET.parse(foreground).getroot()
assert vector.tag==layer.tag=="vector"
assert vector.get(a+"width")==vector.get(a+"height")=="512dp"
assert layer.get(a+"width")==layer.get(a+"height")=="108dp"
for root in (vector,layer):
    assert root.get(a+"viewportWidth")==root.get(a+"viewportHeight")=="100"
group=layer.find("group")
assert group.get(a+"pivotX")==group.get(a+"pivotY")=="50"
assert group.get(a+"scaleX")==group.get(a+"scaleY")=="0.56"
paths=group.findall("path")
assert [p.get(a+"name") for p in paths]==["arch","AI","hand","word","suffix"]
assert len(vector.findall("path"))==6
for p in paths:
    assert p.get(a+"fillType")=="evenOdd"
    master_path=next(x for x in vector.findall("path") if x.get(a+"name")==p.get(a+"name"))
    assert p.get(a+"pathData")==master_path.get(a+"pathData")
    # All commands in the artwork are absolute M/L/C/Z. A cubic is inside
    # the convex hull of its control points, so this is a conservative bound.
    data=p.get(a+"pathData")
    assert set(re.findall(r"[A-Za-z]",data))<={"M","L","C","Z"}
    coords=[float(x) for x in re.findall(r"-?\d+(?:\.\d+)?",data)]
    assert len(coords)%2==0 and coords
    assert max(math.hypot(x-50,y-50)*.56*1.08 for x,y in zip(coords[::2],coords[1::2]))<33
    assert p.find(".//gradient") is not None
for filename in ("ic_launcher.xml","ic_launcher_round.xml"):
    adaptive=ET.parse(res/"mipmap-anydpi-v26"/filename).getroot()
    assert adaptive.find("foreground").get(a+"drawable")=="@drawable/onhand_launcher_inset"
    assert adaptive.find("background").get(a+"drawable")=="@color/launcher_background"
    fallback=ET.parse(res/"mipmap-anydpi"/filename).getroot()
    assert fallback.get(a+"drawable")=="@drawable/onhand_approved_icon"
    for density in ("mdpi","hdpi","xhdpi","xxhdpi","xxxhdpi"):
        assert not (res/("mipmap-"+density)/(filename[:-4]+".png")).exists()
assert ET.parse(res/"values/launcher_colors.xml").getroot().find("color").text=="#FCF1EA"
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
    "OnHandGlyphs.tile(this,glyph)",
    "iconAction(sendButton,OnHandGlyphs.Kind.SEND)","OnHandGlyphs.refresh(v)"):
    assert required in main,required
# F10A59: chat-first home replaces the large hero and six descriptive cards.
starter=main.split("private Button starterChip(",1)[1].split("private void openKnowledgeWorkspace()",1)[0]
for required in ("ChatWorkspacePolicy.starterMinHeightDp()", "chip.setMinimumWidth(dp(48))",
    "chip.setSingleLine(false)", "chip.setEllipsize(null)", "WorkbenchStyle.quickAction(this,chip)",
    "OnHandGlyphs.label(chip,glyph,false)", 'chip.setContentDescription(detail.isEmpty()?title:title+" · "+detail)',
    "chip.setOnClickListener(v->action.run())"):
    assert required in starter,required
home=main.split("private void populateStarterCards(",1)[1].split("private void setChatBusy(",1)[0]
for required in ("R.string.workspace_starter_write,R.string.workspace_starter_docs",
    "R.string.integration_vision,R.string.workspace_starter_automation",
    "ChatWorkspacePolicy.STARTER_COUNT","pickVisionForAutomation()",
    "chooseDocumentBatch(loadedHistoryScope)","openLocalAutomation()"):
    assert required in home,required
assert "R.string.workspace_starter_plan" not in home and "R.string.workspace_starter_code" not in home
assert "brandArtwork(68)" not in main
assert "messages.setGravity(Gravity.CENTER_VERTICAL)" in main
assert "refreshStarterVisibility();messages.removeAllViews();messages.setGravity(Gravity.TOP)" in main
tools=main.split("private void showChatActions()",1)[1].split("private View workbenchCard(",1)[0]
assert "() ->fillStarter" not in tools
for required in ("()->fillStarter(getString(R.string.example_plan))", "()->fillStarter(getString(R.string.example_code))",
    "this::openCloud","Math.min(g*3+3,titles.length)","closeWorkbench();actions[index].run()"):
    assert required in tools,required
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
# F10A67: all four actual selectors share one measured, scrollable line.
filter_layout=main.split("private void addModelFilter(",1)[1].split("private void createModelPage()",1)[0]
for required in ("params.setMarginEnd(dp(WorkspaceLayoutPolicy.FILTER_GAP_DP))",
    "line.addView(filter,params)","paint.measureText(value)","WorkspaceLayoutPolicy.filterChipWidthPx(",
    "filter.getPaddingStart()+filter.getPaddingEnd()","params.width!=width","filter.setLayoutParams(params)"):
    assert required in filter_layout,required
for forbidden in ("setAdapter(","setSelection(","setOnItemSelectedListener(","removeView("):
    assert forbidden not in filter_layout,forbidden
# F10A51: shared status precedence and contrast in both grid/list renderers.
status=main.split("private WorkbenchVisualPolicy.ModelStatus modelCardStatus(",1)[1].split("private LinearLayout buildModelTile(",1)[0]
assert "item.id.equals(downloadingCardId)&&pullBusy" in status
assert "p.modelStatusInk(status)" in status and "p.modelStatusFill(status)" in status
assert "modelCardStatusLabel(status)" in status and "R.string.model_recommend_mark" in status
grid=main.split("private LinearLayout buildModelTile(",1)[1].split("private LinearLayout buildModelListRow(",1)[0]
listing=main.split("private LinearLayout buildModelListRow(",1)[1].split("private void updateTileDownloadProgress()",1)[0]
for block,compact in ((grid,"true"),(listing,"false")):
    assert "modelCardStatus(item,selected)" in block
    assert "modelCardAccessibility(item,cardStatus)" in block
    assert "modelStatusBadge(cardStatus,"+compact+")" in block
    assert "card.setOnClickListener(v->activateTile(item))" in block
    assert "tileDownloadBars.put(item.id,progress)" in block
# F10A52: one font-aware budget for grouped and ungrouped grids, no forced minimum width.
assert "uiTextScale*Math.max(1f,getResources().getConfiguration().fontScale)" in main.split("private int modelCardMinimumPx()",1)[1].split("private LinearLayout buildModelTile(",1)[0]
assert main.count("minCard=modelCardMinimumPx()")==2
assert main.count("WorkspaceLayoutPolicy.modelCardHeightPx(sidePx,minCard)")==2
assert "Math.max(dp(110),(space-gap*(columns-1))/columns)" not in main
assert "TextView variant=text(exact,11,secondary,false)" in grid
assert "variant.setEllipsize(TextUtils.TruncateAt.MIDDLE)" in grid
assert "title.setMaxLines(2)" in listing and "details.setMaxLines(2)" in listing
# F10A53: isolated popup, cloud form and spinner integration; no new transmission path.
window=(src/"WorkbenchWindow.java").read_text()
assert "fit(owner,dialog,false)" in window and "fit(owner,dialog,true)" in window
assert "compact?WorkbenchLayoutPolicy.compactDialogWidthDp(usableDp,scale):WorkbenchLayoutPolicy.dialogWidthDp(usableDp,scale)" in window
for filename,targets in (("OllamaQuantPicker.java",("loading","picker","help")),("OllamaFamilyPicker.java",("loading","chooser","errorDialog"))):
    picker=(src/filename).read_text()
    for target in targets:
        assert "WorkbenchWindow.fitCompact(activity,"+target+")" in picker
    assert ".setOnDismissListener(" in picker and ".setOnCancelListener(" in picker
cloud=(src/"CloudChatActivity.java").read_text()
assert "OnHandGlyphs.spinner(provider)" in cloud
assert "WorkbenchStyle.fieldLabel(this,parent,label,field)" in cloud
for field,label in (("model","cloud_model"),("key","cloud_key"),("prompt","cloud_prompt")):
    assert "addLabeledField(fields,"+field+",R.string."+label+")" in cloud
for required in ("FLAG_SECURE","TYPE_TEXT_VARIATION_PASSWORD","key.setSaveEnabled(false)","View.IMPORTANT_FOR_AUTOFILL_NO",
    "setPositiveButton(R.string.send,(d,w)->request(p,id,token,q))","WorkbenchWindow.fitCompact(this,confirmation)","key.setText(\"\")"):
    assert required in cloud,required
glyph=(src/"OnHandGlyphs.java").read_text().split("static void spinner(Spinner view)",1)[1].split("static void refresh(View v)",1)[0]
for required in ("state_enabled","state_focused","spinnerSurface(c,p.field,p.focus,2)","setLayerInsetRelative(","setPaddingRelative(","setMinimumHeight(dp(c,48))"):
    assert required in glyph,required
for forbidden in ("setAdapter(","setSelection(","setOnItemSelectedListener("):
    assert forbidden not in glyph,forbidden
# F10A54: synchronize card status semantics and live/history cloud presentation.
assert "case DOWNLOADING:return modelDownloadStatusLabel();" in main
assert main.count("registerModelDownloadAccessibility(item,card);")==2
assert "tileDownloadAccessibility.clear();" in main
a11y=main.split("private void registerModelDownloadAccessibility(",1)[1].split("private TextView modelStatusBadge(",1)[0]
assert "if(!TextUtils.equals(card.getContentDescription(),next))card.setContentDescription(next)" in a11y
progress=main.split("private void updateTileDownloadProgress()",1)[1].split("/** Search both providers",1)[0]
for required in ("String next=modelDownloadStatusLabel()", "if(!TextUtils.equals(status.getText(),next))", "tileDownloadAccessibility.get(downloadingCardId)", "if(refreshAccessibility!=null)refreshAccessibility.run()"):
    assert required in progress,required
panel=(src/"CloudChatPanel.java").read_text()
for required in ("TextView text=conversationText(user,true)", "TextView question=conversationText(true,false)",
    "TextView partial=conversationText(false,true)", "TextView fragment=conversationText(false,true)", "p.text,p.field,scale",
    "status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE)",
    "conversations.setContentDescription(getResources().getString(R.string.cloud_conversations))",
    "cloudHeaderParams(hs,true)", "cloudHeaderParams(hs,false)",
    "content.setAutoLinkMask(0)", "content.setTextIsSelectable(false)", "content.setSaveEnabled(false)",
    "request(p,id,target,q,s,revision,messages)"):
    assert required in panel,required
assert "showAction(dialog);WorkbenchWindow.fit(owner,dialog)" not in panel
# F10A55: table typography, heading hierarchy and compact settings selections.
rich=(src/"ChatRichView.java").read_text()
for required in ("card.addView(heading,new LinearLayout.LayoutParams(-1,-2))","heading.setAccessibilityHeading(true)",
    "cell.setAccessibilityHeading(true)","WorkbenchLayoutPolicy.tableColumnWidthDp(len,combinedScale)",
    "horizontal(c,table)","SensitiveClipboard.text(","ChatBlocks.spreadsheetTsv(b)"):
    assert required in rich,required
assert "header.addView(heading" not in rich
assert "WorkbenchWindow.fitCompact(this,dialog)" in main.split("private void showCompactSettingsDialog(",1)[1].split("private void chooseWorkspaceMode()",1)[0]
for control in ("theme","cityChoice","typeSize","license"):
    assert control+".setOnClickListener(v->showCompactSettingsDialog(new AlertDialog.Builder(this)" in main
assert "showCompactSettingsDialog(new AlertDialog.Builder(this).setTitle(R.string.workspace_mode)" in main
# F10A56: native chart presentation, no change to typed export or clipboard safeguards.
chart=(src/"ChartArtifactView.java").read_text()
for required in ("new ChartPlotPolicy.Axis(minimum,maximum)","axis.y(0)*span","axis.y(chart.points.get(i).value)*span",
    "palette.primary,palette.muted","frame.setColor(palette.surface)","WorkbenchStyle.button((android.app.Activity)c,b,label)",
    "b.setTextSize(13*scale)","chart::csv","chart::html","SensitiveClipboard.text(",
    "plot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO)","table.setTextIsSelectable(true)"):
    assert required in chart,required
assert "h/2" not in chart and "setAlpha(100)" not in chart
rich_action=rich.split("private static Button action(",1)[1].split("private static void section(",1)[0]
assert rich_action.index("WorkbenchStyle.button(")<rich_action.index("b.setTextSize(13*scale)")
glyph_source=(src/"OnHandGlyphs.java").read_text()
for resource in ("chat_copy_math","chat_copy_spreadsheet","integration_copy_csv","integration_copy_html"):
    assert "id==R.string."+resource in glyph_source
# F10A57: voice cards and status-aware native glyphs, no change to storage or consent.
voice=(src/"VoiceModelsUi.java").read_text()
for required in ("WorkbenchStyle.card(owner,section,false)","OnHandGlyphs.accentLabel(name,OnHandGlyphs.Kind.MIC)",
    "AdaptiveAnswerActions actions=new AdaptiveAnswerActions(owner)","WorkbenchWindow.fitCompact(owner,child)",
    "palette.dangerText:palette.modelStatusInk(visual)","row.install.setEnabled(!busy&&!present&&supported)",
    "row.remove.setEnabled(!busy&&present)","setPositiveButton(R.string.voice_model_download_agree,(d,w)->run(model,false))",
    "setPositiveButton(R.string.voice_model_remove,(d,w)->run(model,true))","store.install(model,operation::get",
    "store.cancelPendingInstall()","OPEN.get(owner)==this"):
    assert required in voice,required
for required in ("d.labelColors=v.getTextColors()","d.setState(v.getDrawableState())",
    "labelColors.getColorForState(state,labelColors.getDefaultColor())","Math.round(alpha*stateAlpha/255f)",
    "id==R.string.voice_model_install","id==R.string.voice_model_remove","id==R.string.voice_model_license"):
    assert required in glyph_source,required
assert "assert " not in voice
# F10A58: independent workbench confirmation and attachment presentation scope.
for filename in ("ProjectWorkspaceUi.java","KnowledgeWorkspaceUi.java"):
    source=(src/filename).read_text()
    assert "secure(d);d.show();secure(d);WorkbenchWindow.fitCompact(owner,d)" in source,filename
    assert "confirmation==x&&!WORK.busy())action.run()" in source,filename
batch=(src/"DocumentBatchUi.java").read_text()
assert "d.show();secure(d);WorkbenchWindow.fit(owner,d);\n    }\n    private void enabled()" in batch
assert "chooser.show();secure(chooser);WorkbenchWindow.fitCompact(owner,chooser)" in batch
assert "visible()&&dialog==screen&&confirmation==x&&!busy())action.run()" in batch
assert main.count("WorkbenchWindow.fitCompact(this,d);d.getButton(AlertDialog.BUTTON_POSITIVE)")==2
assert main.count("OnHandGlyphs.spinner(encoding);WorkbenchStyle.fieldLabel(this,panel,R.string.attachment_encoding,encoding)")==2
for required in ("WorkbenchWindow.fitCompact(this,confirmation)","WorkbenchWindow.fitCompact(this,chooser)",
    "WorkbenchWindow.fitCompact(this,setup)","WorkbenchWindow.fitCompact(this,copyReview)",
    "WorkbenchStyle.fieldLabel(this,panel,R.string.attachment_segment,segment)",
    "WorkbenchStyle.fieldLabel(this,panel,R.string.attachment_pdf_first,page)",
    "WorkbenchStyle.fieldLabel(this,panel,R.string.attachment_ocr_page,page)",
    "OnHandGlyphs.spinner(language)","WorkbenchWindow.fit(this,dialog);\n        saveKnowledge.setOnClickListener",
    "SensitiveClipboard.text(preview.sourceName()"):
    assert required in main,required
# F10A59R1: top-right new chat pictogram and model identity subtitle.
assert "iconAction(fresh,OnHandGlyphs.Kind.NEW_CHAT)" in main
assert "fresh.setContentDescription(getString(R.string.new_chat))" in main
assert "fresh.setTooltipText(getString(R.string.new_chat))" in main
assert "fresh.setOnClickListener(v -> newChat())" in main
assert "newChatShortcut.setText(" not in main
assert "topPageSubtitle=text(getString(R.string.coop_select_model),12,MUTED,false)" in main
header=main.split("void refreshChatHeader(){",1)[1].split("private void fillStarter(",1)[0]
for required in ("cloudPanel.selectedModelName()", "knownModels.contains(currentModel)",
    "getString(cloudMode?R.string.coop_select_model:R.string.models)", "topPageSubtitle.setText(label)",
    "topPageSubtitle.setTooltipText(selected.isEmpty()?null:selected)"):
    assert required in header,required
assert "currentSessionId()" not in header and "getPreferences(" not in header
assert "modelChooser" not in main
assert "if (messages == null) return;\n        refreshChatHeader();" in main
cloud=(src/"CloudChatPanel.java").read_text()
assert "String selectedModelName(){return model;}" in cloud
assert "if(active&&owner instanceof MainActivity)((MainActivity)owner).refreshChatHeader();" in cloud
new_glyph=(src/"OnHandGlyphs.java").read_text().split("case NEW_CHAT:",1)[1].split("case MORE:",1)[0]
assert "bubble.close();c.drawPath(bubble,pen)" in new_glyph
assert "line(c,12,7.5f,12,14.5f);line(c,8.5f,11,15.5f,11)" in new_glyph
for locale in ("values","values-ko","values-ja","values-b+zh+Hans","values-b+zh+Hant"):
    assert ET.parse(res/locale/"strings.xml").getroot().find("string[@name='coop_select_model']").text
# F10A60: single-surface composer, stateful circular actions and compact tools.
composer_skin=main.split("private void styleLocalComposer()",1)[1].split("private void createChatPage()",1)[0]
for required in ("WorkbenchStyle.composerSurface(this,chatComposer,chatInput.hasFocus())",
    "WorkbenchStyle.composerInput(this,chatInput)","attachFileButton,chatActionsButton,voiceButton",
    "sendButton,stopChatButton","WorkbenchStyle.composerAction(this,action,false)","WorkbenchStyle.composerAction(this,action,true)"):
    assert required in composer_skin,required
assert "chatInput.setOnFocusChangeListener((v,focused)->WorkbenchStyle.composerSurface(this,chatComposer,focused))" in main
assert "styleLocalComposer(); // Restore stateful colors" in main
composer_style=(src/"WorkbenchStyle.java").read_text().split("static void composerSurface(",1)[1].split("static void field(",1)[0]
for required in ("input.setBackgroundColor(Color.TRANSPARENT)","p.composerActionFill(primary,false)",
    "p.composerActionInk(primary,true)","p.composerActionFocus(primary)","state_focused","state_enabled","OnHandGlyphs.refresh(button)"):
    assert required in composer_style,required
for forbidden in ("setOnClickListener(","setEnabled(","setText(","setFilters("):
    assert forbidden not in composer_style,forbidden
tool_row=main.split("private View workbenchCard(",1)[1].split("private void addModelFilter(",1)[0]
assert "card=row()" in tool_row and "card.setMinimumHeight(dp(56))" in tool_row
assert "margin(caption,labels,3,0)" in tool_row and 'card.setContentDescription(title+" · "+detail)' in tool_row
assert "card.setOnClickListener(v->action.run())" in tool_row
# F10A61: flat section hierarchy and non-destructive detailed-filter disclosure.
flat_section=main.split("private LinearLayout brandSection(",1)[1].split("private void createUi()",1)[0]
assert "section.setBackground(" not in flat_section
assert "margin(sectionTitle(title,glyph),section,0,6)" in flat_section
nav_style=(src/"WorkbenchStyle.java").read_text().split("static void navigationRow(",1)[1].split("static void field(",1)[0]
for required in ("p.canvas","p.disabledText","p.focus","state_focused","state_enabled",
    "b.setSingleLine(false)","b.setEllipsize(null)","b.setMinimumHeight(Math.max(",
    "b.setMinimumWidth(Math.max(","OnHandGlyphs.refresh(b)"):
    assert required in nav_style,required
for forbidden in ("setOnClickListener(","setEnabled(","setText(","setVisibility(","setSelected("):
    assert forbidden not in nav_style,forbidden
assert "for(Button button:flatNavigationButtons)WorkbenchStyle.navigationRow(this,button)" in main
model_page=main.split("private void createModelPage()",1)[1].split("private void createServerPage()",1)[0]
assert "modelFilterDetails" not in main and "responsiveModelFilterPair(" not in main
assert 'selectedSource="전체에서 찾기", selectedSort="추천 순"' in main
assert "sourceFilter.setAdapter(makeFilterAdapter(" in model_page
assert "sortFilter.setAdapter(makeFilterAdapter(" in model_page
assert "margin(modelSearchStatus,searchPanel,0,8)" in model_page
order=["addModelFilter(filterRow,sourceFilter,0)","addModelFilter(filterRow,sortFilter,1)",
    "addModelFilter(filterRow,familyFilter,2)","addModelFilter(filterRow,quantizationFilter,3)"]
assert [model_page.index(x) for x in order]==sorted(model_page.index(x) for x in order)
assert "modelFilterStrip.addView(filterRow,new android.widget.HorizontalScrollView.LayoutParams(-2,-2))" in model_page
assert "margin(modelFilterStrip,searchPanel,0,4);\n        modelRows=column();margin(modelRows,searchPanel,0,7)" in model_page
assert "modelFilterStrip.setFillViewport(true)" in model_page
assert "int maxChip=viewport>0?Math.min(dp(240),viewport):dp(240)" in main
assert "modelFilterStrip.addOnLayoutChangeListener" in model_page
assert "filter.getPaddingStart()+filter.getPaddingEnd()+dp(4),maxChip" in main
assert "refreshModelFilterLabels();\n        if(modelRows==null)return;" in main
assert "OnHandGlyphs.compactFilterSpinner(sourceFilter)" in model_page
assert "OnHandGlyphs.compactFilterSpinner(quantizationFilter)" in model_page
# F10A62: shared warm-neutral signature and quiet outline hierarchy.
assert "flatNavigationButton(menu)" in main and "flatNavigationButton(fresh)" in main
assert 'topPageTitle.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL))' in main
assert 'greeting.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL))' in main
assert "refreshQuickActionPaint();" in main
quick=(src/"WorkbenchStyle.java").read_text().split("static void quickAction(",1)[1].split("static void navigationRow(",1)[0]
for required in ("composerShape(a,p.surface,p.border,1,24)","composerShape(a,p.surface,p.focus,2,24)",
    "composerShape(a,p.disabled,p.border,1,24)","colors(p.text,p.disabledText)","OnHandGlyphs.refresh(b)",
    "b.setSingleLine(false)","b.setEllipsize(null)"):
    assert required in quick,required
for forbidden in ("setOnClickListener(","setEnabled(","setText(","setSelected(","setVisibility("):
    assert forbidden not in quick,forbidden
assert colors["onhand_canvas_light"]=="#F9F8F5" and colors["onhand_canvas_dark"]=="#191A19"
assert colors["onhand_accent_light"]=="#79503E" and colors["onhand_accent_dark"]=="#E7C1AD"
# F10A62R1: distinguish the user-approved peach fill from accessible companion ink.
assert colors["onhand_primary_fill_light"]=="#E5BBA6" and colors["onhand_primary_fill_dark"]=="#D4A58E"
for mode in ("light","dark"):
    assert colors["onhand_primary_ink_"+mode]==("#6E3B28" if mode=="light" else "#5F3121")
    assert colors["onhand_bubble_ink_"+mode]==("#8A5039" if mode=="light" else "#F2C9B4")
    assert colors["onhand_bubble_muted_ink_"+mode]==("#895B47" if mode=="light" else "#E7B69B")
    assert colors["onhand_choice_fill_"+mode]==("#F4E3D9" if mode=="light" else "#4B3930")
    assert colors["onhand_choice_ink_"+mode]==("#79503E" if mode=="light" else "#F2C9B4")
    assert colors["onhand_choice_border_"+mode]==("#C9A48F" if mode=="light" else "#8C6A59")
    for token in ("primary_fill","primary_ink","bubble_ink","bubble_muted_ink","choice_fill","choice_ink","choice_border"):
        assert "0xff"+colors["onhand_"+token+"_"+mode][1:].lower() in policy
assert "this.primaryFill=primaryFill" in policy
assert "role==Role.PRIMARY?primaryFill" in policy and "status==ModelStatus.ACTIVE?choiceFill(true)" in policy
assert "return primaryAction?(enabled?primaryFill:disabled):surface" in policy
assert "positive?primary:muted" in policy
style_source=(src/"WorkbenchStyle.java").read_text()
primary_paint=style_source.split("static void primaryButtonPaint(",1)[1].split("static void composerSurface(",1)[0]
for required in ("p.primaryFill,p.primaryOutline(),1,radius","p.primaryFill,p.primaryText,2,radius",
    "colors(p.primaryText,p.disabledText)","b.setPaddingRelative(start,top,end,bottom)",
    "v.getBackground()==previous.background","R.id.onhand_primary_paint"):
    assert required in primary_paint,required
for forbidden in ("setOnClickListener(","setText(","setEnabled(","setVisibility("):
    assert forbidden not in primary_paint,forbidden
assert "WorkbenchStyle.primaryButtonPaint(this,b,14)" in main
assert "WorkbenchStyle.choiceButton(this,buttons[i],active[i])" in main
assert "WorkbenchStyle.refreshPrimaryButtonPaint(this,v)" in main
assert "primary?p.primaryOutline():p.border,primary?1:0,24" in style_source
assert "badgeSurface.setStroke(dp(1),p.choiceBorder(true))" in main
assert main.count("WorkbenchVisualPolicy.activeModel(item.installed,item.id,currentModel)")==2
assert "OnHandGlyphs.label(badge,OnHandGlyphs.Kind.CHECK,false)" in main
assert "WorkbenchStyle.choiceRows(this,options,checked)" in main
assert "getListView().setItemChecked(checked,true)" in main
assert "WorkbenchStyle.choiceRow(MainActivity.this,filterDisplay(getItem(pos)),pos==spinner.getSelectedItemPosition())" in main
assert "TextView caption=text(getString(label),10,MUTED,false)" in main
assert "row.setCheckMarkDrawable(mark)" in style_source
assert "row.setChecked(selected)" in style_source
assert "WorkbenchStyle.choiceRows(activity,labels,checked)" in (src/"OllamaFamilyPicker.java").read_text()
assert "WorkbenchStyle.choiceRows(activity,labels,checked)" in (src/"OllamaQuantPicker.java").read_text()
assert "WorkbenchStyle.choiceRows(a,labels,current)" in (src/"OnHandsLocale.java").read_text()
assert "WorkbenchStyle.spinnerChoices(pick,names)" in (src/"CloudChatPanel.java").read_text()
assert "p.choiceFill(true),p.choiceBorder(true),1" in style_source
assert "p.choiceFill(true),p.focus,2" in style_source
assert "p.choiceFill(false),p.choiceBorder(false),0" in style_source
assert "private void showComputeChoiceDialog(){showComputeChoiceDialog(computeChoiceControl);}" in main
assert "computeChoiceControl=compute;" in main
assert "if(control!=null)control.setText(" in main
assert "ACCENT=p.primary" in main
assert "active?bubbleInk():WHITE" in main
assert "user?bubbleInk():WHITE" in main
assert "selected?bubbleInk():WHITE" in main
assert "selected?WorkbenchVisualPolicy.palette(activeThemeDark).bubbleMutedInk():MUTED" in main
assert main.count("tileMutedColor(selected)")==2
assert "cell.setTextColor(WorkbenchStyle.palette(c).bubbleInk())" in (src/"ChatRichView.java").read_text()
assert "model_recommend_mark),11,bubbleInk(),true)" in main
assert "text.setTextColor(p.bubbleInk())" in (src/"CloudChatPanel.java").read_text()
assert "#B83B12" not in (res/"values/onhand_brand_theme.xml").read_text()
assert "#FFAD7B" not in (res/"values/onhand_brand_theme.xml").read_text()
# F10A63: local/cloud chat visual parity, compact single action and pixel-centered glyphs.
cloud_source=(src/"CloudChatPanel.java").read_text()
cloud_paint=cloud_source.split("void refreshBrandPaint()",1)[1].split("private int headerNaturalDp(",1)[0]
for required in ("WorkbenchStyle.navigationRow(owner,button)","WorkbenchStyle.quickAction(owner,latest)",
    "WorkbenchStyle.composerSurface(owner,composer,prompt.hasFocus())",
    "WorkbenchStyle.composerInput(owner,prompt)","WorkbenchStyle.composerAction(owner,send,true)",
    "WorkbenchStyle.composerAction(owner,stop,true)"):
    assert required in cloud_paint,required
for forbidden in ("setText(","setEnabled(","setVisibility(","setOnClickListener(","render("):
    assert forbidden not in cloud_paint,forbidden
for required in ("composerActions.addView(send,new LayoutParams(dp(48),dp(48)))",
    "composerActions.addView(stop,new LayoutParams(dp(48),dp(48)))",
    "cloudHeaderStacked(width,scale,headerNaturalDp(back,density),headerNaturalDp(settings,density))",
    "button.getPaint().measureText(button.getText().toString())","button.getCompoundPaddingStart()",
    "prompt.setOnFocusChangeListener((v,focused)->WorkbenchStyle.composerSurface(owner,composer,focused))",
    "send.setOnClickListener(v->prepareSend());stop.setOnClickListener(v->cancel())",
    "notice.setText(R.string.cloud_context_notice)"):
    assert required in cloud_source,required
assert "send.setLayoutParams(cs?new LayoutParams(0,-2,1f)" not in cloud_source
assert "if(cloudPanel!=null)cloudPanel.refreshBrandPaint()" in main
assert "if(latestButton!=null)WorkbenchStyle.quickAction(this,latestButton)" in main
glyph_src=(src/"OnHandGlyphs.java").read_text()
assert "WorkbenchLayoutPolicy.iconInsetPx(target,glyph,false)" in glyph_src
assert "WorkbenchLayoutPolicy.iconInsetPx(target,glyph,true)" in glyph_src
assert "v.setPaddingRelative(leading,leading,trailing,trailing)" in glyph_src
# F10A64: user-approved real-device home feedback.
assert main.index("rowItem(header,cloudShortcut,0,4)")<main.index("rowItem(header, fresh, 0, 0)")
assert "paintHeaderCloud(true)" in main
assert "OnHandGlyphs.label(cloudShortcut,OnHandGlyphs.Kind.CLOUD,!labeled)" in main
assert "WorkspaceLayoutPolicy.headerCloudLabelFits(header.getWidth()" in main
assert "topPageTitle.setOnClickListener(v->openHomeFromHeader())" in main
assert "topPageSubtitle.setOnClickListener(v->openHeaderModelPicker())" in main
assert "branding.setOnClickListener(" not in main
assert "header.setGravity(Gravity.TOP)" in main and "branding.setGravity(Gravity.TOP)" in main
assert "topPageTitle.setMinHeight(dp(WorkspaceLayoutPolicy.HEADER_TITLE_MIN_DP))" in main
assert "topPageSubtitle.setMinHeight(dp(WorkspaceLayoutPolicy.HEADER_MODEL_MIN_DP))" in main
assert "topPageSubtitle.setPadding(0,dp(WorkspaceLayoutPolicy.HEADER_MODEL_GAP_DP),0,0)" in main
assert "topPageTitle.setIncludeFontPadding(false)" in main and "topPageSubtitle.setIncludeFontPadding(false)" in main
assert "branding.addView(topPageTitle,lp(-1,-2))" in main and "branding.addView(topPageSubtitle,lp(-1,-2))" in main
assert "topPageTitle.setGravity(Gravity.BOTTOM|Gravity.START)" in main
assert "topPageSubtitle.setGravity(Gravity.TOP|Gravity.START)" in main
title_home=main.split("private void openHomeFromHeader()",1)[1].split("private void openHeaderModelPicker()",1)[0]
assert "if(isUiBusy())" in title_home and "if(cloudMode)returnToLocal()" in title_home
assert "historyReadable&&history.length()==0&&pendingLocalPrompt.isEmpty()" in title_home
assert "newChat();" in title_home and "openHeaderModelPicker(" not in title_home
assert "modelChooser" not in main and "LinearLayout modelRow = row()" not in main
assert "margin(starterPanel,localChatContent,0,0)" in main
assert main.index("margin(starterPanel,localChatContent,0,0)")<main.index("margin(composer, localChatContent, 4, 2)")
assert "margin(starterPanel,messages" not in main and "starterPanel=null" not in main
assert "ChatWorkspacePolicy.homeActionsVisible(activeTab==0&&!cloudMode" in main
assert "installParams=lp(-2,-2)" in main and "installParams.gravity=Gravity.CENTER_HORIZONTAL" in main
assert "WorkbenchStyle.primaryButtonPaint(this,install,8)" in main
picker=main.split("private void openHeaderModelPicker()",1)[1].split("private void selectModel(",1)[0]
for required in ("if(knownModels.isEmpty()){showTab(1);return;}",".setSingleChoiceItems(WorkbenchStyle.choiceRows(this,entries,checked),checked",
    "!knownModels.contains(entries[index])","selectModel(entries[index])","if(voice!=null)voice.cancel()"):
    assert required in picker,required
guide=main.split("private void showFirstRunGuide() {",1)[1].split("private void showHelp()",1)[0]
assert "R.string.onboarding_title" not in guide and "R.string.onboarding_body" not in guide
for required in ("ads.register(guideAd,AdSupport.PLACEMENT_ONBOARDING)","ads.showBanner(guideAd)","ads.detach(guideAd)"):
    assert required in guide,required
help_body=main.split("private void showHelp()",1)[1].split("private void startServer()",1)[0]
assert "R.string.onboarding_body" in help_body and "R.string.help_body" in help_body
assert "ads." not in help_body
# F10A65: explicit debug/license-test billing only; no default or production purchases.
billing=(src/"PlayBillingController.java").read_text()
billing_policy=(src/"BillingEntitlementPolicy.java").read_text()
billing_gradle=Path("app/build.gradle").read_text()
ad_source=(src/"AdSupport.java").read_text()
assert "com.android.billingclient:billing:8.3.0" in billing_gradle
assert "buildConfigField 'boolean', 'BILLING_TEST_ENABLED', 'false'" in billing_gradle
assert 'BillingEntitlementPolicy.enabled(BuildConfig.DEBUG,BuildConfig.BILLING_TEST_ENABLED,key)' in billing
assert 'onhand_remove_ads_lifetime' in billing_policy
for required in ("queryProductDetailsAsync","queryPurchasesAsync","enablePendingPurchases",
    "getFormattedPrice()","getOfferToken()","getRentalDetails()","getPreorderDetails()",
    "BillingEntitlementPolicy.verify(key,purchase.getOriginalJson(),purchase.getSignature())",
    "Purchase.PurchaseState.PURCHASED","Purchase.PurchaseState.PENDING","acknowledgePurchase(",
    "case OWNED:applyOwned(true)","case PENDING:applyOwned(false)","case INVALID:applyOwned(false)",
    "billing_restore","client.launchBillingFlow(owner,params)","BillingEntitlementPolicy.mayBuy(",
    "if(!canBuy())","clientSerial","if(!current(ticket))return","previous.endConnection()"):
    assert required in billing,required
for forbidden in ("consumeAsync","getSharedPreferences(","getOrderId()","Log.","HttpURLConnection"):
    assert forbidden not in billing,forbidden
assert billing.count("launchBillingFlow(")==1
assert "debug&&optIn" in billing_policy
assert "if(testAdFree)return false;" in ad_source
assert 'return BuildConfig.DEBUG && prefs.getBoolean("enabled", true)' in ad_source
assert "ads::setTestAdFree" in main and "billing.destroy()" in main
assert "ads.register(downloadAdSlot,AdSupport.PLACEMENT_DOWNLOAD)" in main
print("PASS: approved launcher SHA-256, both adaptive masks, light/dark dialog palettes and native UI wiring")

# F10A78: the model picker stays discoverable and long workflow context is blocked before attempt reservation.
assert "topPageSubtitle.setCompoundDrawablesRelative(null,null,local?OnHandGlyphs.drawable(this,OnHandGlyphs.Kind.CARET" in main
assert "if(!cloudMode)label+=\" ▾\";" not in main
workflow_source=(src/"LocalWorkflow.java").read_text()
assert workflow_source.index("if(!DocumentBatchPolicy.fits(stepPrompt,WorkflowPolicy.MAX_TOKENS))") < workflow_source.index("c.attempts++;c.totalAttempts++")
assert 'put("content",stepPrompt)' in workflow_source

# F10A79: exact-generation early GPU stream retry, bounded diagnostic readers, memory cache trim, and immutable-context resume guard.
assert "GpuRunner.stopFailedStream(getApplicationContext(),modelAtStart,streamGeneration,()->chatCancelled)" in main
assert "GpuRunner.streamGeneration(modelAtStart)" in main
assert "if(level>=android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)searchCache.clear();" in main
assert "refreshHeaderAccess(); // repaint trailing model caret" in main
for engine in ("GpuRunner.java","DistributedRunner.java","OllamaForegroundService.java"):
    assert "GpuOffloadEvidence.readDiagnosticLine(" in (src/engine).read_text()
assert "WorkflowRunJournal.resumableReason(reason)&&budget(" in (src/"LocalWorkflow.java").read_text()

# F10A80: completed-stream-only cooldown reset and exact installed metadata.
gpu_source=(src/"GpuRunner.java").read_text()
assert gpu_source.count("retryBudget.succeeded(")==1
assert "finishSuccessfulStream(Context context" in gpu_source
assert "boolean retired=GpuRunner.stopFailedStream(" in main and "if(!retired)throw gpuError" in main
assert "GpuRunner.finishSuccessfulStream(getApplicationContext(),modelAtStart,streamGeneration" in main
assert "ModelTileCatalog.displayBytes(variantInstalled(v)" in main
assert "ModelTileCatalog.displayQuant(variantInstalled(v)" in main
assert "return !v.hf()&&knownModels.contains(v.model)" in main
assert "WorkflowPolicy.resumeChoiceEnabled(idle,current.resumable()" in (src/"AutomationUi.java").read_text()

# F10A81: preserve the last valid installed snapshot and recheck download consent.
assert "final long sequence=++installedModelRefreshSerial" in main
assert "ModelTileCatalog.acceptSnapshot(sequence,installedModelRefreshSerial" in main
assert "list==null||!ModelTileCatalog.validSnapshotCount(list.length())" in main
assert "beginDownload(model,featuredBytes)" in main
assert "ModelDownloadBudget.requiresReconfirmation(reviewedBytes,currentBytes)" in main
assert main.index("if(!hasDownloadStorage(Math.max(reviewedBytes,currentBytes)))return;") < main.index("pullBusy = true; pullCancelled = false;")
assert "BenchmarkRecommendation.adjustment(profile.recommendableCpuRate(),profile.recommendableGpuRate())" in main
assert "DocumentBatchPolicy.fits(WorkflowPolicy.firstPrompt(plan),WorkflowPolicy.MAX_TOKENS)" in workflow_source

# F10A82: require a successful pull protocol and exact installed tag before showing chat action.
assert "OllamaHttp.streamPull(connection, OllamaHttp.pullBody(model)" in main
assert "ModelTileCatalog.resolveInstalledTag(model,knownModels)" in main
assert "selectModel(installed);showTab(0)" in main
assert "NativeWeightPreflight.validGguf(copied,total)" in main
assert "ggufImportBusy||chatBusy" in main
hf_source=(src/"HuggingFaceCatalog.java").read_text()
assert "NativeWeightPreflight.validGguf(gguf,weight.size)" in hf_source
assert "ModelDownloadBudget.registrationRequiredBytes(weight.size)" in hf_source
assert "if(count>weight.size-downloaded)" in hf_source
