package com.rakensi.existdb.xquery.functions.ner;

import static com.rakensi.existdb.xquery.functions.ner.ExtensionFunctionsModule.functionSignature;
import static org.exist.xquery.FunctionDSL.optParam;
import static org.exist.xquery.FunctionDSL.param;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.exist.dom.QName;
import org.exist.dom.QName.IllegalQNameException;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.dom.persistent.ElementImpl;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionCall;
import org.exist.xquery.FunctionDSL;
import org.exist.xquery.FunctionFactory;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.greenmercury.smax.SmaxDocument;
import org.greenmercury.smax.SmaxElement;
import org.greenmercury.smax.SmaxException;
import org.greenmercury.smax.convert.DomElement;
import org.greenmercury.smax.convert.SAX;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.rakensi.xml.ner.Logger;
import com.rakensi.xml.ner.NamedEntityRecognition;
import com.rakensi.xml.ner.VerySimpleElementImpl;

/**
 * Implementation of
 *   named-entity-recognition(
 *     $ner-grammar  as item()?  := (),
 *     $options  as map(*)?  := map{}
 *   )  as function($input as item()) as node()*
 *
 * See the documentation in the [XML-NER project](https://github.com/nverwer/XML-NER), and in `NamedEntityRecognition.java` in that project.
 */
public class FnNamedEntityRecognition extends BasicFunction
{

  private static final String FS_NAMED_ENTITY_RECOGNITION_NAME = "named-entity-recognition";

  static final FunctionSignature FS_NAMED_ENTITY_RECOGNITION =
      functionSignature(
          FnNamedEntityRecognition.FS_NAMED_ENTITY_RECOGNITION_NAME,
          "Returns a NER (named entity recognition) matcher from a grammar.",
          new FunctionReturnSequenceType(Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "A function that is used to match an input string or node."),
          param("grammar", Type.ITEM, "The NER grammar used to generate the matcher. This can be a string or an element() or a xs:anyURI."),
          optParam("options", Type.MAP, "The options for the matcher genarator and the matcher itself.")
      );


  private static final org.apache.logging.log4j.Logger apacheLogger = org.apache.logging.log4j.LogManager.getLogger(FnNamedEntityRecognition.class);

  private static final Logger logger = new Logger() {
    @Override
    public void info(String message)
    {
      apacheLogger.info(message);
    }
    @Override
    public void warning(String message)
    {
      apacheLogger.warn(message);
    }
    @Override
    public void error(String message)
    {
      apacheLogger.error(message);
    }
  };


  public FnNamedEntityRecognition(final XQueryContext context, final FunctionSignature signature) {
      super(context, signature);
  }

  @Override
  public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
    // Grammar parameter.
    if (args[0].getItemCount() != 1) {
      throw new XPathException(this, ErrorCodes.FOAP0001, FS_NAMED_ENTITY_RECOGNITION_NAME+": first parameter (grammar) must be exactly 1 a single item; got " + args[0].getItemCount(), args[0]);
    }

    // Options parameter.
    Map<String, String> nerOptions;
    if (args[1].isEmpty()) {
      nerOptions = new HashMap<String, String>();
    } else {
      final MapType options = (MapType) args[1].itemAt(0);
      nerOptions = extractNEROptions(options);
    }

    NamedEntityRecognition ner = null;
    try {
      Item grammarItem = args[0].itemAt(0);
      if (Type.subTypeOf(grammarItem.getType(), Type.ANY_URI)) {
        // The grammar is given by a URL.
        URL grammar = new URL(grammarItem.getStringValue());
        ner = new NamedEntityRecognition(grammar, nerOptions, logger);
      } else if (Type.subTypeOf(grammarItem.getType(), Type.ELEMENT)) {
        ner = new NamedEntityRecognition((Element)grammarItem, nerOptions, logger);
      } else {
        // The grammar is given by a string.
        String grammar = grammarItem.getStringValue();
        ner = new NamedEntityRecognition(grammar, nerOptions, logger);
      }
    }
    catch (Exception e)
    {
      throw new XPathException(this, ErrorCodes.ERROR, "The NER parser could not be instantiated.", e);
    }

