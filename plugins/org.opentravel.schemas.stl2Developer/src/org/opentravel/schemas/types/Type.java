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
package org.opentravel.schemas.types;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import org.opentravel.schemacompiler.model.AbstractLibrary;
import org.opentravel.schemacompiler.model.NamedEntity;
import org.opentravel.schemas.modelObject.XsdModelingUtils;
import org.opentravel.schemas.node.AliasNode;
import org.opentravel.schemas.node.INode;
import org.opentravel.schemas.node.ImpliedNode;
import org.opentravel.schemas.node.ImpliedNodeType;
import org.opentravel.schemas.node.LibraryNode;
import org.opentravel.schemas.node.ModelNode;
import org.opentravel.schemas.node.Node;
import org.opentravel.schemas.node.NodeFinders;
import org.opentravel.schemas.node.NodeNameUtils;
import org.opentravel.schemas.node.NodeVisitors;
import org.opentravel.schemas.node.NodeVisitors.FixNames;
import org.opentravel.schemas.node.properties.IndicatorElementNode;
import org.opentravel.schemas.node.properties.IndicatorNode;
import org.opentravel.schemas.stl2developer.OtmRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages type assignment and usage.
 * 
 * Note: all type users are treated the same, base, element and type references. To know if it is a base type
 * assignment, see if it is a simpleType, simpleFacet or extension.
 * 
 * @author Dave Hollander
 * 
 */
public class Type {
	private static final Logger LOGGER = LoggerFactory.getLogger(Type.class);

	protected Node typeOwner = null; // the node to which types are assigned
	protected Node typeNode = null; // node used as the type

	// nodes that use this node as a type definition. Includes base type users.
	protected ArrayList<Node> typeUsers = new ArrayList<Node>();

	// nodes that use this node as a base type for extension. Included in typeUsers.
	protected ArrayList<Node> baseUsers = new ArrayList<Node>();

	protected TypeNode treeNode = null;

	public Type(Node n) {
		typeOwner = n;
		treeNode = new TypeNode(n);
	}

	/**
	 * @return the typeNode used in type assignments
	 */
	public Node getTypeNode() {
		return typeNode;
	}

	/**
	 * @return the typeTreeNode - used in views
	 */
	public Node getTypeTreeNode() {
		return treeNode;
	}

	/**
	 * @return the typeOwner
	 */
	public Node getTypeOwner() {
		return typeOwner;
	}

	/**
	 * @return the live list of editable nodes where this node is assigned as a type.
	 */
	public ArrayList<Node> getTypeUsers() {
		if (typeUsers == null) {
			typeUsers = setTypeUsers();
			LOGGER.debug("Set type users durning a getUsers for " + getTypeOwner());
			throw new IllegalStateException("Set type users durning a getUsers for " + getTypeOwner());
		}
		return typeUsers;
	}

	public int getTypeUsersCount() {
		synchronized (this) {
			if (typeUsers == null) {
				typeUsers = setTypeUsers();
				LOGGER.debug("Set type users durning a getUsers for " + getTypeOwner());
				throw new IllegalStateException("Set type users durning a getUsers for " + getTypeOwner());
			}
		}
		return typeUsers.size();
	}

	public ArrayList<Node> getBaseUsers() {
		return baseUsers;
	}

	/**
	 * @return a new list of editable nodes where any member of the owning component (aliases, facets) are is assigned
	 *         as a type or base type.
	 */
	public List<Node> getComponentUsers() {
		if (typeUsers == null) {
			typeUsers = setTypeUsers();
			// LOGGER.debug("Set type users durning a getUsers for " + getTypeOwner());
		}
		List<Node> users = new ArrayList<Node>(getTypeUsers());
		for (Node n : typeOwner.getOwningComponent().getChildren()) {
			if (n.isTypeProvider())
				users.addAll(n.getTypeClass().getTypeUsers());
		}
		return users;
	}

	public int getComponentUsersCount() {
		return getComponentUsers().size();
	}

