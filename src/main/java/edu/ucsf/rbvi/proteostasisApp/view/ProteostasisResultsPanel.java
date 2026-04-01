/* vim: set ts=4 sw=4 et: */
package edu.ucsf.rbvi.proteostasisApp.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;

import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import org.cytoscape.application.swing.CytoPanelComponent;
import org.cytoscape.application.swing.CytoPanelName;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyIdentifiable;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.work.TaskManager;

import edu.ucsf.rbvi.proteostasisApp.Columns;
import edu.ucsf.rbvi.proteostasisApp.tasks.SolveNetworkTaskFactory;
import edu.ucsf.rbvi.proteostasisApp.utils.Utils;

/**
 * A Cytoscape Results Panel (EAST panel) that shows proteostasis
 * concentration data for the currently selected node:
 *
 *   - total_nM       (total concentration)
 *   - free           (unbound/free concentration)
 *   - bound to HSP70 (edge "bound" column where neighbour == "HSP70")
 *   - bound to HSP90 (edge "bound" column where neighbour == "HSP90")
 *
 * Each metric is shown as a stacked card:
 *   ● LABEL
 *   123.45 nM
 *
 * so the numeric value is always the dominant visual element.
 */
public class ProteostasisResultsPanel extends JPanel implements CytoPanelComponent {

    private static final long   serialVersionUID = 1L;
    private static final String PANEL_TITLE      = "Proteostasis";

    // ── Colour palette (matches the app's dark theme) ────────────────────────
    // private static final Color BG_DARK      = new Color(230, 230, 230);
    private static final Color BG_DARK      = Color.WHITE;
    // private static final Color BG_CARD      = new Color(230, 230, 230);
    private static final Color BG_CARD      = Color.WHITE;
    private static final Color FG_WHITE     = Color.WHITE;
    private static final Color FG_DARK      = Color.BLUE;
    // private static final Color FG_MUTED     = new Color(150, 170, 210);
    private static final Color FG_MUTED     = Color.BLUE;
    private static final Color ACCENT_TOTAL = new Color( 74, 222, 128);   // green
    private static final Color ACCENT_FREE  = new Color(230, 199,  95);   // goldenrod
    private static final Color ACCENT_HSP70 = new Color( 21,  94, 253);   // blue
    private static final Color ACCENT_HSP90 = new Color(253,  54,  54);   // red

    // ── Updatable labels ──────────────────────────────────────────────────────
    private final JLabel lblNodeName;
    private final JLabel lblNodeClass;
    private final JTextField valTotal;
    private final JLabel valFree;
    private final JLabel valHsp70;
    private final JLabel valHsp90;
    private final JLabel noSelectionMsg;
    private final JPanel identityCard;
    private final JPanel dataPanel;
    private final JPanel sliderPanel;
    private final JPanel filtersPanel;
    private final JPanel buttonPanel;

    private final CyServiceRegistrar registrar;
    private CyNetwork network;
    private CyNode node;

