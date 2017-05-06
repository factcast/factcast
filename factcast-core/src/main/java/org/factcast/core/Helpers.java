package org.factcast.core;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import com.google.common.collect.Lists;

import lombok.NonNull;

/**
 * tiny helpers to keep FactCast interface clean.
 * 
 * @author uwe.schaefer@mercateo.com
 *
 */
class Helpers {

    private Helpers(){}
    
    static List<Fact> toList(@NonNull Fact fact) {
        return Arrays.asList(fact);
    }

    static List<Fact> toList(@NonNull Fact fact, @NonNull MarkFact mark) {
        return toList(Arrays.asList(fact), mark);
    }

    static List<Fact> toList(@NonNull List<Fact> fact, @NonNull MarkFact mark) {
        LinkedList<Fact> newLinkedList = Lists.newLinkedList(fact);
        newLinkedList.add(mark);
        return newLinkedList;
    }

}
