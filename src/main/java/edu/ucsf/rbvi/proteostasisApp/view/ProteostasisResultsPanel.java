/* vim: set ts=4 sw=4 et: */
package edu.ucsf.rbvi.proteostasisApp.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;

import org.cytoscape.application.swing.CytoPanelComponent;
import org.cytoscape.application.swing.CytoPanelName;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.work.SynchronousTaskManager;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskManager;
import org.cytoscape.work.TunableSetter;

import edu.ucsf.rbvi.proteostasisApp.Columns;
import edu.ucsf.rbvi.proteostasisApp.tasks.AddNodeTaskFactory;
import edu.ucsf.rbvi.proteostasisApp.tasks.SolveNetworkTaskFactory;
import edu.ucsf.rbvi.proteostasisApp.utils.Utils;

/**
 * Cytoscape EAST Results Panel for the Proteostasis app.
 *
 * Layout (top to bottom):
 *   ┌─────────────────────────────────────┐
 *   │  Header: "Proteostasis Details"      │
 *   ├─────────────────────────────────────┤
 *   │  JTabbedPane                        │
 *   │    Tab 1: Node Details              │
 *   │      – total_nM (editable)          │
 *   │      – free_nM, bound→HSP70/90      │
 *   │      – [Add Interactor] button      │
 *   │    Tab 2: Edge Details              │
 *   │      – kd_u_nM, kd_p_nM (editable) │
 *   │      – bound, frac_bound            │
 *   ├─────────────────────────────────────┤
 *   │  Persistent controls strip:         │
 *   │    ┌── Phosphorylation ────────┐    │
 *   │    │ HSP70 [====slider====] xx%│    │
 *   │    │ HSP90 [====slider====] xx%│    │
 *   │    └──────────────────────────┘    │
 *   │    ┌── Filters ────────────────┐   │
 *   │    │  (future content)          │   │
 *   │    └──────────────────────────┘    │
 *   │    [  Solve Network  ]              │
 *   └─────────────────────────────────────┘
 */
public class ProteostasisResultsPanel extends JPanel implements CytoPanelComponent {

    private static final long   serialVersionUID = 1L;
    private static final String PANEL_TITLE      = "Proteostasis";

    // Network-level phospho fraction columns (fraction 0–1 stored, displayed as 0–100%)
    private static final String COL_PCT_P_HSP70 = "pct_p_hsp70";
    private static final String COL_PCT_P_HSP90 = "pct_p_hsp90";

    // ── Colour palette ────────────────────────────────────────────────────────
    private static final Color BG_DARK       = new Color(26,  37,  64);
    private static final Color BG_CARD       = new Color(13,  25,  50);
    private static final Color BG_CONTROLS   = new Color(10,  18,  40);
    private static final Color BG_SUBPANEL   = new Color(18,  30,  58);
    private static final Color FG_WHITE      = Color.WHITE;
    private static final Color FG_MUTED      = new Color(150, 170, 210);
    private static final Color BORDER_DIM    = new Color(42,  74, 127);
    private static final Color ACCENT_TOTAL  = new Color(249, 115,  22);
    private static final Color ACCENT_FREE   = new Color( 74, 222, 128);
    private static final Color ACCENT_HSP70  = new Color( 96, 165, 250);
    private static final Color ACCENT_HSP90  = new Color(248, 113, 113);
    private static final Color ACCENT_KD     = new Color(196, 148, 255);
    private static final Color ACCENT_BOUND  = new Color( 74, 222, 128);
    private static final Color ACCENT_FRAC   = new Color(251, 191,  36);
    private static final Color ACCENT_EDIT   = new Color(250, 204,  21);
    private static final Color ACCENT_ADD    = new Color( 52, 211, 153);

    // ── Service registrar ─────────────────────────────────────────────────────
    private final CyServiceRegistrar registrar;

    // ── Node tab fields ───────────────────────────────────────────────────────
    private final JPanel     nodeIdentCard;
    private final JLabel     lblNodeName;
    private final JLabel     lblNodeClass;
    private final JPanel     nodeDataPanel;
    private final JTextField fldTotalNm;
    private final JLabel     valFree;
    private final JLabel     valHsp70;
    private final JLabel     valHsp90;
    private final JLabel     nodeNoSelMsg;

    // ── Edge tab fields ───────────────────────────────────────────────────────
    private final JPanel     edgeIdentCard;
    private final JLabel     lblEdgeName;
    private final JPanel     edgeDataPanel;
    private final JTextField fldKduNm;
    private final JTextField fldKdpNm;
    private final JLabel     valBound;
    private final JLabel     valFracBound;
    private final JLabel     edgeNoSelMsg;

