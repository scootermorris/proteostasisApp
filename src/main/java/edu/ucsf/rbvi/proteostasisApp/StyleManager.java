/* vim: set ts=4 sw=4 et: */
package edu.ucsf.rbvi.proteostasisApp;

import org.cytoscape.model.CyNode;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.View;
import org.cytoscape.view.model.VisualLexicon;
import org.cytoscape.view.model.VisualProperty;

import org.cytoscape.view.presentation.RenderingEngineManager;
import org.cytoscape.view.presentation.property.ArrowShapeVisualProperty;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
import org.cytoscape.view.presentation.property.LabelBackgroundShapeVisualProperty;
import org.cytoscape.view.presentation.property.LineTypeVisualProperty;
import org.cytoscape.view.presentation.property.NodeShapeVisualProperty;
import org.cytoscape.view.presentation.property.values.Justification;
import org.cytoscape.view.presentation.property.values.LineType;
import org.cytoscape.view.presentation.property.values.NodeShape;
import org.cytoscape.view.presentation.property.values.ObjectPosition;
import org.cytoscape.view.presentation.property.values.Position;
import org.cytoscape.view.vizmap.*;
import org.cytoscape.view.vizmap.mappings.BoundaryRangeValues;
import org.cytoscape.view.vizmap.mappings.ContinuousMapping;
import org.cytoscape.view.vizmap.mappings.DiscreteMapping;
import org.cytoscape.view.vizmap.mappings.PassthroughMapping;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.TaskMonitor;

import edu.ucsf.rbvi.proteostasisApp.utils.Utils;

