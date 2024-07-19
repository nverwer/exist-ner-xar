package com.rakensi.existdb.xquery.functions.ner;

import java.util.ArrayList;
import java.util.List;

public abstract class TrieNER
{

  private Logger logger;

  /**
   * Characters that may appear in a word (next to letters and digits).
   * A named entity consists of words, separated by spaces.
   */
  private String wordChars;

  /**
   * Characters that may not immediately follow a word (next to letters and digits).
   * A word can not end immediately before characters in this string.
   */
  private String noWordBefore;

  /**
   * Characters that may not immediately precede a word (next to letters and digits).
   * A word can not start immediately after characters in this string.
   */
  private String noWordAfter;

  /**
   * The trie used for scanning. Only access this via {@code getTrie()} and {@code setTrie()}.
   * Multiple {@code TrieScanner} instances can be used in the same {@code TrieNER},
   * using {@code setTrie()} and {@code getTrie()}.
   */
  private TrieScanner trie;

  /**
   * Constructor for TrieNER.
   * @param wordChars characters that are considered part of a word, next to characters and digits.
   * @param noWordBefore characters in this string may not occur immediately after a match, next to characters and digits.
   * @param noWordAfter characters in this string may not occur immediately before a match, next to characters and digits.
   */
  public TrieNER(String wordChars, String noWordBefore, String noWordAfter, Logger logger) {
    this.wordChars = wordChars;
    this.noWordBefore = noWordBefore;
    this.noWordAfter = noWordAfter;
    this.logger = logger;
  }

  /**
   * Set the trie for this {@code TrieNER}.
   * @param trie The trie to use in this {@code TrieNER}.
   * The {@code trie} should be a trie that is obtained through {@code getTrie()} of this {@code TrieNER}.
   */
  public void setTrie(TrieScanner trie) {
    this.trie = trie;
  }

  /**
   * Get the trie of this {@code TrieNER}.
   * @return the trie used by this {@code TrieNER}.
   */
  public TrieScanner getTrie() {
    if (trie == null) {
      trie = new TrieScanner(wordChars, noWordBefore, logger);
    }
    return trie;
  }

  /**
   * Do this during the scan for matched text.
   * @param text
   * @param start
   * @param end
   * @param id The id or key belonging to the matched text.
   */
  public abstract void match(CharSequence text, int start, int end, List<String> ids);

  /**
   * Do this during the scan for unmatched text.
   * @param text
   * @param start
   * @param end
   */
  public abstract void noMatch(CharSequence text, int start, int end);

  /**
   * Is 'c' a character that may appear in a word?
   * @param c
   * @return
   */
  private boolean isWordChar(char c) {
    return getTrie().isTrieChar(c);
  }

  /**
   * Is 'c' a character that must not immediately precede a word?
   * @param c
   * @return
   */
  private boolean noWordAfter(char c) {
    return Character.isLetterOrDigit(c) || noWordAfter.indexOf(c) >= 0;
  }

