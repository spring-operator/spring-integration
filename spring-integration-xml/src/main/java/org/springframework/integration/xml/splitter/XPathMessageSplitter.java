/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.xml.splitter;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.springframework.integration.splitter.AbstractMessageSplitter;
import org.springframework.integration.util.Function;
import org.springframework.integration.util.FunctionIterator;
import org.springframework.integration.xml.DefaultXmlPayloadConverter;
import org.springframework.integration.xml.XmlPayloadConverter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandlingException;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.util.Assert;
import org.springframework.xml.DocumentBuilderFactoryUtils;
import org.springframework.xml.namespace.SimpleNamespaceContext;
import org.springframework.xml.transform.StringResult;
import org.springframework.xml.transform.TransformerFactoryUtils;
import org.springframework.xml.xpath.XPathException;
import org.springframework.xml.xpath.XPathExpression;
import org.springframework.xml.xpath.XPathExpressionFactory;

/**
 * Message Splitter that uses an {@link XPathExpression} to split a
 * {@link Document}, {@link File} or {@link String} payload into a {@link NodeList}.
 * The return value will be either Strings or {@link Node}s depending on the
 * received payload type. Additionally, node types will be converted to
 * Documents if the 'createDocuments' property is set to <code>true</code>.
 *
 * @author Jonas Partner
 * @author Mark Fisher
 * @author Artem Bilan
 * @author Gary Russell
 */
public class XPathMessageSplitter extends AbstractMessageSplitter {

	private final TransformerFactory transformerFactory;

	private final Object documentBuilderFactoryMonitor = new Object();

	private final XPathExpression xpathExpression;

	private javax.xml.xpath.XPathExpression jaxpExpression;

	private boolean createDocuments;

	private DocumentBuilderFactory documentBuilderFactory;

	private XmlPayloadConverter xmlPayloadConverter = new DefaultXmlPayloadConverter();

	private Properties outputProperties;

	private boolean iterator = true;

	public XPathMessageSplitter(String expression) {
		this(expression, new HashMap<String, String>());
	}

	/**
	 * Construct an instance based on the provided xpath expression and
	 * {@link TransformerFactory}.
	 * @param expression the xpath expression for splitting.
	 * @param transformerFactory the {@link TransformerFactory}
	 * for parsing and building documents.
	 * @since 4.3.19
	 */
	public XPathMessageSplitter(String expression, TransformerFactory transformerFactory) {
		this(expression, new HashMap<String, String>(), transformerFactory);
	}

	public XPathMessageSplitter(String expression, Map<String, String> namespaces) {
		this(expression, namespaces, TransformerFactoryUtils.newInstance());
	}

	/**
	 * Construct an instance based on the provided xpath expression, namespaces and
	 * {@link TransformerFactory}.
	 * @param expression the xpath expression for splitting.
	 * @param namespaces the XML namespace for parsing.
	 * @param transformerFactory the {@link TransformerFactory}
	 * for parsing and building documents.
	 * @since 4.3.19
	 */
	public XPathMessageSplitter(String expression, Map<String, String> namespaces,
			TransformerFactory transformerFactory) {

		this(XPathExpressionFactory.createXPathExpression(expression, namespaces), transformerFactory);

		XPath xpath = XPathFactory.newInstance().newXPath();
		SimpleNamespaceContext namespaceContext = new SimpleNamespaceContext();
		namespaceContext.setBindings(namespaces);
		xpath.setNamespaceContext(namespaceContext);
		try {
			this.jaxpExpression = xpath.compile(expression);
		}
		catch (XPathExpressionException e) {
			throw new org.springframework.xml.xpath.XPathParseException(
					"Could not compile [" + expression + "] to a XPathExpression: " + e.getMessage(), e);
		}
	}

	public XPathMessageSplitter(XPathExpression xpathExpression) {
		this(xpathExpression, TransformerFactoryUtils.newInstance());
	}

	/**
	 * Construct an instance based on the provided xpath expression and
	 * {@link TransformerFactory}.
	 * @param xpathExpression the xpath expression for splitting.
	 * @param transformerFactory the {@link TransformerFactory}
	 * for parsing and building documents.
	 * @since 4.3.19
	 */
	public XPathMessageSplitter(XPathExpression xpathExpression, TransformerFactory transformerFactory) {
		Assert.notNull(xpathExpression, "'xpathExpression' must not be null.");
		Assert.notNull(transformerFactory, "'transformerFactory' must not be null.");
		this.xpathExpression = xpathExpression;
		this.transformerFactory = transformerFactory;
		this.documentBuilderFactory = DocumentBuilderFactoryUtils.newInstance();
		this.documentBuilderFactory.setNamespaceAware(true);
	}

	public void setCreateDocuments(boolean createDocuments) {
		this.createDocuments = createDocuments;
	}

	@Override
	public String getComponentType() {
		return "xml:xpath-splitter";
	}

	public void setDocumentBuilder(DocumentBuilderFactory documentBuilderFactory) {
		Assert.notNull(documentBuilderFactory, "DocumentBuilderFactory must not be null");
		this.documentBuilderFactory = documentBuilderFactory;
	}

