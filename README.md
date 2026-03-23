# Proteostasis Network Loader — Cytoscape App

A Cytoscape 3 desktop app (OSGi bundle) that fetches the proteostasis
core solver JSON from a URL and builds a fully attributed, colour-coded network.

## What it does

- Downloads JSON from:
  `https://www.cgl.ucsf.edu/home/scooter/proteostasis_core_request_complete.json`
- Creates a CyNetwork with all node/edge attributes from the file:
  - Nodes: `node_class`, `display_name`, `gene_symbol`, `uniprot_id`,
    `total_nM`, `protein_class`, `cluster_id`, …
  - Edges: `edge_class`, `kd_nM`, `has_kd`
- Applies a colour-coded visual style:
  - **HSP70 / HSP90** chaperones — large orange/purple ellipses
  - **CC-TPR** co-chaperones — medium cyan rounded-rectangles
  - **Cluster** nodes — small blue diamonds
- Runs the default Cytoscape layout (force-directed)
- Menu entry: **Apps › Proteostasis › Load Proteostasis Network**

## Requirements

| Tool    | Version |
|---------|---------|
| JDK     | 11+     |
| Maven   | 3.6+    |
| Cytoscape Desktop | 3.9 or 3.10 |

## Build

```bash
cd proteostasis-app
mvn clean package
```

The JAR is produced at:
```
target/proteostasis-app-1.0.0.jar
```

## Install into Cytoscape

**Option A — drag-and-drop (easiest)**
1. Open Cytoscape
2. Drag `target/proteostasis-app-1.0.0.jar` onto the Cytoscape window

**Option B — Apps menu**
1. Apps › App Manager › Install from File…
2. Select `target/proteostasis-app-1.0.0.jar`

**Option C — copy to apps folder**
```
# macOS
cp target/proteostasis-app-1.0.0.jar \
   ~/Library/Application\ Support/CytoscapeConfiguration/3/apps/installed/

# Linux
cp target/proteostasis-app-1.0.0.jar \
   ~/.cytoscape/3/apps/installed/

# Windows
copy target\proteostasis-app-1.0.0.jar ^
     %APPDATA%\CytoscapeConfiguration\3\apps\installed\
```

## Usage

After installation:
1. In Cytoscape: **Apps › Proteostasis › Load Proteostasis Network**
2. The task runs in the background — watch the status bar
3. Network appears with the Proteostasis visual style applied

## Project structure

```
proteostasis-app/
├── pom.xml
└── src/main/java/org/proteostasis/app/
    ├── CyActivator.java            ← OSGi entry point, registers menu item
    ├── LoadNetworkTaskFactory.java ← Cytoscape TaskFactory service
    └── LoadNetworkTask.java        ← Fetches JSON, builds network + visual style
```

## Extending

- Change the URL constant in `LoadNetworkTaskFactory.java`
- Add more visual mappings in `LoadNetworkTask.applyStyle()`
- Add a Tunable `@Tunable String url` to `LoadNetworkTask` to let the user
  enter the URL at runtime via Cytoscape's task dialog