    // ── Phosphorylation sliders and readout labels ────────────────────────────
    // Slider integer range 0–100 corresponds to 0–100 percent.
    private final JSlider sldPhosphoHsp70;
    private final JSlider sldPhosphoHsp90;
    private final JLabel  lblPhosphoHsp70Val;  // live "xx%" readout next to slider
    private final JLabel  lblPhosphoHsp90Val;

    // Guard flag: prevents slider ChangeListener from writing to network
    // while we are programmatically moving the slider during sync.
    private boolean syncingSliders = false;

    // ── State ─────────────────────────────────────────────────────────────────
    private CyNetwork currentNetwork;
    private CyNode    currentNode;
    private CyEdge    currentEdge;

    public ProteostasisResultsPanel(CyServiceRegistrar registrar) {
        this.registrar = registrar;
        setLayout(new BorderLayout());
        setBackground(BG_DARK);
        setPreferredSize(new Dimension(290, 580));

        // ── Header bar ────────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(10, 18, 40));
        header.setBorder(new EmptyBorder(10, 14, 10, 14));
        JLabel title = new JLabel("Proteostasis Details");
        title.setForeground(FG_WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 13));
        header.add(title, BorderLayout.WEST);
        add(header, BorderLayout.NORTH);

        // ── Tabbed pane ───────────────────────────────────────────────────────
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(BG_DARK);
        tabs.setForeground(FG_MUTED);
        tabs.setFont(new Font("SansSerif", Font.PLAIN, 11));

        // ════════════════════════════════════════════════════════════════════
        //  Node Details tab
        // ════════════════════════════════════════════════════════════════════
        JPanel nodeTab = new JPanel();
        nodeTab.setLayout(new BoxLayout(nodeTab, BoxLayout.Y_AXIS));
        nodeTab.setBackground(BG_DARK);
        nodeTab.setBorder(new EmptyBorder(10, 10, 10, 10));

        nodeIdentCard = makeCard();
        lblNodeName   = makeLabel("—", FG_WHITE, 14, Font.BOLD);
        lblNodeClass  = makeLabel("",  FG_MUTED, 10, Font.ITALIC);
        nodeIdentCard.add(lblNodeName);
        nodeIdentCard.add(Box.createVerticalStrut(3));
        nodeIdentCard.add(lblNodeClass);
        nodeTab.add(nodeIdentCard);
        nodeTab.add(Box.createVerticalStrut(8));

        nodeDataPanel = makeCard();

        JLabel nodeConcsTitle = makeLabel("CONCENTRATIONS", FG_MUTED, 9, Font.BOLD);
        nodeConcsTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        nodeDataPanel.add(nodeConcsTitle);
        nodeDataPanel.add(Box.createVerticalStrut(8));

        fldTotalNm = makeEditField(ACCENT_TOTAL);
        nodeDataPanel.add(makeEditRow("Total (nM)", fldTotalNm, ACCENT_TOTAL));
        nodeDataPanel.add(Box.createVerticalStrut(2));
        nodeDataPanel.add(makeEditHint("Edit and Solve to update"));
        nodeDataPanel.add(Box.createVerticalStrut(10));
        nodeDataPanel.add(makeSeparator());
        nodeDataPanel.add(Box.createVerticalStrut(10));

        valFree  = makeBigLabel(ACCENT_FREE);
        valHsp70 = makeBigLabel(ACCENT_HSP70);
        valHsp90 = makeBigLabel(ACCENT_HSP90);

        nodeDataPanel.add(makeMetricBlock("Free (nM)",      valFree,  ACCENT_FREE));
        nodeDataPanel.add(Box.createVerticalStrut(8));
        nodeDataPanel.add(makeSeparator());
        nodeDataPanel.add(Box.createVerticalStrut(8));
        nodeDataPanel.add(makeMetricBlock("Bound to HSP70", valHsp70, ACCENT_HSP70));
        nodeDataPanel.add(Box.createVerticalStrut(8));
        nodeDataPanel.add(makeSeparator());
        nodeDataPanel.add(Box.createVerticalStrut(8));
        nodeDataPanel.add(makeMetricBlock("Bound to HSP90", valHsp90, ACCENT_HSP90));

        nodeDataPanel.add(Box.createVerticalStrut(12));
        nodeDataPanel.add(makeSeparator());
        nodeDataPanel.add(Box.createVerticalStrut(10));
        nodeDataPanel.add(makeAddInteractorButton());

