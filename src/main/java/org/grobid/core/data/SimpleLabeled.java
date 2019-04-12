package org.grobid.core.data;

//import org.grobid.core.utilities.Pair;
import org.apache.commons.lang3.tuple.Pair;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by lfoppiano on 05/05/2017.
 */
public class SimpleLabeled {
    private List<Pair<String, String>> labels = new ArrayList<>();

    public boolean addLabel(Pair<String, String> label) {
        return labels.add(label);
    }

    public List<Pair<String, String>> getLabels() {
        return labels;
    }

    public void setLabels(List<Pair<String, String>> labels) {
        this.labels = labels;
    }

}
