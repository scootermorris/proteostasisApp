/* vim: set ts=4 sw=4 et: */
package edu.ucsf.rbvi.proteostasisApp.tasks;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.work.AbstractTaskFactory;
import org.cytoscape.work.TaskIterator;

import java.util.List;

/**
 * Registered as an OSGi TaskFactory service.
 * Adds Apps > Proteostasis > Add Node to the Cytoscape menu.
 *
 * The menu item is enabled only when exactly one node is currently selected,
 * since the new node will be connected to that node.
 */
public class AddNodeTaskFactory extends AbstractTaskFactory {

    private final CyServiceRegistrar registrar;

    public AddNodeTaskFactory(CyServiceRegistrar registrar) {
        this.registrar = registrar;
    }

    @Override
    public TaskIterator createTaskIterator() {
        return new TaskIterator(new AddNodeTask(registrar));
    }

    /**
     * Enable only when exactly one node is selected in the current network.
     */
    @Override
    public boolean isReady() {
        CyNetwork network = registrar.getService(CyApplicationManager.class).getCurrentNetwork();
        if (network == null) return false;

        List<CyNode> selected = network.getNodeList();
        selected.removeIf(n -> !Boolean.TRUE.equals(
                network.getRow(n).get(CyNetwork.SELECTED, Boolean.class)));

        return selected.size() == 1;
    }
}
