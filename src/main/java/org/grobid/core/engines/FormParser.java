package org.grobid.core.engines;

import org.grobid.core.data.SimpleLabeled;
import org.grobid.core.engines.config.GrobidAnalysisConfig;
import org.grobid.core.engines.label.TaggingLabel;
import org.grobid.core.features.FeatureVectorForm;
import org.grobid.core.features.FeaturesUtils;
import org.grobid.core.features.enums.LineStatus;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.tokenization.TaggingTokenCluster;
import org.grobid.core.tokenization.TaggingTokenClusteror;
import org.grobid.core.utilities.LayoutTokensUtil;
import org.grobid.core.utilities.Pair;
import org.grobid.core.utilities.TextUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Created by lfoppiano on 05/05/2017.
 */
public class FormParser extends AbstractParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(FormParser.class);
    private static volatile FormParser instance;

    public FormParser() {
        super(DictionaryModels.FORM);
    }


    public static FormParser getInstance() {
        if (instance == null) {
            getNewInstance();
        }
        return instance;
    }

    private static synchronized void getNewInstance() {
        instance = new FormParser();
    }

    public SimpleLabeled process(List<LayoutToken> formEntry) {

        StringBuilder sb = new StringBuilder();
        String previousFont = null;
        String fontStatus = null;
        String lineStatus = null;

        int counter = 0;
        int nbToken = formEntry.size();
        for (LayoutToken token : formEntry) {
            String text = token.getText();
            text = text.replace(" ", "");

            if (TextUtilities.filterLine(text) || isBlank(text)) {
                counter++;
                continue;
            }
            if (text.equals("\n") || text.equals("\r") || (text.equals("\n\r"))) {
                counter++;
                continue;
            }

            // First token
            if (counter - 1 < 0) {
                lineStatus = LineStatus.LINE_START.toString();
            } else if (counter + 1 == nbToken) {
                // Last token
                lineStatus = LineStatus.LINE_END.toString();
            } else {
                String previousTokenText;
                Boolean previousTokenIsNewLineAfter;
                String nextTokenText;
                Boolean nextTokenIsNewLineAfter;
                Boolean afterNextTokenIsNewLineAfter = false;

                //The existence of the previousToken and nextToken is already check.
                previousTokenText = formEntry.get(counter - 1).getText();
                previousTokenIsNewLineAfter = formEntry.get(counter - 1).isNewLineAfter();
                nextTokenText = formEntry.get(counter + 1).getText();
                nextTokenIsNewLineAfter = formEntry.get(counter + 1).isNewLineAfter();

                // Check the existence of the afterNextToken
                if ((nbToken > counter + 2) && (formEntry.get(counter + 2) != null)) {
                    afterNextTokenIsNewLineAfter = formEntry.get(counter + 2).isNewLineAfter();
                }

                lineStatus = FeaturesUtils.checkLineStatus(text, previousTokenIsNewLineAfter, previousTokenText, nextTokenIsNewLineAfter, nextTokenText, afterNextTokenIsNewLineAfter);

            }
            counter++;

            String[] returnedFont = FeaturesUtils.checkFontStatus(token.getFont(), previousFont);
            previousFont = returnedFont[0];
            fontStatus = returnedFont[1];

            FeatureVectorForm featureVectorForm = FeatureVectorForm.addFeaturesForm(token, "",
                    lineStatus, fontStatus);

            sb.append(featureVectorForm.printVector() + "\n");
        }

        String features = sb.toString();
        String output = label(features);


        SimpleLabeled simpleLabeled = transformResponse(output, formEntry);

        return simpleLabeled;

    }

    public SimpleLabeled transformResponse(String modelOutput, List<LayoutToken> layoutTokens) {
        TaggingTokenClusteror clusteror = new TaggingTokenClusteror(DictionaryModels.FORM,
                modelOutput, layoutTokens);

        List<TaggingTokenCluster> clusters = clusteror.cluster();
        SimpleLabeled simpleLabeled = new SimpleLabeled();

        for (TaggingTokenCluster cluster : clusters) {
            if (cluster == null) {
                continue;
            }
            TaggingLabel clusterLabel = cluster.getTaggingLabel();
            Engine.getCntManager().i((TaggingLabel) clusterLabel);

            List<LayoutToken> concatenatedTokens = cluster.concatTokens();
            String text = LayoutTokensUtil.toText(concatenatedTokens);
            String tagLabel = clusterLabel.getLabel();

            simpleLabeled.addLabel(new Pair(text, tagLabel));
        }

        return simpleLabeled;

    }
}