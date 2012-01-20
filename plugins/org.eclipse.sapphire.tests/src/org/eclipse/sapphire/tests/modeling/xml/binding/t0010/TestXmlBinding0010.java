/*******************************************************************************
 * Copyright (c) 2012 Accenture Services Pvt Ltd. and Oracle
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Kamesh Sampath - initial implementation
 *    Konstantin Komissarchik - initial implementation review and related changes    
 ******************************************************************************/

package org.eclipse.sapphire.tests.modeling.xml.binding.t0010;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.sapphire.modeling.ByteArrayResourceStore;
import org.eclipse.sapphire.modeling.xml.RootXmlResource;
import org.eclipse.sapphire.modeling.xml.XmlResourceStore;
import org.eclipse.sapphire.tests.SapphireTestCase;

/**
 * Tests InitialValueService and InitialValue annotation in the context of XML binding.
 * 
 * @author <a href="mailto:kamesh.sampath@accenture.com">Kamesh Sampath</a>
 */

public final class TestXmlBinding0010 extends SapphireTestCase
{
    private TestXmlBinding0010(final String name) {
        super(name);
    }

    public static Test suite() {
        final TestSuite suite = new TestSuite();

        suite.setName("TestXmlBinding0010");

        suite.addTest(new TestXmlBinding0010("test"));
        suite.addTest(new TestXmlBinding0010("testImpliedProperty"));
        return suite;
    }

    public void test() throws Exception {
        final ByteArrayResourceStore byteArrayResourceStore = new ByteArrayResourceStore(
                loadResourceAsStream("initial.txt"));
        final XmlResourceStore xmlResourceStore = new XmlResourceStore(
                byteArrayResourceStore);

        final TestModelRoot root = TestModelRoot.TYPE
                .instantiate(new RootXmlResource(xmlResourceStore));
        root.resource().save();
        assertEqualsIgnoreNewLineDiffs(loadResource("test-case-result-1.txt"),
                new String(byteArrayResourceStore.getContents(), "UTF-8"));

    }

    public void testImpliedProperty() throws Exception {
        final ByteArrayResourceStore byteArrayResourceStore = new ByteArrayResourceStore();
        final XmlResourceStore xmlResourceStore = new XmlResourceStore(
                byteArrayResourceStore);
        final TestModelRoot root = TestModelRoot.TYPE
                .instantiate(new RootXmlResource(xmlResourceStore));
        root.getProp5();
        root.resource().save();
        assertEqualsIgnoreNewLineDiffs(loadResource("test-case-result-2.txt"),
                new String(byteArrayResourceStore.getContents(), "UTF-8"));

    }

}
