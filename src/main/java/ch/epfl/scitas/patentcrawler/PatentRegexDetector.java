package ch.epfl.scitas.patentcrawler;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 *  Patent regex detector to decide whether or not to archive a page/document.
 */

public class PatentRegexDetector {

    Pattern pattern = Pattern.compile("(?i)U\\.*\\s*S\\.*\\s*Pat[(\\.)|(ent)]");

    public Boolean detectPatentMentionIn( String text ) {

	Pattern pattern = Pattern.compile("(?i)U\\.*\\s*S\\.*\\s*Pat[(\\.)|(ent)]");
	Matcher matcher = pattern.matcher(text);
	    
	if (matcher.find()) {
	    return true;
	}
	return false;
    }
}
