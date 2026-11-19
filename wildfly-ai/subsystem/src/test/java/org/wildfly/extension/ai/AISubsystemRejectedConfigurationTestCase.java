/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collection;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.version.Stability;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

@RunWith(Parameterized.class)
public class AISubsystemRejectedConfigurationTestCase extends AbstractSubsystemTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> parameters() {
        return Arrays.asList(new Object[][] {
                { "github", "github-chat-model" },
                { "gemini", "api-key" },
                { "openai", "api-key" },
                { "mistral", "api-key" },
                { "google", "api-key" },
                { "tavily", "api-key" },
        });
    }

    private final String caseName;
    private final String rejectedName;

    public AISubsystemRejectedConfigurationTestCase(String caseName, String rejectedName) {
        super(AISubsystemRegistrar.NAME, new AIExtension(), Stability.EXPERIMENTAL);
        this.caseName = caseName;
        this.rejectedName = rejectedName;
    }

    @Test
    public void rejectsConfiguration() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(readResource("ai-rejected-configurations.xml"))));
        NodeList cases = document.getElementsByTagName("case");
        Element subsystem = null;
        int matches = 0;
        for (int i = 0; i < cases.getLength(); i++) {
            Element testCase = (Element) cases.item(i);
            if (caseName.equals(testCase.getAttribute("name"))) {
                subsystem = (Element) testCase.getElementsByTagNameNS("urn:jboss:domain:ai:experimental:2.0", "subsystem").item(0);
                matches++;
            }
        }
        assertEquals("Expected exactly one fixture for " + caseName, 1, matches);
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter xml = new StringWriter();
        transformer.transform(new DOMSource(subsystem), new StreamResult(xml));
        XMLStreamException exception = assertThrows(XMLStreamException.class, () -> parse(xml.toString()));
        assertTrue(exception.getMessage(), exception.getMessage().contains(rejectedName));
    }
}