    // Make a NerParser function. The signature is function(xs:item) as item()+
    FunctionSignature parserSignature = FunctionDSL.functionSignature(
        new QName("generated-ner-parser", "https://greenmercury.org/"),
        "Generated NER parser, only used internally",
        new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "The result of parsing the input"),
        param("input", Type.ITEM, "The input")
    );
    final NerParser nerParser = new NerParser(context, parserSignature, ner);
    // Make a function reference that can be used as the result.
    FunctionCall functionCall = FunctionFactory.wrap(context, nerParser);
    return new FunctionReference(functionCall);
  }

  /**
   * @param options The options as an XQuery map.
   * @return The parsed options.
   * @throws NumberFormatException
   * @throws XPathException
   */
  private Map<String, String> extractNEROptions(final MapType options) throws NumberFormatException, XPathException
  {
    Map<String, String> nerOptions = new HashMap<String, String>();
    options.iterator().forEachRemaining(entry -> {
      try {
        nerOptions.put(entry.key().getStringValue(), entry.value().itemAt(0).getStringValue());
      } catch (XPathException e){}
    });
    if (options.size() != nerOptions.size()) {
      throw new XPathException(this, ErrorCodes.ERROR, "Reading options failed!");
    }
    return nerOptions;
  }


  /**
   * A BasicFunction for the generated NER parser.
   */
  private static final class NerParser extends BasicFunction {

    private NamedEntityRecognition ner;

    public NerParser(XQueryContext context, FunctionSignature signature, NamedEntityRecognition ner) throws XPathException
    {
        super(context, signature);
        this.ner = ner;
        // We must set the arguments, which is not done automatically from the signature.
        final List<Expression> ixmlParserArgs = new ArrayList<>(1);
        ixmlParserArgs.add(new Function.Placeholder(context));
        this.setArguments(ixmlParserArgs);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
    {
      Item inputParameter = args[0].itemAt(0);
      // Create a SMAX document with a <wrapper> root element that will be removed later.
      SmaxDocument smaxDocument = null;
      if (Type.subTypeOf(inputParameter.getType(), Type.STRING)) {
        // Wrap the string in an element.
        final String inputString = inputParameter.getStringValue();
        final SmaxElement wrapper = new SmaxElement("wrapper").setStartPos(0).setEndPos(inputString.length());
        smaxDocument = new SmaxDocument(wrapper, inputString);
      } else if (Type.subTypeOf(inputParameter.getType(), Type.NODE)) {
        Node inputNode = ((NodeValue) inputParameter).getNode();
        if (inputNode instanceof Document || inputNode instanceof DocumentFragment) {
          inputNode = inputNode.getFirstChild();
        }
        Element inputElement = wrap(inputNode);
        try{
          smaxDocument = DomElement.toSmax(inputElement);
        } catch (SmaxException e) {
          throw new XPathException(this, ErrorCodes.ERROR, e);
        }
      } else {
        throw new XPathException(this, ErrorCodes.ERROR, "The generated NER function accepts a string or node, but not a "+Type.getTypeName(inputParameter.getType()));
      }
      // Do Named Entity Recognition on the SMAX document.
      this.ner.scan(smaxDocument);
      // Convert the SMAX document to something that eXist-db can use.
      SAXAdapter saxAdapter = new SAXAdapter();
      try {
        SAX.fromSMAX(smaxDocument, saxAdapter);
      } catch (SAXException e) {
        throw new XPathException(this, ErrorCodes.ERROR, e);
      }
      DocumentImpl outputDocument = saxAdapter.getDocument();
      //ElementImpl outputElement = new ElementImpl(outputDocument, 0);
      //return outputDocument;
      final NodeList children = outputDocument.getDocumentElement().getChildNodes();
      if(children.getLength() == 0) {
          return Sequence.EMPTY_SEQUENCE;
      }
      final ValueSequence result = new ValueSequence(children.getLength());
      for(int i = 0; i < children.getLength(); i++) {
        NodeValue child = (NodeValue)children.item(i);
        result.add(child);
      }
      return result;
    }

    /**
     * The org.exist.dom.memtree.Element does not implement appendChild(), and org.exist.dom.persistent.ElementImpl does not want the owner element of `node`.
     * Therefore, we have to make our own wrapper element, which needs to work for org.greenmercury.smax.convert.DomElement.toSmax(Element).
     * @param node A node that must be wrapped in a "wrapper" element.
     * @return The wrapper element.
     */
    private Element wrap(Node node)
    {
      Element wrapper = new VerySimpleElementImpl("wrapper");
      wrapper.appendChild(node);
      return wrapper;
    }

  }

}