        nodeTab.add(nodeDataPanel);

        nodeNoSelMsg = makeLabel(
                "<html><center>Select a node<br>to view details</center></html>",
                FG_MUTED, 12, Font.ITALIC);
        nodeNoSelMsg.setAlignmentX(Component.CENTER_ALIGNMENT);
        nodeNoSelMsg.setBorder(new EmptyBorder(30, 10, 10, 10));
        nodeNoSelMsg.setHorizontalAlignment(SwingConstants.CENTER);
        nodeTab.add(Box.createVerticalStrut(10));
        nodeTab.add(nodeNoSelMsg);

        nodeIdentCard.setVisible(false);
        nodeDataPanel.setVisible(false);

        tabs.addTab("Node Details", scrollWrap(nodeTab));

        // ════════════════════════════════════════════════════════════════════
        //  Edge Details tab
        // ════════════════════════════════════════════════════════════════════
        JPanel edgeTab = new JPanel();
        edgeTab.setLayout(new BoxLayout(edgeTab, BoxLayout.Y_AXIS));
        edgeTab.setBackground(BG_DARK);
        edgeTab.setBorder(new EmptyBorder(10, 10, 10, 10));

        edgeIdentCard = makeCard();
        lblEdgeName   = makeLabel("—", FG_WHITE, 12, Font.BOLD);
        lblEdgeName.setAlignmentX(Component.LEFT_ALIGNMENT);
        edgeIdentCard.add(lblEdgeName);
        edgeTab.add(edgeIdentCard);
        edgeTab.add(Box.createVerticalStrut(8));

        edgeDataPanel = makeCard();
        JLabel edgeDataTitle = makeLabel("EDGE DATA", FG_MUTED, 9, Font.BOLD);
        edgeDataTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        edgeDataPanel.add(edgeDataTitle);
        edgeDataPanel.add(Box.createVerticalStrut(8));

        fldKduNm = makeEditField(ACCENT_KD);
        fldKdpNm = makeEditField(ACCENT_KD);
        edgeDataPanel.add(makeEditRow("Kd unphosphorylated (nM)", fldKduNm, ACCENT_KD));
        edgeDataPanel.add(Box.createVerticalStrut(2));
        edgeDataPanel.add(makeEditRow("Kd phosphorylated (nM)",   fldKdpNm, ACCENT_KD));
        edgeDataPanel.add(Box.createVerticalStrut(2));
        edgeDataPanel.add(makeEditHint("Edit and Solve to update"));
        edgeDataPanel.add(Box.createVerticalStrut(10));
        edgeDataPanel.add(makeSeparator());
        edgeDataPanel.add(Box.createVerticalStrut(10));

        valBound     = makeBigLabel(ACCENT_BOUND);
        valFracBound = makeBigLabel(ACCENT_FRAC);
        edgeDataPanel.add(makeMetricBlock("Bound (nM)", valBound,     ACCENT_BOUND));
        edgeDataPanel.add(Box.createVerticalStrut(8));
        edgeDataPanel.add(makeSeparator());
        edgeDataPanel.add(Box.createVerticalStrut(8));
        edgeDataPanel.add(makeMetricBlock("Frac Bound", valFracBound, ACCENT_FRAC));

        edgeTab.add(edgeDataPanel);

        edgeNoSelMsg = makeLabel(
                "<html><center>Select an edge<br>to view details</center></html>",
                FG_MUTED, 12, Font.ITALIC);
        edgeNoSelMsg.setAlignmentX(Component.CENTER_ALIGNMENT);
        edgeNoSelMsg.setBorder(new EmptyBorder(30, 10, 10, 10));
        edgeNoSelMsg.setHorizontalAlignment(SwingConstants.CENTER);
        edgeTab.add(Box.createVerticalStrut(10));
        edgeTab.add(edgeNoSelMsg);

        edgeIdentCard.setVisible(false);
        edgeDataPanel.setVisible(false);

        tabs.addTab("Edge Details", scrollWrap(edgeTab));

        add(tabs, BorderLayout.CENTER);

        // ── Build sliders before constructing the controls strip ──────────────
        sldPhosphoHsp70  = makePhosphoSlider(ACCENT_HSP70);
        sldPhosphoHsp90  = makePhosphoSlider(ACCENT_HSP90);
        lblPhosphoHsp70Val = makeSliderValLabel();
        lblPhosphoHsp90Val = makeSliderValLabel();

        add(buildControlsStrip(), BorderLayout.SOUTH);

