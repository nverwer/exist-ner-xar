package com.rakensi.existdb.xquery.functions.ner;

import static org.exist.xquery.FunctionDSL.functionDefs;

import java.util.List;
import java.util.Map;

import org.exist.dom.QName;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.FunctionDSL;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;

/**
 */
public class ExtensionFunctionsModule extends AbstractInternalModule {

    public static final String NAMESPACE_URI = "http://rakensi.com/xquery/functions/ner";
    public static final String PREFIX = "ner";
    public static final String RELEASED_IN_VERSION = "eXist-6.2.0";

    // register the functions of the module
    public static final FunctionDef[] functions = functionDefs(
        functionDefs(FnNamedEntityRecognition.class, FnNamedEntityRecognition.FS_NAMED_ENTITY_RECOGNITION)
    );

    public ExtensionFunctionsModule(final Map<String, List<? extends Object>> parameters) {
        super(functions, parameters);
    }

    @Override
    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }

    @Override
    public String getDefaultPrefix() {
        return PREFIX;
    }

    @Override
    public String getDescription() {
        return "Named Entity Recognition for eXist-db";
    }

    @Override
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }

    static FunctionSignature functionSignature(final String name, final String description,
            final FunctionReturnSequenceType returnType, final FunctionParameterSequenceType... paramTypes) {
        return FunctionDSL.functionSignature(new QName(name, NAMESPACE_URI), description, returnType, paramTypes);
    }

    static FunctionSignature[] functionSignatures(final String name, final String description,
            final FunctionReturnSequenceType returnType, final FunctionParameterSequenceType[][] variableParamTypes) {
        return FunctionDSL.functionSignatures(new QName(name, NAMESPACE_URI), description, returnType, variableParamTypes);
    }

    static class ErrorCode extends ErrorCodes.ErrorCode {
        public ErrorCode(final String code, final String description) {
            super(new QName(code, NAMESPACE_URI, PREFIX), description);
        }
    }
}
