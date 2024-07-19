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
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.SAXAdapter;
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
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.greenmercury.smax.Balancing;
import org.greenmercury.smax.SmaxDocument;
import org.greenmercury.smax.SmaxElement;
import org.greenmercury.smax.SmaxException;
import org.greenmercury.smax.convert.DomElement;
import org.greenmercury.smax.convert.SAX;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import io.lacuna.bifurcan.IEntry;
import net.sf.saxon.value.AtomicValue;

/**
 * Implementation of
 *   named-entity-recognition($ner-grammar, <treaty id=""/>, $ner-options)
 *     $ner-grammar  as item()?  := (),
 *     $ner-template  as element(),
 *     $options  as map(*)?  := {}
 *   )  as function(node()) as item()
 *
 * Example code (see the documentation in NamedEntityRecognition.java):
 *   import module namespace ner = "http://rakensi.com/exist-db/xquery/functions/ner";
 *
 *   let $grammar :=
 *     <grammar>
 *       <entity id="THE"><name>THE</name></entity>
 *       <entity id="CF"><name>CF</name><name>C F</name></entity>
 *       <entity id="RSVP"><name>rsvp</name><name>R S V P</name></entity>
 *     </grammar>
 *   let $input := ``[c.f. the r.s.v.p.]``
 *
 *   let $ner-parse := ner:named-entity-recognition($grammar, <ntt id=""/>,
 *     map{'case-insensitive-min-length': 3, 'fuzzy-min-length': 3, 'word-chars': ''})
 *   return $ner-parse($input)
 */
public class FnNamedEntityRecognition extends BasicFunction
{

  private static final String FS_NAMED_ENTITY_RECOGNITION_NAME = "named-entity-recognition";

  static final FunctionSignature FS_NAMED_ENTITY_RECOGNITION =
      functionSignature(
          FnNamedEntityRecognition.FS_NAMED_ENTITY_RECOGNITION_NAME,
          "Returns a NER (named entity recognition) parser from a grammar. The parser returns an XML representation of the input string as parsed by the provided grammar.",
          new FunctionReturnSequenceType(Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "A function that can be used to parse an input string."),
          param("grammar", Type.ITEM, "The NER grammar used to generate the parser"),
          param("match-template", Type.ELEMENT, "The template for the XML element that is inserted for each content fragment that matches a named entity. This template must have one empty attribute, which will be filled with the id's of the matched named entities, separated by tab characters."),
          optParam("options", Type.MAP, "The options for the parser genarator and the parser itself")
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
      if (args[0].getItemCount() != 1) {
        throw new XPathException(this, ErrorCodes.FOAP0001, FS_NAMED_ENTITY_RECOGNITION_NAME+": first parameter (grammar) must be exactly 1 a single item; got " + args[0].getItemCount(), args[0]);
      }
      if (!Type.subTypeOf(args[1].itemAt(0).getType(), Type.ELEMENT) || args[1].getItemCount() != 1) {
        throw new XPathException(this, ErrorCodes.XPTY0004, "Item is not a single element; got '" + args[1].itemAt(0) + "'", args[1]);
      }

      final NodeValue matchTemplate = (NodeValue) args[1].itemAt(0);

      Map<String, String> nerOptions;
      if (args[2].isEmpty()) {
        nerOptions = new HashMap<String, String>();
      } else {
        final MapType options = (MapType) args[2].itemAt(0);
        nerOptions = extractNEROptions(options);
      }

      NamedEntityRecognition ner = null;
      try {
        Item grammarItem = args[0].itemAt(0);
        if (Type.subTypeOf(grammarItem.getType(), Type.ANY_URI)) {
          // The grammar is given by a URL.
          URL grammar = new URL(grammarItem.getStringValue());
          ner = new NamedEntityRecognition(grammar, matchTemplate.getNode(), nerOptions, logger);
        } else if (Type.subTypeOf(grammarItem.getType(), Type.ELEMENT)) {
          ner = new NamedEntityRecognition((Element)grammarItem, matchTemplate.getNode(), nerOptions, logger);
        } else {
          // The grammar is given by a string.
          String grammar = grammarItem.getStringValue();
          ner = new NamedEntityRecognition(grammar, matchTemplate.getNode(), nerOptions, logger);
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
          new FunctionReturnSequenceType(Type.ITEM, Cardinality.EXACTLY_ONE, "The result of parsing the input"),
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
      // Create a SMAX document.
      SmaxDocument smaxDocument = null;
      if (Type.subTypeOf(inputParameter.getType(), Type.STRING)) {
        // Wrap the string in an element.
        final String inputString = inputParameter.getStringValue();
        final SmaxElement wrapper = new SmaxElement("wrapper").setStartPos(0).setEndPos(inputString.length());
        smaxDocument = new SmaxDocument(wrapper, inputString);
      } else if (Type.subTypeOf(inputParameter.getType(), Type.NODE)) {
        Node inputNode = ((NodeValue) inputParameter).getNode();
        if (inputNode.getNodeType() == Node.ELEMENT_NODE) {
          Element inputElement = (Element) inputNode;
          try{
            smaxDocument = DomElement.toSmax(inputElement);
          } catch (SmaxException e) {
            throw new XPathException(this, ErrorCodes.ERROR, e);
          }
        }
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
      return outputDocument;
    }

  }

}