	/**
	 * Get where the type owner of this type object is used.
	 * 
	 * @param scope
	 * @param editableOnly
	 * @param includeExtensionBase
	 * @return
	 */
	protected List<Node> getWhereUsed(final Node scope, boolean editableOnly, boolean includeExtensionBase) {
		Node target = this.getTypeOwner();
		final List<Node> assigned = new ArrayList<Node>();
		// LOGGER.debug("where used: source = "+target.getName() +
		// " scope = "+scope.getName());

		if (target != null) {
			final Object sourceTLType = target.getTLModelObject();
			if (sourceTLType != null && scope.isInTLLibrary()) {
				if (sourceTLType.equals(scope.getModelObject().getTLType())) {
					if ((editableOnly && scope.isEditable()) || !editableOnly)
						assigned.add(scope);
				}
				if (includeExtensionBase && target.getTLModelObject() instanceof NamedEntity) {
					if (scope.getModelObject().isExtendedBy((NamedEntity) target.getTLModelObject())) {
						if (editableOnly && scope.isEditable())
							assigned.add(scope);
						else if (!editableOnly)
							assigned.add(scope);
					}
				}
			}
			if (scope == Node.getModelNode() || scope.isInTLLibrary())
				for (final Node n : scope.getChildren()) {
					assigned.addAll(getWhereUsed(n, editableOnly, includeExtensionBase));
				}
		}
		// if (assigned.size() > 0)
		// LOGGER.debug("  returning: "+assigned.size()+" nodes where "+target.getName()+" was used as type.");
		return assigned;
	}

	/**
	 * Replace this provider with unassignedNode for all users of this provider as a type. Also replaces type usage of
	 * descendants of this typeOwner node. Also does the TL properties.
	 */
	public void replaceTypeProvider() {
		replaceTypeProvider(ModelNode.getUnassignedNode(), null);
	}

	/**
	 * Replace this provider with replacement for all users of this provider as a type. Also replaces type usage of
	 * descendants of this typeOwner node. Also does the TL properties. Note - user counts may change when business
	 * replace core objects because core is also a valid simple type.
	 * 
	 * It is OK for the owner to not be in a library. This happens when it is being replaced.
	 * 
	 * @param replacement
	 *            is the TypeProvider to use instead. Must be a typeProvider. skips assignment if replacement is not
	 *            compatible with user.
	 * @param libraryScope
	 *            - if null to entire model, otherwise only replace users within specified library.
	 */
	public void replaceTypeProvider(Node replacement, LibraryNode scopeLibrary) {

		// Nothing to do.
		if (getTypeUsers().isEmpty())
			return;
		// LOGGER.debug("Replacing " + typeOwner + " used " + getTypeUsers().size() + " times.");

		if (replacement == null || !replacement.isTypeProvider())
			return;

		// Replace the users of this object as a type.
		replaceUsers(replacement, scopeLibrary);

		// Replace users of all descendants.
		// Create map of replacement candidates
		HashMap<String, Node> replacementTypes = new HashMap<String, Node>();
		for (Node r : replacement.getDescendants_NamedTypes())
			replacementTypes.put(r.getName(), r);

		for (Node n : typeOwner.getDescendants_NamedTypes()) {
			// Try to find a replacement equivalent from replacement object
			Node r = replacementTypes.get(n.getName());
			if (r == null)
				r = ModelNode.getUnassignedNode();
			n.getTypeClass().replaceUsers(r, scopeLibrary);
		}
	}

	// Replace type assignments to all type and base user nodes IFF in scope and editable.
	private void replaceUsers(Node replacement, LibraryNode scope) {
		FixNames nameFixer = new NodeVisitors().new FixNames();
		if (!(replacement instanceof ImpliedNode) && replacement.getLibrary() == null) {
			LOGGER.warn("Error - replacement " + replacement + " is not in a library.");
			replacement = ModelNode.getUnassignedNode();
		}
		// base users
		ArrayList<Node> users = new ArrayList<Node>(getBaseUsers());
		for (Node n : users) {
			if (n.isEditable() && (scope == null || n.getLibrary().equals(scope))) {
				n.getTypeClass().setBaseType(replacement);
				baseUsers.remove(n);
			}
		}

		users = new ArrayList<Node>(getTypeUsers());
		for (Node n : users) {
			if (n.isEditable() && (scope == null || n.getLibrary().equals(scope))) {
				if (n.getTypeClass().setAssignedType(replacement, false)) {
					nameFixer.visit(n);
					getTypeUsers().remove(n);
				} else {
					// setAssignedType if replacement failed, try unassigned.
					if (n.getTypeClass().setAssignedType(ModelNode.getUnassignedNode(), false)) {
						getTypeUsers().remove(n);
						// LOGGER.debug("Set type to " + ModelNode.getUnassignedNode() + " for " +
						// n);
					} else {
						LOGGER.warn("ERROR Setting type to " + replacement + " failed for " + n);
					}
				}
			}
		}
	}

