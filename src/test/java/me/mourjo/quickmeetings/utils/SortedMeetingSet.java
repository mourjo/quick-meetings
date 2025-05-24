package me.mourjo.quickmeetings.utils;

import java.util.Set;
import java.util.TreeSet;

import me.mourjo.quickmeetings.db.Meeting;

public class SortedMeetingSet {

    public static Set<Meeting> create() {
        return new TreeSet<>((o1, o2) -> {
            if (o1.id() == o2.id()) {
                return 0;
            }
            if (o1.startAt().isEqual(o2.startAt())) {
                return Long.compare(o1.id(), o2.id());
            }
            return o1.startAt().compareTo(o2.startAt());
        });
    }

}
