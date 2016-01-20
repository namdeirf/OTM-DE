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
package org.opentravel.schemas.node.resources;

import org.opentravel.schemacompiler.event.ValueChangeEvent;
import org.opentravel.schemacompiler.model.TLAction;
import org.opentravel.schemacompiler.model.TLHttpMethod;
import org.opentravel.schemas.node.Node;
import org.opentravel.schemas.node.listeners.BaseNodeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maintains data for the Action Example URLs. Provides listener to keep example current. Listener should be created
 * with NULL node.
 * 
 * An example: GET http://example.com/basePath/{PathParam}?QueryParam=xxx&Q2=yyy <BO>...</BO>
 * 
 * @author Dave
 *
 */
public class ActionExample {
	private static final Logger LOGGER = LoggerFactory.getLogger(ActionExample.class);

	private final static String system = "http://example.com";
	private TLHttpMethod method;
	private String basePath;
	private String template;
	private String payload = "";
	private String label;
	private TLAction tlAction;
	private String queryTemplate;
	private ActionNode action;

	public ActionExample(ActionNode action) {
		this.action = action;
		this.tlAction = action.getTLModelObject();
		setValues();

		// DON'T set listeners here ... listener call backs are in any order so the examples might update before the
		// data. Set listeners on controllers that instantiate this class.
	}

	protected void setValues() {
		label = tlAction.getActionId();
		queryTemplate = action.getQueryTemplate();
		if (tlAction.getRequest() != null) {
			method = tlAction.getRequest().getHttpMethod();
			template = tlAction.getRequest().getPathTemplate();
			payload = tlAction.getRequest().getPayloadTypeName();
			if (payload == null)
				payload = "";
		}
		if (tlAction.getOwner() != null) {
			basePath = tlAction.getOwner().getBasePath();
			if (basePath != null && basePath.endsWith("/"))
				basePath = basePath.substring(0, basePath.length() - 1);
		}
	}

	public String getPayloadExample() {
		return !payload.isEmpty() ? "<" + payload + ">...</" + payload + ">" : "";
	}

	public String getLabel() {
		label = tlAction.getActionId();
		return label;
	}

	public String getURL() {
		if (method == null)
			return "";
		return method + " " + system + basePath + template + queryTemplate + " " + getPayloadExample();
	}

	@Override
	public String toString() {
		return label + ": " + getURL();
	}

	public class ActionExampleListener extends BaseNodeListener {

		/**
		 * Update example when a field changes. Can't be used to identify node.
		 */
		public ActionExampleListener() {
			super(null);
		}

		private ActionExampleListener(Node node) {
			super(node);
		}

		@Override
		public void processValueChangeEvent(ValueChangeEvent<?, ?> event) {
			super.processValueChangeEvent(event);
			LOGGER.debug("Value change event: " + event.getType() + " on "
					+ event.getSource().getClass().getSimpleName());
			switch (event.getType()) {
			case PARAM_GROUP_ADDED:
			case PARAM_GROUP_MODIFIED:
			case PARAM_GROUP_REMOVED:
			case PARAMETER_ADDED:
			case PARAMETER_REMOVED:
			case PARENT_PARAM_GROUP_MODIFIED:
				// TODO - update path template
			case PAYLOAD_TYPE_MODIFIED:
			case HTTP_METHOD_MODIFIED:
			case FACET_REF_MODIFIED:
			case BASE_PATH_MODIFIED:
				setValues();
				break;
			default:
				break;
			}
		}
	}
}