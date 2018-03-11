package com.github.szil;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRPropertiesUtil;
import net.sf.jasperreports.engine.design.JRCompiler;
import net.sf.jasperreports.engine.design.JRJdtCompiler;
import net.sf.jasperreports.engine.xml.JRReportSaxParserFactory;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.compiler.util.scan.InclusionScanException;
import org.codehaus.plexus.compiler.util.scan.SimpleSourceInclusionScanner;
import org.codehaus.plexus.compiler.util.scan.SourceInclusionScanner;
import org.codehaus.plexus.compiler.util.scan.StaleSourceScanner;
import org.codehaus.plexus.compiler.util.scan.mapping.SourceMapping;
import org.codehaus.plexus.compiler.util.scan.mapping.SuffixMapping;

import java.io.File;
import java.io.IOException; 
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Goal which touches a timestamp file.
 *
 * @goal touch
 * @phase process-sources
 */
@Mojo(name = "compile-jasper", defaultPhase = LifecyclePhase.PROCESS_RESOURCES)
public class JasperCompilerMojo extends AbstractMojo {

    public static final String COMPILER_ERROR_MSG = "Could not compile some report. [%s]";

    /**
     * The compiler that Jasper should use
     * Java compilers:
     *
     * JRJdtCompiler
     * JRJdk13Compiler
     * JRJavacCompiler
     *
     * @see{@link net.sf.jasperreports.engine.design.JRCompiler}
     */
    @Parameter(defaultValue = "net.sf.jasperreports.engine.design.JRJdtCompiler", required = true)
    private String compiler;

    /**
     * Location where the Jasper files will be compiled.
     *
     * Default: jasper
     *
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}/jasper")
    private File outputDirectory;

    /**
     * Location where the Jasper sources files will be searched.
     *
     * Default: jasperreports
     */
    @Parameter(defaultValue = "${basedir}/src/main/resources/jasperreports")
    private File sourceDirectory;

    /**
     * Sources file extension to look for. Default is: .jrxml
     */
    @Parameter(defaultValue = ".jrxml")
    private String sourceFileExtension;

    /**
     * Output file extension of the jasper report files. Default is: .jasper
     */
    @Parameter(defaultValue = ".jasper")
    private String outputFileExtension;

    /**
     * Check the sources files before compiling. Default is true
     */
    @Parameter(defaultValue = "true")
    private boolean xmlValidation;

    /**
     * To enable verbose logging of the compiling task.
     */
    @Parameter(defaultValue = "false")
    private boolean verbose;

    /**
     * The number of threads the compiler plugin will use.
     */
    @Parameter(defaultValue = "4")
    private int numberOfThreads;

    @Parameter(property = "project.compileClasspathElements")
    private List<String> classpathElements;

    /**
     * Use this parameter to add additional properties to the Jasper compiler.
     * For example.
     *
     * <pre>
     * {@code
     * <configuration>
     * 	...
     * 		<additionalProperties>
     * 			<net.sf.jasperreports.awt.ignore.missing.font>true
     *			</net.sf.jasperreports.awt.ignore.missing.font>
     *          <net.sf.jasperreports.default.pdf.font.name>Courier</net.sf.jasperreports.default.pdf.font.name>
     *          <net.sf.jasperreports.default.pdf.encoding>UTF-8</net.sf.jasperreports.default.pdf.encoding>
     *          <net.sf.jasperreports.default.pdf.embedded>true</net.sf.jasperreports.default.pdf.embedded>
     * </additionalProperties>
     * </configuration>
     * }
     * </pre>
     */
    @Parameter
    private Map<String, String> additionalProperties;

    /**
     * Sets if the plugin should check for the sources directory to exists. Default is true
     */
    @Parameter(defaultValue = "true")
    private boolean failOnMissingSourceDirectory;

    /**
     * This is the source inclusion scanner class used, a
     * <code>org.codehaus.plexus.compiler.util.scan.SourceInclusionScanner</code>
     * implementation class.
     */
    @Parameter(defaultValue = "org.codehaus.plexus.compiler.util.scan.StaleSourceScanner")
    private String sourceScanner = StaleSourceScanner.class.getName();

    private Log log;

