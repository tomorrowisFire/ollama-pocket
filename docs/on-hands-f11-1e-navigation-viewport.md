# F11-1E — navigation identity and narrow viewport polish

Baseline F11-1CD 3479e8fbf01d8056fd327097d4a81ba420b29f38: FAST 46141730613 passed, FULL 46141730676 pending at initial check. Isolate until the exact predecessor gates pass.

Scope: route-index-driven selected state, identical modal/docked destination paint, focused outline and ripple, wrapped destination names and actionable accessibility semantics for menu/help/history. Retain navigation order, click listeners, persistence, language resources, sidebar open/close, selected history and data. Centered local/cloud reading widths and local bubble width respect the actual viewport including extreme narrow widths; existing normal-width reading caps remain. No ads, billing, network, engine, permission or stored data changes.

Actually executed local javac --release 17 and WorkspaceChromePolicyTest: 24,401 assertions PASS. Source/test hashes c5c86ca4002b7e4aeeb84302abcc5a035e3510dd and 18d408dddef18b98287d7acd29f4e17f6f084dab. New integration predicates and Android compilation prepared for exact CI, not run locally. Runtime focus, TalkBack, resizing and screen appearance remain NOT_RUN. No release or independent score claim.
