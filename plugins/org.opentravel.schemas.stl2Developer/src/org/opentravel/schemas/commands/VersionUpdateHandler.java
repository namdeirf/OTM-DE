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
package org.opentravel.schemas.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.opentravel.schemacompiler.repository.RepositoryException;
import org.opentravel.schemas.controllers.DefaultRepositoryController;
import org.opentravel.schemas.node.LibraryNode;
import org.opentravel.schemas.node.Node;
import org.opentravel.schemas.node.ProjectNode;
import org.opentravel.schemas.stl2developer.DialogUserNotifier;
import org.opentravel.schemas.types.TypeUserNode;

/**
 * Update type assignments to later versions.
 * 
 * @author Dave Hollander
 * 
 */

public class VersionUpdateHandler extends OtmAbstractHandler {

	public static String COMMAND_ID = "org.opentravel.schemas.commands.VersionUpdate";

	@Override
	public Object execute(ExecutionEvent exEvent) throws ExecutionException {
		ProjectNode project = null;
		Node node = mc.getSelectedNode_NavigatorView();
		if (node == null)
			return null;
		if (node instanceof TypeUserNode)
			updateLibrary((TypeUserNode) node);
		return null;
	}

	@Override
	public String getID() {
		return COMMAND_ID;
	}

	@Override
	public boolean isEnabled() {
		Node n = mc.getSelectedNode_NavigatorView();
		return n != null && n.isEditable() ? n instanceof TypeUserNode : false;
	}

	private void updateLibrary(TypeUserNode userNode) {
		DefaultRepositoryController rc = (DefaultRepositoryController) mc.getRepositoryController();
		List<LibraryNode> usedLibs = new ArrayList<LibraryNode>();
		usedLibs.add(userNode.getOwner());
		LibraryNode libToUpdate = (LibraryNode) userNode.getParent();

		// Create replacement map
		HashMap<LibraryNode, LibraryNode> replacementMap;
		try {
			replacementMap = rc.getVersionUpdateMap(usedLibs);
		} catch (RepositoryException e1) {
			DialogUserNotifier.openWarning("Version Update Warning", e1.getLocalizedMessage());
			return;
		}

		LibraryNode targetLib = null;
		LibraryNode oldLib = null;
		for (Entry<LibraryNode, LibraryNode> e : replacementMap.entrySet()) {
			targetLib = e.getValue();
			oldLib = e.getKey();
		}
		if (targetLib == null) {
			DialogUserNotifier.openWarning("Update to Latest Version", "Did not find a later version.");
			return;
		}

		String question = "Do you want to update " + oldLib.getNameWithPrefix() + " with "
				+ targetLib.getNameWithPrefix() + "?";
		if (DialogUserNotifier.openQuestion("Update to Latest Version", question))
			// replace type users using the replacement map
			libToUpdate.replaceTypeUsers(replacementMap);

		// How to clear the TypeUserNode?
		libToUpdate.getWhereUsedHandler().refreshUsedByNode();
		mc.refresh(libToUpdate);
	}
}