        // Wire all interactive fields
        wireTotalNmField();
        wireKdNmuField();
        wireKdNmpField();
        wirePhosphoSlider(sldPhosphoHsp70, lblPhosphoHsp70Val, COL_PCT_P_HSP70);
        wirePhosphoSlider(sldPhosphoHsp90, lblPhosphoHsp90Val, COL_PCT_P_HSP90);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void showNode(CyNetwork network, CyNode node) {
        SwingUtilities.invokeLater(() -> {
            this.currentNetwork = network;
            this.currentNode    = node;
            this.currentEdge    = null;

            syncPhosphoSlidersFromNetwork(network);

            CyRow  row       = network.getRow(node);
            String name      = row.get(CyNetwork.NAME, String.class);
            String nodeClass = Utils.getStr(row, Columns.COL_NODE_CLASS);
            Double total     = Utils.getDbl(row, Columns.COL_TOTAL_NM);
            Double free      = Utils.getDbl(row, Columns.COL_FREE);

            double  hsp70Bound = 0.0, hsp90Bound = 0.0;
            boolean hasHsp70   = false, hasHsp90   = false;

            List<Double> boundList = Utils.getList(row, Columns.COL_BOUND, Double.class);

            if ("HSP70".equals(name) && boundList != null && boundList.size() >= 4) {
                hsp70Bound = safeSlice(boundList, 2) + safeSlice(boundList, 3);
                hasHsp70 = true;
            } else if ("HSP90".equals(name) && boundList != null && boundList.size() >= 4) {
                hsp90Bound = safeSlice(boundList, 2) + safeSlice(boundList, 3);
                hasHsp90 = true;
            } else if (boundList != null && boundList.size() >= 5) {
                hsp70Bound = safeSlice(boundList, 1) + safeSlice(boundList, 2);
                hsp90Bound = safeSlice(boundList, 3) + safeSlice(boundList, 4);
                hasHsp70 = true;
                hasHsp90 = true;
            } else {
                for (CyEdge edge : network.getAdjacentEdgeList(node, CyEdge.Type.ANY)) {
                    CyNode other     = edge.getSource().equals(node) ? edge.getTarget() : edge.getSource();
                    String otherName = network.getRow(other).get(CyNetwork.NAME, String.class);
                    Double bound     = Utils.getDbl(network.getRow(edge), Columns.COL_BOUND);
                    if (bound == null) continue;
                    if ("HSP70".equals(otherName)) { hsp70Bound += bound; hasHsp70 = true; }
                    else if ("HSP90".equals(otherName)) { hsp90Bound += bound; hasHsp90 = true; }
                }
            }

            lblNodeName.setText(name      != null ? name      : "—");
            lblNodeClass.setText(nodeClass != null ? nodeClass : "");
            fldTotalNm.setText(formatNm(total));
            valFree.setText(formatNm(free));
            valHsp70.setText(hasHsp70 ? formatNm(hsp70Bound) : "N/A");
            valHsp90.setText(hasHsp90 ? formatNm(hsp90Bound) : "N/A");

            nodeIdentCard.setVisible(true);
            nodeDataPanel.setVisible(true);
            nodeNoSelMsg.setVisible(false);
            revalidate(); repaint();
        });
    }

    public void showEdge(CyNetwork network, CyEdge edge) {
        SwingUtilities.invokeLater(() -> {
            this.currentNetwork = network;
            this.currentEdge    = edge;
            this.currentNode    = null;

            syncPhosphoSlidersFromNetwork(network);

            CyRow  row      = network.getRow(edge);
            String edgeName = row.get(CyNetwork.NAME, String.class);
            Double kdu      = Utils.getDbl(row, Columns.COL_KD_U_NM);
            Double kdp      = Utils.getDbl(row, Columns.COL_KD_P_NM);
            Double bound    = Utils.getDbl(row, Columns.COL_BOUND);
            Double frac     = Utils.getDbl(row, Columns.COL_FRAC_BOUND);

            lblEdgeName.setText(edgeName != null ? edgeName : "—");
            fldKduNm.setText(kdu != null ? String.format("%.4g", kdu) : "—");
            fldKdpNm.setText(kdp != null ? String.format("%.4g", kdp) : "—");
            valBound.setText(formatNm(bound));
            valFracBound.setText(frac != null ? String.format("%.4f", frac) : "—");

            edgeIdentCard.setVisible(true);
            edgeDataPanel.setVisible(true);
            edgeNoSelMsg.setVisible(false);
            revalidate(); repaint();
        });
    }

