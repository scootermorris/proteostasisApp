/* vim: set ts=4 sw=4 et: */
package edu.ucsf.rbvi.proteostasisApp.view;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
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

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.application.swing.CytoPanelComponent;
import org.cytoscape.application.swing.CytoPanelName;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.view.model.View;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
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
 * The Node Details tab shows different fields depending on node type:
 *
 *   cc_tpr / generic nodes (6 rows):
 *     Total (nM) | Free (nM) |
 *     Bound to HSP70 (u) | Bound to HSP70 (p) |
 *     Bound to HSP90 (u) | Bound to HSP90 (p)
 *
 *   Chaperone nodes HSP70 / HSP90 (6 rows):
 *     Total (nM) | Free |
 *     Free (u) | Free (p) |
 *     Bound (u) | Bound (p)
 *
 * Colours in both views match the respective pie-chart slice colours
 * defined in DataManager so the panel and canvas are visually consistent.
 *
 *   CCTPR_PIE slices: [0]#a17800 free [1]#155efd HSP70u [2]#7c3aed HSP70p
 *                     [3]#dc2626 HSP90u [4]#991b1b HSP90p
 *
 *   HSP70_PIE slices: [0]#155efd free_u [1]#2563eb free_p
 *                     [2]#7c3aed bound_u [3]#6d28d9 bound_p
 *
 *   HSP90_PIE slices: [0]#dc2626 free_u [1]#b91c1c free_p
 *                     [2]#991b1b bound_u [3]#7f1d1d bound_p
 */
public class ProteostasisResultsPanel extends JPanel implements CytoPanelComponent {

    private static final long   serialVersionUID = 1L;
    private static final String PANEL_TITLE      = "Proteostasis";

    // Network-level phospho fraction columns
    private static final String COL_PCT_P_HSP70 = "pct_p_hsp70";
    private static final String COL_PCT_P_HSP90 = "pct_p_hsp90";

    // CardLayout keys for the two node-detail views
    private static final String CARD_CCTPR     = "cctpr";
    private static final String CARD_CHAPERONE = "chaperone";

    // ── Colour palette ────────────────────────────────────────────────────────

    private static final Color BG_DARK      = new Color(26,  37,  64);
    private static final Color BG_CARD      = new Color(13,  25,  50);
    private static final Color BG_CONTROLS  = new Color(10,  18,  40);
    private static final Color BG_SUBPANEL  = new Color(18,  30,  58);
    private static final Color FG_WHITE     = Color.WHITE;
    private static final Color FG_MUTED     = new Color(150, 170, 210);
    private static final Color BORDER_DIM   = new Color(42,  74, 127);

    // Total — orange; not a pie slice, consistent across all node types
    private static final Color ACCENT_TOTAL = new Color(249, 115,  22);

    // ── CC-TPR node accent colours — match CCTPR_PIE slices ──────────────────
    // slice [0] #a17800 — Free
    private static final Color CC_FREE      = new Color(161, 120,   0);
    // slice [1] #155efd — Bound HSP70 (u)
    private static final Color CC_H70U      = new Color( 21,  94, 253);
    // slice [2] #7c3aed — Bound HSP70 (p)
    private static final Color CC_H70P      = new Color(124,  58, 237);
    // slice [3] #dc2626 — Bound HSP90 (u)
    private static final Color CC_H90U      = new Color(220,  38,  38);
    // slice [4] #991b1b — Bound HSP90 (p)
    private static final Color CC_H90P      = new Color(153,  27,  27);

    // ── HSP70 node accent colours — match HSP70_PIE slices ───────────────────
    // slice [0] #155efd — Free (u)
    private static final Color H70_FREE_U   = new Color( 21,  94, 253);
    // slice [1] #2563eb — Free (p)
    private static final Color H70_FREE_P   = new Color( 37,  99, 235);
    // slice [2] #7c3aed — Bound (u)
    private static final Color H70_BOUND_U  = new Color(124,  58, 237);
    // slice [3] #6d28d9 — Bound (p)
    private static final Color H70_BOUND_P  = new Color(109,  40, 217);
    // aggregate free = free_u + free_p — mid-blue between the two free slices
    private static final Color H70_FREE     = new Color( 28,  96, 244);

