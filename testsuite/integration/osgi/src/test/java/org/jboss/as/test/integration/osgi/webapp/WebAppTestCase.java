/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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
package org.jboss.as.test.integration.osgi.webapp;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.servlet.Servlet;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;

import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.osgi.web.WebExtension;
import org.jboss.as.test.integration.common.HttpRequest;
import org.jboss.as.test.integration.osgi.api.Echo;
import org.jboss.as.test.integration.osgi.deployment.bundle.DeferredFailActivator;
import org.jboss.as.test.integration.osgi.webapp.bundle.SimpleAnnotatedServlet;
import org.jboss.as.test.integration.osgi.webapp.bundle.SimpleServlet;
import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkUtil;

/**
 * Test webapp deployemnts as OSGi bundles
 *
 * @author thomas.diesler@jboss.com
 *
 * @since 07-Jun-2011
 */
@RunWith(Arquillian.class)
public class WebAppTestCase {

    static final String SIMPLE_WAR = "simple.war";
    static final String BUNDLE_A_WAR = "bundle-a.war";
    static final String BUNDLE_B_WAR = "bundle-b.war";
    static final String BUNDLE_C_WAB = "bundle-c.wab";
    static final String BUNDLE_D_WAB = "bundle-d.wab";
    static final String BUNDLE_E_JAR = "bundle-e.jar";
    static final String BUNDLE_F_WAB = "bundle-f.wab";
    static final String BUNDLE_G_WAR = "bundle-g.war";
    static final String LIB_G_JAR = "lib-g.jar";
    static final String BUNDLE_H_WAR = "bundle-h.war";

    static final Asset STRING_ASSET = new StringAsset("Hello from Resource");

    @ArquillianResource
    Deployer deployer;

    @ArquillianResource
    BundleContext context;

    @ArquillianResource
    ManagementClient managementClient;

