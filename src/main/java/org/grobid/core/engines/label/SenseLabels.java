package org.grobid.core.engines.label;

import org.grobid.core.engines.DictionaryModels;

/**
 * Created by lfoppiano on 05/05/2017.
 */
public class SenseLabels extends TaggingLabels {

    private SenseLabels() {
        super();
    }
    public static final String SUBSENSE_SENSE_LABEL = "<subSense>";
    public static final String NOTE_SENSE_LABEL = "<note>";
    public static final String GRAMMATICAL_GROUP_SENSE_LABEL = "<gramGrp>";
    public static final String DICTSCRAP_SENSE_LABEL = "<dictScrap>";
    public static final String PC_SENSE_LABEL = "<pc>";

    public static final TaggingLabel SENSE_SENSE = new TaggingLabelImpl(DictionaryModels.SENSE, SUBSENSE_SENSE_LABEL);

    public static final TaggingLabel SENSE_NOTE = new TaggingLabelImpl(DictionaryModels.SENSE, NOTE_SENSE_LABEL);

    public static final TaggingLabel SENSE_GRAMMATICAL_GROUP = new TaggingLabelImpl(DictionaryModels.SENSE, GRAMMATICAL_GROUP_SENSE_LABEL);
    public static final TaggingLabel SENSE_DICTSCRAP = new TaggingLabelImpl(DictionaryModels.SENSE, DICTSCRAP_SENSE_LABEL);
    public static final TaggingLabel SENSE_PC = new TaggingLabelImpl(DictionaryModels.SENSE, PC_SENSE_LABEL);



    static {
        register(SENSE_SENSE);

        register(SENSE_NOTE);

        register(SENSE_GRAMMATICAL_GROUP);
        register(SENSE_DICTSCRAP);
        register(SENSE_PC);
    }
    
}