    // ── HSP90 node accent colours — match HSP90_PIE slices ───────────────────
    // slice [0] #dc2626 — Free (u)
    private static final Color H90_FREE_U   = new Color(220,  38,  38);
    // slice [1] #b91c1c — Free (p)
    private static final Color H90_FREE_P   = new Color(185,  28,  28);
    // slice [2] #991b1b — Bound (u)
    private static final Color H90_BOUND_U  = new Color(153,  27,  27);
    // slice [3] #7f1d1d — Bound (p)
    private static final Color H90_BOUND_P  = new Color(127,  29,  29);
    // aggregate free — mid-red
    private static final Color H90_FREE     = new Color(202,  33,  33);

    // ── Edge / misc accents ───────────────────────────────────────────────────
    private static final Color ACCENT_KD    = new Color(196, 148, 255);
    private static final Color ACCENT_BOUND = new Color( 74, 222, 128);
    private static final Color ACCENT_FRAC  = new Color(251, 191,  36);
    private static final Color ACCENT_EDIT  = new Color(250, 204,  21);
    private static final Color ACCENT_ADD   = new Color( 52, 211, 153);

    // ── Phospho slider accents (match dominant chaperone slice colour) ────────
    private static final Color SLD_HSP70    = H70_FREE_U;   // #155efd
    private static final Color SLD_HSP90    = H90_FREE_U;   // #dc2626

    // ── Service registrar ─────────────────────────────────────────────────────
    private final CyServiceRegistrar registrar;

    // ── Node tab — shared chrome ──────────────────────────────────────────────
    private final JPanel     nodeIdentCard;
    private final JLabel     lblNodeName;
    private final JLabel     lblNodeClass;
    private final JPanel     nodeDataPanel;   // holds the CardLayout switcher
    private final CardLayout nodeCardLayout;
    private final JLabel     nodeNoSelMsg;

    // ── Node tab — CC-TPR card (5 data rows + add button) ────────────────────
    private final JTextField fldTotalNm;     // editable, shared
    private final JLabel     valFree;
    private final JLabel     valH70u;
    private final JLabel     valH70p;
    private final JLabel     valH90u;
    private final JLabel     valH90p;

    // ── Node tab — Chaperone card (5 data rows) ───────────────────────────────
    // These labels' colours are set dynamically in showNode() based on HSP70 vs HSP90
    private final JLabel     chapValFree;
    private final JLabel     chapValFreeU;
    private final JLabel     chapValFreeP;
    private final JLabel     chapValBoundU;
    private final JLabel     chapValBoundP;
    // dot labels in the chaperone card — recoloured per chaperone
    private final JLabel     chapDotFree;
    private final JLabel     chapDotFreeU;
    private final JLabel     chapDotFreeP;
    private final JLabel     chapDotBoundU;
    private final JLabel     chapDotBoundP;

    // ── Edge tab fields ───────────────────────────────────────────────────────
    private final JPanel     edgeIdentCard;
    private final JLabel     lblEdgeName;
    private final JPanel     edgeDataPanel;
    private final JTextField fldKduNm;
    private final JTextField fldKdpNm;
    private final JLabel     valBound;
    private final JLabel     valFracBound;
    private final JLabel     edgeNoSelMsg;

    // ── Phosphorylation sliders ───────────────────────────────────────────────
    private final JSlider sldPhosphoHsp70;
    private final JSlider sldPhosphoHsp90;
    private final JLabel  lblPhosphoHsp70Val;
    private final JLabel  lblPhosphoHsp90Val;
    private boolean syncingSliders = false;

