package org.grobid.core.engines;
import org.grobid.core.GrobidModel;
import org.grobid.core.GrobidModels;
/**
 * Created by med on 30.11.16.
 */
public class DictionaryModels {

    public static final GrobidModel LEXICAL_ENTRY = GrobidModels.modelFor("lexical-entry");
    public static final GrobidModel DICTIONARY_SEGMENTATION = GrobidModels.modelFor("dictionary-segmentation");
    public static final GrobidModel DICTIONARY_BODY_SEGMENTATION = GrobidModels.modelFor("dictionary-body-segmentation");

}