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
    for destination,key,glyph in ((1,"models","CHIP"),(2,"settings","SETTINGS"),(3,"devices","DEVICE")):
        assert 'drawerNavigationItem('+str(destination)+',getString(R.string.'+key+'),OnHandGlyphs.Kind.'+glyph+')' in src, key
        assert key in keys, locale
assert 'drawerNavigationItem(int destination,String title,OnHandGlyphs.Kind glyph)' in src
drawer_fn=src.split('private TextView drawerNavigationItem(int destination,String title,OnHandGlyphs.Kind glyph)',1)[1].split('private void showNavigationDrawer()',1)[0]
assert 'TextView item=text(title,14,active?bubbleInk():WHITE,active)' in drawer_fn
assert 'text(mark+' not in drawer_fn
paint_fn=src.split('private void paintDrawerDestination(',1)[1].split('private TextView drawerNavigationItem(',1)[0]
assert 'GradientDrawable normal=bg(active?BUBBLE:BG,14)' in paint_fn
assert 'states.addState(new int[]{android.R.attr.state_focused},focused)' in paint_fn
assert 'item.setSelected(active)' in paint_fn
assert 'WorkspaceChromePolicy.selectedDestination(activeTab,destination)' in drawer_fn
assert 'paintDrawerDestination(item,active)' in drawer_fn
assert 'paintDrawerDestination(dockedDestinations[i],WorkspaceChromePolicy.selectedDestination(activeTab,i+1))' in src
assert 'item.setSingleLine(false);item.setEllipsize(null)' in drawer_fn
assert 'markDrawerAction(item)' in drawer_fn
assert 'info.setClassName(Button.class.getName())' in src.split('private void markDrawerAction(',1)[1].split('private void paintDrawerDestination(',1)[0]
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
assert src.count('setMinimumHeight(WorkspaceLayoutPolicy.modelCardHeightPx(sidePx,minCard))')==2
assert src.count('new LinearLayout.LayoutParams(sidePx,ViewGroup.LayoutParams.WRAP_CONTENT)')==2
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
# Search-cache writes must retain query ownership and the memory-clear epoch.
search_change=src.split("private void onModelSearchChanged(String nextValue,boolean forceRefresh){",1)[1].split("private void fetchSearchSource(",1)[0]
search_worker=src.split("private void fetchSearchSource(",1)[1].split("private void rebuildSearchParts()",1)[0]
assert "onlineSearchWorkers.cancel();" in search_change
assert search_change.index("Object cacheEpoch=searchCache.writeEpoch();")<search_change.index("onlineSearchWorkers.replace(")
assert 'fetchSearchSource(sequence,query,"Ollama",cacheEpoch)' in search_change
assert 'fetchSearchSource(sequence,query,"Hugging Face",cacheEpoch)' in search_change
assert "long sequence,String query,String provider,Object cacheEpoch" in search_worker
assert search_worker.index("sequence!=onlineSearchSerial")<search_worker.index("if(failure.isEmpty())")
assert search_worker.index("if(failure.isEmpty())")<search_worker.index("if(foreground)searchCache.putIfCurrent(cacheEpoch,ModelSearchCache.key(provider,query),result")
assert "searchCache.put(" not in src, "Direct cache writes bypass the memory-clear epoch"
assert "onlineSearchWorkers.close();searchCache.clear();" in src
cache_src=Path("app/src/main/java/com/ollamapocket/app/ModelSearchCache.java").read_text()
assert "if(expected==writeEpoch)put(key,values,now);" in cache_src
assert "entries.clear();variants=0;textChars=0;writeEpoch=new Object();" in cache_src
workers_src=Path("app/src/main/java/com/ollamapocket/app/ModelSearchWorkers.java").read_text()
# Two isolated single-worker lanes: total workers and queued requests remain bounded by two.
assert "new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1))" in workers_src
assert "ollamaLane=lane(),hfLane=lane()" in workers_src
assert workers_src.count("new ThreadPoolExecutor(")==1
assert workers_src.count("=lane()")==2
assert "ollama=ollamaLane.submit(nextOllama)" in workers_src
assert "hf=hfLane.submit(nextHf)" in workers_src
assert "ollama.cancel(true)" in workers_src and "hf.cancel(true)" in workers_src
assert "ollamaLane.purge();hfLane.purge();" in workers_src
assert "ollamaLane.shutdownNow();hfLane.shutdownNow();" in workers_src
assert "ollamaLane.getQueue().size()+hfLane.getQueue().size()" in workers_src
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
assert "R.string.app_name" not in drawer
assert "rowItem(heading,newChatItem,1,4)" in drawer
assert "rowItem(heading,close,0,0)" in drawer
assert "newChatItem.setMinHeight(dp(48))" in drawer
assert "close.setMinWidth(dp(48))" in drawer
print('PASS: shared scrollable drawer, plain help, model navigation, dynamic HUD, safe insets')
