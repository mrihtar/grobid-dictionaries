package org.grobid.core.engines;

import org.apache.lucene.util.IOUtils;
import org.grobid.core.document.DictionaryDocument;
import org.grobid.core.document.TEIDictionaryFormatter;
import org.grobid.core.engines.config.GrobidAnalysisConfig;
import org.grobid.core.engines.label.LexicalEntryLabels;
import org.grobid.core.engines.label.TaggingLabel;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.features.FeatureVectorLexicalEntry;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.layout.LayoutTokenization;
import org.grobid.core.tokenization.TaggingTokenCluster;
import org.grobid.core.tokenization.TaggingTokenClusteror;
import org.grobid.core.utilities.LayoutTokensUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.List;

/**
 * Created by med on 18.10.16.
 */
public class LexicalEntryParser extends AbstractParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(LexicalEntryParser.class);
    private static volatile LexicalEntryParser instance;

    public LexicalEntryParser() {
        super(DictionaryModels.LEXICAL_ENTRY);
    }

    public static LexicalEntryParser getInstance() {
        if (instance == null) {
            getNewInstance();
        }
        return instance;
    }

    /**
     * Create a new instance.
     */
    private static synchronized void getNewInstance() {
        instance = new LexicalEntryParser();
    }

    public String process(File originFile) {
        //Prepare
        GrobidAnalysisConfig config = GrobidAnalysisConfig.defaultInstance();
        DictionaryBodySegmentationParser bodySegmentationParser = new DictionaryBodySegmentationParser();
        DictionaryDocument doc = null;
        StringBuffer LexicalEntries = new StringBuffer();
        try {
            doc = bodySegmentationParser.processing(originFile);
            LayoutTokenization layoutTokenization;
            for (List<LayoutToken> allLayoutokensOfALexicalEntry : doc.getLexicalEntries()) {
                LexicalEntries.append("<entry>");
                layoutTokenization = new LayoutTokenization(allLayoutokensOfALexicalEntry);
                String featSeg = FeatureVectorLexicalEntry.createFeaturesFromLayoutTokens(layoutTokenization).toString();
                String labeledFeatures = null;
                // if featSeg is null, it usually means that no body segment is found in the

                if ((featSeg != null) && (featSeg.trim().length() > 0)) {
                    labeledFeatures = label(featSeg);
                    LexicalEntries.append(toTEILexicalEntry(labeledFeatures, layoutTokenization));
                }
                LexicalEntries.append("</entry>");

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        String LEs = new TEIDictionaryFormatter(doc).toTEIFormatLexicalEntry(config, null, LexicalEntries.toString()).toString();
        return LEs;
    }

    public StringBuilder toTEILexicalEntry(String bodyContentFeatured, LayoutTokenization layoutTokenization) {

        StringBuilder buffer = new StringBuilder();
        TaggingLabel lastClusterLabel = null;
        List<LayoutToken> tokenizations = layoutTokenization.getTokenization();

        TaggingTokenClusteror clusteror = new TaggingTokenClusteror(DictionaryModels.LEXICAL_ENTRY, bodyContentFeatured, tokenizations);

        String tokenLabel = null;
        List<TaggingTokenCluster> clusters = clusteror.cluster();


//        System.out.println(new TaggingTokenClusteror(GrobidModels.FULLTEXT, result, tokenizations).cluster());

        for (TaggingTokenCluster cluster : clusters) {
            if (cluster == null) {
                continue;
            }
            TaggingLabel clusterLabel = cluster.getTaggingLabel();
            Engine.getCntManager().i((TaggingLabel) clusterLabel);

            List<LayoutToken> list1 = cluster.concatTokens();
            String str1 = LayoutTokensUtil.toText(list1);
            String clusterContent = LayoutTokensUtil.normalizeText(str1);
            String tagLabel = clusterLabel.getLabel();


            if (tagLabel.equals(LexicalEntryLabels.LEXICAL_ENTRY_FORM_LABEL)) {
                buffer.append(createMyXMLString("form", clusterContent));
            } else if (tagLabel.equals(LexicalEntryLabels.LEXICAL_ENTRY_ETYM_LABEL)) {
                buffer.append(createMyXMLString("etym", clusterContent));
            } else if (tagLabel.equals(LexicalEntryLabels.LEXICAL_ENTRY_SENSE_LABEL)) {
                buffer.append(createMyXMLString("sense", clusterContent));
            } else if (tagLabel.equals(LexicalEntryLabels.LEXICAL_ENTRY_RE_LABEL)) {
                buffer.append(createMyXMLString("re", clusterContent));
            } else if (tagLabel.equals(LexicalEntryLabels.LEXICAL_ENTRY_OTHER_LABEL)) {
                buffer.append(createMyXMLString("other", clusterContent));
            } else {
                throw new IllegalArgumentException(tagLabel + " is not a valid possible tag");
            }


        }

        return buffer;
    }

    public String createMyXMLString(String elementName, String elementContent) {
        StringBuilder xmlStringElement = new StringBuilder();
        xmlStringElement.append("<");
        xmlStringElement.append(elementName);
        xmlStringElement.append(">");
        xmlStringElement.append(elementContent);
        xmlStringElement.append("</");
        xmlStringElement.append(elementName);
        xmlStringElement.append(">");

        return xmlStringElement.toString();
    }

    @SuppressWarnings({"UnusedParameters"})
    public int createTrainingBatch(String inputDirectory, String outputDirectory) throws IOException {
        try {
            File path = new File(inputDirectory);
            if (!path.exists()) {
                throw new GrobidException("Cannot create training data because input directory can not be accessed: " + inputDirectory);
            }

            File pathOut = new File(outputDirectory);
            if (!pathOut.exists()) {
                throw new GrobidException("Cannot create training data because ouput directory can not be accessed: " + outputDirectory);
            }

            // we process all pdf files in the directory

            int n = 0;

            if (path.isDirectory()) {
                for (File fileEntry : path.listFiles()) {
                    String featuresFile = outputDirectory + "/" + fileEntry.getName().substring(0, fileEntry.getName().length() - 4) + ".training.lexicalEntry";
                    Writer writer = new OutputStreamWriter(new FileOutputStream(new File(featuresFile), false), "UTF-8");
                    writer.write(FeatureVectorLexicalEntry.createFeaturesFromPDF(fileEntry).toString());
                    IOUtils.closeWhileHandlingException(writer);
                    n++;
                }

            } else {
                String featuresFile = outputDirectory + "/" + path.getName().substring(0, path.getName().length() - 4) + ".training.lexicalEntry";
                Writer writer = new OutputStreamWriter(new FileOutputStream(new File(featuresFile), false), "UTF-8");
                writer.write(FeatureVectorLexicalEntry.createFeaturesFromPDF(path).toString());
                IOUtils.closeWhileHandlingException(writer);
                n++;

            }


            System.out.println(n + " files to be processed.");

            return n;
        } catch (final Exception exp) {
            throw new GrobidException("An exception occurred while running Grobid batch.", exp);
        }
    }


}