import javax.swing.*;
import java.awt.Color;
import java.awt.Paint;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class StyleManager {

    private final CyServiceRegistrar           registrar;
    private final VisualStyleFactory           vsf;
    private final VisualMappingManager         vmm;
    private final VisualMappingFunctionFactory continuousFactory;
    private final VisualMappingFunctionFactory discreteFactory;
    private final VisualMappingFunctionFactory passthroughFactory;
    private final VisualLexicon                lex;

    public StyleManager(CyServiceRegistrar registrar) {
        this.registrar = registrar;

        // Visual mapping services
        vmm                    = registrar.getService(VisualMappingManager.class);
        vsf                    = registrar.getService(VisualStyleFactory.class);
        continuousFactory      = registrar.getService(VisualMappingFunctionFactory.class, "(mapping.type=continuous)");
        discreteFactory        = registrar.getService(VisualMappingFunctionFactory.class, "(mapping.type=discrete)");
        passthroughFactory     = registrar.getService(VisualMappingFunctionFactory.class, "(mapping.type=passthrough)");
        lex                    = registrar.getService(RenderingEngineManager.class).getDefaultVisualLexicon();
    }


    // ─── Visual style ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public void applyStyle(CyNetworkView view) {
        VisualStyle style = registrar.getService(VisualStyleFactory.class).createVisualStyle("Proteostasis");

        // Default network appearance
        style.setDefaultValue(BasicVisualLexicon.NETWORK_BACKGROUND_PAINT,               new Color(230,230,230));

        // Default node appearance
        style.setDefaultValue(BasicVisualLexicon.NODE_FILL_COLOR,      Color.GRAY);
        style.setDefaultValue(BasicVisualLexicon.NODE_BORDER_PAINT,    Color.BLACK);
        style.setDefaultValue(BasicVisualLexicon.NODE_BORDER_WIDTH,    1.0);
        style.setDefaultValue(BasicVisualLexicon.NODE_SIZE,            50.0);
        style.setDefaultValue(BasicVisualLexicon.NODE_LABEL_COLOR,     Color.BLACK);
        style.setDefaultValue(BasicVisualLexicon.NODE_LABEL_FONT_SIZE, 10);
        style.setDefaultValue(BasicVisualLexicon.NODE_LABEL_BACKGROUND_COLOR,            new Color(200, 212, 232));
        style.setDefaultValue(BasicVisualLexicon.NODE_LABEL_BACKGROUND_TRANSPARENCY,     120);
        style.setDefaultValue(BasicVisualLexicon.NODE_LABEL_BACKGROUND_SHAPE,            LabelBackgroundShapeVisualProperty.ROUND_RECTANGLE);

        // Passthrough mapping for node label
        PassthroughMapping<String, String> labelMapping = (PassthroughMapping<String, String>) passthroughFactory
          .createVisualMappingFunction(Utils.mkCol(Columns.COL_DISPLAY_NAME), String.class,
              BasicVisualLexicon.NODE_LABEL);
        style.addVisualMappingFunction(labelMapping);

        // Discrete mapping for node label background
        DiscreteMapping<String, Paint> backgroundColorMap =
                (DiscreteMapping<String, Paint>) discreteFactory.createVisualMappingFunction(
                        Utils.mkCol(Columns.COL_NODE_CLASS), String.class, BasicVisualLexicon.NODE_LABEL_BACKGROUND_COLOR);
        backgroundColorMap.putMapValue("chaperone", new Color(222,222,222));
        backgroundColorMap.putMapValue("cc_tpr",    new Color(222,222,222));
        backgroundColorMap.putMapValue("cluster",   Color.WHITE);
        style.addVisualMappingFunction(backgroundColorMap);

        DiscreteMapping<String, Integer> backgroundTransparencyMap =
                (DiscreteMapping<String, Integer>) discreteFactory.createVisualMappingFunction(
                        Utils.mkCol(Columns.COL_NODE_CLASS), String.class, BasicVisualLexicon.NODE_LABEL_BACKGROUND_TRANSPARENCY);
        backgroundTransparencyMap.putMapValue("chaperone", 175);
        backgroundTransparencyMap.putMapValue("cc_tpr",    175);
        backgroundTransparencyMap.putMapValue("cluster",   255);
        style.addVisualMappingFunction(backgroundTransparencyMap);

        // Fill colour — discrete mapping on node_class
        DiscreteMapping<String, Paint> fillMap =
                (DiscreteMapping<String, Paint>) discreteFactory.createVisualMappingFunction(
                        Utils.mkCol(Columns.COL_NODE_CLASS), String.class, BasicVisualLexicon.NODE_FILL_COLOR);
        fillMap.putMapValue("chaperone", new Color(60, 20,  10));
        fillMap.putMapValue("cc_tpr",    new Color(10, 40,  55));
        fillMap.putMapValue("cluster",   Color.WHITE);
        style.addVisualMappingFunction(fillMap);

        // Border colour — discrete mapping on node_class
        DiscreteMapping<String, Paint> borderMap =
                (DiscreteMapping<String, Paint>) discreteFactory.createVisualMappingFunction(
                        Utils.mkCol(Columns.COL_NODE_CLASS), String.class, BasicVisualLexicon.NODE_BORDER_PAINT);
        borderMap.putMapValue("chaperone", new Color(249, 115,  22));
        borderMap.putMapValue("cc_tpr",    new Color( 34, 211, 238));
        borderMap.putMapValue("cluster",   new Color(125, 125, 125));
        style.addVisualMappingFunction(borderMap);

        // Size — discrete mapping on node_class
        DiscreteMapping<String, Double> sizeMap =
                (DiscreteMapping<String, Double>) discreteFactory.createVisualMappingFunction(
                        Utils.mkCol(Columns.COL_NODE_CLASS), String.class, BasicVisualLexicon.NODE_SIZE);
        sizeMap.putMapValue("chaperone", 80.0);
        sizeMap.putMapValue("cc_tpr",    48.0);
        style.addVisualMappingFunction(sizeMap);

        // Passthrough mapping for node tooltop
        PassthroughMapping<String, String> nTooltipMapping = (PassthroughMapping<String, String>) passthroughFactory
          .createVisualMappingFunction(Utils.mkCol(Columns.COL_TOOLTIP), String.class, BasicVisualLexicon.NODE_TOOLTIP);
        style.addVisualMappingFunction(nTooltipMapping);

        // Passthrough mapping for node pie charts
        VisualProperty customGraphics = lex.lookup(CyNode.class, "NODE_CUSTOMGRAPHICS_1");
        PassthroughMapping<String, String> chartMapping = (PassthroughMapping<String, String>) passthroughFactory
          .createVisualMappingFunction(Utils.mkCol(Columns.COL_PIECHART), String.class, customGraphics);
        style.addVisualMappingFunction(chartMapping);

        // Passthrough mapping for node location
        PassthroughMapping<String, Double> xMapping = (PassthroughMapping<String, Double>) passthroughFactory
          .createVisualMappingFunction(Utils.mkCol(Columns.COL_X), String.class, BasicVisualLexicon.NODE_X_LOCATION);
        style.addVisualMappingFunction(xMapping);

        PassthroughMapping<String, Double> yMapping = (PassthroughMapping<String, Double>) passthroughFactory
          .createVisualMappingFunction(Utils.mkCol(Columns.COL_Y), String.class, BasicVisualLexicon.NODE_Y_LOCATION);
        style.addVisualMappingFunction(yMapping);

        // Shape — discrete mapping on node_class
        DiscreteMapping<String, NodeShape> shapeMap =
                (DiscreteMapping<String, NodeShape>) discreteFactory.createVisualMappingFunction(
                        Utils.mkCol(Columns.COL_NODE_CLASS), String.class, BasicVisualLexicon.NODE_SHAPE);
        shapeMap.putMapValue("chaperone", NodeShapeVisualProperty.ELLIPSE);
        shapeMap.putMapValue("cc_tpr",    NodeShapeVisualProperty.ELLIPSE);
        shapeMap.putMapValue("cluster",   NodeShapeVisualProperty.ROUND_RECTANGLE);
        style.addVisualMappingFunction(shapeMap);

        // Default edge appearance
        style.setDefaultValue(BasicVisualLexicon.EDGE_STROKE_UNSELECTED_PAINT, new Color(30, 58, 110));
        style.setDefaultValue(BasicVisualLexicon.EDGE_WIDTH,                   1.5);
        style.setDefaultValue(BasicVisualLexicon.EDGE_TARGET_ARROW_SHAPE,      ArrowShapeVisualProperty.NONE);
        style.setDefaultValue(BasicVisualLexicon.EDGE_TRANSPARENCY,            175);

        // Edge color -- discrete mapping on edge_class
        DiscreteMapping<String, Paint> edgeColorMap =
                (DiscreteMapping<String, Paint>) discreteFactory.createVisualMappingFunction(
                        Utils.mkCol(Columns.COL_TARGET), String.class, BasicVisualLexicon.EDGE_STROKE_UNSELECTED_PAINT);
        edgeColorMap.putMapValue("HSP70", new Color(21,94,253));
        edgeColorMap.putMapValue("HSP90", new Color(253,54,54));
        style.addVisualMappingFunction(edgeColorMap);

        // Edge width -- continuous mapping on frac_bound
        ContinuousMapping<Double, Double> edgeWidthMap =
            (ContinuousMapping<Double, Double>) continuousFactory.createVisualMappingFunction(
                    Utils.mkCol(Columns.COL_FRAC_BOUND), Double.class, BasicVisualLexicon.EDGE_WIDTH);
        edgeWidthMap.addPoint(0.0, new BoundaryRangeValues<Double>(1.0,1.0,1.0));
        edgeWidthMap.addPoint(1.0, new BoundaryRangeValues<Double>(10.0,10.0,10.0));
        style.addVisualMappingFunction(edgeWidthMap);

        // Edge style == discrete mapping for has_kd
        DiscreteMapping<Boolean, LineType> edgeTypeMap =
                (DiscreteMapping<Boolean, LineType>) discreteFactory.createVisualMappingFunction(
                        Utils.mkCol(Columns.COL_HAS_KD), Boolean.class, BasicVisualLexicon.EDGE_LINE_TYPE);
        edgeTypeMap.putMapValue(Boolean.TRUE, LineTypeVisualProperty.SOLID);
        edgeTypeMap.putMapValue(Boolean.FALSE, LineTypeVisualProperty.DOT);
        style.addVisualMappingFunction(edgeTypeMap);

        // Passthrough mapping for edge tooltop
        PassthroughMapping<String, String> eTooltipMapping = (PassthroughMapping<String, String>) passthroughFactory
          .createVisualMappingFunction(Utils.mkCol(Columns.COL_TOOLTIP), String.class, BasicVisualLexicon.EDGE_TOOLTIP);
        style.addVisualMappingFunction(eTooltipMapping);

        vmm.addVisualStyle(style);
        vmm.setVisualStyle(style, view);
        style.apply(view);
    }

    public void applyClusterStyle(CyNetworkView view, CyNode clusterNode) {
        View<CyNode> nodeView = view.getNodeView(clusterNode);
        nodeView.setLockedValue(BasicVisualLexicon.NODE_LABEL_COLOR,     Color.BLACK);

        ObjectPosition clusterLabelPosition = new ObjectPosition(Position.NORTH, Position.SOUTH, Justification.JUSTIFY_CENTER, 0, 0);
        nodeView.setLockedValue(BasicVisualLexicon.NODE_LABEL_POSITION,  clusterLabelPosition);
    }

}