    public void execute() throws MojoExecutionException {
        log = getLog();

        if (verbose) {
            logConfiguration();
        }

        checkOutDirWritable(outputDirectory);

        SourceMapping mapping = new SuffixMapping(sourceFileExtension, outputFileExtension);
        Set<File> sources = jrxmlFilesToCompile(mapping);
        if (sources.isEmpty()) {
            log.info("Nothing to compile - all Jasper reports are up to date");
        } else {
            log.info("Compiling " + sources.size() + " Jasper reports design files.");

            List<CompileTask> tasks = generateTasks(sources, mapping);
            if (tasks.isEmpty()) {
                log.info("Nothing to compile");
                return;
            }

            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(addClassPathElements(classLoader));

            try {
                configureJasper();
                executeTasks(tasks);
            } finally {
                if (classLoader != null) {
                    Thread.currentThread().setContextClassLoader(classLoader);
                }
            }
        }
    }

    private void logConfiguration() {
        log.info("Jasper compiler configuration:");
        log.info("Jasper report compile will use: " + compiler);
        log.info("Output directory path: " + outputDirectory.getPath());
        log.info("Source directory path: " + sourceDirectory.getPath());
        log.info("Output file extension: " + outputFileExtension);
        log.info("Source file extension: " + sourceFileExtension);
        log.info("XML Validation: " + xmlValidation);
        log.info("Number of threads: " + numberOfThreads);
        log.info("Class path elements: " + Arrays.toString(classpathElements.toArray()));
        log.info("Source scanner: " + sourceScanner);
        log.info("Additional properties: ");
        if (additionalProperties != null) {
            for (Map.Entry<String, String> entry : additionalProperties.entrySet()) {
                log.info("Key: " + entry.getKey());
                log.info("Value: " + entry.getValue());
            }
        }
        log.info("\n");

    }

    private void executeTasks(List<CompileTask> tasks) throws MojoExecutionException {
        try {
            long t1 = System.currentTimeMillis();
            List<Future<Void>> output =
                    Executors.newFixedThreadPool(numberOfThreads).invokeAll(tasks);
            long time = (System.currentTimeMillis() - t1);
            log.info("Generated " + output.size() + " jasper reports in " + (time / 1000.0) + " seconds");
            checkForExceptions(output);
        } catch (InterruptedException e) {
            log.error("Failed to compile Japser reports: Interrupted!", e);
            throw new MojoExecutionException("Error while compiling Jasper reports", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof JRException) {
                throw new MojoExecutionException(COMPILER_ERROR_MSG, e);
            } else {
                throw new MojoExecutionException("Error while compiling Jasper reports", e);
            }
        }
    }

    private void checkForExceptions(List<Future<Void>> output) throws InterruptedException, ExecutionException {
        for (Future<Void> future : output) {
            future.get();
        }
    }

    /**
     * Check if the output directory exist and is writable. If not, try to
     * create an output dir and see if that is writable.
     *
     * @param outputDirectory The dir where the result will be placed
     * @throws MojoExecutionException When the output directory is not writable
     */
    private void checkOutDirWritable(File outputDirectory) throws MojoExecutionException {
        if (!outputDirectory.exists()) {
            checkIfOutputCanBeCreated();
            checkIfOutputDirIsWritable();
            if (verbose) {
                log.info("Output dir check OK");
            }
        } else if (!outputDirectory.canWrite()) {
            throw new MojoExecutionException(
                    "The output dir exists but was not writable. "
                            + "Try running maven with the 'clean' goal.");
        }
    }

    private void checkIfOutputCanBeCreated() throws MojoExecutionException {
        if (!outputDirectory.mkdirs()) {
            throw new MojoExecutionException(this, "Output folder could not be created", "Outputfolder "
                    + outputDirectory.getAbsolutePath() + " is not a folder");
        }
    }

    private void checkIfOutputDirIsWritable() throws MojoExecutionException {
        if (!outputDirectory.canWrite()) {
            throw new MojoExecutionException(this, "Could not write to output folder",
                    "Could not write to output folder: " + outputDirectory.getAbsolutePath());
        }
    }

    private void createDestination(File destinationDirectory) throws MojoExecutionException {
        if (!destinationDirectory.exists()) {
            if (destinationDirectory.mkdirs()) {
                log.debug("Created directory " + destinationDirectory.getName());
            } else {
                throw new MojoExecutionException("Could not create directory " + destinationDirectory.getName());
            }
        }
    }

