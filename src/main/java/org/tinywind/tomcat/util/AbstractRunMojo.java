/**
 * Copyright (c) 2016, Jeon JaeHyeong (http://github.com/tinywind)
 * All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tinywind.tomcat.util;

import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.loader.WebappLoader;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.StandardRoot;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.classworlds.ClassRealm;
import org.codehaus.classworlds.ClassWorld;
import org.codehaus.classworlds.DuplicateRealmException;

import java.io.File;
import java.net.MalformedURLException;
import java.util.List;
import java.util.Set;

/**
 * @author tinywind
 * @since 2016-09-10
 */
public abstract class AbstractRunMojo extends AbstractMojo {
    protected static final String webAppMount = "/WEB-INF/classes";
    /**
     * The Maven project.
     */
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;
    /**
     * Whether to skip the execution of the Maven Plugin for this module.
     */
    @Parameter
    protected boolean skip;
    @Parameter(defaultValue = "8080")
    protected Integer port;
    @Parameter(defaultValue = "/")
    protected String contextPath;
    @Parameter(defaultValue = "${project.basedir}/src/main/webapp")
    protected String baseDir;
    @Parameter(defaultValue = "${project.build.directory}/tomcat")
    protected File configurationDir;

    @Parameter(defaultValue = "${localRepository}", required = true, readonly = true)
    protected ArtifactRepository local;
    @Parameter(defaultValue = "${project.packaging}", required = true, readonly = true)
    protected String packaging;
    @Parameter(defaultValue = "${plugin.artifacts}", required = true)
    protected List<Artifact> pluginArtifacts;
    @Parameter(defaultValue = "${project.artifacts}", required = true, readonly = true)
    protected Set<Artifact> dependencies;

    protected ClassRealm getTomcatClassLoader() throws MojoExecutionException {
        try {
            final ClassWorld world = new ClassWorld();
            final ClassRealm root = world.newRealm("tomcat", Thread.currentThread().getContextClassLoader());
            for (Artifact pluginArtifact : pluginArtifacts)
                if (pluginArtifact.getFile() != null)
                    root.addConstituent(pluginArtifact.getFile().toURI().toURL());
            return root;
        } catch (DuplicateRealmException | MalformedURLException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    protected ClassRealm createWebappClassLoader() throws Exception {
        final ClassWorld world = new ClassWorld();
        final ClassRealm root = world.newRealm("webapp", Thread.currentThread().getContextClassLoader());
        for (Artifact artifact : dependencies)
            if (artifact.getFile() != null)
                root.addConstituent(artifact.getFile().toURI().toURL());
        root.addConstituent(new File(project.getBuild().getOutputDirectory()).toURI().toURL());
        return root;
    }

    protected WebappLoader createWebappLoader() throws Exception {
        return new WebappLoader(createWebappClassLoader().getClassLoader());
    }

    protected void runTomcat() throws Exception {
        final Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(configurationDir.getAbsolutePath());

        final StandardContext context = (StandardContext) tomcat.addWebapp(contextPath, new File(baseDir).getAbsolutePath());

        context.setParentClassLoader(getTomcatClassLoader().getClassLoader());
        context.setLoader(createWebappLoader());

        final WebResourceRoot resources = new StandardRoot(context);
        resources.addPreResources(new DirResourceSet(resources, webAppMount, new File(project.getBuild().getOutputDirectory()).getAbsolutePath(), "/"));
        context.setResources(resources);
        tomcat.setPort(port);

        getLog().info("Start TOMCAT-EMBED");
        tomcat.start();
        tomcat.getServer().await();
    }

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skip TOMCAT-EMBED Maven");
            return;
        }
        final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            runTomcat();
        } catch (Exception e) {
            getLog().info(e.getMessage(), e);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }
}