	/**
	 * Set the type node and add this owner to that user list. Remove typeOwner from typeNode-typeUsers. Restricted use
	 * - Does NOT set the type the TLModel type {@link setAssignedType}
	 */
	public boolean setTypeNode(Node target) {
		if (target != null) {
			if (target.typeUsers() == null) {
				LOGGER.error("Null users on target.");
			}
			if (!target.typeUsers().contains(this))
				target.typeUsers().add(this.typeOwner);
		}
		// 6/25/2013 - dmh
		if (typeNode != null && typeNode.typeUsers() != null)
			typeNode.typeUsers().remove(typeOwner); // break existing link
		typeNode = target;
		return true;
	}

	/**
	 * Set assigned type. Sets type or base type as appropriate for the node. Extends setAssignedType() by using the
	 * passed source node to get the Qname of the node to find. To set the QName, the TLTypeObject is tried first if not
	 * null, otherwise the assigned xsd type in the documentation.
	 * 
	 * If the source node is not found, the Unassigned ImpliedNode is assigned.
	 * 
	 * Implied types (indicators, enumerations) are assigned to their appropriate implied Node.
	 * 
	 * @return true if successfully assigned.
	 */
	public boolean setAssignedTypeForThisNode() {
		return setAssignedTypeForThisNode(this.getTypeOwner(), null);
	}

	/**
	 * @param source
	 *            - the target and the node to use to find the type.
	 */
	public boolean setAssignedTypeForThisNode(Node source) {
		return setAssignedTypeForThisNode(source, null);
	}

	/**
	 * @param source
	 *            - the target <b>and</b> the node to use to find the type.
	 */
	public boolean setAssignedTypeForThisNode(Node source, Map<QName, Node> providerMap) {
		QName typeQname = null;
		LibraryNode typeLibrary = null;
		AbstractLibrary srcLib = null;

		if (!source.isTypeUser()) {
			LOGGER.warn("Tried to assign type to a node that is not a type user. " + source);
			return false;
		}
		if (source instanceof ImpliedNode) {
			LOGGER.warn("Tried to assign type to an implied node: " + source);
			return false;
		}

		// Get what we know about the assigned type.
		// TargetNode if the node has a default type or else QName
		Node target = source.getDefaultType();
		if (target == null) {
			if (source.getTLTypeObject() != null && source.getTLTypeObject().getOwningLibrary() == null) {
				// LOGGER.debug(source + " TL type cant be found because it is not in a library!");
				target = ModelNode.getUnassignedNode();
			}
			// try to get it from the property type or documentation
			typeQname = source.getTLTypeQName();
			if (typeQname == null)
				LOGGER.debug("Qname is null for " + source);
		}

		// If we don' have a map, get the library for the finder.
		if (providerMap == null) {
			srcLib = source.getModelObject().getOwningLibrary();
			if (srcLib != null) {
				for (LibraryNode ln : Node.getAllLibraries()) {
					if (ln.getTLaLib().equals(srcLib)) {
						typeLibrary = ln;
						break;
					}
				}
			}
		}

		// Find the node to assign
		if (target == null && typeQname != null && !typeQname.getLocalPart().isEmpty()) {
			if (providerMap == null) {
				// Try to find the exact node, regardless of duplicates.
				target = NodeFinders.findTypeProviderByQName(typeQname, typeLibrary);
				if (target == null)
					// if not in the type's library, then try all libraries
					target = NodeFinders.findTypeProviderByQName(typeQname);
			} else {
				// Use the provider map to find the type to assign.
				target = providerMap.get(typeQname);
				if (target == null)
					// Try the name without the "type" suffix
					target = providerMap.get(new QName(typeQname.getNamespaceURI(), NodeNameUtils
							.stripTypeSuffix(typeQname.getLocalPart())));
				if (target == null)
					// Try a chameleon with the local part name.
					target = providerMap.get(new QName(XsdModelingUtils.ChameleonNS, typeQname.getLocalPart()));
				if (target == null)
					// Finally try without the "type" suffix in chameleon ns
					target = providerMap.get(new QName(XsdModelingUtils.ChameleonNS, NodeNameUtils
							.stripTypeSuffix(typeQname.getLocalPart())));
			}
		}

		// If the target was not found, use the UnassignedNode
		boolean ret = true;
		if (target == null || !target.isTypeProvider()) {
			target = ModelNode.getUnassignedNode();
			ret = false;
			// LOGGER.warn("Missing assignment target for: " + source + " with qName = " +
			// typeQname);
		}

		if (source.getTLTypeObject() != null || target instanceof ImpliedNode) {
			// Set only the type class, not the underlying TL model.
			try {
				source.getTypeClass().setTypeNode(target);
			} catch (IllegalArgumentException e) {
				LOGGER.debug("Could not set TL Type " + e);
			}
			// LOGGER.debug("Type of " + source + " assigned " + target);
			verifyAssignment(providerMap, typeQname);
		} else {
			// Assign both type class and TL model to the found target
			source.getTypeClass().setAssignedType(target, false);
			// LOGGER.debug("Type and model of " + source + " assigned " + target);
		}
		return ret;
	}