    // ── Filter checkboxes ─────────────────────────────────────────────────────
    private final JCheckBox chkShowHsp70Edges;
    private final JCheckBox chkShowHsp90Edges;
    private final JCheckBox chkShowNonChapEdges;
    private final JCheckBox chkShowGhostEdges;

    // ── State ─────────────────────────────────────────────────────────────────
    private CyNetwork currentNetwork;
    private CyNode    currentNode;
    private CyEdge    currentEdge;

    public ProteostasisResultsPanel(CyServiceRegistrar registrar) {
        this.registrar = registrar;
        setLayout(new BorderLayout());
        setBackground(BG_DARK);
        setPreferredSize(new Dimension(290, 620));

        // ── Header ────────────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(10, 18, 40));
        header.setBorder(new EmptyBorder(10, 14, 10, 14));
        JLabel title = new JLabel("Proteostasis Details");
        title.setForeground(FG_WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 13));
        header.add(title, BorderLayout.WEST);
        add(header, BorderLayout.NORTH);

        // ── Tabs ──────────────────────────────────────────────────────────────
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

        // Identity card
        nodeIdentCard = makeCard();
        lblNodeName   = makeLabel("—", FG_WHITE, 14, Font.BOLD);
        lblNodeClass  = makeLabel("",  FG_MUTED, 10, Font.ITALIC);
        nodeIdentCard.add(lblNodeName);
        nodeIdentCard.add(Box.createVerticalStrut(3));
        nodeIdentCard.add(lblNodeClass);
        nodeTab.add(nodeIdentCard);
        nodeTab.add(Box.createVerticalStrut(8));

        // Editable total field — shared between both cards
        fldTotalNm = makeEditField(ACCENT_TOTAL);

        // ── CC-TPR concentration card ─────────────────────────────────────────
        JPanel cctprCard = makeCard();
        JLabel cctprTitle = makeLabel("CONCENTRATIONS", FG_MUTED, 9, Font.BOLD);
        cctprTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        cctprCard.add(cctprTitle);
        cctprCard.add(Box.createVerticalStrut(8));
        cctprCard.add(makeEditRow("Total (nM)", fldTotalNm, ACCENT_TOTAL));
        cctprCard.add(Box.createVerticalStrut(2));
        cctprCard.add(makeEditHint("Edit and Solve to update"));
        cctprCard.add(Box.createVerticalStrut(10));
        cctprCard.add(makeSeparator());
        cctprCard.add(Box.createVerticalStrut(10));

        valFree = makeBigLabel(CC_FREE);
        valH70u = makeBigLabel(CC_H70U);
        valH70p = makeBigLabel(CC_H70P);
        valH90u = makeBigLabel(CC_H90U);
        valH90p = makeBigLabel(CC_H90P);

        cctprCard.add(makeMetricBlock("Free (nM)",        valFree, CC_FREE));
        cctprCard.add(Box.createVerticalStrut(8)); cctprCard.add(makeSeparator()); cctprCard.add(Box.createVerticalStrut(8));
        cctprCard.add(makeMetricBlock("HSP70 bound (u)",  valH70u, CC_H70U));
        cctprCard.add(Box.createVerticalStrut(8)); cctprCard.add(makeSeparator()); cctprCard.add(Box.createVerticalStrut(8));
        cctprCard.add(makeMetricBlock("HSP70 bound (p)",  valH70p, CC_H70P));
        cctprCard.add(Box.createVerticalStrut(8)); cctprCard.add(makeSeparator()); cctprCard.add(Box.createVerticalStrut(8));
        cctprCard.add(makeMetricBlock("HSP90 bound (u)",  valH90u, CC_H90U));
        cctprCard.add(Box.createVerticalStrut(8)); cctprCard.add(makeSeparator()); cctprCard.add(Box.createVerticalStrut(8));
        cctprCard.add(makeMetricBlock("HSP90 bound (p)",  valH90p, CC_H90P));
        cctprCard.add(Box.createVerticalStrut(12)); cctprCard.add(makeSeparator()); cctprCard.add(Box.createVerticalStrut(10));
        cctprCard.add(makeAddInteractorButton());