    public void clearSelection() {
        SwingUtilities.invokeLater(() -> {
            this.currentNode = null;
            this.currentEdge = null;

            nodeIdentCard.setVisible(false);
            nodeDataPanel.setVisible(false);
            nodeNoSelMsg.setVisible(true);

            edgeIdentCard.setVisible(false);
            edgeDataPanel.setVisible(false);
            edgeNoSelMsg.setVisible(true);

            revalidate(); repaint();
        });
    }

    // ── CytoPanelComponent ────────────────────────────────────────────────────

    @Override public Component     getComponent()     { return this; }
    @Override public CytoPanelName getCytoPanelName() { return CytoPanelName.EAST; }
    @Override public String        getTitle()         { return PANEL_TITLE; }
    @Override public Icon          getIcon()          { return null; }

    // ── Controls strip ────────────────────────────────────────────────────────

    private JPanel buildControlsStrip() {
        JPanel strip = new JPanel();
        strip.setLayout(new BoxLayout(strip, BoxLayout.Y_AXIS));
        strip.setBackground(BG_CONTROLS);
        strip.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_DIM),
                new EmptyBorder(10, 10, 12, 10)));

        strip.add(makePhosphorylationPanel());
        strip.add(Box.createVerticalStrut(8));

        strip.add(makeSubPanel("Filters"));
        strip.add(Box.createVerticalStrut(10));

        JButton btnSolve = new JButton("Solve Network");
        btnSolve.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnSolve.setBackground(new Color(37, 99, 235));
        btnSolve.setForeground(FG_WHITE);
        btnSolve.setFocusPainted(false);
        btnSolve.setOpaque(true);
        btnSolve.setBorderPainted(false);
        btnSolve.setBorder(new EmptyBorder(7, 18, 7, 18));
        btnSolve.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnSolve.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        btnSolve.addActionListener(e -> fireSolveTask());
        strip.add(btnSolve);

        return strip;
    }

    /**
     * Phosphorylation panel — contains one slider row per chaperone.
     *
     *  ┌── Phosphorylation ───────────────────────────────┐
     *  │  ● HSP70  [━━━━━━━━━━━━━━━━━━━━━━━━━]  42%      │
     *  │  ● HSP90  [━━━━━━━━━━━━━]              18%      │
     *  └─────────────────────────────────────────────────┘
     */
    private JPanel makePhosphorylationPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_SUBPANEL);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        TitledBorder tb = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BORDER_DIM, 1),
                "Phosphorylation",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 10),
                FG_MUTED);
        panel.setBorder(BorderFactory.createCompoundBorder(tb, new EmptyBorder(4, 6, 8, 6)));

        panel.add(makeSliderRow("HSP70", sldPhosphoHsp70, lblPhosphoHsp70Val, ACCENT_HSP70));
        panel.add(Box.createVerticalStrut(6));
        panel.add(makeSliderRow("HSP90", sldPhosphoHsp90, lblPhosphoHsp90Val, ACCENT_HSP90));

        return panel;
    }

    /**
     * A single labelled slider row:
     *
     *   ● LABEL  [━━━━━━━━━━━━━━━━━━━]  42%
     *
     * @param label      short label shown to the left of the slider (e.g. "HSP70")
     * @param slider     the JSlider to embed
     * @param valLabel   the JLabel that shows the current value as "xx%"
     * @param accent     colour for the dot indicator
     */
    private JPanel makeSliderRow(String label, JSlider slider, JLabel valLabel, Color accent) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(BG_SUBPANEL);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

        // Left: coloured dot + short label
        JPanel left = new JPanel(new BorderLayout(4, 0));
        left.setBackground(BG_SUBPANEL);
        left.setPreferredSize(new Dimension(58, 20));

        JLabel dot = new JLabel("●");
        dot.setForeground(accent);
        dot.setFont(new Font("SansSerif", Font.PLAIN, 8));

        JLabel lbl = new JLabel(label);
        lbl.setForeground(FG_MUTED);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 10));

        left.add(dot, BorderLayout.WEST);
        left.add(lbl, BorderLayout.CENTER);

        // Right: fixed-width percentage label ("100%")
        valLabel.setPreferredSize(new Dimension(36, 20));
        valLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        row.add(left,     BorderLayout.WEST);
        row.add(slider,   BorderLayout.CENTER);
        row.add(valLabel, BorderLayout.EAST);

        return row;
    }

    /**
     * Creates a themed JSlider spanning 0–100 (percent).
     * Painting is kept minimal to fit the dark theme.
     */
    private JSlider makePhosphoSlider(Color thumbColor) {
        JSlider slider = new JSlider(JSlider.HORIZONTAL, 0, 100, 0);
        slider.setBackground(BG_SUBPANEL);
        slider.setForeground(FG_MUTED);
        slider.setPaintTicks(false);
        slider.setPaintLabels(false);
        slider.setSnapToTicks(false);
        slider.setOpaque(false);
        return slider;
    }

    /** Small bold label for the live slider percentage readout. */
    private JLabel makeSliderValLabel() {
        JLabel lbl = new JLabel("0%");
        lbl.setForeground(FG_MUTED);
        lbl.setFont(new Font("Monospaced", Font.BOLD, 10));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }

    private JPanel makeSubPanel(String title) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_SUBPANEL);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        TitledBorder tb = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BORDER_DIM, 1),
                title,
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 10),
                FG_MUTED);
        panel.setBorder(BorderFactory.createCompoundBorder(tb, new EmptyBorder(4, 6, 6, 6)));

        JLabel placeholder = new JLabel("(controls will appear here)");
        placeholder.setForeground(new Color(80, 100, 140));
        placeholder.setFont(new Font("SansSerif", Font.ITALIC, 9));
        placeholder.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(placeholder);

        return panel;
    }

    private JPanel makeAddInteractorButton() {
        JButton btn = new JButton("+ Add Interactor");
        btn.setFont(new Font("SansSerif", Font.BOLD, 11));
        btn.setBackground(new Color(6, 55, 45));
        btn.setForeground(ACCENT_ADD);
        btn.setFocusPainted(false);
        btn.setOpaque(true);
        btn.setBorderPainted(true);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ACCENT_ADD, 1),
                new EmptyBorder(5, 12, 5, 12)));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        btn.addActionListener(e -> fireAddInteractorTask());

        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBackground(BG_CARD);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        wrapper.add(btn);
        return wrapper;
    }

    @SuppressWarnings("rawtypes")
    private void fireAddInteractorTask() {
        if (currentNode == null) return;
        try {
            TaskManager tm = registrar.getService(TaskManager.class);
            tm.execute(new AddNodeTaskFactory(registrar).createTaskIterator());
        } catch (Exception ex) {
            System.err.println("[ProteostasisApp] Could not fire AddNode task: " + ex.getMessage());
        }
    }

    @SuppressWarnings("rawtypes")
    private void fireSolveTask() {
        try {
            if (currentNetwork != null) syncPhosphoToNetwork(currentNetwork);
            SynchronousTaskManager tm = registrar.getService(SynchronousTaskManager.class);
            TunableSetter ts = registrar.getService(TunableSetter.class);

            // Get the tunable values
            // TODO: These should be taken from the network table or properties
            Map<String, Object> tunableValues = new HashMap<>();
            CyRow netRow = currentNetwork.getRow(currentNetwork);
            tunableValues.put("max_iter", Utils.getInt(netRow, Columns.COL_MAX_ITER));
            tunableValues.put("tol", Utils.getDbl(netRow, Columns.COL_TOLERANCE));
            tunableValues.put("damping", Utils.getDbl(netRow, Columns.COL_DAMPING));
            TaskIterator tasks = new SolveNetworkTaskFactory(registrar).createTaskIterator();
            TaskIterator tunedTasks = ts.createTaskIterator(tasks, tunableValues);
            tm.execute(tunedTasks);
        } catch (Exception ex) {
            System.err.println("[ProteostasisApp] Could not fire Solve task: " + ex.getMessage());
        }
    }

    // ── Slider wiring ─────────────────────────────────────────────────────────

    /**
     * Attaches a ChangeListener to {@code slider} that:
     *   1. Updates the live percentage label ({@code valLabel}) on every move.
     *   2. When the user releases the slider (or it stops adjusting), writes
     *      the new fraction (0–1) to the named network-level column.
     *
     * The {@code syncingSliders} flag suppresses writes during programmatic
     * sync (i.e. when a new network or node is selected).
     */
    private void wirePhosphoSlider(JSlider slider, JLabel valLabel, String colName) {
        slider.addChangeListener((ChangeEvent e) -> {
            int pct = slider.getValue();
            valLabel.setText(pct + "%");

            // Only commit to the network when the user has finished dragging
            if (!slider.getValueIsAdjusting() && !syncingSliders && currentNetwork != null) {
                setNetworkPct(currentNetwork, colName, pct / 100.0);
                fireSolveTask();
            }
        });
    }

    // ── Kd field wiring ───────────────────────────────────────────────────────

    private void wireTotalNmField() {
        Runnable commit = () -> {
            if (currentNetwork == null || currentNode == null) return;
            try {
                String txt = fldTotalNm.getText().trim()
                        .replace(" nM", "").replace(" µM", "").replace(" uM", "");
                double val = Double.parseDouble(txt);
                if (fldTotalNm.getText().contains("µ") || fldTotalNm.getText().contains("uM"))
                    val *= 1000.0;
                Utils.setDbl(currentNetwork.getRow(currentNode), Columns.COL_TOTAL_NM, val);
                fldTotalNm.setText(formatNm(val));
                valFree.setText("—"); valHsp70.setText("—"); valHsp90.setText("—");
            } catch (NumberFormatException ignored) {
                Double v = Utils.getDbl(currentNetwork.getRow(currentNode), Columns.COL_TOTAL_NM);
                fldTotalNm.setText(formatNm(v));
            }
        };
        fldTotalNm.addActionListener(e -> commit.run());
        fldTotalNm.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { commit.run(); }
        });
    }

    private void wireKdNmuField() {
        Runnable commit = () -> {
            if (currentNetwork == null || currentEdge == null) return;
            try {
                String txt = fldKduNm.getText().trim()
                        .replace(" nM", "").replace(" µM", "").replace(" uM", "");
                double val = Double.parseDouble(txt);
                if (fldKduNm.getText().contains("µ") || fldKduNm.getText().contains("uM"))
                    val *= 1000.0;
                Utils.setDbl(currentNetwork.getRow(currentEdge), Columns.COL_KD_U_NM, val);
                currentNetwork.getRow(currentEdge).set(Utils.mkCol(Columns.COL_HAS_KD), true);
                fldKduNm.setText(formatNm(val));
                valBound.setText("—"); valFracBound.setText("—");
            } catch (NumberFormatException ignored) {
                Double v = Utils.getDbl(currentNetwork.getRow(currentEdge), Columns.COL_KD_U_NM);
                fldKduNm.setText(v != null ? String.format("%.4g", v) : "—");
            }
        };
        fldKduNm.addActionListener(e -> commit.run());
        fldKduNm.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { commit.run(); }
        });
    }

    private void wireKdNmpField() {
        Runnable commit = () -> {
            if (currentNetwork == null || currentEdge == null) return;
            try {
                String txt = fldKdpNm.getText().trim()
                        .replace(" nM", "").replace(" µM", "").replace(" uM", "");
                double val = Double.parseDouble(txt);
                if (fldKdpNm.getText().contains("µ") || fldKdpNm.getText().contains("uM"))
                    val *= 1000.0;
                Utils.setDbl(currentNetwork.getRow(currentEdge), Columns.COL_KD_P_NM, val);
                currentNetwork.getRow(currentEdge).set(Utils.mkCol(Columns.COL_HAS_KD), true);
                fldKdpNm.setText(formatNm(val));
                valBound.setText("—"); valFracBound.setText("—");
            } catch (NumberFormatException ignored) {
                Double v = Utils.getDbl(currentNetwork.getRow(currentEdge), Columns.COL_KD_P_NM);
                fldKdpNm.setText(v != null ? String.format("%.4g", v) : "—");
            }
        };
        fldKdpNm.addActionListener(e -> commit.run());
        fldKdpNm.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { commit.run(); }
        });
    }

    // ── Phospho network helpers ───────────────────────────────────────────────

    /** Write a 0–1 fraction to the named column on the network row. */
    private void setNetworkPct(CyNetwork network, String colName, double frac) {
        Utils.ensureColumn(network.getDefaultNetworkTable(), colName, Double.class, false);
        network.getRow(network).set(colName, frac);
    }

    /**
     * Push current network column values into the sliders and value labels.
     * Uses the {@code syncingSliders} guard so slider ChangeListeners do not
     * trigger writes back to the network while being moved programmatically.
     */
    private void syncPhosphoSlidersFromNetwork(CyNetwork network) {
        if (network == null) return;
        Utils.ensureColumn(network.getDefaultNetworkTable(), COL_PCT_P_HSP70, Double.class, false);
        Utils.ensureColumn(network.getDefaultNetworkTable(), COL_PCT_P_HSP90, Double.class, false);

        Double p70 = network.getRow(network).get(COL_PCT_P_HSP70, Double.class);
        Double p90 = network.getRow(network).get(COL_PCT_P_HSP90, Double.class);

        int pct70 = (int) Math.round((p70 == null ? 0.0 : p70) * 100.0);
        int pct90 = (int) Math.round((p90 == null ? 0.0 : p90) * 100.0);

        syncingSliders = true;
        try {
            sldPhosphoHsp70.setValue(Math.max(0, Math.min(100, pct70)));
            sldPhosphoHsp90.setValue(Math.max(0, Math.min(100, pct90)));
            lblPhosphoHsp70Val.setText(pct70 + "%");
            lblPhosphoHsp90Val.setText(pct90 + "%");
        } finally {
            syncingSliders = false;
        }
    }

    /**
     * Flush the current slider positions to the network before solving,
     * so the solver always reads up-to-date phosphorylation fractions.
     */
    private void syncPhosphoToNetwork(CyNetwork network) {
        setNetworkPct(network, COL_PCT_P_HSP70, sldPhosphoHsp70.getValue() / 100.0);
        setNetworkPct(network, COL_PCT_P_HSP90, sldPhosphoHsp90.getValue() / 100.0);
    }

    // ── Misc helpers ──────────────────────────────────────────────────────────

    private double safeSlice(List<Double> values, int idx) {
        if (values == null || idx < 0 || idx >= values.size()) return 0.0;
        Double v = values.get(idx);
        return v == null ? 0.0 : v;
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private JScrollPane scrollWrap(JPanel content) {
        JScrollPane sp = new JScrollPane(content);
        sp.setBorder(null);
        sp.setBackground(BG_DARK);
        sp.getViewport().setBackground(BG_DARK);
        return sp;
    }

    private JPanel makeCard() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_DIM, 1),
                new EmptyBorder(10, 12, 10, 12)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return card;
    }

    private JLabel makeLabel(String text, Color color, int size, int style) {
        JLabel lbl = new JLabel(text);
        lbl.setForeground(color);
        lbl.setFont(new Font("SansSerif", style, size));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }

    private JLabel makeBigLabel(Color color) {
        JLabel lbl = new JLabel("—");
        lbl.setForeground(color);
        lbl.setFont(new Font("Monospaced", Font.BOLD, 15));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }

    private JPanel makeMetricBlock(String labelText, JLabel valueLbl, Color accent) {
        JPanel block = new JPanel();
        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
        block.setBackground(BG_CARD);
        block.setAlignmentX(Component.LEFT_ALIGNMENT);
        block.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));

        JPanel top = new JPanel(new BorderLayout(4, 0));
        top.setBackground(BG_CARD);
        top.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));

        JLabel dot = new JLabel("●");
        dot.setForeground(accent);
        dot.setFont(new Font("SansSerif", Font.PLAIN, 8));

        JLabel lbl = new JLabel("  " + labelText.toUpperCase());
        lbl.setForeground(FG_MUTED);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 9));

        top.add(dot, BorderLayout.WEST);
        top.add(lbl, BorderLayout.CENTER);

        valueLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        block.add(top);
        block.add(Box.createVerticalStrut(3));
        block.add(valueLbl);
        return block;
    }

    private JPanel makeEditRow(String labelText, JTextField field, Color accent) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(BG_CARD);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        JPanel left = new JPanel(new BorderLayout(4, 0));
        left.setBackground(BG_CARD);

        JLabel dot = new JLabel("●");
        dot.setForeground(accent);
        dot.setFont(new Font("SansSerif", Font.PLAIN, 8));

        JLabel lbl = new JLabel("  " + labelText.toUpperCase());
        lbl.setForeground(FG_MUTED);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 9));

        left.add(dot, BorderLayout.WEST);
        left.add(lbl, BorderLayout.CENTER);

        row.add(left, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    private JTextField makeEditField(Color accent) {
        JTextField field = new JTextField("—", 9);
        field.setFont(new Font("Monospaced", Font.BOLD, 13));
        field.setForeground(accent);
        field.setBackground(new Color(20, 35, 70));
        field.setCaretColor(ACCENT_EDIT);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_DIM, 1),
                new EmptyBorder(2, 5, 2, 5)));
        field.setHorizontalAlignment(JTextField.RIGHT);
        return field;
    }

    private JLabel makeEditHint(String text) {
        JLabel hint = new JLabel(text);
        hint.setForeground(new Color(100, 120, 160));
        hint.setFont(new Font("SansSerif", Font.ITALIC, 9));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.setBorder(new EmptyBorder(0, 14, 0, 0));
        return hint;
    }

    private JPanel makeSeparator() {
        JPanel sep = new JPanel();
        sep.setBackground(BORDER_DIM);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        return sep;
    }

    private String formatNm(Double value) {
        if (value == null) return "—";
        if (value >= 1000.0) return String.format("%.2f \u00b5M", value / 1000.0);
        return String.format("%.2f nM", value);
    }
}