	/**
	 * Set the assigned type to the unassigned node without refresh.
	 * 
	 * @return
	 */
	public boolean setAssignedType() {
		return setAssignedType(ModelNode.getUnassignedNode(), false);
	}

	/**
	 * Set Assigned Type. Sets the Assigned type node and add this owner to that user list. This method assures there is
	 * a target and that the owner is editable. Sets the type class properties as well as the TLModel type If the target
	 * represents a complex type (BO, Core, facet, alias) then the owner's name is changed.
	 * 
	 * @return true if assignment could be made, false otherwise
	 */
	public boolean setAssignedType(Node target) {
		return setAssignedType(target, false);
	}

	public boolean setAssignedType(Node target, boolean refresh) {
		// LOGGER.debug("setAssignedType begin, owner = "+typeOwner+" \t target = "+target);

		if (typeOwner instanceof IndicatorNode)
			target = ModelNode.getIndicatorNode();
		if (typeOwner instanceof IndicatorElementNode)
			target = ModelNode.getIndicatorNode();

		if (target == null)
			target = ModelNode.getUnassignedNode();

		if (!typeOwner.canAssign(target)) {
			// LOGGER.warn("Cannot assign " + target.getClass().getSimpleName() + ":" + target
			// + " to " + typeOwner.getClass().getSimpleName() + ":" + typeOwner);
			// Make no changes if we don't know what to assign.
			return false;
		}

		Node oldTypeNode = typeNode;
		NamedEntity oldTLType = typeOwner.getTLTypeObject();
		if (typeNode != null)
			removeAssignedType(); // Remove from current type node user list.

		setTypeNode(target);
		if (target instanceof ImpliedNode) {
			typeOwner.getModelObject().clearTLType();
		} else {
			if (target.isXsdType() && target.getXsdNode() != null) {
				// If the target is an xsd type use the xsd type from xsd node.
				try {
					typeOwner.getModelObject().setTLType(target.getXsdNode().getModelObject());
				} catch (IllegalArgumentException e) {
					// Type could not be assigned. Clear type node.
					setTypeNode(oldTypeNode);
					typeOwner.getModelObject().setTLType(oldTLType);
					return false;
				}
			} else if (typeOwner.isEditable()) {
				// on initial import, xsd nodes are editable allowing them
				// to have their OTM model object typed.
				try {
					typeOwner.getModelObject().setTLType(target.getModelObject());
				} catch (IllegalArgumentException e) {
					// Type could not be assigned. Clear type node.
					setTypeNode(oldTypeNode);
					typeOwner.getModelObject().setTLType(oldTLType);
					return false;
				}
			} else {
				// LOGGER.debug("Can't set type on " + typeOwner + " because it is not editable.");
				setTypeNode(oldTypeNode);
				typeOwner.getModelObject().setTLType(oldTLType);
				return false;
			}
		}
		// Update the where used display node
		if (refresh && OtmRegistry.getNavigatorView() != null)
			OtmRegistry.getNavigatorView().refresh(treeNode);

		// verifyAssignment(); // DEBUG ONLY
		return true;
	}

