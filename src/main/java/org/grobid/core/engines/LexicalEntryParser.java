package org.grobid.core.engines;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.util.IOUtils;
import org.grobid.core.data.LabeledLexicalInformation;
import org.grobid.core.document.DictionaryDocument;
import org.grobid.core.document.DocumentUtils;

import org.grobid.core.engines.label.DictionaryBodySegmentationLabels;
import org.grobid.core.engines.label.DictionarySegmentationLabels;
import org.grobid.core.engines.label.LexicalEntryLabels;
import org.grobid.core.engines.label.TaggingLabel;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.features.FeatureVectorLexicalEntry;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.layout.LayoutTokenization;
import org.grobid.core.tokenization.TaggingTokenCluster;
import org.grobid.core.tokenization.TaggingTokenClusteror;
import org.grobid.core.utilities.LayoutTokensUtil;
import org.grobid.core.utilities.Pair;
import org.grobid.core.utilities.TextUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

import java.util.List;

import static org.grobid.core.document.TEIDictionaryFormatter.createMyXMLString;
import static org.grobid.core.engines.label.DictionaryBodySegmentationLabels.DICTIONARY_ENTRY_LABEL;
import static org.grobid.core.engines.label.LexicalEntryLabels.LEXICAL_ENTRY_SENSE_LABEL;
import static org.grobid.service.DictionaryPaths.PATH_LEXICAL_ENTRY;

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

    private static synchronized void getNewInstance() {
        instance = new LexicalEntryParser();
    }

    public String processToTei(List<LayoutToken> entry, String modelToRun) {
        StringBuilder bodyWithSegmentedLexicalEntries = new StringBuilder();


        // Get the clustors of token in the LE
        LabeledLexicalInformation labeledEntry = process(entry, DICTIONARY_ENTRY_LABEL);


        //According the request, either show the text of the lexical entry or process its components

        if (modelToRun.equals(PATH_LEXICAL_ENTRY)) {
            //In the simple case, just return segmentation of the LE
            for (Pair<List<LayoutToken>, String> entryComponent : labeledEntry.getLabels()) {
                bodyWithSegmentedLexicalEntries.append(toTEILexicalEntry(entryComponent));
            }
        } else {
            //In the complete case, parse the component of the LE
            for (Pair<List<LayoutToken>, String> entryComponent : labeledEntry.getLabels()) {
                bodyWithSegmentedLexicalEntries.append(toTEILexicalEntryAndBeyond(entryComponent));
            }


        }

        return bodyWithSegmentedLexicalEntries.toString();
    }

    public DictionaryDocument initiateProcess(File originFile) {
        //Prepare
        DictionaryBodySegmentationParser bodySegmentationParser = new DictionaryBodySegmentationParser();
        DictionaryDocument doc = null;
        try {
            doc = bodySegmentationParser.processing(originFile);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return doc;
    }


    public LabeledLexicalInformation process(List<LayoutToken> entry, String parentTag) {
        LabeledLexicalInformation labeledLexicalEntry = new LabeledLexicalInformation();

        LayoutTokenization layoutTokenization = new LayoutTokenization(entry);

        String featSeg = FeatureVectorLexicalEntry.createFeaturesFromLayoutTokens(layoutTokenization.getTokenization(), DICTIONARY_ENTRY_LABEL).toString();

        if (StringUtils.isNotBlank(featSeg)) {
            // Run the lexical entry model to label the features
            String modelOutput = label(featSeg);
            TaggingTokenClusteror clusteror = new TaggingTokenClusteror(DictionaryModels.LEXICAL_ENTRY, modelOutput, entry);

            List<TaggingTokenCluster> clusters = clusteror.cluster();

            for (TaggingTokenCluster cluster : clusters) {
                if (cluster == null) {
                    continue;
                }
                TaggingLabel clusterLabel = cluster.getTaggingLabel();
                Engine.getCntManager().i((TaggingLabel) clusterLabel);

                List<LayoutToken> concatenatedTokens = cluster.concatTokens();
                String tagLabel = clusterLabel.getLabel();

                labeledLexicalEntry.addLabel(new Pair(concatenatedTokens, tagLabel));
            }
        }


        return labeledLexicalEntry;
    }


    public String toTEILexicalEntry(Pair<List<LayoutToken>, String> entry) {
        final StringBuilder sb = new StringBuilder();

        String token = LayoutTokensUtil.normalizeText(LayoutTokensUtil.toText(entry.getA()));
        String label = entry.getB();
        produceXmlNode(sb, token, label);

        return sb.toString();
    }

    public String toTEILexicalEntryAndBeyond(Pair<List<LayoutToken>, String> entryComponent) {
        final StringBuilder sb = new StringBuilder();
        String token = LayoutTokensUtil.normalizeText(LayoutTokensUtil.toText(entryComponent.getA()));
        String label = entryComponent.getB();

        if (label.equals("<form>")) {
            sb.append(new FormParser().processToTEI(entryComponent.getA()));


        } else if (label.equals("<sense>")) {
            sb.append(new SenseParser().processToTEI(entryComponent.getA()));

//            } else if (label.equals("<re>")) {
//                //I apply the same model recursively on the relative entry
//                sb.append("<re>").append("\n");
//                //I apply the form also to the sense to recognise the grammatical group, if any!
//                LabeledLexicalEntry labeledEntries = new LexicalEntryParser().process(entry.getA(), LEXICAL_ENTRY_RE_LABEL);
//                for (Pair<List<LayoutToken>, String> lexicalEntry : labeledEntries.getLabels()) {
//                    String tokenForm = LayoutTokensUtil.normalizeText(LayoutTokensUtil.toText(lexicalEntry.getA()));
//                    String labelForm = lexicalEntry.getB();
//
//                    String content = TextUtilities.HTMLEncode(tokenForm);
//                    content = content.replace("&lt;lb/&gt;", "<lb/>");
//                    if (!labelForm.equals("<dictScrap>")) {
//                        sb.append(createMyXMLString(labelForm.replaceAll("[<>]", ""), content));
//                    } else {
//                        sb.append(content);
//                    }
//                }
//                sb.append("</re>").append("\n");

        } else {
            produceXmlNode(sb, token, label);
        }

        return sb.toString();
    }


    public StringBuilder toTEILexicalEntry(String bodyContentFeatured, List<LayoutToken> layoutTokens,
                                           boolean isTrainingData) {
        StringBuilder buffer = new StringBuilder();

        TaggingTokenClusteror clusteror = new TaggingTokenClusteror(DictionaryModels.LEXICAL_ENTRY,
                bodyContentFeatured, layoutTokens);

        List<TaggingTokenCluster> clusters = clusteror.cluster();

        for (TaggingTokenCluster cluster : clusters) {
            if (cluster == null) {
                continue;
            }
            TaggingLabel clusterLabel = cluster.getTaggingLabel();
            Engine.getCntManager().i((TaggingLabel) clusterLabel);

            List<LayoutToken> list1 = cluster.concatTokens();
            String str1 = LayoutTokensUtil.toText(list1);
            String clusterContent;
            if (isTrainingData) {
                clusterContent = DocumentUtils.replaceLinebreaksWithTags(str1);
            } else {
                clusterContent = LayoutTokensUtil.normalizeText(str1);
            }

            String tagLabel = clusterLabel.getLabel();


            produceXmlNode(buffer, clusterContent, tagLabel);
        }

        return buffer;
    }

    private void produceXmlNode(StringBuilder buffer, String clusterContent, String tagLabel) {
        if (tagLabel.equals(DictionaryBodySegmentationLabels.DICTIONARY_ENTRY_LABEL)) {
            clusterContent = TextUtilities.HTMLEncode(clusterContent);
            clusterContent = clusterContent.replace("&lt;lb/&gt;", "<lb/>");
            buffer.append(createMyXMLString("entry", clusterContent));
        } else if (tagLabel.equals(DictionaryBodySegmentationLabels.DICTIONARY_DICTSCRAP_LABEL)) {
            clusterContent = TextUtilities.HTMLEncode(clusterContent);
            clusterContent = clusterContent.replace("&lt;lb/&gt;", "<lb/>");
            buffer.append(createMyXMLString("dictScarp", clusterContent));
        } else if (tagLabel.equals(DictionaryBodySegmentationLabels.PUNCTUATION_LABEL)) {
            clusterContent = TextUtilities.HTMLEncode(clusterContent);
            clusterContent = clusterContent.replace("&lt;lb/&gt;", "<lb/>");
            buffer.append(createMyXMLString("pc", clusterContent));
        } else if (tagLabel.equals(LexicalEntryLabels.LEXICAL_ENTRY_FORM_LABEL)) {
            clusterContent = TextUtilities.HTMLEncode(clusterContent);
            clusterContent = clusterContent.replace("&lt;lb/&gt;", "<lb/>");
            buffer.append(createMyXMLString("form", clusterContent));
        } else if (tagLabel.equals(LexicalEntryLabels.LEXICAL_ENTRY_ETYM_LABEL)) {
            clusterContent = TextUtilities.HTMLEncode(clusterContent);
            clusterContent = clusterContent.replace("&lt;lb/&gt;", "<lb/>");
            buffer.append(createMyXMLString("etym", clusterContent));
        } else if (tagLabel.equals(LEXICAL_ENTRY_SENSE_LABEL)) {
            clusterContent = TextUtilities.HTMLEncode(clusterContent);
            clusterContent = clusterContent.replace("&lt;lb/&gt;", "<lb/>");
            buffer.append(createMyXMLString("sense", clusterContent));
        } else if (tagLabel.equals(LexicalEntryLabels.LEXICAL_ENTRY_RE_LABEL)) {
            clusterContent = TextUtilities.HTMLEncode(clusterContent);
            clusterContent = clusterContent.replace("&lt;lb/&gt;", "<lb/>");
            buffer.append(createMyXMLString("re", clusterContent));
        } else if (tagLabel.equals(LexicalEntryLabels.LEXICAL_ENTRY_OTHER_LABEL)) {
            clusterContent = TextUtilities.HTMLEncode(clusterContent);
            clusterContent = clusterContent.replace("&lt;lb/&gt;", "<lb/>");
            buffer.append(createMyXMLString("dictScrap", clusterContent));
        } else if (tagLabel.equals(LexicalEntryLabels.LEXICAL_ENTRY_PC_LABEL)) {
            clusterContent = TextUtilities.HTMLEncode(clusterContent);
            clusterContent = clusterContent.replace("&lt;lb/&gt;", "<lb/>");
            buffer.append(createMyXMLString("pc", clusterContent));
        } else {
            throw new IllegalArgumentException(tagLabel + " is not a valid possible tag");
        }
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

            int n = 0;

            // we process all pdf files in the directory
            if (path.isDirectory()) {
                for (File fileEntry : path.listFiles()) {
                    // Create the pre-annotated file and the raw text
                    createTrainingLexicalEntries(fileEntry, outputDirectory,false);
                    n++;
                }

            } else {
                createTrainingLexicalEntries(path, outputDirectory,false);
                n++;

            }


            System.out.println(n + " files to be processed.");
            return n;
        } catch (final Exception exp) {
            throw new GrobidException("An exception occurred while running Grobid batch.", exp);
        }
    }

    @SuppressWarnings({"UnusedParameters"})
    public int createAnnotatedTrainingBatch(String inputDirectory, String outputDirectory) throws IOException {
        try {
            File path = new File(inputDirectory);
            if (!path.exists()) {
                throw new GrobidException("Cannot create training data because input directory can not be accessed: " + inputDirectory);
            }

            File pathOut = new File(outputDirectory);
            if (!pathOut.exists()) {
                throw new GrobidException("Cannot create training data because ouput directory can not be accessed: " + outputDirectory);
            }

            int n = 0;
            // we process all pdf files in the directory
            if (path.isDirectory()) {
                for (File fileEntry : path.listFiles()) {
                    // Create the pre-annotated file and the raw text
                    createTrainingLexicalEntries(fileEntry, outputDirectory,true);
                    n++;
                }

            } else {
                createTrainingLexicalEntries(path, outputDirectory,true);
                n++;

            }


            System.out.println(n + " files to be processed.");
            return n;
        } catch (final Exception exp) {
            throw new GrobidException("An exception occurred while running Grobid batch.", exp);
        }
    }

    public void createTrainingLexicalEntries(File path, String outputDirectory, Boolean isAnnotated) throws Exception {
        // Calling previous cascading model 
        DictionaryBodySegmentationParser bodySegmentationParser = new DictionaryBodySegmentationParser();
        DictionaryDocument doc = bodySegmentationParser.processing(path);

        //Writing feature file
        String featuresFile = outputDirectory + "/" + path.getName().substring(0, path.getName().length() - 4) + ".training.lexicalEntry";
        Writer featureWriter = new OutputStreamWriter(new FileOutputStream(new File(featuresFile), false), "UTF-8");
        //Create rng and css files for guiding the annotation
        File existingRngFile = new File("resources/templates/lexicalEntry.rng");
        File newRngFile = new File(outputDirectory + "/" +"lexicalEntry.rng");
        copyFileUsingStream(existingRngFile,newRngFile);

        File existingCssFile = new File("resources/templates/lexicalEntry.css");
        File newCssFile = new File(outputDirectory + "/" +"lexicalEntry.css");
//        Files.copy(Gui.getClass().getResourceAsStream("resources/templates/lexicalEntry.css"), Paths.get("new_project","css","lexicalEntry.css"))
        copyFileUsingStream(existingCssFile,newCssFile);

        StringBuffer rawtxt = new StringBuffer();

        StringBuffer lexicalEntries = new StringBuffer();
        for (Pair<List<LayoutToken>, String> lexicalEntryLayoutTokens : doc.getBodyComponents().getLabels()) {

            if (lexicalEntryLayoutTokens.getB().equals(DictionaryBodySegmentationLabels.DICTIONARY_ENTRY_LABEL)) {
                for (LayoutToken txtline : lexicalEntryLayoutTokens.getA()) {
                    rawtxt.append(txtline.getText());
                }
                lexicalEntries.append("<entry>");
                LayoutTokenization layoutTokenization = new LayoutTokenization(lexicalEntryLayoutTokens.getA());
                if(isAnnotated){
                    String featSeg = FeatureVectorLexicalEntry.createFeaturesFromLayoutTokens(layoutTokenization.getTokenization()).toString();
                    String labeledFeatures = null;
                    // if featSeg is null, it usually means that no body segment is found in the

                    if ((featSeg != null) && (featSeg.trim().length() > 0)) {
                        featureWriter.write(featSeg + "\n");

                        labeledFeatures = label(featSeg);
                        lexicalEntries.append(toTEILexicalEntry(labeledFeatures, layoutTokenization.getTokenization(), true));
                    }
                }
                else {
                    lexicalEntries.append(DocumentUtils.replaceLinebreaksWithTags(LayoutTokensUtil.toText(lexicalEntryLayoutTokens.getA())));
                }


                lexicalEntries.append("</entry>");
            }



        }

        //Writing RAW file (only text)
        String outPathRawtext = outputDirectory + "/" + path.getName().substring(0, path.getName().length() - 4) + ".training.lexicalEntry.rawtxt";
        FileUtils.writeStringToFile(new File(outPathRawtext), rawtxt.toString(), "UTF-8");


        // write the TEI file
        String outTei = outputDirectory + "/" + path.getName().substring(0, path.getName().length() - 4) + ".training.lexicalEntry.tei.xml";
        Writer teiWriter = new OutputStreamWriter(new FileOutputStream(new File(outTei), false), "UTF-8");
        teiWriter.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<?xml-model href=\"lexicalEntry.rng\" type=\"application/xml\" schematypens=\"http://relaxng.org/ns/structure/1.0\"\n" +
                "?>\n" + "<?xml-stylesheet type=\"text/css\" href=\"lexicalEntry.css\"?>\n"+
                "<tei xml:space=\"preserve\">\n\t<teiHeader>\n\t\t<fileDesc xml:id=\"" +
                "\"/>\n\t</teiHeader>\n\t<text>");

        teiWriter.write("\n\t\t<body>");
        teiWriter.write(lexicalEntries.toString());
        teiWriter.write("</body>");

        teiWriter.write("\n\t</text>\n</tei>\n");


        IOUtils.closeWhileHandlingException(featureWriter, teiWriter);
    }

    private static void copyFileUsingStream(File source, File dest) throws IOException {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new FileInputStream(source);
            os = new FileOutputStream(dest);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        } finally {
            is.close();
            os.close();
        }
    }




}
