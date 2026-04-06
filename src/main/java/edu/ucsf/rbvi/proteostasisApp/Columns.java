/* vim: set ts=4 sw=4 et: */
package edu.ucsf.rbvi.proteostasisApp;

public class Columns {

    // Column names — nodes
    public static final String COL_NODE_CLASS    = "node_class";
    public static final String COL_DISPLAY_NAME  = "display_name";
    public static final String COL_LABEL         = "label";
    public static final String COL_FAMILY        = "family";
    public static final String COL_GENE_SYMBOL   = "gene_symbol";
    public static final String COL_UNIPROT_ID    = "uniprot_id";
    public static final String COL_REP_UNIPROT   = "representative_uniprot_id";
    public static final String COL_TOTAL_NM      = "total_nM";
    public static final String COL_BOUND         = "bound"; // Also an edge attribute
    public static final String COL_FREE          = "free";
    public static final String COL_HAS_TOTAL     = "has_total";
    public static final String COL_PROTEIN_CLASS = "protein_class";
    public static final String COL_CLUSTER_ID    = "cluster_id";
    public static final String COL_CLUSTER_LABEL = "cluster_label";
    public static final String COL_PIECHART      = "PieChart";
    public static final String COL_X             = "X";
    public static final String COL_Y             = "Y";
    public static final String COL_TOOLTIP       = "tooltip";

    // This may or may not already be there
    public static final String COL_SOURCE        = "Source_name";
    public static final String COL_TARGET        = "Target_name";

    // Column names — edges
    public static final String COL_EDGE_CLASS    = "edge_class";

    // NEW: solver-compatible single-KD field
    public static final String COL_KD_NM         = "kd_nM";

    public static final String COL_KD_U_NM       = "kd_u_nM";
    public static final String COL_KD_P_NM       = "kd_p_nM";
    public static final String COL_HAS_KD        = "has_kd";
    public static final String COL_FRAC_BOUND    = "frac_bound";
}