    @Deployment
    public static Archive<?> deployment() {
        final JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "osgi-webapp-test");
        jar.addClasses(HttpRequest.class);
        jar.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(jar.getName());
                builder.addBundleManifestVersion(2);
                builder.addImportPackages(ManagementClient.class);
                return builder.openStream();
            }
        });
        return jar;
    }

    @Test
    public void testWarDeployment() throws Exception {
        deployer.deploy(SIMPLE_WAR);
        try {
            String result = performCall("/simple/servlet?input=Hello");
            Assert.assertEquals("null called with: Hello", result);
            // Test resource access
            result = performCall("/simple/message.txt");
            Assert.assertEquals("Hello from Resource", result);
        } finally {
            deployer.undeploy(SIMPLE_WAR);
        }
    }

    @Test
    public void testWarStructureDeployment() throws Exception {
        deployer.deploy(BUNDLE_A_WAR);
        try {
            String result = performCall("/bundle-a/servlet?input=Hello");
            Assert.assertEquals("bundle-a.war called with: Hello", result);
            result = performCall("/bundle-a/message.txt");
            Assert.assertEquals("Hello from Resource", result);
        } finally {
            deployer.undeploy(BUNDLE_A_WAR);
        }
    }

    @Test
    public void testOSGiStructureDeployment() throws Exception {
        deployer.deploy(BUNDLE_B_WAR);
        try {
            String result = performCall("/bundle-b/servlet?input=Hello");
            Assert.assertEquals("bundle-b.war called with: Hello", result);
            result = performCall("/bundle-b/message.txt");
            Assert.assertEquals("Hello from Resource", result);
        } finally {
            deployer.undeploy(BUNDLE_B_WAR);
        }
    }

    @Test
    public void testOSGiStructureDeploymentWithLib() throws Exception {
        deployer.deploy(BUNDLE_G_WAR);
        try {
            String result = performCall("/bundle-g/servlet?input=Hello");
            Assert.assertEquals("bundle-g.war called with: Hello", result);
            result = performCall("/bundle-g/message.txt");
            Assert.assertEquals("Hello from Resource", result);
        } finally {
            deployer.undeploy(BUNDLE_G_WAR);
        }
    }

    @Test
    public void testOSGiStructureDeploymentWithLibAndWebXML() throws Exception {
        deployer.deploy(BUNDLE_H_WAR);
        try {
            String result = performCall("/bundle-h/servlet?input=Hello");
            Assert.assertEquals("bundle-h.war called with: Hello", result);
            result = performCall("/bundle-h/message.txt");
            Assert.assertEquals("Hello from Resource", result);
        } finally {
            deployer.undeploy(BUNDLE_H_WAR);
        }
    }

    @Test
    public void testSimpleBundleWithWabExtension() throws Exception {
        deployer.deploy(BUNDLE_C_WAB);
        try {
            String result = performCall("/bundle-c/servlet?input=Hello");
            Assert.assertEquals("bundle-c.wab called with: Hello", result);
            result = performCall("/bundle-c/message.txt");
            Assert.assertEquals("Hello from Resource", result);
        } finally {
            deployer.undeploy(BUNDLE_C_WAB);
        }
    }


    @Test
    public void testBundleWithWebContextPath() throws Exception {
        deployer.deploy(BUNDLE_D_WAB);
        try {
            Bundle bundle = context.getBundle(BUNDLE_D_WAB);

            String result = performCall("/bundle-d/servlet?input=Hello");
            Assert.assertEquals("bundle-d.wab called with: Hello", result);
            result = performCall("/bundle-d/message.txt");
            Assert.assertEquals("Hello from Resource", result);

            bundle.stop();
            Assert.assertEquals("RESOLVED", Bundle.RESOLVED, bundle.getState());

            try {
                performCall("/bundle-d/servlet?input=Hello");
                Assert.fail("IOException expected");
            } catch (IOException ex) {
                // expected
            }

            try {
                performCall("/bundle-d/message.txt");
                Assert.fail("IOException expected");
            } catch (IOException ex) {
                // expected
            }

            bundle.start();
            Assert.assertEquals("ACTIVE", Bundle.ACTIVE, bundle.getState());

            result = performCall("/bundle-d/servlet?input=Hello");
            Assert.assertEquals("bundle-d.wab called with: Hello", result);
            result = performCall("/bundle-d/message.txt");
            Assert.assertEquals("Hello from Resource", result);
        } finally {
            deployer.undeploy(BUNDLE_D_WAB);
        }
    }

    @Test
    public void testSimpleBundleWithJarExtension() throws Exception {
        deployer.deploy(BUNDLE_E_JAR);
        try {
            String result = performCall("/bundle-e/servlet?input=Hello");
            Assert.assertEquals("bundle-e.jar called with: Hello", result);
            result = performCall("/bundle-e/message.txt");
            Assert.assertEquals("Hello from Resource", result);
        } finally {
            deployer.undeploy(BUNDLE_E_JAR);
        }
    }

    @Test
    public void testDeferredBundleWithWabExtension() throws Exception {
        InputStream input = deployer.getDeployment(BUNDLE_C_WAB);
        Bundle bundle = context.installBundle(BUNDLE_C_WAB, input);
        try {
            Assert.assertEquals("INSTALLED", Bundle.INSTALLED, bundle.getState());
            try {
                performCall("/bundle-c/servlet?input=Hello");
                Assert.fail("IOException expected");
            } catch (IOException ex) {
                // expected
            }
            try {
                performCall("/bundle-c/message.txt");
                Assert.fail("IOException expected");
            } catch (IOException ex) {
                // expected
            }

            bundle.start();
            Assert.assertEquals("ACTIVE", Bundle.ACTIVE, bundle.getState());

            String result = performCall("/bundle-c/servlet?input=Hello");
            Assert.assertEquals("bundle-c.wab called with: Hello", result);
            result = performCall("/bundle-c/message.txt");
            Assert.assertEquals("Hello from Resource", result);
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    @Ignore("[AS7-5653] Cannot restart webapp bundle after activation failure")
    public void testDeferredBundleWithFailure() throws Exception {
        InputStream input = deployer.getDeployment(BUNDLE_F_WAB);
        Bundle bundle = context.installBundle(BUNDLE_F_WAB, input);
        try {
            Assert.assertEquals("INSTALLED", Bundle.INSTALLED, bundle.getState());
            try {
                performCall("/bundle-f/servlet?input=Hello");
                Assert.fail("IOException expected");
            } catch (IOException ex) {
                // expected
            }

            try {
                bundle.start();
                Assert.fail("BundleException expected");
            } catch (BundleException ex) {
                // expected
            }
            Assert.assertEquals("RESOLVED", Bundle.RESOLVED, bundle.getState());
            try {
                performCall("/bundle-f/servlet?input=Hello");
                Assert.fail("IOException expected");
            } catch (IOException ex) {
                // expected
            }

            bundle.start();
            Assert.assertEquals("ACTIVE", Bundle.ACTIVE, bundle.getState());

            String result = performCall("/bundle-f/servlet?input=Hello");
            Assert.assertEquals("bundle-f.wab called with: Hello", result);
            result = performCall("/bundle-f/message.txt");
            Assert.assertEquals("Hello from Resource", result);
        } finally {
            bundle.uninstall();
        }
    }

    private String performCall(String path) throws Exception {
        String urlspec = managementClient.getWebUri() + path;
        return HttpRequest.get(urlspec, 5, TimeUnit.SECONDS);
    }

    @Deployment(name = SIMPLE_WAR, managed = false, testable = false)
    public static Archive<?> getSimpleWar() {
        final WebArchive archive = ShrinkWrap.create(WebArchive.class, SIMPLE_WAR);
        archive.addClasses(SimpleAnnotatedServlet.class, Echo.class);
        archive.addAsWebResource(STRING_ASSET, "message.txt");
        return archive;
    }

    @Deployment(name = BUNDLE_A_WAR, managed = false, testable = false)
    public static Archive<?> getBundleA() {
        final WebArchive archive = ShrinkWrap.create(WebArchive.class, BUNDLE_A_WAR);
        archive.addClasses(SimpleAnnotatedServlet.class, Echo.class);
        archive.addAsWebResource(STRING_ASSET, "message.txt");
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addImportPackages(PostConstruct.class, WebServlet.class);
                builder.addImportPackages(Servlet.class, HttpServlet.class);
                builder.addImportPackages(FrameworkUtil.class);
                builder.addBundleClasspath("WEB-INF/classes");
                return builder.openStream();
            }
        });
        return archive;
    }

    @Deployment(name = BUNDLE_B_WAR, managed = false, testable = false)
    public static Archive<?> getBundleB() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, BUNDLE_B_WAR);
        archive.addClasses(SimpleAnnotatedServlet.class, Echo.class);
        archive.addAsResource(STRING_ASSET, "message.txt");
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addImportPackages(PostConstruct.class, WebServlet.class);
                builder.addImportPackages(Servlet.class, HttpServlet.class);
                builder.addImportPackages(FrameworkUtil.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    @Deployment(name = BUNDLE_C_WAB, managed = false, testable = false)
    public static Archive<?> getBundleC() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, BUNDLE_C_WAB);
        archive.addClasses(SimpleAnnotatedServlet.class, Echo.class);
        archive.addAsResource(STRING_ASSET, "message.txt");
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addImportPackages(PostConstruct.class, WebServlet.class);
                builder.addImportPackages(Servlet.class, HttpServlet.class);
                builder.addImportPackages(FrameworkUtil.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    @Deployment(name = BUNDLE_D_WAB, managed = false, testable = false)
    public static Archive<?> getBundleD() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, BUNDLE_D_WAB);
        archive.addClasses(SimpleAnnotatedServlet.class, Echo.class);
        archive.addAsResource(STRING_ASSET, "message.txt");
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addImportPackages(PostConstruct.class, WebServlet.class);
                builder.addImportPackages(Servlet.class, HttpServlet.class);
                builder.addImportPackages(FrameworkUtil.class);
                builder.addManifestHeader(WebExtension.WEB_CONTEXTPATH, "/bundle-d");
                return builder.openStream();
            }
        });
        return archive;
    }

    @Deployment(name = BUNDLE_E_JAR, managed = false, testable = false)
    public static Archive<?> getBundleE() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, BUNDLE_E_JAR);
        archive.addClasses(SimpleAnnotatedServlet.class, Echo.class);
        archive.addAsResource(STRING_ASSET, "message.txt");
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addImportPackages(PostConstruct.class, WebServlet.class);
                builder.addImportPackages(Servlet.class, HttpServlet.class);
                builder.addImportPackages(FrameworkUtil.class);
                builder.addManifestHeader(WebExtension.WEB_CONTEXTPATH, "/bundle-e");
                return builder.openStream();
            }
        });
        return archive;
    }

    @Deployment(name = BUNDLE_F_WAB, managed = false, testable = false)
    public static Archive<?> getBundleF() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, BUNDLE_F_WAB);
        archive.addClasses(SimpleAnnotatedServlet.class, Echo.class, DeferredFailActivator.class);
        archive.addAsResource(STRING_ASSET, "message.txt");
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addBundleActivator(DeferredFailActivator.class);
                builder.addImportPackages(PostConstruct.class, WebServlet.class);
                builder.addImportPackages(Servlet.class, HttpServlet.class);
                builder.addImportPackages(FrameworkUtil.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    @Deployment(name = BUNDLE_G_WAR, managed = false, testable = false)
    public static Archive<?> getBundleG() {
        final JavaArchive libjar = ShrinkWrap.create(JavaArchive.class, LIB_G_JAR);
        libjar.addClasses(SimpleAnnotatedServlet.class, Echo.class);
        final WebArchive archive = ShrinkWrap.create(WebArchive.class, BUNDLE_G_WAR);
        archive.addAsLibraries(libjar);
        archive.addAsWebResource(STRING_ASSET, "message.txt");
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addImportPackages(PostConstruct.class, WebServlet.class);
                builder.addImportPackages(Servlet.class, HttpServlet.class);
                builder.addImportPackages(FrameworkUtil.class);
                builder.addBundleClasspath("WEB-INF/lib/" + LIB_G_JAR);
                return builder.openStream();
            }
        });
        return archive;
    }

    @Deployment(name = BUNDLE_H_WAR, managed = false, testable = false)
    public static Archive<?> getBundleH() {
        final JavaArchive libjar = ShrinkWrap.create(JavaArchive.class, LIB_G_JAR);
        libjar.addClasses(SimpleServlet.class, Echo.class);
        final WebArchive archive = ShrinkWrap.create(WebArchive.class, BUNDLE_H_WAR);
        archive.addAsLibraries(libjar);
        archive.addAsWebResource(STRING_ASSET, "message.txt");
        archive.setWebXML(new StringAsset("<web-app version='2.5' xmlns='http://java.sun.com/xml/ns/javaee' " +
        		"xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' " +
        		"xsi:schemaLocation='http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd'>" +
        		"<servlet>" +
        		"<servlet-name>appServlet</servlet-name>" +
        		"<servlet-class>" + SimpleServlet.class.getName() + "</servlet-class>" +
        		"<load-on-startup>1</load-on-startup>" +
        		"</servlet>" +
                "<servlet-mapping>" +
                "<servlet-name>appServlet</servlet-name>" +
                "<url-pattern>/servlet</url-pattern>" +
                "</servlet-mapping>" +
        		"</web-app>"));
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addImportPackages(Servlet.class, HttpServlet.class);
                builder.addImportPackages(FrameworkUtil.class);
                builder.addBundleClasspath("WEB-INF/lib/" + LIB_G_JAR);
                return builder.openStream();
            }
        });
        return archive;
    }
}
