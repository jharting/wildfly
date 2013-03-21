/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.test.integration.weld.multideployment;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests that CDI beans defined in a bundled library can be used in a deployment
 *
 * @author Jozef Hartinger
 *
 */
@RunWith(Arquillian.class)
public class WeldBundledLibraryDeploymentTestCase {

    private static final String BUNDLED_LIBRARY_NAME = "weld-bundled-library.jar";
    private static final String EXTENSION_NAME = "org.jboss.as.test.integration.weld.multideployment.WeldBundledLibraryDeploymentTestCase";

    private static File libraryRoot;
    private static File bundledLibraryFile;

    @Inject
    private SimpleBean bean;

    @Test
    public void testSimpleBeanInjected() {
        Assert.assertNotNull(bean);
        bean.ping();
    }


    @Deployment
    public static Archive<?> getDeployment() throws Exception {
        doSetup();
        JavaArchive jar = ShrinkWrap.create(JavaArchive.class);
        jar.addClasses(WeldBundledLibraryDeploymentTestCase.class);
        jar.addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
        jar.setManifest(new StringAsset("Extension-List: weld1\nweld1-Extension-Name: " + EXTENSION_NAME + "\n"));
        return jar;
    }

    @AfterClass
    public static void tearDown() throws Exception {
        deleteFile(bundledLibraryFile);
    }

    public static void doSetup() throws Exception {
        libraryRoot = createLibraryRoot();
        bundledLibraryFile = new File(libraryRoot, BUNDLED_LIBRARY_NAME);
        deleteFile(bundledLibraryFile);
        createTestModule(bundledLibraryFile);
    }

    private static void deleteFile(File file) {
        if (file.exists()) {
            file.delete();
        }
    }

    private static void createTestModule(File bundledLibraryFile) throws IOException {
        JavaArchive jar = ShrinkWrap.create(JavaArchive.class, BUNDLED_LIBRARY_NAME);
        jar.addClasses(SimpleBean.class);
        jar.addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
        jar.setManifest(new StringAsset("Extension-Name: " + EXTENSION_NAME + "\n"));
        jar.as(ZipExporter.class).exportTo(bundledLibraryFile, true);
    }

    private static File createLibraryRoot() {
        String jbossHome = System.getProperty("jboss.home", null);
        if (jbossHome == null) {
            throw new IllegalStateException("-Djboss.home not set");
        }
        String standalonePath = jbossHome + File.separatorChar + "standalone";
        File standaloneDir = new File(standalonePath);
        if (!standaloneDir.exists()) {
            throw new IllegalStateException("Determined standalone folder path " + standalonePath + " does not exist");
        }
        if (!standaloneDir.isDirectory()) {
            throw new IllegalStateException("Determined standalone folder path " + standalonePath + " is not a dir");
        }

        String libraryPath = standalonePath + File.separatorChar + "lib" + File.separatorChar + "ext";
        File libraryDir = new File(libraryPath);
        if (libraryDir.exists()) {
            if (!libraryDir.isDirectory()) {
                throw new IllegalStateException("Determined library folder path " + libraryPath + " is not a dir");
            }
        } else {
            libraryDir.mkdirs();
        }
        return libraryDir;
    }
}
