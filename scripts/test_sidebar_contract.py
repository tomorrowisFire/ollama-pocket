"""Shared structural navigation and model-search contract; not device verification."""
from pathlib import Path
src=Path('app/src/main/java/com/ollamapocket/app/MainActivity.java').read_text()
assert 'showNavigationDrawer()' in src
assert 'allSavedConversations()' in src
import xml.etree.ElementTree as ET
from pathlib import Path
resource_root=Path("app/src/main/res")
locales=["values","values-ko","values-b+zh+Hans","values-b+zh+Hant","values-ja"]
keys=None
for locale in locales:
    entries={e.attrib["name"]:e.text for e in ET.parse(resource_root/locale/"strings.xml").getroot().findall("string")}
    assert all(entries.values()), locale
    assert entries["app_name"] == "OnHand AI", locale
    assert keys is None or keys == set(entries), locale
    keys=set(entries)
    for key in ("models","settings","devices"):
        assert 'drawerNavigationItem(getString(R.string.'+key+'))' in src, key
        assert key in keys, locale
assert 'drawerNavigationItem(String title)' in src
drawer_fn=src.split('private TextView drawerNavigationItem(String title)',1)[1].split('private void showNavigationDrawer()',1)[0]
assert 'TextView item=text(title,14,active?bubbleInk():WHITE,active)' in drawer_fn
assert 'text(mark+' not in drawer_fn
assert 'item.setBackground(bg(active?BUBBLE:BG,14))' in drawer_fn
assert 'OnHandGlyphs.accentLabel(item' in drawer_fn
assert 'if(radius>0&&(color==CARD||color==FIELD))' not in src
assert 'BUBBLE=p.bubble' in src
assert 'R.style.OnHandTheme_Dark:R.style.OnHandTheme_Light' in src
assert 'profile.setOnClickListener' not in src
assert "WorkspaceLayoutPolicy.drawerWidthDp(workspaceMode" in src
assert "if(menuButton!=null)menuButton.setVisibility(View.VISIBLE)" in src
assert "WorkspaceLayoutPolicy.railWidthDp(workspaceMode,workspaceWidthDp(),scale)" in src
assert "buildNavigationSidebar(null)" in src and "buildNavigationSidebar(drawer)" in src
assert "workspace.addView(dockedSidebar,0" in src and "workspace.removeView(dockedSidebar)" in src
assert "workspace.addView(mainPane,new LinearLayout.LayoutParams(0,-1,1f))" in src
assert "mainPane.addView(pages,new LinearLayout.LayoutParams(-1,0,1f))" in src
assert "navigationListActive(list,drawer)" in src
assert "registerOnSharedPreferenceChangeListener(dockedHistoryListener)" in src
assert "unregisterOnSharedPreferenceChangeListener(dockedHistoryListener)" in src
assert "brandArtwork(" not in src
assert 'SolarTheme.isNight(' in src
assert 'switchAutoThemeWithoutRestart()' in src
assert 'UiText.cityLabels(this)' in src and 'SolarTheme.CITY_CODES[index]' in src
assert 'searchCache.get(' in src
assert 'fetchSearchSource(' in src
assert 'renderGroupedSearch(' in src
assert 'ModelDownloadBudget.requiredBytes(' in src
assert 'ModelDownloadBudget.layerLabel(' in src
assert 'downloadRetryButton.setVisibility(' in src
assert 'BenchmarkRecommendation.adjustment(' in src
assert 'getString("ui_theme","light")' in src
assert 'new String[]{getString(R.string.theme_light_default),getString(R.string.theme_dark),getString(R.string.theme_auto_detail)}' in src
assert 'navButtons' not in src
assert 'private void showChatList()' not in src
assert 'buildModelTile(' in src
assert 'buildModelListRow(' in src
assert 'setModelViewMode(' in src
assert 'model_list_view' in src
assert 'selectedSource="전체에서 찾기"' in src
assert 'Arrays.asList("전체에서 찾기","Ollama에서 찾기","Hugging Face에서 찾기")' in src
assert '직접 모델 다운로드' not in src
assert 'manualDownloadPanel.setVisibility(View.GONE)' in src
assert 'cancelPullButton.setVisibility(View.VISIBLE)' in src
assert 'Button importButton=button(getString(R.string.import_gguf),false)' in src
assert 'if(modelListView){' in src
assert 'deviceRecommendedTiles()' in src
assert 'ModelTileCatalog.forDevice(' in src
assert src.count('new LinearLayout.LayoutParams(sidePx,WorkspaceLayoutPolicy.modelCardHeightPx(sidePx,minCard))')==2
assert 'ModelVariantCatalog.columns(' in src
assert 'HuggingFaceCatalog.searchVariantsDetailed(' in src
assert 'ModelVariantCatalog.searchOllamaDetailed(' in src
assert 'ModelVariantCatalog.compareSize(' in src
assert 'OllamaQuantPicker.show(' in src
assert '모델 받기 ↓' not in src
assert 'text(getString(R.string.model_recommend_mark),10,secondary,true)' in src
assert 'bestQuantizations(' in src
assert 'allSearchTiles(filter)' in src
assert 'onlineSearchSerial' in src
assert 'if(!batch.complete())error=' in src
assert 'searchCache.put(ModelSearchCache.key(provider,query),result' in src
assert 'onModelSearchChanged(s.toString())' in src
assert 'downloadingCardId' in src
assert '이 기기에 추천하는 모델' not in src
assert 'recommendationRows' not in src
assert 'renderRecommendations' not in src
assert 'WindowInsets.Type.statusBars()' in src
assert 'performanceHudView.setText(identity+' in src
assert 'updateTileDownloadProgress()' in src
assert 'AiPocketDrawerMotion' in src
assert 'ViewPropertyAnimator' not in src or 'animate()' in src
# Plain-text Help follows the existing three navigation items.
modal=src.split("private void showNavigationDrawer()",1)[1].split("private ScrollView buildNavigationSidebar(",1)[0]
drawer=src.split("private ScrollView buildNavigationSidebar(",1)[1].split("private void ensureSessionListed(",1)[0]
assert drawer.index("margin(diagnostics,shell")<drawer.index("TextView help=drawerHelpItem()")<drawer.index("return drawerScroll;")
help_item=src.split("private TextView drawerHelpItem()",1)[1].split("private void showNavigationDrawer()",1)[0]
assert "R.string.help" in help_item and "OnHandGlyphs" not in help_item
assert "help.setOnClickListener(v->{if(drawer!=null)drawer.dismiss();showHelp();})" in drawer
assert drawer.count("new ScrollView(this)")==1
assert "drawerScroll.setFillViewport(true)" in drawer
assert "drawerScroll.addView(shell,new ScrollView.LayoutParams(-1,-2))" in drawer
assert "shell.addView(chatList,lp(-1,-2))" in drawer
assert "shell.addView(new View(this),new LinearLayout.LayoutParams(-1,0,1f))" in drawer
assert "drawer.setContentView(drawerScroll,new ViewGroup.LayoutParams(-1,-1))" in modal
assert "SOFT_INPUT_ADJUST_RESIZE" in modal and "WindowInsets.Type.ime()" in drawer
assert "if(drawer!=null)shell.setOnApplyWindowInsetsListener" in drawer
assert "if(availableRailDp()==0){showNavigationDrawer();return;}" in src
assert "workspaceReadingColumnDp((int)(width/density))" in src
assert "rowItem(heading,brandArtwork" not in drawer
print('PASS: shared scrollable drawer, plain help, model navigation, dynamic HUD, safe insets')