        // ── Chaperone concentration card ──────────────────────────────────────
        // Colours are set dynamically; we keep refs to both dots and value labels.
        JPanel chapCard = makeCard();
        JLabel chapTitle = makeLabel("CONCENTRATIONS", FG_MUTED, 9, Font.BOLD);
        chapTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        chapCard.add(chapTitle);
        chapCard.add(Box.createVerticalStrut(8));
        // Total row reuses the same fldTotalNm text field (only one card visible at a time)
        chapCard.add(makeEditRow("Total (nM)", fldTotalNm, ACCENT_TOTAL));
        chapCard.add(Box.createVerticalStrut(2));
        chapCard.add(makeEditHint("Edit and Solve to update"));
        chapCard.add(Box.createVerticalStrut(10));
        chapCard.add(makeSeparator());
        chapCard.add(Box.createVerticalStrut(10));

        // Initialise with placeholder colour; showNode() will re-colour dynamically
        chapValFree   = makeBigLabel(H70_FREE);
        chapValFreeU  = makeBigLabel(H70_FREE_U);
        chapValFreeP  = makeBigLabel(H70_FREE_P);
        chapValBoundU = makeBigLabel(H70_BOUND_U);
        chapValBoundP = makeBigLabel(H70_BOUND_P);

        // Build metric blocks and hold refs to dots for re-colouring
        chapDotFree   = new JLabel("●"); chapDotFree.setFont(new Font("SansSerif", Font.PLAIN, 8));
        chapDotFreeU  = new JLabel("●"); chapDotFreeU.setFont(new Font("SansSerif", Font.PLAIN, 8));
        chapDotFreeP  = new JLabel("●"); chapDotFreeP.setFont(new Font("SansSerif", Font.PLAIN, 8));
        chapDotBoundU = new JLabel("●"); chapDotBoundU.setFont(new Font("SansSerif", Font.PLAIN, 8));
        chapDotBoundP = new JLabel("●"); chapDotBoundP.setFont(new Font("SansSerif", Font.PLAIN, 8));

        chapCard.add(makeChapMetricBlock("Free",     chapDotFree,   chapValFree));
        chapCard.add(Box.createVerticalStrut(8)); chapCard.add(makeSeparator()); chapCard.add(Box.createVerticalStrut(8));
        chapCard.add(makeChapMetricBlock("Free (u)", chapDotFreeU,  chapValFreeU));
        chapCard.add(Box.createVerticalStrut(8)); chapCard.add(makeSeparator()); chapCard.add(Box.createVerticalStrut(8));
        chapCard.add(makeChapMetricBlock("Free (p)", chapDotFreeP,  chapValFreeP));
        chapCard.add(Box.createVerticalStrut(8)); chapCard.add(makeSeparator()); chapCard.add(Box.createVerticalStrut(8));
        chapCard.add(makeChapMetricBlock("Bound (u)",chapDotBoundU, chapValBoundU));
        chapCard.add(Box.createVerticalStrut(8)); chapCard.add(makeSeparator()); chapCard.add(Box.createVerticalStrut(8));
        chapCard.add(makeChapMetricBlock("Bound (p)",chapDotBoundP, chapValBoundP));

        // ── CardLayout switcher ───────────────────────────────────────────────
        nodeCardLayout = new CardLayout();
        nodeDataPanel  = new JPanel(nodeCardLayout);
        nodeDataPanel.setBackground(BG_DARK);
        nodeDataPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        nodeDataPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        nodeDataPanel.add(cctprCard,  CARD_CCTPR);
        nodeDataPanel.add(chapCard,   CARD_CHAPERONE);

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