    private String getRelativePath(String root, File file) throws MojoExecutionException {
        try {
            return file.getCanonicalPath().substring(root.length() + 1);
        } catch (IOException e) {
            throw new MojoExecutionException("Could not getCanonicalPath from file " + file, e);
        }
    }

    /**
     * Determines source files to be compiled.
     *
     * @param mapping The source files
     * @return set of jxml files to compile
     * @throws MojoExecutionException When there's trouble with the input
     */
    protected Set<File> jrxmlFilesToCompile(SourceMapping mapping) throws MojoExecutionException {
        if (!sourceDirectory.isDirectory()) {
            String message = sourceDirectory.getName() + " is not a directory";
            if (failOnMissingSourceDirectory) {
                throw new IllegalArgumentException(message);
            } else {
                log.warn(message + ", skip JasperReports reports compiling.");
                return Collections.emptySet();
            }
        }

        try {
            SourceInclusionScanner scanner = createSourceInclusionScanner();
            scanner.addSourceMapping(mapping);
            return scanner.getIncludedSources(sourceDirectory, outputDirectory);
        } catch (InclusionScanException e) {
            throw new MojoExecutionException("Error scanning source root: \'" + sourceDirectory + "\'.", e);
        }
    }

    private List<CompileTask> generateTasks(Set<File> sources, SourceMapping mapping) throws MojoExecutionException {
        List<CompileTask> tasks = new LinkedList<CompileTask>();
        try {
            String root = sourceDirectory.getCanonicalPath();

            for (File src : sources) {
                String srcName = getRelativePath(root, src);
                try {
                    File destination = mapping.getTargetFiles(outputDirectory, srcName).iterator().next();
                    createDestination(destination.getParentFile());
                    tasks.add(new CompileTask(src, destination, log, verbose));
                } catch (InclusionScanException e) {
                    throw new MojoExecutionException("Error compiling report design : " + src, e);
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Could not getCanonicalPath from source directory " + sourceDirectory, e);
        }
        return tasks;
    }

    private ClassLoader addClassPathElements(ClassLoader classLoader)
            throws MojoExecutionException {
        List<URL> classpath = new ArrayList<URL>();
        if (classpathElements != null) {
            for (String element : classpathElements) {
                try {
                    File f = new File(element);
                    classpath.add(f.toURI().toURL());
                    log.debug("Added to classpath " + element);
                } catch (Exception e) {
                    throw new MojoExecutionException(
                            "Error setting classpath " + element + " " + e.getMessage());
                }
            }
        }

        URL[] urls = classpath.toArray(new URL[classpath.size()]);
        return new URLClassLoader(urls, classLoader);
    }

    private SourceInclusionScanner createSourceInclusionScanner() throws MojoExecutionException {
        if (sourceScanner.equals(StaleSourceScanner.class.getName())) {
            return new StaleSourceScanner();
        } else if (sourceScanner.equals(SimpleSourceInclusionScanner.class.getName())) {
            return new SimpleSourceInclusionScanner(Collections.singleton("**/*" + sourceFileExtension),
                    Collections.<String>emptySet());
        } else {
            throw new MojoExecutionException("sourceScanner not supported: \'" + sourceScanner + "\'.");
        }
    }

    private void configureJasper() {
        DefaultJasperReportsContext jrContext = DefaultJasperReportsContext.getInstance();

        jrContext.setProperty(JRReportSaxParserFactory.COMPILER_XML_VALIDATION, String.valueOf(xmlValidation));
        jrContext.setProperty(JRCompiler.COMPILER_PREFIX, compiler == null ? JRJdtCompiler.class.getName() : compiler);
        jrContext.setProperty(JRCompiler.COMPILER_KEEP_JAVA_FILE, Boolean.FALSE.toString());

        if (additionalProperties != null) {
            configureAdditionalProperties(JRPropertiesUtil.getInstance(jrContext));
        }
    }

    private void configureAdditionalProperties(JRPropertiesUtil propertiesUtil) {
        for (Map.Entry<String, String> additionalProperty : additionalProperties.entrySet()) {
            propertiesUtil.setProperty(additionalProperty.getKey(), additionalProperty.getValue());
        }
    }

}
