package org.grobid.trainer.sax;

        import org.grobid.core.engines.label.LexicalEntryLabels;
        import org.grobid.core.utilities.TextUtilities;
        import org.xml.sax.Attributes;
        import org.xml.sax.SAXException;
        import org.xml.sax.helpers.DefaultHandler;

        import java.util.ArrayList;
        import java.util.List;
        import java.util.Stack;
        import java.util.StringTokenizer;

        import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Created by med on 15.11.16.
 */
public class TEIDictionaryBodySegmentationSaxParser extends DefaultHandler {

    private StringBuffer accumulator = null; // current accumulated text

    private String output = null;
    private Stack<String> currentTags = null;
    private String currentTag = null;


    private List<String> labeled = null; // store line by line the labeled data

    public TEIDictionaryBodySegmentationSaxParser() {
        labeled = new ArrayList<String>();
        currentTags = new Stack<String>();
        accumulator = new StringBuffer();
    }

    //Store the text of an element
    public void characters(char[] buffer, int start, int length) {
        accumulator.append(buffer, start, length);
    }

    //Get the text of the document
    public String getText() {
        if (accumulator != null) {
            //System.out.println(accumulator.toString().trim());
            return accumulator.toString().trim();
        } else {
            return null;
        }
    }

    public List<String> getLabeledResult() {
        return labeled;
    }

    public void endElement(java.lang.String uri,
                           java.lang.String localName,
                           java.lang.String qName) throws SAXException {
        if ( (!qName.equals("lb")) && (!qName.equals("pb")) ) {
            writeData(qName, true);
            if (!currentTags.empty()) {
                currentTag = currentTags.peek();
            }
        }

    }

    public void startElement(String namespaceURI,
                             String localName,
                             String qName,
                             Attributes atts)
            throws SAXException {
        if (qName.equals("lb")) {
            accumulator.append(" +L+ ");
        }
        else if (qName.equals("pb")) {
            accumulator.append(" +PAGE+ ");
        }
        else if (qName.equals("space")) {
            accumulator.append(" ");
        }
        else {
            // we have to write first what has been accumulated yet with the upper-level tag
            String text = getText();
            if (!isBlank(text)) {
                writeData(qName, false);

            }
            accumulator.setLength(0);

            currentTags.push("<"+qName+">");
            currentTag = "<"+qName+">";

        }

    }

    private void writeData(String qName, boolean pop) {
        if ( (qName.equals("entry"))               ) {
            if (currentTag == null) {
                return;
            }

            if (pop) {
                if (!currentTags.empty()) {
                    currentTags.pop();
                }
            }

            String text = getText();
            boolean begin = true;
//System.out.println(text);
            // we segment the text line by line first
            //StringTokenizer st = new StringTokenizer(text, "\n", true);
            String[] tokens = text.split("\\+L\\+");
            //while (st.hasMoreTokens()) {
            boolean page = false;
            for(int p=0; p<tokens.length; p++) {
                //String line = st.nextToken().trim();
                String line = tokens[p].trim();
                if (line.equals("\n"))
                    continue;
                if (line.length() == 0)
                    continue;
                if (line.indexOf("+PAGE+") != -1) {
                    // page break should be a distinct feature
                    //labeled.add("@newpage\n");
                    line = line.replace("+PAGE+", "");
                    page = true;
                }

                StringTokenizer st = new StringTokenizer(line, " \t");
                if (!st.hasMoreTokens())
                    continue;
                String tok = st.nextToken();

                //String tok = line.replace(" ", "").replace("\t", "");
                //if (tok.length() > 10)
                //	tok = tok.substring(0,10);

                //StringTokenizer st2 = new StringTokenizer(text, " \t" + TextUtilities.fullPunctuations, true);

                //if (st2.hasMoreTokens()) {
                //String tok = st2.nextToken().trim();
                if (tok.length() == 0) continue;

                //if (tok.equals("+L+")) {
                //    labeled.add("@newline\n");
                //} else

                //if (tok.length() > 0) {
                if (begin) {
                    labeled.add(tok + " I-" + currentTag + "\n");
                    begin = false;
                } else {
                    labeled.add(tok + " " + currentTag + "\n");
                }
                if (page) {
                    labeled.add("@newpage\n");
                    page = false;
                }
                //}
            }
            accumulator.setLength(0);
        }
    }

}