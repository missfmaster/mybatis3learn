package org.test.xml;

import java.io.InputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class DOM {

	public static void main(String[] args) throws Exception {
		
		DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
		// 开启验证
		documentBuilderFactory.setValidating(false);
		documentBuilderFactory.setNamespaceAware(false);
		documentBuilderFactory.setIgnoringComments(true);
		documentBuilderFactory.setIgnoringElementContentWhitespace(false);
		documentBuilderFactory.setCoalescing(false);
		documentBuilderFactory.setExpandEntityReferences(true);
		// 创建DocumentBuilder
		DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
		builder.setErrorHandler(new ErrorHandler() {

			@Override
			public void warning(SAXParseException exception) throws SAXException {
				System.out.println("warning:" + exception.getMessage());
			}

			@Override
			public void error(SAXParseException exception) throws SAXException {
				System.out.println("error:" + exception.getMessage());
			}

			@Override
			public void fatalError(SAXParseException exception) throws SAXException {
				System.out.println("fatalError:" + exception.getMessage());
			}});
		
		InputStream is = DOM.class.getResourceAsStream("inventory.xml");
		Document doc = builder.parse(is);
		
		XPathFactory factory = XPathFactory.newInstance();
		XPath xpath = factory.newXPath();
		XPathExpression expr = xpath.compile("//book[author='Neal Stephenson']/title/text()");
		Object result = expr.evaluate(doc, XPathConstants.NODESET);
		NodeList nodes = (NodeList) result;
		for(int i = 0; i < nodes.getLength(); i++) {
			System.out.println(nodes.item(i).getNodeValue());
		}
		
		is.close();
	}

}