    public ProteostasisResultsPanel(final CyServiceRegistrar registrar) {
        this.registrar = registrar;

        setLayout(new BorderLayout());
        setBackground(BG_DARK);
        setPreferredSize(new Dimension(280, 420));

        // ── Header bar ────────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_CARD);
        header.setBorder(new EmptyBorder(12, 14, 12, 14));
        JLabel title = new JLabel("Node Details");
        title.setForeground(FG_DARK);
        title.setFont(new Font("SansSerif", Font.BOLD, 14));
        header.add(title, BorderLayout.WEST);
        add(header, BorderLayout.NORTH);

        // ── Scrollable content area ───────────────────────────────────────────
        JPanel centre = new JPanel();
        centre.setLayout(new BoxLayout(centre, BoxLayout.Y_AXIS));
        centre.setBackground(BG_DARK);
        centre.setBorder(new EmptyBorder(10, 10, 10, 10));

        // -- Identity card (node name + class) ---------------------------------
        identityCard = makeCard();
        identityCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 75));
        lblNodeName  = new JLabel("—");
        lblNodeName.setForeground(FG_DARK);
        lblNodeName.setFont(new Font("SansSerif", Font.BOLD, 15));
        lblNodeName.setAlignmentX(Component.LEFT_ALIGNMENT);

        lblNodeClass = new JLabel("");
        lblNodeClass.setForeground(FG_MUTED);
        lblNodeClass.setFont(new Font("SansSerif", Font.ITALIC, 11));
        lblNodeClass.setAlignmentX(Component.LEFT_ALIGNMENT);

        identityCard.add(lblNodeName);
        identityCard.add(Box.createVerticalStrut(3));
        identityCard.add(lblNodeClass);
        centre.add(identityCard);
        centre.add(Box.createVerticalStrut(8));

        // -- Concentrations card -----------------------------------------------
        dataPanel = makeCard();
        dataPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel concTitle = new JLabel("Concentrations");
        concTitle.setForeground(FG_MUTED);
        concTitle.setFont(new Font("SansSerif", Font.BOLD, 10));
        concTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        dataPanel.add(concTitle);
        dataPanel.add(Box.createVerticalStrut(10));

        // Create value labels — big, monospaced, coloured
        valTotal = makeEditableBigValueLabel(ACCENT_TOTAL, CyNode.class, Columns.COL_TOTAL_NM); // Make edtiable
        valFree  = makeBigValueLabel(ACCENT_FREE);
        valHsp70 = makeBigValueLabel(ACCENT_HSP70);
        valHsp90 = makeBigValueLabel(ACCENT_HSP90);

        dataPanel.add(makeMetricBlock("Total",         valTotal,  ACCENT_TOTAL));
        dataPanel.add(Box.createVerticalStrut(10));
        dataPanel.add(makeSeparator());
        dataPanel.add(Box.createVerticalStrut(10));
        dataPanel.add(makeMetricBlock("Free",          valFree,   ACCENT_FREE));
        dataPanel.add(Box.createVerticalStrut(10));
        dataPanel.add(makeSeparator());
        dataPanel.add(Box.createVerticalStrut(10));
        dataPanel.add(makeMetricBlock("Bound to HSP70", valHsp70, ACCENT_HSP70));
        dataPanel.add(Box.createVerticalStrut(10));
        dataPanel.add(makeSeparator());
        dataPanel.add(Box.createVerticalStrut(10));
        dataPanel.add(makeMetricBlock("Bound to HSP90", valHsp90, ACCENT_HSP90));

        centre.add(dataPanel);

        // -- Phosphorylation sliders ------------------------------------------
        sliderPanel = makeCard();
        JLabel sliderTitle = new JLabel("Phosphorylation sliders");
        sliderTitle.setForeground(FG_MUTED);
        sliderTitle.setFont(new Font("SansSerif", Font.BOLD, 10));
        sliderTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        sliderPanel.add(sliderTitle);
        sliderPanel.add(Box.createVerticalStrut(10));

        centre.add(sliderPanel);

        // -- Filters ----------------------------------------------------------
        filtersPanel = makeCard();
        JLabel filtersTitle = new JLabel("Filters");
        filtersTitle.setForeground(FG_MUTED);
        filtersTitle.setFont(new Font("SansSerif", Font.BOLD, 10));
        filtersTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        filtersPanel.add(filtersTitle);
        filtersPanel.add(Box.createVerticalStrut(10));

        centre.add(filtersPanel);

        // -- Action buttons ---------------------------------------------------
        buttonPanel = makeCard();
        JButton resolveButton = new JButton("Solve Network");
        resolveButton.setFont(new Font("SansSerif", Font.BOLD, 10));
        resolveButton.setForeground(FG_MUTED);
        resolveButton.addActionListener(e -> {
            TaskManager<?, ?> tm = registrar.getService(TaskManager.class);
            SolveNetworkTaskFactory tf = new SolveNetworkTaskFactory(registrar);
            tm.execute(tf.createTaskIterator());
        });
        buttonPanel.add(resolveButton);
        buttonPanel.add(Box.createVerticalStrut(10));

        centre.add(buttonPanel);

        // -- No-selection placeholder ------------------------------------------
        noSelectionMsg = new JLabel(
                "<html><center>Select a node<br>to view details</center></html>",
                SwingConstants.CENTER);
        noSelectionMsg.setForeground(FG_MUTED);
        noSelectionMsg.setFont(new Font("SansSerif", Font.ITALIC, 12));
        noSelectionMsg.setAlignmentX(Component.CENTER_ALIGNMENT);
        noSelectionMsg.setBorder(new EmptyBorder(40, 10, 10, 10));
        centre.add(Box.createVerticalStrut(10));
        centre.add(noSelectionMsg);

        JScrollPane scroll = new JScrollPane(centre);
        scroll.setBorder(null);
        scroll.setBackground(BG_DARK);
        scroll.getViewport().setBackground(BG_DARK);
        add(scroll, BorderLayout.CENTER);

        // Initial state: data hidden, placeholder visible
        identityCard.setVisible(false);
        dataPanel.setVisible(false);
        sliderPanel.setVisible(false);
        filtersPanel.setVisible(false);
        buttonPanel.setVisible(false);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Show concentration data for the given selected node. */
    public void showNode(CyNetwork network, CyNode node) {
        this.network = network;
        this.node = node;

        SwingUtilities.invokeLater(() -> {
            CyRow row          = network.getRow(node);
            String name        = row.get(CyNetwork.NAME,               String.class);
            String nodeClass   = Utils.getStr(row, Columns.COL_NODE_CLASS);
            Double total       = Utils.getDbl(row, Columns.COL_TOTAL_NM);
            Double free        = Utils.getDbl(row, Columns.COL_FREE);
            List<Double> bound = Utils.getList(row, Columns.COL_BOUND, Double.class);

            // Update all labels
            lblNodeName.setText(name      != null ? name      : "—");
            lblNodeClass.setText(nodeClass != null ? nodeClass : "");

            valTotal.setText(formatNm(total));
            valFree.setText(formatNm(free));

            if (nodeClass.equals("chaperone")) {
                valHsp70.setText("-");
                valHsp90.setText("-");
            } else {
                double  hsp70Bound = bound.get(1);
                double  hsp90Bound = bound.get(2);

                valHsp70.setText(formatNm(hsp70Bound));
                valHsp90.setText(formatNm(hsp90Bound));
            }

            identityCard.setVisible(true);
            dataPanel.setVisible(true);
            sliderPanel.setVisible(true);
            filtersPanel.setVisible(true);
            buttonPanel.setVisible(true);
            noSelectionMsg.setVisible(false);

            revalidate();
            repaint();
        });
    }

    /** Clear the panel when selection is empty or multi-node. */
    public void clearSelection() {
        SwingUtilities.invokeLater(() -> {
            identityCard.setVisible(false);
            dataPanel.setVisible(false);
            sliderPanel.setVisible(false);
            filtersPanel.setVisible(false);
            buttonPanel.setVisible(false);
            noSelectionMsg.setVisible(true);
            revalidate();
            repaint();
        });
    }

    // ── CytoPanelComponent ────────────────────────────────────────────────────

    @Override public Component      getComponent()       { return this; }
    @Override public CytoPanelName  getCytoPanelName()   { return CytoPanelName.EAST; }
    @Override public String         getTitle()           { return PANEL_TITLE; }
    @Override public Icon           getIcon()            { return null; }

    // ── UI helpers ────────────────────────────────────────────────────────────

    /** Dark card container with a subtle border. */
    private JPanel makeCard() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(42, 74, 127), 1),
                new EmptyBorder(12, 14, 12, 14)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        return card;
    }

    /**
     * A stacked metric block:
     *
     *   ● LABEL NAME
     *   123.45 nM
     *
     * The label is small and muted; the value is large, bold and coloured.
     */
    private JPanel makeMetricBlock(String labelText, JComponent valueLabel, Color accentColor) {
        JPanel block = new JPanel();
        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
        block.setBackground(BG_CARD);
        block.setAlignmentX(Component.LEFT_ALIGNMENT);
        block.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));

        // Top: dot + label
        JPanel topRow = new JPanel(new BorderLayout(4, 0));
        topRow.setBackground(BG_CARD);
        topRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        topRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));

        JLabel dot = new JLabel("●");
        dot.setForeground(accentColor);
        dot.setFont(new Font("SansSerif", Font.PLAIN, 8));

        JLabel lbl = new JLabel("  " + labelText.toUpperCase());
        lbl.setForeground(FG_MUTED);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 10));

        topRow.add(dot, BorderLayout.WEST);
        topRow.add(lbl, BorderLayout.CENTER);

        // Bottom: the big value
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        block.add(topRow);
        block.add(Box.createVerticalStrut(3));
        block.add(valueLabel);
        return block;
    }

    /** Returns a pre-styled large value label, ready to receive text. */
    private JLabel makeBigValueLabel(Color color) {
        JLabel lbl = new JLabel("—");
        lbl.setForeground(color);
        lbl.setFont(new Font("Monospaced", Font.BOLD, 16));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }

    /** Returns a pre-styled large value text field, ready to receive text. */
    private JTextField makeEditableBigValueLabel(Color color, Class<? extends CyIdentifiable> clzz, String column) {
        JTextField tf = new JTextField("—");
        tf.setForeground(color);
        tf.setFont(new Font("Monospaced", Font.BOLD, 16));
        tf.setAlignmentX(Component.LEFT_ALIGNMENT);
        tf.addActionListener(e -> {
            String txt = tf.getText();
            String[] parts = txt.split(" "); // To remove units
            Double value = Double.parseDouble(parts[0]);
            if (parts.length >=2 && !parts[1].equals("nM")) {
                // Assume uM
                value *= 1000;
            }
            if (clzz.equals(CyNode.class)) {
                network.getRow(node).set(Utils.mkCol(column), value);
            } else {
            }
        });
        return tf;
    }

    /** A thin horizontal rule used between metric blocks. */
    private JPanel makeSeparator() {
        JPanel sep = new JPanel();
        sep.setBackground(new Color(42, 74, 127));
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        return sep;
    }

    /** Format a concentration value: µM above 1000 nM, else nM to 2 d.p. */
    private String formatNm(Double value) {
        if (value == null) return "—";
        if (value >= 1000.0)
            return String.format("%.2f \u00b5M", value / 1000.0);
        return String.format("%.2f nM", value);
    }
}
