/**
 * Copyright (C) 2014 OpenTravel Alliance (info@opentravel.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opentravel.schemas.actions;

import org.opentravel.schemas.node.ComponentNode;
import org.opentravel.schemas.node.Node;
import org.opentravel.schemas.properties.StringProperties;
import org.opentravel.schemas.stl2developer.MainWindow;

/**
 * Implements the "Copy" menu action.
 * 
 * @author Agnieszka Janowska
 * 
 */
public class CloneSelectedTreeNodesAction extends OtmAbstractAction {

    /**
	 *
	 */
    public CloneSelectedTreeNodesAction(final MainWindow mainWindow, final StringProperties props) {
        super(mainWindow, props);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.jface.action.Action#run()
     */
    @Override
    public void run() {
        getMainController().copySelectedNodes();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.opentravel.schemas.actions.IWithNodeAction.AbstractWithNodeAction#isEnabled(org.opentravel.schemas
     * .node.Node)
     */
    @Override
    public boolean isEnabled() {
        Node currentNode = mc.getSelectedNode_NavigatorView();
        return currentNode instanceof ComponentNode;
    }

}