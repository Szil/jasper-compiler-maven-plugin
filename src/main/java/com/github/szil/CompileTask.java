package com.github.szil;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable
 * law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under the License.
 */

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import org.apache.maven.plugin.logging.Log;

import java.io.*;
import java.util.concurrent.Callable;

/**
 * A task that compiles a Jasper source file.
 */
public class CompileTask implements Callable<Void> {

    private final File source;
    private final File destination;
    private final Log log;
    private final boolean verbose;

    /**
     * @param source      The source file.
     * @param destination The destination file.
     * @param log         The logger.
     * @param verbose     If the output should be verbose.
     */
    public CompileTask(File source, File destination, Log log, boolean verbose) {
        this.source = source;
        this.destination = destination;
        this.log = log;
        this.verbose = verbose;
    }

    @Override
    public Void call() throws Exception {
        OutputStream out = null;
        InputStream in = null;
        if (verbose) {
            log.info("Compiling: " + source.getName());
        }
        try {
            out = new FileOutputStream(destination);
            in = new FileInputStream(source);

            JasperCompileManager.compileReportToStream(in, out);
        } catch (Exception e) {
            cleanUpAndThrowError(destination, e);
        } finally {
            closeStream(out);
            closeStream(in);
        }

        return null;
    }

    private void cleanUpAndThrowError(File out, Exception e) throws JRException {
        log.error("Could not compile " + source.getName() + " because " + e.getMessage(), e);
        if (out != null && out.exists()) {
            out.delete();
        }
        throw new JRException("Could not compile " + source.getName(), e);
    }

    private void closeStream(Closeable stream) throws IOException {
        if (stream != null) {
            stream.close();
        }
    }

}