        // ── Build sliders ─────────────────────────────────────────────────────
        sldPhosphoHsp70    = makePhosphoSlider();
        sldPhosphoHsp90    = makePhosphoSlider();
        lblPhosphoHsp70Val = makeSliderValLabel();
        lblPhosphoHsp90Val = makeSliderValLabel();

        // ── Build filter checkboxes ───────────────────────────────────────────
        chkShowHsp70Edges   = makeFilterCheckBox("Show HSP70 edges");
        chkShowHsp90Edges   = makeFilterCheckBox("Show HSP90 edges");
        chkShowNonChapEdges = makeFilterCheckBox("Show non-chaperone edges");
        chkShowGhostEdges   = makeFilterCheckBox("Show ghost edges");

        add(buildControlsStrip(), BorderLayout.SOUTH);

        wireTotalNmField();
        wireKdNmuField();
        wireKdNmpField();
        wirePhosphoSlider(sldPhosphoHsp70, lblPhosphoHsp70Val, COL_PCT_P_HSP70);
        wirePhosphoSlider(sldPhosphoHsp90, lblPhosphoHsp90Val, COL_PCT_P_HSP90);
        wireFilterCheckboxes();
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

            List<Double> boundList = Utils.getList(row, Columns.COL_BOUND, Double.class);

            boolean isHsp70 = "HSP70".equals(name);
            boolean isHsp90 = "HSP90".equals(name);

            if (isHsp70 || isHsp90) {
                // ── Chaperone node ────────────────────────────────────────────
                // boundList = [free_u, free_p, bound_u, bound_p]  (size 4)
                double freeU  = safeSlice(boundList, 0);
                double freeP  = safeSlice(boundList, 1);
                double boundU = safeSlice(boundList, 2);
                double boundP = safeSlice(boundList, 3);
                // aggregate free (from COL_FREE) = freeU + freeP
                double freeTotal = (free != null) ? free : (freeU + freeP);

                // Re-colour dots and value labels to match this chaperone's pie
                Color cFree   = isHsp70 ? H70_FREE   : H90_FREE;
                Color cFreeU  = isHsp70 ? H70_FREE_U  : H90_FREE_U;
                Color cFreeP  = isHsp70 ? H70_FREE_P  : H90_FREE_P;
                Color cBoundU = isHsp70 ? H70_BOUND_U : H90_BOUND_U;
                Color cBoundP = isHsp70 ? H70_BOUND_P : H90_BOUND_P;

                recolour(chapDotFree,   chapValFree,   cFree);
                recolour(chapDotFreeU,  chapValFreeU,  cFreeU);
                recolour(chapDotFreeP,  chapValFreeP,  cFreeP);
                recolour(chapDotBoundU, chapValBoundU, cBoundU);
                recolour(chapDotBoundP, chapValBoundP, cBoundP);

                chapValFree.setText(formatNm(freeTotal));
                chapValFreeU.setText(formatNm(freeU));
                chapValFreeP.setText(formatNm(freeP));
                chapValBoundU.setText(formatNm(boundU));
                chapValBoundP.setText(formatNm(boundP));

                nodeCardLayout.show(nodeDataPanel, CARD_CHAPERONE);

            } else {
                // ── CC-TPR / generic node ─────────────────────────────────────
                // boundList = [free, HSP70_u, HSP70_p, HSP90_u, HSP90_p]  (size 5)
                double h70u = safeSlice(boundList, 1);
                double h70p = safeSlice(boundList, 2);
                double h90u = safeSlice(boundList, 3);
                double h90p = safeSlice(boundList, 4);

                // Fallback to adjacent edge sums if pool data not yet available
                if (boundList == null || boundList.size() < 5) {
                    for (CyEdge edge : network.getAdjacentEdgeList(node, CyEdge.Type.ANY)) {
                        CyNode other     = edge.getSource().equals(node) ? edge.getTarget() : edge.getSource();
                        String otherName = network.getRow(other).get(CyNetwork.NAME, String.class);
                        Double bound     = Utils.getDbl(network.getRow(edge), Columns.COL_BOUND);
                        if (bound == null) continue;
                        if ("HSP70".equals(otherName)) h70u += bound;
                        else if ("HSP90".equals(otherName)) h90u += bound;
                    }
                }

                valFree.setText(formatNm(free));
                valH70u.setText(formatNm(h70u));
                valH70p.setText(formatNm(h70p));
                valH90u.setText(formatNm(h90u));
                valH90p.setText(formatNm(h90p));

                nodeCardLayout.show(nodeDataPanel, CARD_CCTPR);
            }

