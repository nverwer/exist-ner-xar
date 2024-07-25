package com.rakensi.ner;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.greenmercury.smax.SmaxDocument;
import org.greenmercury.smax.convert.XmlString;
import org.junit.jupiter.api.Test;

import com.rakensi.existdb.xquery.functions.ner.NamedEntityRecognition;
import com.rakensi.existdb.xquery.functions.ner.Logger;

class NamedEntityRecognitionTest
{
  private static final org.junit.platform.commons.logging.Logger junitLogger = org.junit.platform.commons.logging.LoggerFactory.getLogger(NamedEntityRecognitionTest.class);
  private static final Logger logger = new Logger() {
    @Override
    public void info(String message)
    {
      junitLogger.info(() -> message);
    }
    @Override
    public void warning(String message)
    {
      junitLogger.warn(() -> message);
    }
    @Override
    public void error(String message)
    {
      junitLogger.error(() -> message);
    }
  };

  @Test
  void test_FuzzyMinLength() throws Exception
  {
    String grammar =
      "eg1 <- e.g." + "\n" +
      "eg2 <- e g" + "\n" +
      "eg3 <- eg" + "\n" +
      "1 <- A A\tB A" + "\n" +
      "2 <- A B\tB B" + "\n" +
      "3 <- A C\tB C" + "\n";
    Map<String, String> options = new HashMap<String, String>();
    options.put("fuzzy-min-length", "1");
    NamedEntityRecognition ner = new NamedEntityRecognition(grammar, options, logger);
    SmaxDocument document = XmlString.toSmax("<r>A   A  e.g. e g eg  B A B B B C</r>");
    ner.scan(document);
    String output = XmlString.fromSmax(document).replaceAll("<\\?.*?\\?>", "").replaceAll("\\s*xmlns:.+?=\".*?\"", "");
    String expectedOutput = "<r><fn:match id=\"1\">A   A</fn:match>  <fn:match id=\"eg2\">e.g</fn:match>. <fn:match id=\"eg2\">e g</fn:match> <fn:match id=\"eg1&#9;eg3\">eg</fn:match>  <fn:match id=\"1\">B A</fn:match> <fn:match id=\"2\">B B</fn:match> <fn:match id=\"3\">B C</fn:match></r>";
    assertEquals(expectedOutput, output);
  }

  private String grammar_the_cf =
    "THE <- THE" + "\n" +
    "CF <- C F" + "\n" + "\n";

  @Test
  void test_CaseInsensitiveMinLenth_FuzzyMinLength_4() throws Exception
  {
    Map<String, String> options = new HashMap<String, String>();
    options.put("fuzzy-min-length", "4");
    options.put("case-insensitive-min-length", "4");
    NamedEntityRecognition ner = new NamedEntityRecognition(grammar_the_cf, options, logger);
    // test case-insensitive-min-length
    SmaxDocument document = XmlString.toSmax("<r>Do the right thing.</r>");
    ner.scan(document);
    String output = XmlString.fromSmax(document).replaceAll("<\\?.*?\\?>", "").replaceAll("\\s*xmlns:.+?=\".*?\"", "");
    String expectedOutput = "<r>Do the right thing.</r>";
    assertEquals(expectedOutput, output);
    // test fuzzy-min-length
    document = XmlString.toSmax("<r>C.F. Gauss was a German mathematician.</r>");
    ner.scan(document);
    output = XmlString.fromSmax(document).replaceAll("<\\?.*?\\?>", "").replaceAll("\\s*xmlns:.+?=\".*?\"", "");
    expectedOutput = "<r>C.F. Gauss was a German mathematician.</r>";
    assertEquals(expectedOutput, output);
  }

  @Test
  void test_CaseInsensitiveMinLenth_FuzzyMinLength_3() throws Exception
  {
    Map<String, String> options = new HashMap<String, String>();
    options.put("fuzzy-min-length", "3");
    options.put("case-insensitive-min-length", "3");
    NamedEntityRecognition ner = new NamedEntityRecognition(grammar_the_cf, options, logger);
    // test case-insensitive-min-length
    SmaxDocument document = XmlString.toSmax("<r>Do the right thing.</r>");
    ner.scan(document);
    String output = XmlString.fromSmax(document).replaceAll("<\\?.*?\\?>", "").replaceAll("\\s*xmlns:.+?=\".*?\"", "");
    String expectedOutput = "<r>Do <fn:match id=\"THE\">the</fn:match> right thing.</r>";
    assertEquals(expectedOutput, output);
    // test fuzzy-min-length
    document = XmlString.toSmax("<r>C.F. Gauss was a German mathematician.</r>");
    ner.scan(document);
    output = XmlString.fromSmax(document).replaceAll("<\\?.*?\\?>", "").replaceAll("\\s*xmlns:.+?=\".*?\"", "");
    expectedOutput = "<r><fn:match id=\"CF\">C.F</fn:match>. Gauss was a German mathematician.</r>";
    assertEquals(expectedOutput, output);
  }

  @Test
  void test_WordChars() throws Exception
  {
    String grammar =
      "RSVP <- R S V P" + "\n";
    Map<String, String> options = new HashMap<String, String>();
    options.put("fuzzy-min-length", "4");
    options.put("case-insensitive-min-length", "4");
    NamedEntityRecognition ner = new NamedEntityRecognition(grammar, options, logger);
    // test without word-chars
    SmaxDocument document = XmlString.toSmax("<r>Put an r.s.v.p. at the end.</r>");
    ner.scan(document);
    String output = XmlString.fromSmax(document).replaceAll("<\\?.*?\\?>", "").replaceAll("\\s*xmlns:.+?=\".*?\"", "");
    String expectedOutput = "<r>Put an <fn:match id=\"RSVP\">r.s.v.p</fn:match>. at the end.</r>";
    assertEquals(expectedOutput, output);
    // test with word-chars
    options.put("word-chars", ".");
    ner = new NamedEntityRecognition(grammar, options, logger);
    document = XmlString.toSmax("<r>Put an r.s.v.p. at the end.</r>");
    ner.scan(document);
    output = XmlString.fromSmax(document).replaceAll("<\\?.*?\\?>", "").replaceAll("\\s*xmlns:.+?=\".*?\"", "");
    expectedOutput = "<r>Put an r.s.v.p. at the end.</r>";
    assertEquals(expectedOutput, output);
  }
}
