# Proteostasis Network App — Cytoscape App v2.0.0

A Cytoscape 3 desktop app (OSGi bundle) that fetches the proteostasis core
solver JSON from a remote URL, builds a fully attributed colour-coded network,
and provides an interactive Results Panel for exploring and editing model
parameters.

---

## What it does

### Network loading
- Downloads the initial network topology and data from:
  - `https://www.rbvi.ucsf.edu/cc-tpr/static/proteostasis_initial_network.json`
  - `https://www.rbvi.ucsf.edu/cc-tpr/static/proteostasis_initial_data.json`
- Creates a `CyNetwork` with all node/edge attributes in the `prtsts::` namespace:
  - **Nodes:** `prtsts::node_class`, `prtsts::display_name`, `prtsts::gene_symbol`,
    `prtsts::uniprot_id`, `prtsts::total_nM`, `prtsts::free`, `prtsts::bound`,
    `prtsts::protein_class`, `prtsts::cluster_id`, …
  - **Edges:** `prtsts::edge_class`, `prtsts::kd_nM`, `prtsts::has_kd`,
    `prtsts::bound`, `prtsts::frac_bound`
- Applies a colour-coded visual style:
  - **HSP70 / HSP90** chaperones — large orange/cyan-bordered ellipses with pie
    charts showing free vs. bound fractions
  - **CC-TPR** co-chaperones — medium ellipses with tri-colour pie charts
  - **Cluster** nodes — rounded-rectangle compound node containers
  - Edge width scales with `prtsts::frac_bound`; edge colour (blue/red)
    indicates HSP70 vs. HSP90 target; dashed edges indicate estimated Kd

### Results Panel (East panel — "Proteostasis Details")
Selecting a node or edge in the network populates the Results Panel:

**Node Details tab**
| Field | Column | Editable |
|-------|--------|----------|
| Total (nM) | `prtsts::total_nM` | ✅ |
| Free (nM) | `prtsts::free` | — |
| Bound to HSP70 | `prtsts::bound` on HSP70 edge | — |
| Bound to HSP90 | `prtsts::bound` on HSP90 edge | — |

**Edge Details tab**
| Field | Column | Editable |
|-------|--------|----------|
| Kd (nM) | `prtsts::kd_nM` | ✅ |
| Bound (nM) | `prtsts::bound` | — |
| Frac Bound | `prtsts::frac_bound` | — |

Editing `total_nM` or `kd_nM` in the panel commits the change directly to the
node/edge table when you press Enter or click away. Press **Solve Network** in
the persistent controls strip to recalculate equilibrium concentrations.

**Persistent controls strip** (always visible, below the tabs):
- **Phosphorylation** checkbox — toggles phosphorylation state (plumbed for
  future solver integration)
- **Filters** checkbox — toggles network filter state (plumbed for future use)
- **Solve Network** button — fires the solver task against the current network

### Solver
`Apps › Proteostasis › Solve Proteostasis Network` sends the current network
to the remote solver endpoint and writes the response concentrations back into
the node and edge tables, updating pie charts and edge widths in real time.

Solver options (presented as a Tunable dialog):
- Maximum iterations (default 400)
- Tolerance (default 1e-8)
- Damping (default 0.35)

### Add Node
`Apps › Proteostasis › Add Node` (enabled when exactly one node is selected)
presents a Tunable dialog:

| Tunable | Description | Default |
|---------|-------------|---------|
| Node name / ID | Identifier for the new node | `NewProtein` |
| Total concentration (nM) | `prtsts::total_nM` for the new node | `1000.0` |
| Kd (nM) | `prtsts::kd_nM` for the connecting edge | `500.0` |
| Edge class | `prtsts::edge_class` for the new edge | `binding` |

The new node is placed outside the bounding box of the existing network.
The placement direction is determined by the position of the anchor node
relative to the network centre:
- Anchor in right half → new node placed further right
- Anchor in left half  → new node placed further left
- Anchor in top half   → new node placed further above
- Anchor in bottom half → new node placed further below

The visual style is re-applied after addition so the new node and edge
inherit the correct style mappings.

---

## Requirements

| Tool | Version |
|------|---------|
| JDK  | 11+     |
| Maven | 3.6+   |
| Cytoscape Desktop | 3.9 or 3.10 |

---

## Build

```bash
cd proteostasisApp
mvn clean package
```

Output JAR: `target/proteostasisApp-2.0.0.jar`

---

## Install into Cytoscape

**Option A — drag-and-drop (easiest)**
1. Open Cytoscape
2. Drag `target/proteostasisApp-2.0.0.jar` onto the Cytoscape window

**Option B — Apps menu**
1. Apps › App Manager › Install from File…
2. Select `target/proteostasisApp-2.0.0.jar`

**Option C — copy to apps folder**
```bash
# macOS
cp target/proteostasisApp-2.0.0.jar \
   ~/Library/Application\ Support/CytoscapeConfiguration/3/apps/installed/

# Linux
cp target/proteostasisApp-2.0.0.jar \
   ~/.cytoscape/3/apps/installed/

# Windows
copy target\proteostasisApp-2.0.0.jar ^
     %APPDATA%\CytoscapeConfiguration\3\apps\installed\
```

---

## Usage

After installation:

1. **Apps › Proteostasis › Load Proteostasis Network** — loads and styles the network
2. **Click a node** — the Results Panel (East tab "Proteostasis Details") shows
   Node Details; edit `Total (nM)` and press Enter to commit
3. **Click an edge** — Edge Details tab shows `Kd (nM)` (editable),
   `Bound (nM)`, and `Frac Bound`
4. **Apps › Proteostasis › Solve Proteostasis Network** (or press **Solve Network**
   in the panel) — recalculates equilibrium and updates the display
5. **Select one node, then Apps › Proteostasis › Add Node** — adds a new
   `cc_tpr` protein connected to the selected node with specified `total_nM`
   and `kd_nM`

---

## Project structure

```
proteostasisApp/
├── pom.xml
└── src/main/java/edu/ucsf/rbvi/proteostasisApp/
    ├── CyActivator.java                   ← OSGi entry point; registers all services
    ├── Columns.java                       ← Column name constants (without namespace)
    ├── DataManager.java                   ← Applies solver response to network tables
    ├── NodeSelectionListener.java         ← Routes node/edge selection to Results Panel
    ├── ProteostasisResultsPanel.java      ← Tabbed East Results Panel
    ├── StyleManager.java                  ← Builds and applies the Proteostasis visual style
    ├── utils/
    │   └── Utils.java                     ← Column I/O helpers with prtsts:: namespace
    └── tasks/
        ├── AddNodeTask.java               ← Adds a new node + edge via Tunable dialog
        ├── AddNodeTaskFactory.java        ← Factory; enabled when 1 node selected
        ├── LoadNetworkTask.java           ← Fetches JSON, builds network + groups
        ├── LoadNetworkTaskFactory.java    ← Factory for load action
        ├── SolveNetworkTask.java          ← POSTs solve request, writes response
        └── SolveNetworkTaskFactory.java   ← Factory; enabled when network loaded
```

---

## Extending

- Change the remote URL constants in `tasks/LoadNetworkTaskFactory.java`
- Add more visual mappings in `StyleManager.applyStyle()`
- Connect the Phosphorylation / Filters checkboxes in `ProteostasisResultsPanel`
  to solver parameters in `SolveNetworkTask`
- Add more node types to `AddNodeTask.edgeClass` choices using `@Tunable` with
  a `ListChangeListener` for a dropdown

---

## About

Developed at UCSF RBVI for proteostasis network modelling with Cytoscape 3.