            fldTotalNm.setText(formatNm(total));
            lblNodeName.setText(name      != null ? name      : "—");
            lblNodeClass.setText(nodeClass != null ? nodeClass : "");

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
        strip.add(makeFiltersPanel());
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

    private JPanel makePhosphorylationPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_SUBPANEL);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        TitledBorder tb = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BORDER_DIM, 1),
                "Phosphorylation",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 10), FG_MUTED);
        panel.setBorder(BorderFactory.createCompoundBorder(tb, new EmptyBorder(4, 6, 8, 6)));

        panel.add(makeSliderRow("HSP70", sldPhosphoHsp70, lblPhosphoHsp70Val, SLD_HSP70));
        panel.add(Box.createVerticalStrut(6));
        panel.add(makeSliderRow("HSP90", sldPhosphoHsp90, lblPhosphoHsp90Val, SLD_HSP90));
        return panel;
    }

    private JPanel makeFiltersPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_SUBPANEL);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        TitledBorder tb = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BORDER_DIM, 1),
                "Filters",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 10), FG_MUTED);
        panel.setBorder(BorderFactory.createCompoundBorder(tb, new EmptyBorder(4, 6, 8, 6)));

        panel.add(chkShowHsp70Edges);
        panel.add(Box.createVerticalStrut(3));
        panel.add(chkShowHsp90Edges);
        panel.add(Box.createVerticalStrut(3));
        panel.add(chkShowNonChapEdges);
        panel.add(Box.createVerticalStrut(3));
        panel.add(chkShowGhostEdges);
        return panel;
    }

    private JCheckBox makeFilterCheckBox(String text) {
        JCheckBox cb = new JCheckBox(text, true);
        cb.setForeground(FG_MUTED);
        cb.setBackground(BG_SUBPANEL);
        cb.setFont(new Font("SansSerif", Font.PLAIN, 11));
        cb.setAlignmentX(Component.LEFT_ALIGNMENT);
        cb.setFocusPainted(false);
        return cb;
    }

    private void wireFilterCheckboxes() {
        chkShowHsp70Edges  .addActionListener(e -> applyEdgeFilters());
        chkShowHsp90Edges  .addActionListener(e -> applyEdgeFilters());
        chkShowNonChapEdges.addActionListener(e -> applyEdgeFilters());
        chkShowGhostEdges  .addActionListener(e -> applyEdgeFilters());
    }

    private void applyEdgeFilters() {
        updateCurrentNetwork();

        if (currentNetwork == null) return;
        Collection<CyNetworkView> views =
                registrar.getService(CyNetworkViewManager.class).getNetworkViews(currentNetwork);
        if (views.isEmpty()) return;
        CyNetworkView view = views.iterator().next();

        boolean showHsp70   = chkShowHsp70Edges  .isSelected();
        boolean showHsp90   = chkShowHsp90Edges  .isSelected();
        boolean showNonChap = chkShowNonChapEdges.isSelected();
        boolean showGhost   = chkShowGhostEdges  .isSelected();

        for (CyEdge edge : currentNetwork.getEdgeList()) {
            CyRow   eRow   = currentNetwork.getRow(edge);
            String  target = Utils.getStr(eRow, Columns.COL_TARGET);
            Boolean hasKd  = eRow.get(Utils.mkCol(Columns.COL_HAS_KD), Boolean.class);

            boolean isHsp70   = "HSP70".equals(target);
            boolean isHsp90   = "HSP90".equals(target);
            boolean isGhost   = Boolean.FALSE.equals(hasKd);
            boolean isNonChap = !isHsp70 && !isHsp90;

            boolean visible = true;
            if (isHsp70   && !showHsp70)   visible = false;
            if (isHsp90   && !showHsp90)   visible = false;
            if (isNonChap && !showNonChap) visible = false;
            if (isGhost   && !showGhost)   visible = false;

            View<CyEdge> ev = view.getEdgeView(edge);
            if (ev != null) ev.setLockedValue(BasicVisualLexicon.EDGE_VISIBLE, visible);
        }
        view.updateView();
    }

    // ── Phospho slider helpers ────────────────────────────────────────────────

    private JPanel makeSliderRow(String label, JSlider slider, JLabel valLabel, Color accent) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(BG_SUBPANEL);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

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

        valLabel.setPreferredSize(new Dimension(36, 20));
        valLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        row.add(left,     BorderLayout.WEST);
        row.add(slider,   BorderLayout.CENTER);
        row.add(valLabel, BorderLayout.EAST);
        return row;
    }

    private JSlider makePhosphoSlider() {
        JSlider s = new JSlider(JSlider.HORIZONTAL, 0, 100, 0);
        s.setBackground(BG_SUBPANEL);
        s.setForeground(FG_MUTED);
        s.setPaintTicks(false);
        s.setPaintLabels(false);
        s.setSnapToTicks(false);
        s.setOpaque(false);
        return s;
    }

    private JLabel makeSliderValLabel() {
        JLabel lbl = new JLabel("0%");
        lbl.setForeground(FG_MUTED);
        lbl.setFont(new Font("Monospaced", Font.BOLD, 10));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }

    private void wirePhosphoSlider(JSlider slider, JLabel valLabel, String colName) {
        slider.addChangeListener((ChangeEvent e) -> {
            updateCurrentNetwork();
            int pct = slider.getValue();
            valLabel.setText(pct + "%");
            if (!slider.getValueIsAdjusting() && !syncingSliders && currentNetwork != null) {
                setNetworkPct(currentNetwork, colName, pct / 100.0);
                fireSolveTask();
            }
        });
    }

    // ── Add Interactor button ─────────────────────────────────────────────────

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
            updateCurrentNetwork();
            if (currentNetwork == null) return; // Shouldn't happen

            syncPhosphoToNetwork(currentNetwork);

            SynchronousTaskManager tm = registrar.getService(SynchronousTaskManager.class);
            TunableSetter ts = registrar.getService(TunableSetter.class);
            Map<String, Object> tunableValues = new HashMap<>();
            CyRow netRow = currentNetwork.getRow(currentNetwork);
            tunableValues.put("max_iter", Utils.getInt(netRow, Columns.COL_MAX_ITER));
            tunableValues.put("tol",      Utils.getDbl(netRow, Columns.COL_TOLERANCE));
            tunableValues.put("damping",  Utils.getDbl(netRow, Columns.COL_DAMPING));
            TaskIterator tasks = new SolveNetworkTaskFactory(registrar).createTaskIterator();
            tm.execute(ts.createTaskIterator(tasks, tunableValues));

            if (currentNode != null)
                showNode(currentNetwork, currentNode);
            else if (currentEdge != null)
                showEdge(currentNetwork, currentEdge);
        } catch (Exception ex) {
            System.err.println("[ProteostasisApp] Could not fire Solve task: " + ex.getMessage());
        }
    }

    // ── Field wiring ──────────────────────────────────────────────────────────

    private void wireTotalNmField() {
        Runnable commit = () -> {
            updateCurrentNetwork();
            if (currentNetwork == null || currentNode == null) return;
            try {
                String txt = fldTotalNm.getText().trim()
                        .replace(" nM", "").replace(" µM", "").replace(" uM", "");
                double val = Double.parseDouble(txt);
                if (fldTotalNm.getText().contains("µ") || fldTotalNm.getText().contains("uM"))
                    val *= 1000.0;
                Utils.setDbl(currentNetwork.getRow(currentNode), Columns.COL_TOTAL_NM, val);
                fldTotalNm.setText(formatNm(val));
                clearNodeValues();
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

    /** Reset all node value labels to "—" after an edit. */
    private void clearNodeValues() {
        valFree.setText("—"); valH70u.setText("—"); valH70p.setText("—");
        valH90u.setText("—"); valH90p.setText("—");
        chapValFree.setText("—"); chapValFreeU.setText("—"); chapValFreeP.setText("—");
        chapValBoundU.setText("—"); chapValBoundP.setText("—");
    }

    private void wireKdNmuField() {
        Runnable commit = () -> {
            updateCurrentNetwork();
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
            updateCurrentNetwork();
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

    private void setNetworkPct(CyNetwork network, String colName, double frac) {
        Utils.ensureColumn(network.getDefaultNetworkTable(), colName, Double.class, false);
        network.getRow(network).set(colName, frac);
    }

    private void syncPhosphoSlidersFromNetwork(CyNetwork network) {
        if (network == null) return;
        Utils.ensureColumn(network.getDefaultNetworkTable(), COL_PCT_P_HSP70, Double.class, false);
        Utils.ensureColumn(network.getDefaultNetworkTable(), COL_PCT_P_HSP90, Double.class, false);

        Double p70 = network.getRow(network).get(COL_PCT_P_HSP70, Double.class);
        Double p90 = network.getRow(network).get(COL_PCT_P_HSP90, Double.class);
        int pct70  = (int) Math.round((p70 == null ? 0.0 : p70) * 100.0);
        int pct90  = (int) Math.round((p90 == null ? 0.0 : p90) * 100.0);

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

    private void syncPhosphoToNetwork(CyNetwork network) {
        setNetworkPct(network, COL_PCT_P_HSP70, sldPhosphoHsp70.getValue() / 100.0);
        setNetworkPct(network, COL_PCT_P_HSP90, sldPhosphoHsp90.getValue() / 100.0);
    }

    // ── Misc helpers ──────────────────────────────────────────────────────────

    private void recolour(JLabel dot, JLabel value, Color c) {
        dot.setForeground(c);
        value.setForeground(c);
    }

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

    /**
     * Standard metric block with a pre-set dot colour — used for cc_tpr rows
     * where the colour never changes.
     */
    private JPanel makeMetricBlock(String labelText, JLabel valueLbl, Color accent) {
        JLabel dot = new JLabel("●");
        dot.setForeground(accent);
        dot.setFont(new Font("SansSerif", Font.PLAIN, 8));
        return buildMetricBlock(labelText, dot, valueLbl);
    }

    /**
     * Chaperone metric block with externally-owned dot — allows the dot and
     * value to be recoloured at runtime when switching between HSP70/HSP90.
     */
    private JPanel makeChapMetricBlock(String labelText, JLabel dot, JLabel valueLbl) {
        dot.setForeground(H70_FREE);   // initial colour; overwritten by recolour()
        dot.setFont(new Font("SansSerif", Font.PLAIN, 8));
        return buildMetricBlock(labelText, dot, valueLbl);
    }

    private JPanel buildMetricBlock(String labelText, JLabel dot, JLabel valueLbl) {
        JPanel block = new JPanel();
        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
        block.setBackground(BG_CARD);
        block.setAlignmentX(Component.LEFT_ALIGNMENT);
        block.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));

        JPanel top = new JPanel(new BorderLayout(4, 0));
        top.setBackground(BG_CARD);
        top.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));

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

    private void updateCurrentNetwork() {
        if (this.currentNetwork == null)
            this.currentNetwork = registrar.getService(CyApplicationManager.class).getCurrentNetwork();
    }
}