	/**
	 * Set the base type of the type owner. Links the type owner to target's type user list.
	 * 
	 * @param target
	 *            is the node to be assigned as base type.
	 */
	public void setBaseType(INode target) {
		if (typeOwner.isCoreObject() || typeOwner.isBusinessObject() || typeOwner.isExtensionPointFacet()
				|| typeOwner.isValueWithAttributes()) {
			if ((target == null) || (!target.isTypeProvider())) {
				target = ModelNode.getUnassignedNode();
			}
			// Unlink if base type is already set.
			if ((typeNode != null) && (typeNode.typeUsers() != null)) {
				typeNode.typeUsers().remove(typeOwner);
			}
			// Assign target to the type node and TL model, and add typeOwner to
			// user list.
			if (target != null) {
				target.getTypeUsers().add(typeOwner);
				((Node) target).getTypeClass().baseUsers.add(typeOwner);
				typeNode = (Node) target;
				if (!(target instanceof ImpliedNode))
					typeOwner.getModelObject().setExtendsType(target.getModelObject());
				else
					typeOwner.getModelObject().setExtendsType(null);
			}

			// LOGGER.debug("Set base type of " + typeOwner + " to " + target);
		}
	}

	private boolean isTLTypeAssigned() {
		NamedEntity ownerTLType = typeOwner.getTLTypeObject();
		// Name and library namespace are used to find the matching node. Must not be null.
		boolean ret = true;
		if (ownerTLType == null)
			ret = false;
		else if (ownerTLType.getOwningLibrary() == null)
			ret = false;
		else if (ownerTLType.getLocalName() == null)
			ret = false;
		else
			ret = (!(ownerTLType.getLocalName().equals("Undefined")) || ownerTLType.getLocalName().equals(
					"Unassigned-missingAssignment"));
		// if (!ret)
		// LOGGER.debug("Type owner " + typeOwner + " does not have tl type assigned.");
		return ret;
	}

	private boolean isTypeNodeAssigned() {
		Node type = typeOwner.getTypeClass().getTypeNode();
		if (type == null)
			return false;
		if (type.getLibrary() == null)
			return false;
		return ((type instanceof ImpliedNode)) ? false : true;
	}

	/**
	 * USE ONLY FOR TESTING
	 * 
	 * @return true if the TypeOwner TL_TypeObject matches the typeNode's TL_ModelObject
	 */
	public boolean verifyAssignment() {
		return verifyAssignment(null, null);
	}

	public boolean verifyAssignment(Map<QName, Node> providerMap, QName typeQname) {
		// LOGGER.debug("Verify assignments for type owner: " + typeOwner);
		if (!typeOwner.isTypeUser()) {
			return true;
		}
		if (typeOwner == null) {
			LOGGER.debug("NULL type owner: " + typeOwner);
			return false;
		}
		if (typeNode == null || typeNode.getTLModelObject() == null) {
			LOGGER.debug("Invalid type node: " + typeNode);
			return false;
		}

		// If the TL type is not assigned, then the type node must not be either.
		if (!isTLTypeAssigned()) {
			if (isTypeNodeAssigned()) {
				LOGGER.debug("Invalid: " + typeOwner + " TLType is not assigned " + " but TypeNode is assigned: "
						+ typeOwner.getTypeClass().getTypeNode());
				return false;
			} else
				return true;
		}

		boolean ret = typeOwner.getTLTypeObject().getValidationIdentity()
				.equals(typeNode.getTLModelObject().getValidationIdentity());

		if (!ret)
			if (Node.getModelNode().getDuplicateTypes().contains(typeNode)) {
				LOGGER.debug("Warning, typeNode " + typeNode + " is a duplicate.");
				ret = true;
			} else if ((typeNode instanceof ImpliedNode)
					&& ((ImpliedNode) typeNode).getImpliedType().equals(ImpliedNodeType.String)) {
				LOGGER.debug("Note - owner: " + typeOwner + " has an implied string type.");
				ret = true;
			} else if ((typeNode instanceof ImpliedNode)
					&& ((ImpliedNode) typeNode).getImpliedType().equals(ImpliedNodeType.Union)) {
				LOGGER.debug("Note - owner: " + typeOwner + " has an union type.");
				ret = true;
			} else if (typeNode instanceof AliasNode) {
				// aliases report different validation identities.
				LOGGER.debug("Skipping test of alias: " + typeNode);
				ret = true;
			} else {
				LOGGER.debug("INVALID Assignment to " + typeOwner.getTLModelObject().getValidationIdentity()
						+ " with TL type " + typeOwner.getTLTypeObject().getValidationIdentity());
				LOGGER.debug("    but type node has " + typeNode.getTLModelObject().getValidationIdentity());
				if (providerMap != null) {
					LOGGER.debug("Map results for " + typeQname + " = " + providerMap.get(typeQname));

				}
			}
		// else
		// LOGGER.debug(typeOwner + " is correctly assigned " + typeNode + "? " + ret);
		return ret;
	}

