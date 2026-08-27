package com.mediparse.extraction;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The one normalization rule worth automating here: a lab line that reports
 * a value and a reference range but never spells out NORMAL/HIGH/LOW gets
 * its status derived by comparing the value against the range. Everything
 * else extracted is already in a directly usable shape.
 */
@Component
public class EntityNormalizer {

    private static final Pattern RANGE = Pattern.compile("([\\d.]+)-([\\d.]+)");

    public List<ExtractedEntityDraft> normalize(List<ExtractedEntityDraft> drafts) {
        return drafts.stream().map(this::deriveStatusIfMissing).toList();
    }

    private ExtractedEntityDraft deriveStatusIfMissing(ExtractedEntityDraft draft) {
        if (draft.entityType() != EntityType.LAB_RESULT
                || draft.status() != null
                || draft.numericValue() == null
                || draft.referenceRange() == null) {
            return draft;
        }

        Matcher range = RANGE.matcher(draft.referenceRange());
        if (!range.matches()) {
            return draft;
        }

        BigDecimal low = new BigDecimal(range.group(1));
        BigDecimal high = new BigDecimal(range.group(2));
        BigDecimal value = draft.numericValue();

        ResultStatus derived;
        if (value.compareTo(low) < 0) {
            derived = ResultStatus.LOW;
        } else if (value.compareTo(high) > 0) {
            derived = ResultStatus.HIGH;
        } else {
            derived = ResultStatus.NORMAL;
        }

        return draft.withStatus(derived);
    }
}