  /**
   * Scan a text for substrings matching an entity in the trie.
   * This function will call the functions `match` on matched entities and `noMatch` on unmatched text.
   * @param text The text that will be scanned for entities.
   * @param caseInsensitiveMinLength Matches with at least this length will be done case-insensitive.
   *        Set to -1 to always match case-sensitive. Set to 0 to always match case-insensitive.
   * @param fuzzyMinLength Matches with at least this length may be not exact, i.e. there may be non-trie characters in the match.
   *        Set to -1 to match exact. Set to 0 to match fuzzy.
   */
  public void scan(CharSequence text, int caseInsensitiveMinLength, int fuzzyMinLength) {
    TrieScanner trie = getTrie(); // Make sure the trie is initialized.
    // Internally, we will work with normalized text.
    CharSequence normalizedOneToOneText = StringUtils.normalizeOneToOne(text);
    int start = 0; // Starting position to search in text.
    final int length = text.length();
    StringBuilder unmatched = new StringBuilder(); // Collects unmatched characters, up to the next match.
    int nrResults = 0;
    logger.info("TrieNER.scan start");
    while (start < length) {
      // Set start at the next first letter of a word.
      char c = 0;
      // A word must start with letter, digit or word-character.
      // It cannot start *immediately after* a word-character or a noWordAfter-character.
      while ( start < length &&
              ( !isWordChar(c = normalizedOneToOneText.charAt(start)) ||
                ( start > 0 && noWordAfter(normalizedOneToOneText.charAt(start-1)) )
              )
            ) {
        // c == normalizedText.charAt(start)
        unmatched.append(c);
        ++start;
        // c == normalizedText.charAt(start - 1)
      }
      // Scan for a match, starting at the word beginning at normalizedText[start].
      ArrayList<TrieScanner.ScanResult> results = trie.scan(normalizedOneToOneText, start, caseInsensitiveMinLength >= 0);
      if (results != null) nrResults += results.size();
      /* Determine if the match qualifies:
       * - There is a result.
       * - There may be accented characters, which are taken out in normalizedMatchedText.
       * - If (caseInsensitiveMinLength >= 0) the result-match was case-insensitive,
       *   which is correct if the matched text was long enough,
       *   otherwise there must be a case-sensitive match but no noise characters, so use matchedKey.
       * - The result-match ignores noise, i.e., non-word characters, which are interpreted as whitespace.
       *   If the match is longer than fuzzyMinLength that is correct.
       *   Otherwise, the match must be exact, including noise characters.
       */
      ArrayList<String> matchedIds = new ArrayList<String>();
      int matchedStart = -1;
      int matchedEnd = -1;
      if (results != null) {
        for (TrieScanner.ScanResult result : results) {
          if (matchedStart < 0) {
            matchedStart = result.start;
          } else if (matchedStart != result.start) {
            throw new RuntimeException("Match starts at both "+result.start+" and "+matchedStart);
          }
          if (matchedEnd < 0) {
            matchedEnd = result.end;
          } else if (matchedEnd != result.end) {
            throw new RuntimeException("Match ends at both "+result.end+" and "+matchedEnd);
          }
          String onlyTrieCharsMatched = trie.toTrieCharsNormalizingNonTrieChars(text.subSequence(result.start, result.end));
          String normalizedMatchedText = result.matchedText; // was: normalizedOneToOneText.subSequence(result.start, result.end).toString();
          // Test caseInsensitiveMinLength. If caseInsensitiveMinLength >= 0, match result is case-insensitive, which may not be correct.
          boolean satisfiesCaseInsensitiveMinLength =
            caseInsensitiveMinLength >= 0 && result.end - result.start >= caseInsensitiveMinLength
            ||
            onlyTrieCharsMatched.equals(result.matchedKey);
          // Test fuzzyMinLength. For non-fuzzy matching, insignificant characters become significant.
          boolean satisfiesFuzzyMinLength =
            fuzzyMinLength >= 0 && result.end - result.start >= fuzzyMinLength
            ||
            normalizedMatchedText.equals(result.matchedKey);
          if (satisfiesCaseInsensitiveMinLength && satisfiesFuzzyMinLength) { // This is a correct match.
            if (start == result.end) {
              throw new RuntimeException("No progress matching from '"+text.subSequence(result.start, text.length())+"'");
            }
            // Add ids that are not already present.
            for (String value : result.values) {
              if (!matchedIds.contains(value)) {
                matchedIds.add(value);
              }
            }
          }
        }
      }
      if (matchedIds.size() > 0) {
        // Output the characters before the match.
        unMatched(unmatched, text, start);
        // Process the match.
        match(text, matchedStart, matchedEnd, matchedIds);
        // Continue after the match.
        start = matchedEnd;
      } else if (start < length) { // There is no match and there is more to see.
        unmatched.append(c = text.charAt(start++));
        // Skip over the rest of a word containing letters and digits, but not wordChars.
        if (Character.isLetterOrDigit(c)) {
          while (start < length && Character.isLetterOrDigit(c = text.charAt(start))) {
            unmatched.append(c);
            ++ start;
          }
        }
      }
    } // while (start < length)
    // Output left-over characters.
    unMatched(unmatched, text, length);
    logger.info("TrieNER.scan: "+length+" characters scanned, "+nrResults+" matches");
  }

  /**
   * Output unmatched characters and delete them from `unmatched`.
   * @param sb The characters to output.
   * @param text The string from where these characters come.
   * @param end The index in `text` immediately after the characters to output.
   */
  private void unMatched(StringBuilder sb, CharSequence text, int end) {
    int len = sb.length();
    if (len > 0) {
      noMatch(text, end - len, end);
      sb.delete(0, len);
    }
  }

}
