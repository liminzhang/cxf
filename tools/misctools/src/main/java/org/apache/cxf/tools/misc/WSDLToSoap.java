/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cxf.tools.misc;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import org.apache.cxf.common.i18n.Message;
import org.apache.cxf.tools.common.AbstractCXFToolContainer;
import org.apache.cxf.tools.common.ToolConstants;
import org.apache.cxf.tools.common.ToolContext;
import org.apache.cxf.tools.common.ToolException;
import org.apache.cxf.tools.common.toolspec.ToolRunner;
import org.apache.cxf.tools.common.toolspec.ToolSpec;
import org.apache.cxf.tools.common.toolspec.parser.BadUsageException;
import org.apache.cxf.tools.common.toolspec.parser.CommandDocument;
import org.apache.cxf.tools.common.toolspec.parser.ErrorVisitor;
import org.apache.cxf.tools.misc.processor.WSDLToSoapProcessor;

public class WSDLToSoap extends AbstractCXFToolContainer {

    static final String TOOL_NAME = "wsdl2soap";    
    static final String BINDING_NAME_POSFIX = "_Binding";
    static final String STYLE_DEF_VALUE = "document";
    static final String USE_DEF_VALUE = "literal";
    
    private static String[] args;

    public WSDLToSoap(ToolSpec toolspec) throws Exception {
        super(TOOL_NAME, toolspec);
    }
    
    private Set getArrayKeys() {
        return new HashSet<String>();
    }
    
    public void execute(boolean exitOnFinish) {
        WSDLToSoapProcessor processor = new WSDLToSoapProcessor();
        try {
            super.execute(exitOnFinish);
            if (!hasInfoOption()) {
                ToolContext env = new ToolContext();
                env.setParameters(getParametersMap(getArrayKeys()));
                if (isVerboseOn()) {
                    env.put(ToolConstants.CFG_VERBOSE, Boolean.TRUE);
                }
                env.put(ToolConstants.CFG_CMD_ARG, args);

                validate(env);       
                setEnvParamDefValues(env);
                
                processor.setEnvironment(env);
                processor.process();
            }
        } catch (ToolException ex) {
            System.err.println("Error : " + ex.getMessage());
            if (ex.getCause() instanceof BadUsageException) {
                getInstance().printUsageException(TOOL_NAME, (BadUsageException)ex.getCause());
            }
            System.err.println();
            if (isVerboseOn()) {
                ex.printStackTrace();
            }
        } catch (Exception ex) {
            System.err.println("Error : " + ex.getMessage());
            System.err.println();
            if (isVerboseOn()) {
                ex.printStackTrace();
            }
        }
    }

    private void setEnvParamDefValues(ToolContext env) {
        if (!env.optionSet(ToolConstants.CFG_BINDING)) {
            env.put(ToolConstants.CFG_BINDING, env.get(ToolConstants.CFG_PORTTYPE) + BINDING_NAME_POSFIX);
        }
        if (!env.optionSet(ToolConstants.CFG_STYLE)) {
            env.put(ToolConstants.CFG_STYLE, STYLE_DEF_VALUE);
        }
        if (!env.optionSet(ToolConstants.CFG_USE)) {
            env.put(ToolConstants.CFG_USE, USE_DEF_VALUE);
        }        
    }

    private void validate(ToolContext env) throws ToolException {
        String outdir = (String)env.get(ToolConstants.CFG_OUTPUTDIR);
        if (outdir != null) {
            File dir = new File(outdir);
            if (!dir.exists()) {
                Message msg = new Message("DIRECTORY_NOT_EXIST", LOG, outdir);
                throw new ToolException(msg);
            }
            if (!dir.isDirectory()) {
                Message msg = new Message("NOT_A_DIRECTORY", LOG, outdir);
                throw new ToolException(msg);
            }
        }
    }

    public static void main(String[] pargs) {
        args = pargs;
        try {
            ToolRunner.runTool(WSDLToSoap.class,
                               WSDLToSoap.class.getResourceAsStream("wsdl2soap.xml"),
                               false,
                               args);
        } catch (BadUsageException ex) {
            getInstance().printUsageException(TOOL_NAME, ex);
        } catch (Exception ex) {
            System.err.println("Error : " + ex.getMessage());
            System.err.println();
            ex.printStackTrace();
        }
    }

    public void checkParams(ErrorVisitor errors) throws ToolException {
        CommandDocument doc = super.getCommandDocument();

        if (!doc.hasParameter("wsdlurl")) {
            errors.add(new ErrorVisitor.UserError("WSDL/SCHEMA URL has to be specified"));
        }
        if (errors.getErrors().size() > 0) {
            Message msg = new Message("PARAMETER_MISSING", LOG);
            throw new ToolException(msg, new BadUsageException(getUsage(), errors));
        }
    }

}