	/**
	 * Search the model node for all users of the type owner. Sets the typeUsers array with the new list.
	 * 
	 * @return typeUsers arrayList of where the the type owner is used as a type (not base baseType)
	 */
	public ArrayList<Node> setTypeUsers() {
		typeUsers = new ArrayList<Node>();
		typeUsers.addAll(getWhereUsed(Node.getModelNode(), true, false));
		// LOGGER.debug(typeUsers.size()+"\tnew typeUsers found for \t"+typeOwner.getName()+".");
		return typeUsers;
	}

	/**
	 * Remove the assigned type. Removes this node from typeNode's list of typeUsers. Sets typeNode = null.
	 * 
	 * Does Change the TL Model. Does Not Change the Model Object. TEST Sets the TL model object type and adds this node
	 * TEST the assigned type's users list.
	 */
	public void removeAssignedType() {
		clearWhereUsed();
		// Set underlying model to unassigned.
		if (typeOwner.isTypeUser())
			typeOwner.getModelObject().setTLType(ModelNode.getUnassignedNode().getModelObject());

		typeNode = null;
	}

	/**
	 * This owner is removed from assigned type's where-used list, but leaves typeNode and tlType assigned.
	 */
	public void clearWhereUsed() {
		if ((typeNode == null) || (typeNode.typeUsers() == null)) {
			LOGGER.debug("Error, tried to remove assigned type but owner was null.");
		} else {
			typeNode.typeUsers().remove(typeOwner);
		}
	}

	/**
	 * Clear users of the this type provider. Sets all users to unassigned. Intended for use in bulk operations as it
	 * does not do children or support limiting scope. Use ReplaceTypeProvider() for general usage.
	 */
	public void clearTypeUsers() {
		Node target = ModelNode.getUnassignedNode();
		if (typeOwner == target)
			return;
		if (typeUsers == null)
			return;

		// Set everyone who uses this as a type to unassigned
		for (Node n : typeUsers) {
			Type nt = n.getTypeClass();
			// nt.setTypeNode(target);
			// If unassigned doesn't know about this, add it
			if (!target.typeUsers().contains(nt.typeOwner))
				target.typeUsers().add(nt.typeOwner);
			nt.typeNode = target;
		}
		typeUsers.clear();

	}

	/**
	 * Clear out the type node and remove all links to and from type users.
	 */
	public void clear() {
		// 6/25/2013 - dmh - have unassigned nodes useful for tracking dependencies
		setTypeNode(null);
		clearTypeUsers();
		// LOGGER.debug("Cleared type class for owner: " + typeOwner);
	}

	/**
	 * Delete the type node and remove all links to and from type users. Unlike {@link clear()}, delete does change the
	 * type assignments to the underlying TL model.
	 */
	public void delete() {
		if (typeNode != null)
			typeNode.typeUsers().remove(typeOwner);
		setTypeNode(null);
		deleteTypeUsers();
		// LOGGER.debug("Cleared type class for owner: " + typeOwner);
	}

	/**
	 * Deletes users of the this type provider. Sets all users to unassigned. Intended for use in bulk operations as it
	 * does not do children or support limiting scope. Use ReplaceTypeProvider() for general usage. This method
	 * <b>does</b> remove TL Model type assignments to this type.
	 */
	public void deleteTypeUsers() {
		if (typeOwner == ModelNode.getUnassignedNode())
			return;
		if (typeUsers == null)
			return;
		// need to create copy,
		// the setTypeNode is breaking existing links (modifies the typeUsers collection)
		ArrayList<Node> typeUsersCopy = new ArrayList<Node>(typeUsers);
		for (Node n : typeUsersCopy) {
			n.getTypeClass().setTypeNode(ModelNode.getUnassignedNode());
			n.getModelObject().clearTLType();
		}
		typeUsers.clear();
	}

}