	public void setXmlPayloadConverter(XmlPayloadConverter xmlPayloadConverter) {
		Assert.notNull(xmlPayloadConverter, "XmlPayloadConverter must not be null");
		this.xmlPayloadConverter = xmlPayloadConverter;
	}

	/**
	 * The {@code iterator} mode: {@code true} (default) to return an {@link Iterator}
	 * for splitting {@code payload}, {@code false} to return a {@link List}.
	 * @param iterator {@code boolean} flag for iterator mode. Default to {@code true}.
	 * @since 4.2
	 */
	public void setIterator(boolean iterator) {
		this.iterator = iterator;
	}

	/**
	 * A set of output properties that will be
	 * used to override any of the same properties in affect
	 * for the transformation.
	 * @param outputProperties the {@link Transformer} output properties.
	 * @see Transformer#setOutputProperties(Properties)
	 * @since 4.2
	 */
	public void setOutputProperties(Properties outputProperties) {
		this.outputProperties = outputProperties;
	}

	@Override
	protected void doInit() {
		super.doInit();
		if (this.iterator && this.jaxpExpression == null) {
			logger.info("The 'iterator' option isn't available for an external XPathExpression. Will be ignored");
			this.iterator = false;
		}
	}

	@Override
	protected Object splitMessage(Message<?> message) {
		try {
			Object payload = message.getPayload();
			Object result = null;
			if (payload instanceof Node) {
				result = splitNode((Node) payload);
			}
			else {
				Document document = this.xmlPayloadConverter.convertToDocument(payload);
				Assert.notNull(document, "unsupported payload type [" + payload.getClass().getName() + "]");
				result = splitDocument(document);
			}
			return result;
		}
		catch (ParserConfigurationException e) {
			throw new MessageConversionException(message, "failed to create DocumentBuilder", e);
		}
		catch (Exception e) {
			throw new MessageHandlingException(message, "failed to split Message payload", e);
		}
	}

	@SuppressWarnings("unchecked")
	private Object splitDocument(Document document) throws Exception {
		Object nodes = splitNode(document);
		final Transformer transformer;
		synchronized (this.transformerFactory) {
			transformer = this.transformerFactory.newTransformer();
		}
		if (this.outputProperties != null) {
			transformer.setOutputProperties(this.outputProperties);
		}

		if (nodes instanceof List) {
			List<Node> items = (List<Node>) nodes;
			List<String> splitStrings = new ArrayList<String>(items.size());
			for (Node nodeFromList : items) {
				StringResult result = new StringResult();
				transformer.transform(new DOMSource(nodeFromList), result);
				splitStrings.add(result.toString());
			}
			return splitStrings;
		}
		else {
			return new FunctionIterator<Node, String>((Iterator<Node>) nodes, new Function<Node, String>() {

				@Override
				public String apply(Node node) {
					StringResult result = new StringResult();
					try {
						transformer.transform(new DOMSource(node), result);
					}
					catch (TransformerException e) {
						throw new IllegalStateException("failed to create DocumentBuilder", e);
					}
					return result.toString();
				}

			});
		}
	}

	private Object splitNode(Node node) throws ParserConfigurationException {
		if (this.iterator) {
			try {
				NodeList nodeList = (NodeList) this.jaxpExpression.evaluate(node, XPathConstants.NODESET);
				return new NodeListIterator(nodeList);
			}
			catch (XPathExpressionException e) {
				throw new XPathException("Could not evaluate XPath expression:" + e.getMessage(), e);
			}
		}
		else {
			List<Node> nodeList = this.xpathExpression.evaluateAsNodeList(node);
			if (this.createDocuments) {
				return convertNodesToDocuments(nodeList);
			}
			return nodeList;
		}
	}

	private List<Node> convertNodesToDocuments(List<Node> nodes) throws ParserConfigurationException {
		DocumentBuilder documentBuilder = getNewDocumentBuilder();
		List<Node> documents = new ArrayList<Node>(nodes.size());
		for (Node node : nodes) {
			Document document = convertNodeToDocument(documentBuilder, node);
			documents.add(document);
		}
		return documents;
	}

	private Document convertNodeToDocument(DocumentBuilder documentBuilder, Node node) {
		Document document = documentBuilder.newDocument();
		document.appendChild(document.importNode(node, true));
		return document;
	}

	private DocumentBuilder getNewDocumentBuilder() throws ParserConfigurationException {
		synchronized (this.documentBuilderFactoryMonitor) {
			return this.documentBuilderFactory.newDocumentBuilder();
		}
	}

	private final class NodeListIterator implements Iterator<Node> {

		private final DocumentBuilder documentBuilder;

		private final NodeList nodeList;

		private int index;

		NodeListIterator(NodeList nodeList) throws ParserConfigurationException {
			this.nodeList = nodeList;
			if (XPathMessageSplitter.this.createDocuments) {
				this.documentBuilder = getNewDocumentBuilder();
			}
			else {
				this.documentBuilder = null;
			}
		}

		@Override
		public boolean hasNext() {
			return this.index < this.nodeList.getLength();
		}

		@Override
		public Node next() {
			if (!hasNext()) {
				return null;
			}

			Node node = this.nodeList.item(this.index++);
			if (this.documentBuilder != null) {
				node = convertNodeToDocument(this.documentBuilder, node);
			}
			return node;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException("Operation not supported");
		}

	}

}
