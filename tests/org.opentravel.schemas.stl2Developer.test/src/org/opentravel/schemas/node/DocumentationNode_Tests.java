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
/**
 * 
 */
package org.opentravel.schemas.node;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.opentravel.schemacompiler.model.TLCoreObject;
import org.opentravel.schemas.controllers.LibraryController;
import org.opentravel.schemas.controllers.MainController;
import org.opentravel.schemas.controllers.ProjectController;
import org.opentravel.schemas.node.libraries.LibraryNode;
import org.opentravel.schemas.testUtils.LoadFiles;
import org.opentravel.schemas.testUtils.MockLibrary;
import org.opentravel.schemas.testUtils.NodeTesters;

/**
 * These tests cover the Node and ModelObject class functions. NOTE: these functions are NOT used in the
 * documentationView. // TODO - use documentation controller/class not node directly. OBSOLETE
 * 
 * See controllers/DocumentationNodeModelManager_Tests
 * 
 * @author Dave Hollander
 * 
 */
public class DocumentationNode_Tests {
	ModelNode model = null;
	NodeTesters nt = new NodeTesters();

	@Test
	public void coreDescriptionTest() {
		MainController mc = new MainController();
		MockLibrary mockLibUtil = new MockLibrary();
		LibraryController lc = mc.getLibraryController();
		ProjectController pc = mc.getProjectController();
		ProjectNode defaultProject = pc.getDefaultProject();
		final String testDescription = "Test Description";

		LibraryNode lib = mockLibUtil.createNewLibrary(defaultProject.getNamespace() + "/Test", getClass()
				.getSimpleName(), defaultProject);
		Assert.assertNotNull(lib);
		String path = lib.getPath();
		String name = lib.getName();
		String desc = "";
		String d2 = "";

		CoreObjectNode core = mockLibUtil.addCoreObjectToLibrary(lib, "Core1");
		core.addDescription(testDescription);
		Assert.assertFalse(core.getDescription().isEmpty());
		lc.saveLibrary(lib, false);
		// lc.closeLibrary(lib);
		// TODO - find out why the saved value is "null Test Description"

		List<File> files = new ArrayList<>();
		files.add(new File(path));
		defaultProject.add(files); // FIXME
		for (LibraryNode ln : defaultProject.getLibraries()) {
			if (ln.getName().equals(name)) {
				for (Node n : ln.getDescendentsNamedTypes()) {
					if (n.getName().equals("Core1")) {
						desc = n.getDescription();
						TLCoreObject tlc = (TLCoreObject) n.getTLModelObject();
						d2 = tlc.getDocumentation().getDescription();
						Assert.assertNotNull(d2);
						Assert.assertFalse(desc.isEmpty());
					}
				}
			}
		}
		testLoadFromFile();
	}

	@Test
	public void testLoadFromFile() {
		MainController mc = new MainController();
		LoadFiles lf = new LoadFiles();
		// model = mc.getModelNode();

		// File 1 has documentation in it.
		LibraryNode lib1 = lf.loadFile1(mc);

		// Payment has description
		Node n = lib1.findNodeByName("Payment");
		assertTrue(!n.getDescription().isEmpty());

		// PaymentCard_MagneticStrip has description, implementor and more info
		n = lib1.findNodeByName("PaymentCard_MagneticStrip");
		assertTrue(!n.getDescription().isEmpty());
		assertTrue(!n.getDocumentation().getImplementers().isEmpty());
		assertTrue(!n.getDocumentation().getMoreInfos().isEmpty());

		// TODO - move to eq/ex junit
		// Date_MMYY has both ex and eq
		n = lib1.findNodeByName("Date_MMYY");
		assertTrue(!((SimpleTypeNode) n).getExample(null).isEmpty());
		assertTrue(!((SimpleTypeNode) n).getEquivalent(null).isEmpty());

		// Age has both equivalents and examples
		n = lib1.findNodeByName("Age");
		assertTrue(!((VWA_Node) n).getExample(null).isEmpty());
		assertTrue(!((VWA_Node) n).getEquivalent(null).isEmpty());
	}

	@Test
	public void docTests() throws Exception {
		MainController thisModel = new MainController();
		LoadFiles lf = new LoadFiles();
		model = thisModel.getModelNode();

		lf.loadTestGroupA(thisModel);
		for (LibraryNode ln : Node.getAllLibraries()) {
			nt.visitAllNodes(ln);
			getDocs(ln);
		}
		// NodeModelTestUtils.testNodeModel();
	}

	private void getDocs(LibraryNode ln) {
		// //for (Node n : ln.getChildren_NamedTypes()) {
		for (Node n : ln.getDescendants()) {
			if (!n.isDocumentationOwner())
				continue;

			final String doc = "ABCdef124 now is the time for all good...you know";
			String s;
			int index = 0;

			// TODO - use documentation controller/class not node directly.
			assertTrue(n.getDescription() != null);
			assertTrue(n.getDevelopers() != null);
			// Assert.assertNotNull(n.getMoreInfo(index));
			// Assert.assertNotNull(n.getDeprecated(index));
			// Assert.assertNotNull(n.getReferenceLink(index));

			n.setDescription(doc);
			n.setDevelopers(doc, index);
			n.setMoreInfo(doc, index);
			// n.setDeprecated(doc, index);
			n.setReferenceLink(doc, index);

			assertEquals(doc, n.getDescription());
			// Assert.assertEquals(doc, n.getDevelopers(index));
			// Assert.assertEquals(doc, n.getMoreInfo(index));
			// Assert.assertEquals(doc, n.getDeprecated(index));
			// Assert.assertEquals(doc, n.getReferenceLink(index));
		}

	}
}
